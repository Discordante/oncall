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

import com.nextdigital.oncall.dto.ApplicationRequest;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @Mock
    private ApplicationRepository repository;

    @InjectMocks
    private ApplicationServiceImpl service;

    private Application application;
    private ApplicationRequest request;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id(UUID.randomUUID())
                .name("Monitor App")
                .description("Application monitoring")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        request = new ApplicationRequest("Monitor App", "Application monitoring");
    }

    @Test
    void findAll_returnsAllApplications() {
        when(repository.findAll()).thenReturn(List.of(application));

        var result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Monitor App");
        verify(repository).findAll();
    }

    @Test
    void findById_returnsApplicationWhenFound() {
        when(repository.findById(application.getId())).thenReturn(Optional.of(application));

        var result = service.findById(application.getId());

        assertThat(result.name()).isEqualTo("Monitor App");
        assertThat(result.description()).isEqualTo("Application monitoring");
    }

    @Test
    void findById_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Application");
    }

    @Test
    void create_persistsAndReturnsApplication() {
        when(repository.save(any())).thenReturn(application);

        var result = service.create(request);

        assertThat(result.name()).isEqualTo("Monitor App");
        assertThat(result.description()).isEqualTo("Application monitoring");
        verify(repository).save(any());
    }

    @Test
    void update_modifiesAndReturnsApplication() {
        var updatedRequest = new ApplicationRequest("Updated App", "Updated description");
        when(repository.findById(application.getId())).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(application.getId(), updatedRequest);

        assertThat(result.name()).isEqualTo("Updated App");
        assertThat(result.description()).isEqualTo("Updated description");
    }

    @Test
    void update_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesApplicationWhenFound() {
        when(repository.findById(application.getId())).thenReturn(Optional.of(application));

        service.delete(application.getId());

        verify(repository).delete(application);
    }

    @Test
    void delete_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
