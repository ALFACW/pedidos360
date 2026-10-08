package com.pedidos360.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dominio notificaciones: registra el resultado del pago (idempotente por event_id) y envía el correo al cliente. */
@Service
public class PaymentNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;
    private final String senderAddress;

    public PaymentNotificationService(
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            JavaMailSender mailSender,
            @Value("${notifications.email.from}") String senderAddress) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
    }

    @Transactional
    public void handle(String payload) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        String eventType = event.path("eventType").asText();
        if (!"PAYMENT_APPROVED".equals(eventType) && !"PAYMENT_REJECTED".equals(eventType)) {
            throw new IllegalArgumentException("Evento recibido no es un resultado de pago");
        }

        String eventId = event.path("eventId").asText();
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("El evento no incluye eventId");
        }

        JsonNode payment = event.path("payment");
        String orderNumber = payment.path("orderNumber").asText("unknown");
        String customer = payment.path("customer").asText("customer");
        String recipientEmail = payment.path("customerEmail").asText().trim();
        if (recipientEmail.isBlank()) {
            throw new IllegalArgumentException("El resultado del pago no incluye el correo del cliente");
        }

        String result = "PAYMENT_APPROVED".equals(eventType) ? "approved" : "rejected";
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO notifications (event_id, event_type, order_number, customer, customer_email, result)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId, eventType, orderNumber, customer, recipientEmail, result);
        if (inserted == 0) {
            return;
        }

        sendEmail(recipientEmail, customer, orderNumber, result);
        logger.info("Payment notification email sent: order={}, result={}", orderNumber, result);
    }

    private void sendEmail(String recipientEmail, String customer, String orderNumber, String result)
            throws Exception {
        String resultText = "approved".equals(result) ? "aprobado" : "rechazado";
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(senderAddress);
        helper.setTo(recipientEmail);
        helper.setSubject("Pedido " + orderNumber + ": pago " + resultText);
        helper.setText("Hola " + customer + ",\n\n"
                + "El pago de tu pedido " + orderNumber + " fue " + resultText + ".\n\n"
                + "Gracias por comprar en Pedidos360.");
        mailSender.send(message);
    }
}