package com.nextdigital.oncall.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.ApplicationRepository;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;

@ExtendWith(MockitoExtension.class)
class OnCallGroupServiceImplTest {

    @Mock
    private OnCallGroupRepository repository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private OnCallGroupServiceImpl service;

    private OnCallGroup group;
    private Application application;
    private Person person;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id(UUID.randomUUID())
                .name("Monitor App")
                .build();

        person = Person.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .email("alice@example.com")
                .isActive(true)
                .build();

        group = OnCallGroup.builder()
                .id(UUID.randomUUID())
                .name("Weekend OnCall")
                .grossPricePerDay(new BigDecimal("150.00"))
                .applications(Set.of(application))
                .people(Set.of(person))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void findAll_returnsAllGroups() {
        when(repository.findAll()).thenReturn(List.of(group));

        var result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Weekend OnCall");
    }

    @Test
    void findById_returnsGroupWithNestedRelations() {
        when(repository.findById(group.getId())).thenReturn(Optional.of(group));

        var result = service.findById(group.getId());

        assertThat(result.name()).isEqualTo("Weekend OnCall");
        assertThat(result.applications()).hasSize(1);
        assertThat(result.people()).hasSize(1);
        assertThat(result.grossPricePerDay()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void findById_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("OnCallGroup");
    }

    @Test
    void create_withApplicationsAndPeople() {
        var request = new OnCallGroupRequest(
                "Holiday OnCall",
                new BigDecimal("200.00"),
                Set.of(application.getId()),
                Set.of(person.getId()));

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        when(repository.save(any())).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, OnCallGroup.class);
            // Simulate ID generation
            var idField = OnCallGroup.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(saved, UUID.randomUUID());
            var createdAtField = OnCallGroup.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(saved, OffsetDateTime.now());
            var updatedAtField = OnCallGroup.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(saved, OffsetDateTime.now());
            return saved;
        });

        var result = service.create(request);

        assertThat(result.name()).isEqualTo("Holiday OnCall");
        assertThat(result.applications()).hasSize(1);
        assertThat(result.people()).hasSize(1);
        verify(repository).save(any());
    }

    @Test
    void create_withoutApplicationsAndPeople() {
        var request = new OnCallGroupRequest("Empty Group", null, null, null);

        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(request);

        assertThat(result.name()).isEqualTo("Empty Group");
        assertThat(result.applications()).isEmpty();
        assertThat(result.people()).isEmpty();
    }

    @Test
    void create_throwsWhenApplicationNotFound() {
        var appId = UUID.randomUUID();
        var request = new OnCallGroupRequest("Group", null, Set.of(appId), null);

        when(applicationRepository.findById(appId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Application");
    }

    @Test
    void update_modifiesAndReturnsGroup() {
        var newApp = Application.builder().id(UUID.randomUUID()).name("New App").build();
        var request = new OnCallGroupRequest("Updated Group", new BigDecimal("250.00"),
                Set.of(newApp.getId()), Set.of(person.getId()));

        when(repository.findById(group.getId())).thenReturn(Optional.of(group));
        when(applicationRepository.findById(newApp.getId())).thenReturn(Optional.of(newApp));
        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(group.getId(), request);

        assertThat(result.name()).isEqualTo("Updated Group");
        assertThat(result.applications()).hasSize(1);
        assertThat(result.people()).hasSize(1);
    }

    @Test
    void delete_removesGroupWhenFound() {
        when(repository.findById(group.getId())).thenReturn(Optional.of(group));

        service.delete(group.getId());

        verify(repository).delete(group);
    }

    @Test
    void delete_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
