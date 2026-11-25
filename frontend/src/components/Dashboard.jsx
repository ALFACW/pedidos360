import { useEffect, useState } from 'react';

const top = (conteo) => Object.entries(conteo).sort((a, b) => b[1] - a[1]).slice(0, 5);

/** Métricas en tiempo real: cada interacción llega por WebSocket (DynamoDB Stream -> Lambda -> API WebSocket). */
export function Dashboard({ wsUrl }) {
  const [conectado, setConectado] = useState(false);
  const [metricas, setMetricas] = useState({ total: 0, ver: 0, carrito: 0, vistos: {}, categorias: {} });

  useEffect(() => {
    const ws = new WebSocket(wsUrl);
    ws.onopen = () => setConectado(true);
    ws.onclose = () => setConectado(false);
    ws.onmessage = (mensaje) => {
      const i = JSON.parse(mensaje.data);
      setMetricas((m) => ({
        total: m.total + 1,
        ver: m.ver + (i.accion === 'VER' ? 1 : 0),
        carrito: m.carrito + (i.accion === 'CARRITO' ? 1 : 0),
        vistos: i.accion === 'VER' ? { ...m.vistos, [i.nombreProducto]: (m.vistos[i.nombreProducto] || 0) + 1 } : m.vistos,
        categorias: { ...m.categorias, [i.categoria]: (m.categorias[i.categoria] || 0) + 1 },
      }));
    };
    return () => ws.close();
  }, [wsUrl]);

  return (
    <div className="orders-view">
      <h2>📊 Dashboard en tiempo real {conectado ? '🟢' : '🔴'}</h2>
      <div className="cart-layout">
        <div className="cart-summary-card">
          <h3>Volumen de interacciones</h3>
          <div className="summary-line total-line"><span>Total</span><strong>{metricas.total}</strong></div>
          <div className="summary-line"><span>Vistas de producto</span><strong>{metricas.ver}</strong></div>
          <div className="summary-line"><span>Agregados al carrito</span><strong>{metricas.carrito}</strong></div>
        </div>
        <div className="cart-summary-card">
          <h3>Productos más vistos</h3>
          {top(metricas.vistos).map(([nombre, n]) => (
            <div key={nombre} className="summary-line"><span>{nombre}</span><strong>{n}</strong></div>
          ))}
        </div>
        <div className="cart-summary-card">
          <h3>Categorías más activas</h3>
          {top(metricas.categorias).map(([categoria, n]) => (
            <div key={categoria} className="summary-line"><span>{categoria}</span><strong>{n}</strong></div>
          ))}
        </div>
      </div>
    </div>
  );
}
