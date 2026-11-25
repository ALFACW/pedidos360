package com.pedidos360.recomendaciones.service;

import com.pedidos360.recomendaciones.dto.InteraccionDTO;
import com.pedidos360.recomendaciones.dto.RecomendacionDTO;
import com.pedidos360.recomendaciones.model.Interaccion;
import com.pedidos360.recomendaciones.repository.InteraccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recomendación simple por similitud de categoría:
 * 1. Interés del usuario por categoría = suma de pesos de sus interacciones (VER=1, CARRITO=2, COMPRA=3).
 * 2. Candidatos = productos de esas categorías que el usuario aún no agregó al carrito ni compró.
 * 3. Puntaje = interés en la categoría + popularidad del producto entre todos los usuarios. Top 5.
 */
@Service
@RequiredArgsConstructor
public class RecomendacionService {

    private static final Map<String, Integer> PESOS = Map.of("VER", 1, "CARRITO", 2, "COMPRA", 3);

    private final InteraccionRepository interaccionRepository;

    public void registrar(InteraccionDTO dto) {
        Interaccion interaccion = new Interaccion();
        interaccion.setUsuario(dto.usuario());
        interaccion.setFecha(dto.fecha().toEpochMilli());
        interaccion.setProductoId(dto.productoId());
        interaccion.setNombreProducto(dto.nombreProducto());
        interaccion.setCategoria(dto.categoria());
        interaccion.setAccion(dto.accion());
        interaccion.setPeso(PESOS.get(dto.accion()));
        interaccionRepository.guardar(interaccion);
    }

    public List<RecomendacionDTO> recomendar(String usuario) {
        List<Interaccion> propias = interaccionRepository.buscarPorUsuario(usuario);
        Map<String, Integer> interesPorCategoria = propias.stream()
                .collect(Collectors.groupingBy(Interaccion::getCategoria, Collectors.summingInt(Interaccion::getPeso)));
        Set<Long> yaElegidos = propias.stream()
                .filter(i -> !"VER".equals(i.getAccion()))
                .map(Interaccion::getProductoId)
                .collect(Collectors.toSet());

        Map<Long, Interaccion> productos = new HashMap<>();
        Map<Long, Integer> popularidad = new HashMap<>();
        for (Interaccion i : interaccionRepository.buscarTodas()) {
            if (interesPorCategoria.containsKey(i.getCategoria()) && !yaElegidos.contains(i.getProductoId())) {
                productos.putIfAbsent(i.getProductoId(), i);
                popularidad.merge(i.getProductoId(), i.getPeso(), Integer::sum);
            }
        }

        return productos.values().stream()
                .map(p -> new RecomendacionDTO(p.getProductoId(), p.getNombreProducto(), p.getCategoria(),
                        interesPorCategoria.get(p.getCategoria()) + popularidad.get(p.getProductoId())))
                .sorted(Comparator.comparingInt(RecomendacionDTO::puntaje).reversed())
                .limit(5)
                .toList();
    }

    /** Métricas históricas del dashboard; el WebSocket suma en vivo los eventos que llegan después. */
    public Map<String, Object> metricas() {
        List<Interaccion> todas = interaccionRepository.buscarTodas();
        Map<String, Long> porAccion = todas.stream().collect(Collectors.groupingBy(Interaccion::getAccion, Collectors.counting()));
        return Map.of(
                "total", todas.size(),
                "ver", porAccion.getOrDefault("VER", 0L),
                "carrito", porAccion.getOrDefault("CARRITO", 0L),
                "compras", porAccion.getOrDefault("COMPRA", 0L),
                "vistos", contar(todas, "VER", Interaccion::getNombreProducto),
                "comprados", contar(todas, "COMPRA", Interaccion::getNombreProducto),
                "categorias", contar(todas, null, Interaccion::getCategoria),
                "ultimas", todas.stream().sorted(Comparator.comparing(Interaccion::getFecha).reversed()).limit(8).toList());
    }

    private static Map<String, Long> contar(List<Interaccion> todas, String accion, Function<Interaccion, String> clave) {
        return todas.stream()
                .filter(i -> accion == null || accion.equals(i.getAccion()))
                .collect(Collectors.groupingBy(clave, Collectors.counting()));
    }
}
