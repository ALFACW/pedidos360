package com.pedidos360.payments.service;

import cl.transbank.common.IntegrationApiKeys;
import cl.transbank.common.IntegrationCommerceCodes;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import com.pedidos360.payments.config.KafkaConfig;
import com.pedidos360.payments.dto.IntentoPagoDTO;
import com.pedidos360.payments.dto.SolicitudPagoDTO;
import com.pedidos360.payments.event.PaymentEventPublisher;
import com.pedidos360.payments.model.Pago;
import com.pedidos360.payments.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/** Pago con WebPay Plus (ambiente de integración de Transbank) y envío de cada intento a Kafka. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    private final WebpayPlus.Transaction webpay =
            WebpayPlus.Transaction.buildForIntegration(IntegrationCommerceCodes.WEBPAY_PLUS, IntegrationApiKeys.WEBPAY);

    private final PagoRepository pagoRepository;
    private final DeteccionFraudeService deteccionFraudeService;
    private final KafkaTemplate<String, IntentoPagoDTO> kafkaTemplate;
    private final PaymentEventPublisher paymentEventPublisher;

    @Value("${app.webpay.url-retorno}")
    private String urlRetorno;

    public Map<String, String> iniciar(SolicitudPagoDTO solicitud) throws Exception {
        Pago pago = pagoRepository.findById(solicitud.pedidoId())
                .filter(p -> "PENDIENTE".equals(p.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "El pedido no tiene un pago pendiente"));
        // Solo se guardan los últimos 4 dígitos; es la clave del mensaje (misma tarjeta -> misma partición)
        String tarjeta = "****" + solicitud.numeroTarjeta().substring(12);
        if (deteccionFraudeService.estaBloqueada(tarjeta)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Tarjeta bloqueada por sospecha de fraude");
        }

        kafkaTemplate.send(KafkaConfig.TOPICO_INTENTOS_PAGO, tarjeta,
                new IntentoPagoDTO(tarjeta, pago.getCliente(), pago.getPedidoId(), pago.getMonto(), Instant.now()));

        var respuesta = webpay.create(pago.getNumeroPedido(), pago.getPedidoId().toString(),
                pago.getMonto().setScale(0, RoundingMode.HALF_UP).doubleValue(), urlRetorno);
        pago.setTarjeta(tarjeta);
        pago.setToken(respuesta.getToken());
        pagoRepository.save(pago);
        log.info("WebPay iniciado pedido={} tarjeta={}", pago.getNumeroPedido(), tarjeta);
        return Map.of("url", respuesta.getUrl(), "token", respuesta.getToken());
    }

    public Map<String, String> confirmar(String token) throws Exception {
        Pago pago = pagoRepository.findByToken(token)
                .filter(p -> "PENDIENTE".equals(p.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada"));
        // Si la tarjeta quedó bloqueada mientras el cliente estaba en WebPay, no se confirma el cobro
        boolean aprobado = !deteccionFraudeService.estaBloqueada(pago.getTarjeta())
                && "AUTHORIZED".equals(webpay.commit(token).getStatus());

        pago.setEstado(aprobado ? "APROBADO" : "RECHAZADO");
        pagoRepository.save(pago);
        paymentEventPublisher.publishResult(Map.of(
                "eventId", "payment-" + pago.getPedidoId() + "-" + (aprobado ? "approved" : "rejected"),
                "orderId", pago.getPedidoId().toString(),
                "orderNumber", pago.getNumeroPedido(),
                "customer", pago.getCliente(),
                "customerEmail", pago.getCorreo(),
                "amount", pago.getMonto(),
                "status", aprobado ? "APPROVED" : "REJECTED"));
        return Map.of("pedido", pago.getNumeroPedido(), "estado", pago.getEstado());
    }
}
