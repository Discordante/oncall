package com.nextdigital.oncall.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.nextdigital.oncall.dto.IncidentRequest;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.Incident;
import com.nextdigital.oncall.entity.ZadarmaCall;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.ApplicationRepository;
import com.nextdigital.oncall.repository.IncidentRepository;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.ZadarmaCallRepository;

@ExtendWith(MockitoExtension.class)
class IncidentServiceImplTest {

    @Mock
    IncidentRepository incidentRepository;
    @Mock
    ApplicationRepository applicationRepository;
    @Mock
    OnCallGroupRepository groupRepository;
    @Mock
    ZadarmaCallRepository callRepository;

    @InjectMocks
    IncidentServiceImpl service;

    private Application application;
    private ZadarmaCall call1; // Oct 5
    private ZadarmaCall call2; // Oct 3 — earlier
    private Incident incident;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id(UUID.randomUUID()).name("Test App").description("desc").build();

        call1 = ZadarmaCall.builder()
                .id(UUID.randomUUID())
                .externalCallId("ext-1").sipId("921495")
                .callStartedAt(OffsetDateTime.parse("2026-10-05T10:00:00Z"))
                .billSeconds(60).disposition("answered")
                .build();

        call2 = ZadarmaCall.builder()
                .id(UUID.randomUUID())
                .externalCallId("ext-2").sipId("921495")
                .callStartedAt(OffsetDateTime.parse("2026-10-03T09:00:00Z"))
                .billSeconds(30).disposition("answered")
                .build();

        incident = Incident.builder()
                .id(UUID.randomUUID()).title("Test incident")
                .application(application)
                .calls(new java.util.HashSet<>())
                .build();
    }

    @Test
    void create_withNoApplication_isAllowed() {
        var request = new IncidentRequest("Test incident", null, null, false, null, null, null, null);

        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        assertThat(result.applicationId()).isNull();
        assertThat(result.applicationName()).isNull();
        verifyNoInteractions(applicationRepository);
    }

    @Test
    void create_withNoCalls_usesManualStartedAt() {
        var startedAt = OffsetDateTime.parse("2026-10-05T09:00:00Z");
        var request = new IncidentRequest("Test incident", application.getId(), null, false, startedAt, null, null, null);

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        assertThat(result.startedAt()).isEqualTo(startedAt);
        assertThat(result.linkedCallsCount()).isZero();
    }

    @Test
    void create_withNoCalls_andNoManualStartedAt_startedAtIsNull() {
        var request = new IncidentRequest("Test incident", application.getId(), null, false, null, null, null, null);

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        assertThat(result.startedAt()).isNull();
    }

    @Test
    void create_withCalls_setsStartedAtFromEarliestCall() {
        var request = new IncidentRequest("Test incident", application.getId(), null, false, null, null,
                List.of(call1.getId(), call2.getId()), null);

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(callRepository.findAllById(List.of(call1.getId(), call2.getId())))
                .thenReturn(List.of(call1, call2));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        // call2 is earlier (Oct 3 vs Oct 5)
        assertThat(result.startedAt()).isEqualTo(call2.getCallStartedAt());
        assertThat(result.linkedCallsCount()).isEqualTo(2);
    }

    @Test
    void create_withCalls_ignoresManualStartedAt() {
        var manualDate = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        var request = new IncidentRequest("Test", application.getId(), null, false, manualDate, null,
                List.of(call1.getId()), null);

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(callRepository.findAllById(List.of(call1.getId()))).thenReturn(List.of(call1));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        // auto wins over manual
        assertThat(result.startedAt()).isEqualTo(call1.getCallStartedAt());
    }

    @Test
    void linkCall_updatesStartedAtToEarlierCall() {
        incident.getCalls().add(call1);
        incident.setStartedAt(call1.getCallStartedAt());

        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(callRepository.findById(call2.getId())).thenReturn(Optional.of(call2));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.linkCall(incident.getId(), call2.getId());

        assertThat(result.startedAt()).isEqualTo(call2.getCallStartedAt());
        assertThat(result.linkedCallsCount()).isEqualTo(2);
    }

    @Test
    void unlinkCall_recalculatesStartedAt_toRemainingCall() {
        var calls = new java.util.HashSet<>(List.of(call1, call2));
        incident.setCalls(calls);
        incident.setStartedAt(call2.getCallStartedAt());

        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(callRepository.findById(call2.getId())).thenReturn(Optional.of(call2));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.unlinkCall(incident.getId(), call2.getId());

        assertThat(incident.getStartedAt()).isEqualTo(call1.getCallStartedAt());
    }

    @Test
    void unlinkLastCall_setsStartedAtToNull() {
        incident.getCalls().add(call1);
        incident.setStartedAt(call1.getCallStartedAt());

        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(callRepository.findById(call1.getId())).thenReturn(Optional.of(call1));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.unlinkCall(incident.getId(), call1.getId());

        assertThat(incident.getStartedAt()).isNull();
    }

    @Test
    void durationSeconds_isNullWhenEndedAtMissing() {
        var request = new IncidentRequest("Test", application.getId(), null, false,
                OffsetDateTime.parse("2026-10-05T10:00:00Z"), null, null, null);

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        assertThat(result.durationSeconds()).isNull();
    }

    @Test
    void update_withNullCallRecordIds_preservesExistingCalls() {
        incident.getCalls().add(call1);
        incident.setStartedAt(call1.getCallStartedAt());

        var request = new IncidentRequest("Updated title", application.getId(), null, false, null, null, null, null);

        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.update(incident.getId(), request);

        // Linked call must still be present
        assertThat(result.linkedCallsCount()).isEqualTo(1);
        // startedAt must not be nulled out
        assertThat(result.startedAt()).isEqualTo(call1.getCallStartedAt());
    }

    @Test
    void findAll_paginates_withApplicationFilter() {
        var pageable = PageRequest.of(0, 10);
        when(incidentRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(incident)));

        var result = service.findAll(pageable, application.getId(), null, null, null);

        assertThat(result.getContent()).hasSize(1);
        verify(incidentRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findAll_passesGroupAndDateRangeFilters() {
        var pageable = PageRequest.of(0, 10);
        var groupId = UUID.randomUUID();
        var from = OffsetDateTime.parse("2026-09-01T00:00:00Z");
        var to = OffsetDateTime.parse("2026-10-31T23:59:59Z");
        when(incidentRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(incident)));

        var result = service.findAll(pageable, null, groupId, from, to);

        assertThat(result.getContent()).hasSize(1);
        verify(incidentRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void create_withOnCallGroup_assignsGroup() {
        var group = com.nextdigital.oncall.entity.OnCallGroup.builder()
                .id(UUID.randomUUID()).name("Guardia test").build();
        var request = new IncidentRequest("Test", null, null, false, null, null, null, group.getId());

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        assertThat(result.onCallGroupId()).isEqualTo(group.getId());
        assertThat(result.onCallGroupName()).isEqualTo("Guardia test");
    }

    @Test
    void create_infersGroupFromLinkedCalls() {
        var group = com.nextdigital.oncall.entity.OnCallGroup.builder()
                .id(UUID.randomUUID()).name("Guardia test").build();
        call1.setOnCallGroup(group);
        var request = new IncidentRequest("Test", null, null, false, null, null,
                List.of(call1.getId()), null);

        when(callRepository.findAllById(List.of(call1.getId()))).thenReturn(List.of(call1));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        assertThat(result.onCallGroupId()).isEqualTo(group.getId());
    }

    @Test
    void findById_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(incidentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_throwsWhenApplicationNotFound() {
        var request = new IncidentRequest("Test", UUID.randomUUID(), null, false, null, null, null, null);
        when(applicationRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Application");
    }

    @Test
    void delete_removesIncident() {
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));

        service.delete(incident.getId());

        verify(incidentRepository).delete(incident);
    }

    @Test
    void delete_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(incidentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void linkCall_throwsWhenCallNotFound() {
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        var fakeCallId = UUID.randomUUID();
        when(callRepository.findById(fakeCallId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkCall(incident.getId(), fakeCallId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ZadarmaCall");
    }
}
