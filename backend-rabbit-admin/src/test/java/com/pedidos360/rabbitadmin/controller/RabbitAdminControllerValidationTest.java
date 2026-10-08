package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.QueueResponse;
import com.pedidos360.rabbitadmin.exception.GlobalExceptionHandler;
import com.pedidos360.rabbitadmin.exception.ProtectedResourceException;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RabbitAdminControllerValidationTest {

    private final RabbitAdminService service = mock(RabbitAdminService.class);
    private final MockMvc mockMvc = buildMockMvc();

    private MockMvc buildMockMvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new RabbitAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsValidQueue() throws Exception {
        when(service.createQueue(any())).thenReturn(new QueueResponse("demo.queue", 0, 0));

        mockMvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo.queue\",\"deadLetterExchange\":\"demo.dlx\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/rabbit/queues/demo.queue"))
                .andExpect(jsonPath("$.name").value("demo.queue"));
    }

    @Test
    void rejectsQueueWithEmptyName() throws Exception {
        mockMvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsReservedAmqPrefix() throws Exception {
        mockMvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"amq.hack\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void rejectsDeadLetterRoutingKeyWithoutExchange() throws Exception {
        mockMvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo.queue\",\"deadLetterRoutingKey\":\"dead\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deadLetterConfigValid").exists());
    }

    @Test
    void rejectsUnknownExchangeType() throws Exception {
        mockMvc.perform(post("/api/rabbit/exchanges").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo.x\",\"type\":\"queue\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").value("debe ser direct, topic, fanout o headers"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBindingWithoutRoutingKey() throws Exception {
        mockMvc.perform(post("/api/rabbit/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"demo.queue\",\"exchange\":\"demo.x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.routingKey").exists());
    }

    @Test
    void rejectsInvalidQueueNameInPath() throws Exception {
        mockMvc.perform(delete("/api/rabbit/queues/{name}", "cola con espacios"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void returnsConflictForProtectedQueues() throws Exception {
        doThrow(new ProtectedResourceException("protegida")).when(service).deleteQueue("pedidos360.x");

        mockMvc.perform(delete("/api/rabbit/queues/{name}", "pedidos360.x"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON).content("{nombre"))
                .andExpect(status().isBadRequest());
    }
}
