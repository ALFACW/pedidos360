import { useEffect, useState } from 'react';
import { ImagenProducto } from './ui';

/** Productos recomendados por ms-recomendaciones según la navegación del usuario (eventos Kafka). */
export function Recomendaciones({ apiBaseUrl, getAccessToken, actualizar, productos, formatCLP, onVer }) {
  const [recomendaciones, setRecomendaciones] = useState([]);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${apiBaseUrl}/api/v1/recomendaciones`, {
          headers: { Authorization: `Bearer ${await getAccessToken()}` },
        });
        if (res.ok) setRecomendaciones(await res.json());
      } catch (e) {
        console.warn('No se pudieron cargar las recomendaciones:', e);
      }
    })();
  }, [apiBaseUrl, actualizar]);

  if (recomendaciones.length === 0) return null;
  return (
    <section aria-label="Recomendado para ti" className="mb-12">
      <h2 className="mb-3 border-b border-tinta pb-2 font-display text-2xl font-bold uppercase">Recomendado para ti</h2>
      <div className="flex gap-4 overflow-x-auto pb-2">
        {recomendaciones.map((r) => {
          const producto = productos.find((p) => p.id === r.productoId);
          return (
            <button key={r.productoId} onClick={() => onVer(r.productoId)}
                    className="flex min-w-60 items-center gap-3 bg-placa p-3 text-left cursor-pointer transition-colors hover:bg-tinta hover:text-papel">
              <span className="size-16 shrink-0 overflow-hidden"><ImagenProducto producto={producto || { name: r.nombreProducto }} tamanoEmoji="text-4xl" /></span>
              <span>
                <span className="block font-display uppercase leading-tight">{r.nombreProducto}</span>
                <span className="text-sm">{producto ? formatCLP(producto.price) : r.categoria}</span>
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
