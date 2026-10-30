package com.nextdigital.oncall.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.IncidentRequest;
import com.nextdigital.oncall.dto.IncidentResponse;
import com.nextdigital.oncall.service.IncidentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidencias", description = "Gestión de incidencias vinculadas a llamadas y aplicaciones")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    @Operation(summary = "Listar incidencias", description = "Devuelve incidencias paginadas, opcionalmente filtradas por aplicación")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Listado obtenido")
    })
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> findAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @Parameter(description = "Filtrar por ID de aplicación") @RequestParam(required = false) UUID applicationId) {
        return ResponseEntity.ok(ApiResponse.success(incidentService.findAll(pageable, applicationId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener incidencia por ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidencia encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No encontrada")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(incidentService.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Crear incidencia")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Incidencia creada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Aplicación no encontrada")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> create(@Valid @RequestBody IncidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(incidentService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar incidencia")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Actualizada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No encontrada")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody IncidentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(incidentService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar incidencia")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Eliminada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No encontrada")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        incidentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/calls/{callId}")
    @Operation(summary = "Vincular llamada a incidencia", description = "Asocia una llamada de Zadarma a la incidencia. started_at se recalcula automáticamente.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Llamada vinculada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incidencia o llamada no encontrada")
    })
    public ResponseEntity<ApiResponse<IncidentResponse>> linkCall(@PathVariable UUID id,
            @PathVariable UUID callId) {
        return ResponseEntity.ok(ApiResponse.success(incidentService.linkCall(id, callId)));
    }

    @DeleteMapping("/{id}/calls/{callId}")
    @Operation(summary = "Desvincular llamada de incidencia")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Llamada desvinculada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incidencia o llamada no encontrada")
    })
    public ResponseEntity<Void> unlinkCall(@PathVariable UUID id, @PathVariable UUID callId) {
        incidentService.unlinkCall(id, callId);
        return ResponseEntity.noContent().build();
    }
}
