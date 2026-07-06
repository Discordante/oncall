package com.nextdigital.oncall.zadarma.service;

import java.util.Optional;
import java.util.UUID;

import com.nextdigital.oncall.zadarma.dto.SipInfoResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaStatsResponse;

/**
 * Service for managing Zadarma VoIP call redirections and analytics queries.
 */
public interface ZadarmaService {

    // ──────────────────────────────────────────────
    // Call redirection operations
    // ──────────────────────────────────────────────

    /**
     * Redirects the specified group's Zadarma SIP to the given person's phone.
     *
     * @param groupId  the on-call group ID
     * @param personId the on-call person ID
     * @return true if the redirection was successful, false if it failed
     */
    boolean redirectToPerson(UUID groupId, UUID personId);

    /**
     * Disables call redirection for the group (no active on-call person).
     *
     * @param groupId the on-call group ID
     * @return true if disabling was successful, false if it failed or not
     *         applicable
     */
    boolean disableRedirection(UUID groupId);

    /**
     * Gets the current redirection destination phone number for a group.
     *
     * @param groupId the on-call group ID
     * @return the current destination phone number, or empty if not set or not
     *         available
     */
    Optional<String> getCurrentRedirectionDestination(UUID groupId);

    /**
     * Normalizes a raw phone number to international format without "+" prefix.
     * Strips spaces, dashes, parentheses, and the leading "+".
     *
     * @param raw the raw phone number string
     * @return normalized phone number (digits only), or null if input is null/blank
     */
    String normalizePhoneNumber(String raw);

    // ──────────────────────────────────────────────
    // Read-only analytics queries
    // ──────────────────────────────────────────────

    /**
     * Returns the list of SIP IDs associated with the Zadarma account,
     * enriched with the on-call group name if the SIP is assigned to a group.
     *
     * @return list of {@link SipInfoResponse} with sipId and optional groupName
     */
    java.util.List<SipInfoResponse> getSipList();

    /**
     * Returns the raw incoming-call statistics from Zadarma for the given period.
     *
     * @param start start of the period ({@code YYYY-MM-DD HH:MM:SS}), optional
     * @param end   end of the period ({@code YYYY-MM-DD HH:MM:SS}), optional
     * @param sip   optional SIP number filter
     * @return the full Zadarma API response with status, timeframe and call records
     */
    ZadarmaStatsResponse getCallsSummary(String start, String end, String sip);
}
