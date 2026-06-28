package com.nextdigital.oncall.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.dto.ShiftResponse;
import com.nextdigital.oncall.service.ShiftService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
@Tag(name = "Turnos", description = "Operaciones CRUD para gestionar turnos de guardia")
public class ShiftController {

    private final ShiftService service;

    @GetMapping
    @Operation(summary = "Listar turnos", description = "Obtiene un listado de turnos con filtros opcionales por persona, grupo y rango de fechas")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    })
    public ResponseEntity<ApiResponse<List<ShiftResponse>>> findAll(
            @Parameter(description = "Filtrar por ID de persona") @RequestParam(required = false) UUID personId,
            @Parameter(description = "Filtrar por ID de grupo de guardia") @RequestParam(required = false) UUID onCallGroupId,
            @Parameter(description = "Inicio del rango de fechas (ISO 8601)") @RequestParam(required = false) OffsetDateTime from,
            @Parameter(description = "Fin del rango de fechas (ISO 8601)") @RequestParam(required = false) OffsetDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(service.findAll(personId, onCallGroupId, from, to)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener turno por ID", description = "Obtiene los detalles de un turno específico por su ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Turno encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Turno no encontrado")
    })
    public ResponseEntity<ApiResponse<ShiftResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Crear turno", description = "Registra un nuevo turno de guardia. Si el rango solapa con turnos de otras personas, "
            +
            "se dividen automáticamente. Si solapa con turnos de la misma persona, se rechaza.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Turno creado correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "La persona ya tiene un turno en ese rango")
    })
    public ResponseEntity<ApiResponse<ShiftResponse>> create(@Valid @RequestBody ShiftRequest request) {
        var response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar turno", description = "Actualiza un turno existente. Si se extiende o acorta, los turnos adyacentes "
            +
            "de la misma persona se ajustan automáticamente. Si solapa con turnos de otras " +
            "personas, se dividen automáticamente.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Turno actualizado correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Turno no encontrado")
    })
    public ResponseEntity<ApiResponse<ShiftResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody ShiftRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar turno", description = "Elimina un turno de guardia por su ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Turno eliminado correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Turno no encontrado")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
