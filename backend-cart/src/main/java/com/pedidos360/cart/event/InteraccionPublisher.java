package com.pedidos360.cart.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InteraccionPublisher {

    public static final String TOPICO = "pedidos360.interacciones-usuario";
    private static final Logger log = LoggerFactory.getLogger(InteraccionPublisher.class);

    private final KafkaTemplate<String, InteraccionDTO> kafkaTemplate;

    public InteraccionPublisher(KafkaTemplate<String, InteraccionDTO> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicar(Jwt jwt, Long productoId, String nombreProducto, String categoria, String accion) {
        String usuario = jwt.getClaimAsString("preferred_username");
        kafkaTemplate.send(TOPICO, usuario,
                        new InteraccionDTO(usuario, productoId, nombreProducto, categoria, accion, Instant.now()))
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar la interacción {} del usuario {}: {}", accion, usuario, error.getMessage());
                    }
                });
    }
}
