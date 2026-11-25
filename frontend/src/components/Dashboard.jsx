import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';

const VERBOS = { VER: 'vio', CARRITO: 'agregó al carrito', COMPRA: 'compró' };
const VACIO = { total: 0, ver: 0, carrito: 0, compras: 0, vistos: {}, comprados: {}, categorias: {}, ultimas: [] };
const sumar = (mapa, clave) => ({ ...mapa, [clave]: (mapa[clave] || 0) + 1 });

function Kpi({ etiqueta, valor, destacado }) {
  return (
    <div className="border-t border-tinta pt-2">
      <p className="font-display uppercase text-gris">{etiqueta}</p>
      <motion.p key={valor} initial={{ y: -12, opacity: 0 }} animate={{ y: 0, opacity: 1 }}
                className={`font-display text-7xl font-extrabold leading-none sm:text-8xl ${destacado ? 'text-acento' : ''}`}>
        {valor}
      </motion.p>
    </div>
  );
}

function Ranking({ titulo, conteo }) {
  const filas = Object.entries(conteo).sort((a, b) => b[1] - a[1]).slice(0, 5);
  const maximo = filas[0]?.[1] || 1;
  return (
    <section>
      <h2 className="border-b border-tinta pb-2 font-display text-2xl font-bold uppercase">{titulo}</h2>
      {filas.length === 0 ? <p className="py-4 text-gris">Sin datos todavía.</p> : (
        <ol className="mt-3 space-y-3">
          {filas.map(([nombre, n]) => (
            <li key={nombre}>
              <div className="flex justify-between gap-2 text-sm"><span className="uppercase">{nombre}</span><strong>{n}</strong></div>
              <motion.div className="mt-1 h-2 bg-tinta" initial={false} animate={{ width: `${(n / maximo) * 100}%` }} />
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

/** Métricas en tiempo real: histórico desde /metricas (DynamoDB) + cada evento nuevo por WebSocket (Stream -> Lambda). */
export function Dashboard({ wsUrl, apiBaseUrl, getAccessToken }) {
  const [conectado, setConectado] = useState(false);
  const [m, setM] = useState(VACIO);

  useEffect(() => {
    (async () => {
      const res = await fetch(`${apiBaseUrl}/api/v1/recomendaciones/metricas`, {
        headers: { Authorization: `Bearer ${await getAccessToken()}` },
      });
      if (res.ok) setM(await res.json());
    })().catch((e) => console.warn('No se pudieron cargar las métricas:', e));

    const ws = new WebSocket(wsUrl);
    ws.onopen = () => setConectado(true);
    ws.onclose = () => setConectado(false);
    ws.onmessage = (mensaje) => {
      const i = JSON.parse(mensaje.data);
      setM((m) => ({
        total: m.total + 1,
        ver: m.ver + (i.accion === 'VER' ? 1 : 0),
        carrito: m.carrito + (i.accion === 'CARRITO' ? 1 : 0),
        compras: m.compras + (i.accion === 'COMPRA' ? 1 : 0),
        vistos: i.accion === 'VER' ? sumar(m.vistos, i.nombreProducto) : m.vistos,
        comprados: i.accion === 'COMPRA' ? sumar(m.comprados, i.nombreProducto) : m.comprados,
        categorias: sumar(m.categorias, i.categoria),
        ultimas: [i, ...m.ultimas].slice(0, 8),
      }));
    };
    return () => ws.close();
  }, [wsUrl, apiBaseUrl]);

  return (
    <div className="mt-10 space-y-12">
      <p className="flex items-center gap-2 font-display uppercase" aria-live="polite">
        <span className={`size-3 rounded-full ${conectado ? 'animate-pulse bg-acento' : 'bg-gris'}`} aria-hidden="true" />
        {conectado ? 'Conectado: los eventos de los clientes llegan en vivo' : 'Sin conexión con el WebSocket'}
      </p>

      <div className="grid grid-cols-2 gap-6 lg:grid-cols-4">
        <Kpi etiqueta="Interacciones" valor={m.total} />
        <Kpi etiqueta="Vistas" valor={m.ver} />
        <Kpi etiqueta="Al carrito" valor={m.carrito} />
        <Kpi etiqueta="Compras" valor={m.compras} destacado />
      </div>

      <div className="grid gap-10 lg:grid-cols-3">
        <Ranking titulo="Más vistos" conteo={m.vistos} />
        <Ranking titulo="Más comprados" conteo={m.comprados} />
        <Ranking titulo="Categorías activas" conteo={m.categorias} />
      </div>

      <section>
        <h2 className="border-b border-tinta pb-2 font-display text-2xl font-bold uppercase">Actividad en vivo</h2>
        {m.ultimas.length === 0 && <p className="py-4 text-gris">Cuando un cliente vea un producto, lo agregue al carrito o compre, aparecerá aquí.</p>}
        <ul className="divide-y divide-tinta/20">
          <AnimatePresence initial={false}>
            {m.ultimas.map((i) => (
              <motion.li key={`${i.usuario}-${i.fecha}`} layout initial={{ opacity: 0, x: -24, backgroundColor: '#ffe3ef' }}
                         animate={{ opacity: 1, x: 0, backgroundColor: '#ffffff' }} transition={{ duration: 0.6 }}
                         className="flex flex-wrap items-baseline justify-between gap-2 py-3">
                <span><strong>{i.usuario.split('@')[0]}</strong> {VERBOS[i.accion] || i.accion} <span className="font-display text-lg uppercase">{i.nombreProducto}</span></span>
                <span className="text-sm text-gris">{new Date(i.fecha).toLocaleTimeString('es-CL')}</span>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      </section>
    </div>
  );
}
