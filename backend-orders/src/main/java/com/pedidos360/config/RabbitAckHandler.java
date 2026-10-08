package com.pedidos360.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Confirmación manual de mensajes (acknowledge-mode: manual) compartida por los consumidores.
 *
 * <ul>
 *   <li>Éxito: ACK.</li>
 *   <li>Error no recuperable (mensaje mal formado o inválido): NACK sin reencolar, RabbitMQ lo envía a la DLQ.</li>
 *   <li>Error recuperable (BD, SMTP, red): reintenta con backoff exponencial; si se agotan los intentos,
 *       NACK sin reencolar hacia la DLQ.</li>
 * </ul>
 * Cada decisión queda registrada en los logs.
 */
@Component
public class RabbitAckHandler {

    private static final Logger logger = LoggerFactory.getLogger(RabbitAckHandler.class);

    @FunctionalInterface
    public interface MessageWork {
        void run() throws Exception;
    }

    private final int maxAttempts;
    private final long initialIntervalMs;

    public RabbitAckHandler(
            @Value("${app.rabbitmq.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.rabbitmq.retry.initial-interval-ms:1000}") long initialIntervalMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialIntervalMs = Math.max(0, initialIntervalMs);
    }

    public void process(Channel channel, long deliveryTag, String queue, String messageId, MessageWork work)
            throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                work.run();
                channel.basicAck(deliveryTag, false);
                logger.info("ACK cola={} messageId={}", queue, messageId);
                return;
            } catch (Exception exception) {
                if (isUnrecoverable(exception)) {
                    logger.error("NACK -> DLQ (mensaje inválido) cola={} messageId={} motivo={}",
                            queue, messageId, exception.getMessage());
                    channel.basicNack(deliveryTag, false, false);
                    return;
                }
                if (attempt >= maxAttempts) {
                    logger.error("NACK -> DLQ (reintentos agotados: {}) cola={} messageId={} motivo={}",
                            maxAttempts, queue, messageId, exception.getMessage(), exception);
                    channel.basicNack(deliveryTag, false, false);
                    return;
                }
                long backoff = initialIntervalMs << (attempt - 1);
                logger.warn("Error recuperable cola={} messageId={} intento {}/{}: {}. Reintento en {} ms",
                        queue, messageId, attempt, maxAttempts, exception.getMessage(), backoff);
                if (!sleep(backoff)) {
                    // El contenedor se está deteniendo: se devuelve el mensaje a la cola para no perderlo
                    channel.basicNack(deliveryTag, false, true);
                    return;
                }
            }
        }
    }

    static boolean isUnrecoverable(Throwable exception) {
        return exception instanceof IllegalArgumentException
                || exception instanceof JsonProcessingException;
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
