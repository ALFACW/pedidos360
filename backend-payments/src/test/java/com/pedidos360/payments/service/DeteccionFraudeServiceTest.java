package com.pedidos360.payments.service;

import com.pedidos360.payments.dto.IntentoPagoDTO;
import com.pedidos360.payments.model.EstadoTarjeta;
import com.pedidos360.payments.repository.AlertaFraudeRepository;
import com.pedidos360.payments.repository.EstadoTarjetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeteccionFraudeServiceTest {

    private final Map<String, EstadoTarjeta> estados = new HashMap<>();
    private final EstadoTarjetaRepository estadoRepository = mock(EstadoTarjetaRepository.class);
    private final AlertaFraudeRepository alertaRepository = mock(AlertaFraudeRepository.class);
    private final DeteccionFraudeService service = new DeteccionFraudeService(estadoRepository, alertaRepository);
    private final Instant inicio = Instant.parse("2026-11-26T12:00:00Z");

    @BeforeEach
    void repositorioEnMemoria() {
        when(estadoRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(estados.get(i.<String>getArgument(0))));
        when(estadoRepository.save(any())).thenAnswer(i -> {
            EstadoTarjeta estado = i.getArgument(0);
            estados.put(estado.getTarjeta(), estado);
            return estado;
        });
    }

    private void intento(int segundo) {
        service.registrarIntento(new IntentoPagoDTO("****6623", "cliente", 1L, BigDecimal.TEN, inicio.plusSeconds(segundo)));
    }

    @Test
    void bloqueaAlCuartoIntentoDentroDe10Segundos() {
        intento(0);
        intento(2);
        intento(4);
        assertNull(estados.get("****6623").getBloqueadoHasta());

        intento(6);
        assertNotNull(estados.get("****6623").getBloqueadoHasta());
        verify(alertaRepository, times(1)).save(any());
    }

    @Test
    void noBloqueaSiLosIntentosEstanSeparados() {
        intento(0);
        intento(5);
        intento(11);
        intento(16);

        assertNull(estados.get("****6623").getBloqueadoHasta());
        verify(alertaRepository, never()).save(any());
    }
}
