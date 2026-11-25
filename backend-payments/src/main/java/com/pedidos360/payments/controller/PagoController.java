package com.pedidos360.payments.controller;

import com.pedidos360.payments.dto.ConfirmacionPagoDTO;
import com.pedidos360.payments.dto.SolicitudPagoDTO;
import com.pedidos360.payments.service.DeteccionFraudeService;
import com.pedidos360.payments.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;
    private final DeteccionFraudeService deteccionFraudeService;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/alertas")
    public Map<String, Object> alertas() {
        return deteccionFraudeService.resumen();
    }

    @PostMapping("/iniciar")
    public Map<String, String> iniciar(@Valid @RequestBody SolicitudPagoDTO solicitud) throws Exception {
        return pagoService.iniciar(solicitud);
    }

    @PostMapping("/confirmar")
    public Map<String, String> confirmar(@Valid @RequestBody ConfirmacionPagoDTO confirmacion) throws Exception {
        return pagoService.confirmar(confirmacion.token());
    }
}
