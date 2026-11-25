package com.pedidos360.recomendaciones.service;

import com.pedidos360.recomendaciones.dto.RecomendacionDTO;
import com.pedidos360.recomendaciones.model.Interaccion;
import com.pedidos360.recomendaciones.repository.InteraccionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecomendacionServiceTest {

    private final InteraccionRepository repository = mock(InteraccionRepository.class);
    private final RecomendacionService service = new RecomendacionService(repository);

    private static Interaccion interaccion(String usuario, long productoId, String categoria, String accion) {
        Interaccion i = new Interaccion();
        i.setUsuario(usuario);
        i.setProductoId(productoId);
        i.setNombreProducto("producto-" + productoId);
        i.setCategoria(categoria);
        i.setAccion(accion);
        i.setPeso("CARRITO".equals(accion) ? 2 : 1);
        return i;
    }

    @Test
    void recomiendaProductosDeLaMismaCategoriaQueNoEstanEnElCarrito() {
        List<Interaccion> deAna = List.of(
                interaccion("ana", 1, "Monitores", "VER"),
                interaccion("ana", 1, "Monitores", "CARRITO"));
        when(repository.buscarPorUsuario("ana")).thenReturn(deAna);
        when(repository.buscarTodas()).thenReturn(List.of(
                deAna.get(0), deAna.get(1),
                interaccion("luis", 2, "Monitores", "VER"),
                interaccion("luis", 3, "Monitores", "CARRITO"),
                interaccion("luis", 4, "Audio", "CARRITO")));

        List<RecomendacionDTO> resultado = service.recomendar("ana");

        assertEquals(List.of(3L, 2L), resultado.stream().map(RecomendacionDTO::productoId).toList());
    }
}
