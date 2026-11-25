package com.pedidos360.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Publica una interacción COMPRA por producto cuando se aprueba el pago (alimenta recomendaciones y dashboard). */
@Component
public class CompraPublisher {

    private static final String TOPICO = "pedidos360.interacciones-usuario";
    private static final Logger log = LoggerFactory.getLogger(CompraPublisher.class);

    private final KafkaTemplate<String, InteraccionDTO> kafkaTemplate;

    public CompraPublisher(KafkaTemplate<String, InteraccionDTO> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicar(String usuario, Long productoId, String nombreProducto, String categoria) {
        kafkaTemplate.send(TOPICO, usuario, new InteraccionDTO(usuario, productoId, nombreProducto, categoria, "COMPRA", Instant.now()))
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar la compra del producto {} de {}: {}", productoId, usuario, error.getMessage());
                    }
                });
    }
}
