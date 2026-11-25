package com.pedidos360.payments.service;

import com.pedidos360.payments.dto.IntentoPagoDTO;
import com.pedidos360.payments.model.AlertaFraude;
import com.pedidos360.payments.model.EstadoTarjeta;
import com.pedidos360.payments.repository.AlertaFraudeRepository;
import com.pedidos360.payments.repository.EstadoTarjetaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Regla antifraude: la misma tarjeta más de 3 veces en 10 segundos se bloquea 30 minutos. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeteccionFraudeService {

    static final int MAX_INTENTOS = 3;
    static final Duration VENTANA = Duration.ofSeconds(10);
    static final Duration BLOQUEO = Duration.ofMinutes(30);

    private final EstadoTarjetaRepository estadoRepository;
    private final AlertaFraudeRepository alertaRepository;

    public boolean estaBloqueada(String tarjeta) {
        return estadoRepository.findById(tarjeta)
                .map(estado -> estado.getBloqueadoHasta() != null && Instant.now().isBefore(estado.getBloqueadoHasta()))
                .orElse(false);
    }

    /** Para el dashboard: últimas alertas y cuántas tarjetas siguen bloqueadas. */
    public Map<String, Object> resumen() {
        return Map.of(
                "alertas", alertaRepository.findTop10ByOrderByFechaDesc(),
                "tarjetasBloqueadas", estadoRepository.countByBloqueadoHastaAfter(Instant.now()));
    }

    @Transactional
    public void registrarIntento(IntentoPagoDTO intento) {
        EstadoTarjeta estado = estadoRepository.findById(intento.tarjeta()).orElseGet(() -> {
            EstadoTarjeta nuevo = new EstadoTarjeta();
            nuevo.setTarjeta(intento.tarjeta());
            return nuevo;
        });
        // Se usa la hora del evento (no la de consumo) para que un retraso del consumidor no altere la ventana
        Instant fecha = intento.fecha();
        if (estado.getInicioVentana() == null || Duration.between(estado.getInicioVentana(), fecha).compareTo(VENTANA) > 0) {
            estado.setInicioVentana(fecha);
            estado.setIntentos(0);
        }
        estado.setIntentos(estado.getIntentos() + 1);

        boolean yaBloqueada = estado.getBloqueadoHasta() != null && fecha.isBefore(estado.getBloqueadoHasta());
        if (estado.getIntentos() > MAX_INTENTOS && !yaBloqueada) {
            estado.setBloqueadoHasta(fecha.plus(BLOQUEO));
            AlertaFraude alerta = new AlertaFraude();
            alerta.setTarjeta(intento.tarjeta());
            alerta.setCliente(intento.cliente());
            alerta.setIntentos(estado.getIntentos());
            alerta.setFecha(fecha);
            alertaRepository.save(alerta);
            log.error("ALERTA FRAUDE tarjeta={} cliente={} intentos={} en {} s. Bloqueada hasta {}",
                    intento.tarjeta(), intento.cliente(), estado.getIntentos(), VENTANA.toSeconds(), estado.getBloqueadoHasta());
        }
        estadoRepository.save(estado);
    }
}
