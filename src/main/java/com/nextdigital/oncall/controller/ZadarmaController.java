package com.nextdigital.oncall.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.CallRecordResponse;
import com.nextdigital.oncall.service.CallSyncService;
import com.nextdigital.oncall.zadarma.dto.SipInfoResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaDirectNumbersResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaSipRedirectionResponse;
import com.nextdigital.oncall.zadarma.service.ZadarmaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/zadarma")
@RequiredArgsConstructor
@Tag(name = "Zadarma Analytics", description = "Consultas analíticas de solo lectura sobre la API de Zadarma")
public class ZadarmaController {

        private final ZadarmaService zadarmaService;
        private final CallSyncService callSyncService;

        @GetMapping("/sips")
        @Operation(summary = "Listar SIPs", description = "Obtiene la lista de IDs SIP configurados en la cuenta de Zadarma, enriquecida con el nombre del grupo de guardia que lo emplea (si existe)")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de SIPs obtenida correctamente"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Error en la comunicación con la API de Zadarma")
        })
        public ResponseEntity<ApiResponse<List<SipInfoResponse>>> getSips() {
                return ResponseEntity.ok(ApiResponse.success(zadarmaService.getSipList()));
        }

        @GetMapping("/sip-redirections")
        @Operation(summary = "Reenvíos SIP", description = "Obtiene la configuración actual de reenvío de llamadas (redirección) para todos los números SIP de la cuenta, o filtrado por un SIP concreto")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reenvíos obtenidos correctamente"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Error en la comunicación con la API de Zadarma")
        })
        public ResponseEntity<ApiResponse<ZadarmaSipRedirectionResponse>> getSipRedirections(
                        @Parameter(description = "ID de SIP específico (opcional). Si no se indica, se devuelven todos los SIP") @RequestParam(required = false) String id) {
                return ResponseEntity.ok(ApiResponse.success(zadarmaService.getSipRedirections(id)));
        }

        @GetMapping("/calls-summary")
        @Operation(summary = "Llamadas entrantes", description = "Devuelve registros de llamadas desde la BD local (sincroniza con Zadarma si el rango no está cubierto). Usar force=true para forzar sincronización.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registros obtenidos correctamente"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Error en la comunicación con la API de Zadarma")
        })
        public ResponseEntity<ApiResponse<List<CallRecordResponse>>> getCallsSummary(
                        @Parameter(description = "Inicio del periodo (YYYY-MM-DD HH:MM:SS)") @RequestParam(required = false) String start,
                        @Parameter(description = "Fin del periodo (YYYY-MM-DD HH:MM:SS)") @RequestParam(required = false) String end,
                        @Parameter(description = "Filtrar por número SIP (opcional)") @RequestParam(required = false) String sip,
                        @Parameter(description = "Forzar sincronización con Zadarma ignorando el cache local") @RequestParam(defaultValue = "false") boolean force) {
                var records = force
                                ? callSyncService.syncAndGet(start, end, sip)
                                : callSyncService.getCallRecords(start, end, sip);
                return ResponseEntity.ok(ApiResponse.success(records));
        }

        @GetMapping("/direct-numbers")
        @Operation(summary = "Números virtuales", description = "Obtiene la información sobre los números virtuales (directos) contratados en la cuenta de Zadarma. "
                        + "El conjunto de campos puede variar según el tipo de número. "
                        + "Posibles estados: on, parking, checking, checking_upload, reserved_on, reserved_checking, reserved_checking_upload. "
                        + "Posibles tipos: common (virtual), rufree (Moscú gratuito), order (reservado sin conectar).")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Números virtuales obtenidos correctamente"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Error en la comunicación con la API de Zadarma")
        })
        public ResponseEntity<ApiResponse<ZadarmaDirectNumbersResponse>> getDirectNumbers() {
                return ResponseEntity.ok(ApiResponse.success(zadarmaService.getDirectNumbers()));
        }
}
