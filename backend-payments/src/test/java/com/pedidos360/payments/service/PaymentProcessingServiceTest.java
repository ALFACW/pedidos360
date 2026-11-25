package com.pedidos360.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.payments.repository.PagoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentProcessingServiceTest {

    private final PagoRepository pagoRepository = mock(PagoRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PaymentProcessingService service =
            new PaymentProcessingService(new ObjectMapper(), pagoRepository, jdbcTemplate);

    @Test
    void registraPagoPendiente() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        service.processOrderCreated("""
                {"eventId":"order-42","eventType":"ORDER_CREATED","order":{"id":42,"orderNumber":"PED-42","user":"Test User","customerEmail":"test@example.com","total":19.99}}
                """);

        verify(pagoRepository).save(argThat(pago -> pago.getPedidoId() == 42
                && "PENDIENTE".equals(pago.getEstado())
                && new BigDecimal("19.99").equals(pago.getMonto())
                && "test@example.com".equals(pago.getCorreo())));
    }

    @Test
    void ignoraEventoYaProcesado() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        service.processOrderCreated("""
                {"eventId":"order-42","eventType":"ORDER_CREATED","order":{"id":42,"total":19.99}}
                """);

        verifyNoInteractions(pagoRepository);
    }

    @Test
    void rechazaEventoInesperado() {
        assertThrows(IllegalArgumentException.class,
                () -> service.processOrderCreated("{\"eventType\":\"UNKNOWN\"}"));
        verifyNoInteractions(pagoRepository, jdbcTemplate);
    }
}
