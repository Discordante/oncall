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
import com.nextdigital.oncall.dto.ApplicationRequest;
import com.nextdigital.oncall.dto.ApplicationResponse;
import com.nextdigital.oncall.service.ApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Aplicaciones", description = "Operaciones CRUD para gestionar aplicaciones")
public class ApplicationController {

    private final ApplicationService service;

    @GetMapping
    @Operation(summary = "Listar todas las aplicaciones", description = "Obtiene un listado de todas las aplicaciones registradas")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    })
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(service.findAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener aplicación por ID", description = "Obtiene los detalles de una aplicación específica por su ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Aplicación encontrada"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Aplicación no encontrada")
    })
    public ResponseEntity<ApiResponse<ApplicationResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Crear aplicación", description = "Registra una nueva aplicación en el sistema")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Aplicación creada correctamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    public ResponseEntity<ApiResponse<ApplicationResponse>> create(@Valid @RequestBody ApplicationRequest request) {
        var response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar aplicación", description = "Actualiza los datos de una aplicación existente")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Aplicación actualizada correctamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Aplicación no encontrada")
    })
    public ResponseEntity<ApiResponse<ApplicationResponse>> update(@PathVariable UUID id,
                                                                   @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar aplicación", description = "Elimina una aplicación del sistema")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Aplicación eliminada correctamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Aplicación no encontrada")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
