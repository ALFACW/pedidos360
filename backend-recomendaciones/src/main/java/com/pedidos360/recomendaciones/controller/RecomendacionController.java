package com.pedidos360.recomendaciones.controller;

import com.pedidos360.recomendaciones.dto.RecomendacionDTO;
import com.pedidos360.recomendaciones.service.RecomendacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recomendaciones")
@RequiredArgsConstructor
public class RecomendacionController {

    private final RecomendacionService recomendacionService;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping
    public List<RecomendacionDTO> recomendaciones(@AuthenticationPrincipal Jwt jwt) {
        return recomendacionService.recomendar(jwt.getClaimAsString("preferred_username"));
    }
}
