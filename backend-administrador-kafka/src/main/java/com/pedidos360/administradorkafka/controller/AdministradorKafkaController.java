package com.pedidos360.administradorkafka.controller;

import com.pedidos360.administradorkafka.dto.ModificacionTopicoDTO;
import com.pedidos360.administradorkafka.dto.TopicoDTO;
import com.pedidos360.administradorkafka.service.AdministradorKafkaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GET    /api/v1/kafka/topicos            lista los tópicos
 * GET    /api/v1/kafka/topicos/{nombre}   particiones, réplicas, líder, ISR y configuración
 * POST   /api/v1/kafka/topicos            crea un tópico
 * PUT    /api/v1/kafka/topicos/{nombre}   aumenta particiones y/o cambia retención y cleanup policy
 * DELETE /api/v1/kafka/topicos/{nombre}   elimina un tópico
 * GET    /api/v1/kafka/grupos             lista los consumer groups
 * GET    /api/v1/kafka/grupos/{id}        offsets y lag por partición
 */
@RestController
@RequestMapping("/api/v1/kafka")
public class AdministradorKafkaController {

    private final AdministradorKafkaService service;

    public AdministradorKafkaController(AdministradorKafkaService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/topicos")
    public Set<String> listarTopicos() throws Exception {
        return service.listarTopicos();
    }

    @GetMapping("/topicos/{nombre}")
    public Map<String, Object> describirTopico(@PathVariable String nombre) throws Exception {
        return service.describirTopico(nombre);
    }

    @PostMapping("/topicos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearTopico(@Valid @RequestBody TopicoDTO topico) throws Exception {
        service.crearTopico(topico);
        return service.describirTopico(topico.nombre());
    }

    @PutMapping("/topicos/{nombre}")
    public Map<String, Object> modificarTopico(@PathVariable String nombre, @Valid @RequestBody ModificacionTopicoDTO cambios) throws Exception {
        service.modificarTopico(nombre, cambios);
        return service.describirTopico(nombre);
    }

    @DeleteMapping("/topicos/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTopico(@PathVariable String nombre) throws Exception {
        service.eliminarTopico(nombre);
    }

    @GetMapping("/grupos")
    public List<String> listarGrupos() throws Exception {
        return service.listarGrupos();
    }

    @GetMapping("/grupos/{id}")
    public List<Map<String, Object>> describirGrupo(@PathVariable String id) throws Exception {
        return service.describirGrupo(id);
    }
}
