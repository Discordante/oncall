package com.nextdigital.oncall.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.GenerateShiftsResponse;
import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.OnCallGroupResponse;
import com.nextdigital.oncall.service.OnCallGroupService;
import com.nextdigital.oncall.service.ShiftGenerationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/on-call-groups")
@RequiredArgsConstructor
@Tag(name = "Grupos de Guardia", description = "Operaciones CRUD para gestionar grupos de guardia")
public class OnCallGroupController {

    private final OnCallGroupService service;
    private final ShiftGenerationService shiftGenerationService;

    @GetMapping
    @Operation(summary = "Listar todos los grupos de guardia",
               description = "Obtiene un listado de todos los grupos de guardia registrados")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    })
    public ResponseEntity<ApiResponse<List<OnCallGroupResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(service.findAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener grupo de guardia por ID",
               description = "Obtiene los detalles de un grupo de guardia específico por su ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Grupo de guardia encontrado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grupo de guardia no encontrado")
    })
    public ResponseEntity<ApiResponse<OnCallGroupResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Crear grupo de guardia",
               description = "Registra un nuevo grupo de guardia en el sistema")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Grupo de guardia creado correctamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    public ResponseEntity<ApiResponse<OnCallGroupResponse>> create(@Valid @RequestBody OnCallGroupRequest request) {
        var response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar grupo de guardia",
               description = "Actualiza los datos de un grupo de guardia existente")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Grupo de guardia actualizado correctamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grupo de guardia no encontrado")
    })
    public ResponseEntity<ApiResponse<OnCallGroupResponse>> update(@PathVariable UUID id,
                                                                   @Valid @RequestBody OnCallGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar grupo de guardia",
               description = "Elimina un grupo de guardia del sistema")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Grupo de guardia eliminado correctamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grupo de guardia no encontrado")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/generate-shifts")
    @Operation(summary = "Generar turnos automáticamente",
               description = "Genera turnos semanales para las siguientes semanas a partir del turno más lejano del grupo. "
                           + "Los turnos se asignan a la persona con menor tiempo total de guardia para garantizar equidad.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Turnos generados correctamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grupo de guardia no encontrado")
    })
    public ResponseEntity<ApiResponse<GenerateShiftsResponse>> generateShifts(@PathVariable UUID id) {
        var result = shiftGenerationService.generateForGroup(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
