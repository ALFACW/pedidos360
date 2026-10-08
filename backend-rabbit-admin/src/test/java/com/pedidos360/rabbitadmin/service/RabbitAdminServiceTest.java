package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.dto.BindingRequest;
import com.pedidos360.rabbitadmin.dto.ExchangeRequest;
import com.pedidos360.rabbitadmin.dto.QueueRequest;
import com.pedidos360.rabbitadmin.exception.ProtectedResourceException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final RabbitAdminService service = new RabbitAdminService(amqpAdmin, "pedidos360.");

    @Test
    void createsQueueWithDeadLetterExchange() {
        when(amqpAdmin.getQueueInfo("demo.queue")).thenReturn(new QueueInformation("demo.queue", 0, 0));

        var response = service.createQueue(new QueueRequest("demo.queue", true, "demo.dlx", "demo.dead", 60000));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());
        Queue queue = captor.getValue();
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "demo.dlx")
                .containsEntry("x-dead-letter-routing-key", "demo.dead")
                .containsEntry("x-message-ttl", 60000);
        assertThat(response.name()).isEqualTo("demo.queue");
    }

    @Test
    void createsDirectExchange() {
        service.createExchange(new ExchangeRequest("demo.direct", "DIRECT", null));

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("direct");
        assertThat(captor.getValue().isDurable()).isTrue();
    }

    @Test
    void createsBindingFromExchangeToQueue() {
        service.createBinding(new BindingRequest("demo.queue", "pedidos360.events", "order.*"));

        ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(captor.capture());
        assertThat(captor.getValue().getDestination()).isEqualTo("demo.queue");
        assertThat(captor.getValue().getExchange()).isEqualTo("pedidos360.events");
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("order.*");
    }

    @Test
    void refusesToDeleteMicroserviceQueues() {
        assertThatThrownBy(() -> service.deleteQueue("pedidos360.orders.payment-results.queue"))
                .isInstanceOf(ProtectedResourceException.class);

        verify(amqpAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void reportsMissingQueueOnDelete() {
        when(amqpAdmin.getQueueInfo("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.deleteQueue("missing")).isInstanceOf(ResourceNotFoundException.class);
        verify(amqpAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void refusesToCreateQueuesWithProtectedPrefix() {
        assertThatThrownBy(() -> service.createQueue(new QueueRequest("pedidos360.hack", true, null, null, null)))
                .isInstanceOf(ProtectedResourceException.class);

        verify(amqpAdmin, never()).declareQueue(any(Queue.class));
    }
}
