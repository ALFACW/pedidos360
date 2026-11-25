import { useEffect, useState } from 'react';

/** Productos recomendados por ms-recomendaciones según la navegación del usuario (eventos Kafka). */
export function Recomendaciones({ apiBaseUrl, getAccessToken, actualizar, onVer }) {
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
    <div className="category-filters">
      <strong>✨ Recomendado para ti:</strong>
      {recomendaciones.map((r) => (
        <button key={r.productoId} className="cat-pill" onClick={() => onVer(r.productoId)}>
          {r.nombreProducto} · {r.categoria}
        </button>
      ))}
    </div>
  );
}
