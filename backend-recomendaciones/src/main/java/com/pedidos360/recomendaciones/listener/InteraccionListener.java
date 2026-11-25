package com.pedidos360.recomendaciones.listener;

import com.pedidos360.recomendaciones.config.KafkaConfig;
import com.pedidos360.recomendaciones.dto.InteraccionDTO;
import com.pedidos360.recomendaciones.service.RecomendacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Dominio recomendaciones: consume el clickstream (consumer group recomendaciones). */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteraccionListener {

    private final RecomendacionService recomendacionService;

    @KafkaListener(topics = KafkaConfig.TOPICO_INTERACCIONES)
    public void consumir(
            @Valid @Payload InteraccionDTO interaccion,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Interacción {} usuario={} producto={} particion={} offset={}",
                interaccion.accion(), interaccion.usuario(), interaccion.productoId(), particion, offset);
        recomendacionService.registrar(interaccion);
    }
}
