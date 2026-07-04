package com.nextdigital.oncall.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@RestController
@Tag(name = "Health", description = "Endpoint de verificación de salud del servicio")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Verificar estado del servicio", description = "Devuelve el estado actual del servicio. "
            + "Útil para health checks y monitorización.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Servicio operativo", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"UP\"}")))
    })
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
