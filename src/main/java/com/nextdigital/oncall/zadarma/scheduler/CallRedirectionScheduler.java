package com.nextdigital.oncall.zadarma.scheduler;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.config.ZadarmaConfig;
import com.nextdigital.oncall.entity.Shift;
import com.nextdigital.oncall.repository.ShiftRepository;
import com.nextdigital.oncall.zadarma.event.ZadarmaRedirectFailedEvent;
import com.nextdigital.oncall.zadarma.event.ZadarmaRedirectSuccessEvent;
import com.nextdigital.oncall.zadarma.service.ZadarmaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Event-driven scheduler that ensures Zadarma call redirection is always
 * pointed
 * at the currently active on-call person for each group.
 *
 * <h3>How it works</h3>
 * <ul>
 * <li><b>Startup recovery:</b> On {@link ApplicationReadyEvent}, queries all
 * future/active shifts from DB and schedules their transitions.</li>
 * <li><b>Event-driven:</b> Shift start → redirect to the person. Shift end →
 * redirect to next shift or disable.</li>
 * <li><b>Fallback checker:</b> Every 5 minutes, verifies all active shifts
 * have correct redirection and fixes any drift.</li>
 * <li><b>Retry on failure:</b> If a redirect fails, it retries with
 * increasing delay up to {@code retryMaxAttempts}.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallRedirectionScheduler {

    private final TaskScheduler taskScheduler;
    private final ZadarmaService zadarmaService;
    private final ShiftRepository shiftRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ZadarmaConfig config;

    /**
     * Self-reference to ensure {@link #redirectToOnCallPerson} is called
     * through the Spring AOP proxy so that {@code @Transactional} is applied.
     * Required because {@link TaskScheduler#schedule} lambdas capture
     * {@code this} (the raw instance), bypassing the proxy.
     */
    @Lazy
    @Autowired
    private CallRedirectionScheduler self;

    /**
     * Maps shiftId → {startTask, endTask} futures for cancellation on
     * update/delete.
     */
    private final Map<UUID, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> endTasks = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────
    // Public API (called by ShiftServiceImpl)
    // ──────────────────────────────────────────────

    /**
     * Schedules redirection transitions for a shift.
     * Called after shift creation or update.
     */
    public void scheduleShiftTransitions(Shift shift) {
        cancelShiftTransitions(shift.getId()); // Clean up any old schedules

        OffsetDateTime now = OffsetDateTime.now();

        // Schedule the START transition
        if (shift.getStartTime().isAfter(now)) {
            ScheduledFuture<?> startFuture = taskScheduler.schedule(
                    () -> redirectToOnCallPerson(shift.getId()),
                    shift.getStartTime().toInstant());
            scheduledTasks.put(shift.getId(), startFuture);
            log.debug("Scheduled start redirect for shift {} at {}", shift.getId(), shift.getStartTime());
        } else if (isActive(shift, now)) {
            // Shift is already active → schedule redirect with a short delay
            // to avoid blocking the caller (e.g. during startup recovery)
            log.debug("Shift {} is already active — scheduling redirect in 2s", shift.getId());
            ScheduledFuture<?> startFuture = taskScheduler.schedule(
                    () -> self.redirectToOnCallPerson(shift.getId()),
                    Instant.now().plusSeconds(2));
            scheduledTasks.put(shift.getId(), startFuture);
        }

        // Schedule the END transition
        if (shift.getEndTime().isAfter(now)) {
            ScheduledFuture<?> endFuture = taskScheduler.schedule(
                    () -> handleShiftEnd(shift.getId()),
                    shift.getEndTime().toInstant());
            endTasks.put(shift.getId(), endFuture);
            log.debug("Scheduled end transition for shift {} at {}", shift.getId(), shift.getEndTime());
        }
    }

    /**
     * Cancels all scheduled transitions for a shift.
     * Called before shift update or on shift delete.
     */
    public void cancelShiftTransitions(UUID shiftId) {
        cancelFuture(scheduledTasks.remove(shiftId));
        cancelFuture(endTasks.remove(shiftId));
    }

    /**
     * Triggers an immediate redirection check for the currently active shift
     * in the given group. Called after deleting an active shift.
     */
    public void handleActiveShiftRemoved(UUID groupId) {
        OffsetDateTime now = OffsetDateTime.now();
        var activeShifts = shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                groupId, now);
        if (!activeShifts.isEmpty()) {
            var current = activeShifts.getFirst();
            if (isActive(current, now)) {
                redirectToOnCallPerson(current.getId());
            }
        } else {
            log.info("No active shifts for group {} — keeping last redirection active", groupId);
        }
    }

    // ──────────────────────────────────────────────
    // Redirection logic
    // ──────────────────────────────────────────────

    /**
     * Redirects calls to the person assigned to the given shift.
     * If it fails, marks the shift as failed and schedules a retry.
     */
    @Transactional
    public void redirectToOnCallPerson(UUID shiftId) {
        var shiftOpt = shiftRepository.findById(shiftId);
        if (shiftOpt.isEmpty()) {
            log.debug("Shift {} no longer exists — skipping redirect", shiftId);
            return;
        }

        var shift = shiftOpt.get();
        OffsetDateTime now = OffsetDateTime.now();

        // If shift has already ended, don't redirect
        if (!shift.getEndTime().isAfter(now)) {
            log.debug("Shift {} has ended — skipping redirect", shiftId);
            return;
        }

        // If the group doesn't use Zadarma (no sipId), skip silently
        String sipId = shift.getOnCallGroup().getZadarmaSipId();
        if (sipId == null || sipId.isBlank()) {
            log.debug("Group '{}' has no zadarmaSipId — skipping redirect for shift {}",
                    shift.getOnCallGroup().getName(), shiftId);
            return;
        }

        boolean success = zadarmaService.redirectToPerson(
                shift.getOnCallGroup().getId(), shift.getPerson().getId());

        shift.setRedirectLastAttemptAt(now);
        if (success) {
            shift.setRedirectStatus("ok");
            shift.setRedirectRetryCount(0);
            shift.setRedirectFailedAt(null);
            log.info("Redirect OK: shift {} → group '{}' → person '{}'",
                    shiftId, shift.getOnCallGroup().getName(), shift.getPerson().getName());

            eventPublisher.publishEvent(new ZadarmaRedirectSuccessEvent(
                    shift.getId(),
                    shift.getOnCallGroup().getId(),
                    shift.getOnCallGroup().getName(),
                    shift.getPerson().getId(),
                    shift.getPerson().getName(),
                    shift.getPerson().getPhoneNumber(),
                    shift.getStartTime(),
                    shift.getEndTime()));
        } else {
            handleRedirectFailure(shift);
        }
        shiftRepository.save(shift);
    }

    /**
     * Handles a shift ending. The redirection is NOT changed here — the next
     * shift's own start handler ({@link #redirectToOnCallPerson}) will take care
     * of updating the forwarding to the new on-call person.
     */
    @Transactional
    public void handleShiftEnd(UUID shiftId) {
        var shiftOpt = shiftRepository.findById(shiftId);
        if (shiftOpt.isEmpty()) {
            return;
        }

        var shift = shiftOpt.get();
        log.info("Shift {} ended (person '{}' in group '{}')",
                shiftId, shift.getPerson().getName(), shift.getOnCallGroup().getName());
    }

    // ──────────────────────────────────────────────
    // Failure handling
    // ──────────────────────────────────────────────

    private void handleRedirectFailure(Shift shift) {
        int retryCount = shift.getRedirectRetryCount();
        int maxRetries = config.getRedirect().getRetryMaxAttempts();
        int initialDelay = config.getRedirect().getRetryInitialDelaySeconds();

        shift.setRedirectStatus("failed");
        shift.setRedirectFailedAt(OffsetDateTime.now());
        shift.setRedirectRetryCount(retryCount + 1);

        log.warn("Redirect FAILED for shift {} (attempt {}/{})",
                shift.getId(), retryCount + 1, maxRetries);

        // Publish event for Telegram alerting
        eventPublisher.publishEvent(new ZadarmaRedirectFailedEvent(
                shift.getId(),
                shift.getOnCallGroup().getId(),
                shift.getOnCallGroup().getName(),
                shift.getPerson().getId(),
                shift.getPerson().getName(),
                shift.getPerson().getPhoneNumber(),
                "Zadarma API returned error or was unreachable",
                retryCount + 1,
                maxRetries,
                OffsetDateTime.now()));

        // Schedule retry with increasing delay.
        // Use self (proxy) to ensure @Transactional is applied.
        if (retryCount < maxRetries) {
            long delaySeconds = (long) initialDelay * (retryCount + 1);
            log.info("Scheduling retry for shift {} in {} seconds", shift.getId(), delaySeconds);
            taskScheduler.schedule(
                    () -> self.redirectToOnCallPerson(shift.getId()),
                    Instant.now().plusSeconds(delaySeconds));
        } else {
            log.error("Shift {} redirect FAILED after {} attempts — will be retried by fallback checker only",
                    shift.getId(), maxRetries);
        }
    }

    // ──────────────────────────────────────────────
    // Startup recovery
    // ──────────────────────────────────────────────

    /**
     * On application startup, reschedule all pending transitions from the database.
     * This handles server restarts gracefully.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void onStartup() {
        log.info("Recovering scheduled shift transitions on startup...");
        OffsetDateTime now = OffsetDateTime.now();

        var allFutureShifts = shiftRepository.findAllActiveAndFuture(now);
        int activeCount = 0;
        int futureCount = 0;

        for (var shift : allFutureShifts) {
            if (isActive(shift, now)) {
                activeCount++;
            } else {
                futureCount++;
            }
            scheduleShiftTransitions(shift);
        }

        log.info("Startup recovery complete: {} active shifts (redirecting now), {} future shifts scheduled",
                activeCount, futureCount);
    }

    // ──────────────────────────────────────────────
    // Periodic fallback checker
    // ──────────────────────────────────────────────

    /**
     * Every N minutes, verify all currently active shifts have the correct
     * Zadarma redirection. Fixes any drift caused by external changes or
     * missed transitions.
     */
    @Scheduled(fixedDelayString = "#{@zadarmaConfig.redirect.fallbackCheckIntervalSeconds * 1000}")
    @Transactional
    public void fallbackCheck() {
        OffsetDateTime now = OffsetDateTime.now();
        var activeShifts = shiftRepository.findCurrentlyActive(now);

        if (activeShifts.isEmpty()) {
            return;
        }

        log.debug("Fallback check: verifying {} active shifts", activeShifts.size());

        for (var shift : activeShifts) {
            // Skip groups that don't use Zadarma
            String sipId = shift.getOnCallGroup().getZadarmaSipId();
            if (sipId == null || sipId.isBlank()) {
                continue;
            }

            try {
                var currentDest = zadarmaService.getCurrentRedirectionDestination(
                        shift.getOnCallGroup().getId());
                String expectedPhone = zadarmaService.normalizePhoneNumber(
                        shift.getPerson().getPhoneNumber());

                boolean needsUpdate = currentDest.isEmpty()
                        || (expectedPhone != null && !expectedPhone.equals(currentDest.get()));

                // Also retry shifts marked as failed
                if ("failed".equals(shift.getRedirectStatus())) {
                    needsUpdate = true;
                }

                if (needsUpdate) {
                    log.info("Fallback: fixing redirection for shift {} (group '{}', person '{}')",
                            shift.getId(), shift.getOnCallGroup().getName(), shift.getPerson().getName());
                    self.redirectToOnCallPerson(shift.getId());
                }
            } catch (Exception e) {
                log.warn("Fallback check error for shift {}: {}", shift.getId(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────

    private static boolean isActive(Shift shift, OffsetDateTime now) {
        return !shift.getStartTime().isAfter(now) && shift.getEndTime().isAfter(now);
    }

    private static void cancelFuture(ScheduledFuture<?> future) {
        if (future != null && !future.isCancelled() && !future.isDone()) {
            future.cancel(false);
        }
    }
}
