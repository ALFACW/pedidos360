package com.pedidos360.notifications.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentResultListenerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PaymentResultListener listener = new PaymentResultListener(
            new ObjectMapper(), jdbcTemplate, mailSender, "no-reply@pedidos360.local");

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void storesAndEmailsNewPaymentNotification() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        listener.handle("""
                {"eventId":"payment-42-approved","eventType":"PAYMENT_APPROVED",
                 "payment":{"orderNumber":"PED-42","customer":"Test User",
                            "customerEmail":"test@example.com"}}
                """);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();
        assertEquals("test@example.com", sentMessage.getAllRecipients()[0].toString());
        assertEquals("Pedido PED-42: pago aprobado", sentMessage.getSubject());
        assertTrue(sentMessage.getContent().toString().contains("fue aprobado"));
    }

    @Test
    void doesNotEmailDuplicatePaymentNotification() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        listener.handle("""
                {"eventId":"payment-42-approved","eventType":"PAYMENT_APPROVED",
                 "payment":{"orderNumber":"PED-42","customer":"Test User",
                            "customerEmail":"test@example.com"}}
                """);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        verifyNoInteractions(mailSender);
    }

    @Test
    void rejectsEventsWithoutIdsBeforeWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> listener.handle("""
                        {"eventType":"PAYMENT_APPROVED",
                         "payment":{"orderNumber":"PED-42","customer":"Test User",
                                    "customerEmail":"test@example.com"}}
                        """));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsPaymentResultsWithoutCustomerEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> listener.handle("""
                        {"eventId":"payment-42-approved","eventType":"PAYMENT_APPROVED",
                         "payment":{"orderNumber":"PED-42","customer":"Test User"}}
                        """));

        verifyNoInteractions(jdbcTemplate, mailSender);
    }

    @Test
    void propagatesMailFailureSoRabbitCanRetry() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class,
                () -> listener.handle("""
                        {"eventId":"payment-42-approved","eventType":"PAYMENT_APPROVED",
                         "payment":{"orderNumber":"PED-42","customer":"Test User",
                                    "customerEmail":"test@example.com"}}
                        """));

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        verify(mailSender).send(any(MimeMessage.class));
    }
}
