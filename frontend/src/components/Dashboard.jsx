import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { AlertTriangle, Package, ShieldAlert, XCircle } from 'lucide-react';
import { ImagenProducto } from './ui';

const VERBOS = { VER: 'vio', CARRITO: 'agregó al carrito', COMPRA: 'compró' };
const VACIO = { total: 0, usuarios: 0, ver: 0, carrito: 0, compras: 0, vistos: {}, comprados: {}, categorias: {}, ultimas: [] };
const sumar = (mapa, clave) => ({ ...mapa, [clave]: (mapa[clave] || 0) + 1 });
const porcentaje = (parte, total) => (total ? Math.round((parte / total) * 100) : 0);

function Titulo({ children }) {
  return <h2 className="border-b border-tinta pb-2 font-display text-2xl font-bold uppercase">{children}</h2>;
}

function Kpi({ etiqueta, valor, nota, destacado }) {
  return (
    <div className="border-t border-tinta pt-2">
      <p className="font-display uppercase text-gris">{etiqueta}</p>
      <motion.p key={valor} initial={{ y: -10, opacity: 0 }} animate={{ y: 0, opacity: 1 }}
                className={`font-display text-5xl font-extrabold leading-none sm:text-6xl ${destacado ? 'text-acento' : ''}`}>
        {valor}
      </motion.p>
      {nota && <p className="mt-1 text-sm text-gris">{nota}</p>}
    </div>
  );
}

function Ranking({ titulo, filas, vacio }) {
  const maximo = filas[0]?.[1] || 1;
  return (
    <section>
      <Titulo>{titulo}</Titulo>
      {filas.length === 0 ? <p className="py-4 text-gris">{vacio}</p> : (
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

/** Bloque de alerta con su contador y el detalle de cada caso. */
function BloqueAlerta({ Icono, titulo, ayuda, activa, children }) {
  return (
    <div className={`border p-5 ${activa ? 'border-tinta' : 'border-tinta/20'}`}>
      <div className="flex items-start gap-3">
        <Icono className={activa ? 'text-error' : 'text-gris'} aria-hidden="true" />
        <span><strong className="font-display text-xl uppercase">{titulo}</strong><span className="block text-sm text-gris">{ayuda}</span></span>
      </div>
      {activa && <ul className="mt-3 max-h-72 divide-y divide-tinta/20 overflow-y-auto border-t border-tinta/20">{children}</ul>}
    </div>
  );
}

/** Quién, qué, cuánto y cuándo de un pedido. */
function FilaPedido({ pedido, formatCLP }) {
  return (
    <li className="py-2 text-sm">
      <div className="flex justify-between gap-2"><strong className="font-display text-base uppercase">{pedido.orderNumber}</strong><strong>{formatCLP(pedido.total)}</strong></div>
      <div>{pedido.customerEmail || pedido.user}</div>
      <div className="text-gris">{(pedido.items || []).map((it) => `${it.name} x ${it.qty}`).join(', ')}</div>
      <div className="text-gris">Creado el {pedido.createdAt}</div>
    </li>
  );
}

const top = (conteo) => Object.entries(conteo).sort((a, b) => b[1] - a[1]).slice(0, 5);

/**
 * Qué medimos y por qué:
 * - Ventas (ms-pedidos): cuánto se vende y cuánto queda pendiente de pago.
 * - Embudo (Kafka -> DynamoDB): en qué paso se pierden los clientes (vista -> carrito -> compra).
 * - Rankings: qué interesa, qué se vende y qué se mira pero no se compra.
 * - Alertas: fraude (ms-pagos), stock bajo (ms-productos) y pagos pendientes.
 * El histórico llega de /metricas y cada evento nuevo por WebSocket (DynamoDB Stream -> Lambda).
 */
export function Dashboard({ wsUrl, apiBaseUrl, getAccessToken, pedidos, productos, formatCLP, onCompra }) {
  const [conectado, setConectado] = useState(false);
  const [m, setM] = useState(VACIO);
  const [fraude, setFraude] = useState({ alertas: [], tarjetasBloqueadas: 0 });

  useEffect(() => {
    const obtener = async (ruta) => {
      const res = await fetch(`${apiBaseUrl}${ruta}`, { headers: { Authorization: `Bearer ${await getAccessToken()}` } });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return res.json();
    };
    obtener('/api/v1/recomendaciones/metricas').then(setM).catch((e) => console.warn('Métricas:', e));
    const cargarFraude = () => obtener('/api/v1/pagos/alertas').then(setFraude).catch((e) => console.warn('Fraude:', e));
    cargarFraude();
    const intervalo = setInterval(cargarFraude, 15000);

    const ws = new WebSocket(wsUrl);
    ws.onopen = () => setConectado(true);
    ws.onclose = () => setConectado(false);
    ws.onmessage = (mensaje) => {
      const i = JSON.parse(mensaje.data);
      if (i.accion === 'COMPRA') onCompra();
      setM((m) => ({
        ...m,
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
    return () => { ws.close(); clearInterval(intervalo); };
  }, [wsUrl, apiBaseUrl]);

  const pagados = pedidos.filter((p) => p.status === 'CONFIRMADO');
  const pendientes = pedidos.filter((p) => p.status === 'PENDIENTE_PAGO');
  const cancelados = pedidos.filter((p) => p.status === 'CANCELADO');
  const ventas = pagados.reduce((suma, p) => suma + Number(p.total), 0);
  const sinCompra = top(Object.fromEntries(Object.entries(m.vistos).filter(([nombre]) => !m.comprados[nombre])));
  const stockBajo = productos.filter((p) => p.stock <= 5);

  return (
    <div className="mt-10 space-y-14">
      <p className="flex items-center gap-2 font-display uppercase" aria-live="polite">
        <span className={`size-3 rounded-full ${conectado ? 'animate-pulse bg-acento' : 'bg-gris'}`} aria-hidden="true" />
        {conectado ? 'En vivo: cada acción de los clientes llega al instante' : 'Sin conexión con el WebSocket'}
      </p>

      <section>
        <Titulo>Ventas</Titulo>
        <div className="mt-4 grid grid-cols-2 gap-6 lg:grid-cols-4">
          <Kpi etiqueta="Ventas confirmadas" valor={formatCLP(ventas)} destacado />
          <Kpi etiqueta="Pedidos pagados" valor={pagados.length} />
          <Kpi etiqueta="Ticket promedio" valor={formatCLP(pagados.length ? ventas / pagados.length : 0)} />
          <Kpi etiqueta="Clientes activos" valor={m.usuarios} nota="Con al menos una interacción" />
        </div>
      </section>

      <section>
        <Titulo>Embudo de conversión</Titulo>
        <div className="mt-4 grid gap-6 md:grid-cols-3">
          {[
            ['Vistas de producto', m.ver, 'Clics en un producto'],
            ['Agregados al carrito', m.carrito, `${porcentaje(m.carrito, m.ver)}% de las vistas`],
            ['Compras pagadas', m.compras, `${porcentaje(m.compras, m.carrito)}% de lo agregado al carrito`],
          ].map(([etiqueta, valor, nota], i) => (
            <div key={etiqueta} className="bg-placa p-5" style={{ opacity: 1 - i * 0.12 }}>
              <Kpi etiqueta={etiqueta} valor={valor} nota={nota} />
            </div>
          ))}
        </div>
      </section>

      <div className="grid gap-10 lg:grid-cols-3">
        <Ranking titulo="Más vistos" filas={top(m.vistos)} vacio="Todavía nadie ha visto productos." />
        <Ranking titulo="Más comprados" filas={top(m.comprados)} vacio="Todavía no hay compras pagadas." />
        <Ranking titulo="Interés sin compra" filas={sinCompra} vacio="Todo lo visto se ha comprado al menos una vez." />
      </div>

      <section>
        <Titulo>Alertas</Titulo>
        <div className="mt-4 grid gap-6 md:grid-cols-2">
          <BloqueAlerta Icono={AlertTriangle} titulo={`${pendientes.length} pedidos pendientes de pago`}
                        ayuda="Crearon el pedido y no terminaron en WebPay" activa={pendientes.length > 0}>
            {pendientes.map((p) => <FilaPedido key={p.id} pedido={p} formatCLP={formatCLP} />)}
          </BloqueAlerta>
          <BloqueAlerta Icono={XCircle} titulo={`${cancelados.length} pedidos cancelados`}
                        ayuda="Cancelados antes de pagar" activa={cancelados.length > 0}>
            {cancelados.map((p) => <FilaPedido key={p.id} pedido={p} formatCLP={formatCLP} />)}
          </BloqueAlerta>
          <BloqueAlerta Icono={Package} titulo={`${stockBajo.length} productos con stock bajo`}
                        ayuda="5 unidades o menos: hay que reponer" activa={stockBajo.length > 0}>
            {stockBajo.map((p) => (
              <li key={p.id} className="flex items-center gap-3 py-2">
                <span className="size-12 shrink-0 overflow-hidden bg-placa"><ImagenProducto producto={p} tamanoEmoji="text-2xl" /></span>
                <span className="flex-1"><span className="block font-display uppercase leading-tight">{p.name}</span><span className="text-sm text-gris">{p.category}</span></span>
                <strong className="font-display text-2xl text-error">{p.stock} u.</strong>
              </li>
            ))}
          </BloqueAlerta>
          <BloqueAlerta Icono={ShieldAlert} titulo={`${fraude.tarjetasBloqueadas} tarjetas bloqueadas por fraude`}
                        ayuda="Misma tarjeta más de 3 veces en 10 segundos (Kafka, ms-pagos)" activa={fraude.tarjetasBloqueadas > 0}>
            {fraude.alertas.map((a) => (
              <li key={a.id} className="grid grid-cols-[1fr_auto] gap-x-3 py-2 text-sm">
                <span><strong>Tarjeta {a.tarjeta}</strong> de {a.cliente}</span>
                <span className="font-semibold text-error">{a.intentos} intentos</span>
                <span className="col-span-2 text-gris">{new Date(a.fecha).toLocaleString('es-CL')}</span>
              </li>
            ))}
          </BloqueAlerta>
        </div>
      </section>

      <section>
        <Titulo>Actividad en vivo</Titulo>
        {m.ultimas.length === 0 && <p className="py-4 text-gris">Cuando un cliente vea un producto, lo agregue al carrito o compre, aparecerá aquí.</p>}
        <ul className="divide-y divide-tinta/20">
          <AnimatePresence initial={false}>
            {m.ultimas.map((i) => (
              <motion.li key={`${i.usuario}-${i.fecha}-${i.productoId}`} layout initial={{ opacity: 0, x: -24, backgroundColor: '#ffe3ef' }}
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
