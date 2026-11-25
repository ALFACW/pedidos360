package com.pedidos360.payments.listener;

import com.pedidos360.payments.config.KafkaConfig;
import com.pedidos360.payments.dto.IntentoPagoDTO;
import com.pedidos360.payments.service.DeteccionFraudeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Dominio antifraude: consume los intentos de pago (consumer group deteccion-fraude). */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentoPagoListener {

    private final DeteccionFraudeService deteccionFraudeService;

    @KafkaListener(topics = KafkaConfig.TOPICO_INTENTOS_PAGO)
    public void consumir(
            @Valid @Payload IntentoPagoDTO intento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Intento de pago tarjeta={} pedido={} particion={} offset={}",
                intento.tarjeta(), intento.pedidoId(), particion, offset);
        deteccionFraudeService.registrarIntento(intento);
    }
}
