package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.PersonRepository;

@ExtendWith(MockitoExtension.class)
class PersonServiceImplTest {

    @Mock
    private PersonRepository repository;

    @InjectMocks
    private PersonServiceImpl service;

    private Person activePerson;
    private Person inactivePerson;
    private PersonRequest request;

    @BeforeEach
    void setUp() {
        activePerson = Person.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .email("alice@example.com")
                .phoneNumber("+34 600 000 000")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        inactivePerson = Person.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .email("bob@example.com")
                .isActive(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        request = new PersonRequest("Alice", "alice@example.com", "+34 600 000 000", true);
    }

    @Test
    void findAll_withIncludeInactiveFalse_returnsOnlyActive() {
        when(repository.findByIsActiveTrue()).thenReturn(List.of(activePerson));

        var result = service.findAll(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Alice");
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    void findAll_withIncludeInactiveTrue_returnsAllPeople() {
        when(repository.findAll()).thenReturn(List.of(activePerson, inactivePerson));

        var result = service.findAll(true);

        assertThat(result).hasSize(2);
    }

    @Test
    void findById_returnsPersonWhenFound() {
        when(repository.findById(activePerson.getId())).thenReturn(Optional.of(activePerson));

        var result = service.findById(activePerson.getId());

        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.email()).isEqualTo("alice@example.com");
    }

    @Test
    void findById_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Person");
    }

    @Test
    void create_persistsWithDefaultIsActiveWhenNull() {
        var requestWithoutIsActive = new PersonRequest("Charlie", "charlie@example.com", null, null);
        var savedPerson = Person.builder()
                .id(UUID.randomUUID())
                .name("Charlie")
                .email("charlie@example.com")
                .isActive(true)
                .build();

        when(repository.save(any())).thenReturn(savedPerson);

        var result = service.create(requestWithoutIsActive);

        assertThat(result.name()).isEqualTo("Charlie");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void create_persistsWithCustomIsActive() {
        when(repository.save(any())).thenReturn(activePerson);

        var result = service.create(request);

        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.isActive()).isTrue();
        verify(repository).save(any());
    }

    @Test
    void update_modifiesAndReturnsPerson() {
        var updatedRequest = new PersonRequest("Alice Updated", "alice.new@example.com", "+34 600 000 001", true);
        when(repository.findById(activePerson.getId())).thenReturn(Optional.of(activePerson));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(activePerson.getId(), updatedRequest);

        assertThat(result.name()).isEqualTo("Alice Updated");
        assertThat(result.email()).isEqualTo("alice.new@example.com");
        assertThat(result.phoneNumber()).isEqualTo("+34 600 000 001");
    }

    @Test
    void update_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_performsSoftDelete() {
        when(repository.findById(activePerson.getId())).thenReturn(Optional.of(activePerson));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(activePerson.getId());

        assertThat(activePerson.getIsActive()).isFalse();
        verify(repository).save(activePerson);
    }

    @Test
    void delete_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
