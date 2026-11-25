package com.pedidos360.administradorkafka.service;

import com.pedidos360.administradorkafka.dto.ModificacionTopicoDTO;
import com.pedidos360.administradorkafka.dto.TopicoDTO;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Toda la administración de Kafka (tópicos, configuraciones y consumer groups) pasa por este servicio. */
@Service
public class AdministradorKafkaService {

    private final AdminClient admin;

    public AdministradorKafkaService(KafkaAdmin kafkaAdmin) {
        this.admin = AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @PreDestroy
    void cerrar() {
        admin.close();
    }

    public TreeSet<String> listarTopicos() throws Exception {
        return new TreeSet<>(admin.listTopics().names().get());
    }

    public Map<String, Object> describirTopico(String nombre) throws Exception {
        TopicDescription descripcion = admin.describeTopics(List.of(nombre)).allTopicNames().get().get(nombre);
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        var config = admin.describeConfigs(List.of(recurso)).all().get().get(recurso);
        return Map.of(
                "nombre", nombre,
                "particiones", descripcion.partitions().stream().map(p -> Map.of(
                        "particion", p.partition(),
                        "lider", p.leader().id(),
                        "replicas", p.replicas().stream().map(n -> n.id()).toList(),
                        "enSincronia", p.isr().stream().map(n -> n.id()).toList())).toList(),
                "retencionMs", config.get(TopicConfig.RETENTION_MS_CONFIG).value(),
                "cleanupPolicy", config.get(TopicConfig.CLEANUP_POLICY_CONFIG).value(),
                "minInsyncReplicas", config.get(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG).value());
    }

    public void crearTopico(TopicoDTO topico) throws Exception {
        NewTopic nuevo = new NewTopic(topico.nombre(), topico.particiones(), topico.replicas()).configs(Map.of(
                TopicConfig.RETENTION_MS_CONFIG, topico.retencionMs().toString(),
                TopicConfig.CLEANUP_POLICY_CONFIG, topico.cleanupPolicy()));
        admin.createTopics(List.of(nuevo)).all().get();
    }

    public void modificarTopico(String nombre, ModificacionTopicoDTO cambios) throws Exception {
        if (cambios.particiones() != null) {
            admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(cambios.particiones()))).all().get();
        }
        List<AlterConfigOp> operaciones = new ArrayList<>();
        if (cambios.retencionMs() != null) {
            operaciones.add(new AlterConfigOp(new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, cambios.retencionMs().toString()), AlterConfigOp.OpType.SET));
        }
        if (cambios.cleanupPolicy() != null) {
            operaciones.add(new AlterConfigOp(new ConfigEntry(TopicConfig.CLEANUP_POLICY_CONFIG, cambios.cleanupPolicy()), AlterConfigOp.OpType.SET));
        }
        if (!operaciones.isEmpty()) {
            admin.incrementalAlterConfigs(Map.of(new ConfigResource(ConfigResource.Type.TOPIC, nombre), operaciones)).all().get();
        }
    }

    public void eliminarTopico(String nombre) throws Exception {
        if (nombre.startsWith("__")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Los tópicos internos de Kafka no se pueden eliminar");
        }
        admin.deleteTopics(List.of(nombre)).all().get();
    }

    public List<String> listarGrupos() throws Exception {
        return admin.listConsumerGroups().all().get().stream().map(ConsumerGroupListing::groupId).sorted().toList();
    }

    /** Por partición: offset confirmado por el grupo, último offset del tópico y lag (mensajes pendientes). */
    public List<Map<String, Object>> describirGrupo(String grupo) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> confirmados =
                admin.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata().get();
        var finales = admin.listOffsets(confirmados.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()))).all().get();
        return confirmados.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.toString().compareTo(b.toString())))
                .map(e -> Map.<String, Object>of(
                        "topico", e.getKey().topic(),
                        "particion", e.getKey().partition(),
                        "offsetConfirmado", e.getValue().offset(),
                        "offsetFinal", finales.get(e.getKey()).offset(),
                        "lag", finales.get(e.getKey()).offset() - e.getValue().offset()))
                .toList();
    }
}
