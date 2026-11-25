import { RefreshCw } from 'lucide-react';
import { Boton } from './ui';

// Estado del pedido según ms-pedidos (se actualiza cuando llega payment.approved / payment.rejected por RabbitMQ)
const ESTADOS = {
  PENDIENTE_PAGO: { texto: 'Pendiente de pago', estilo: 'border border-tinta' },
  CONFIRMADO: { texto: 'Pagado', estilo: 'bg-ok text-papel' },
  PAGO_RECHAZADO: { texto: 'Pago rechazado', estilo: 'bg-error text-papel' },
  CANCELADO: { texto: 'Cancelado', estilo: 'bg-papel text-gris line-through' },
};

export function Pedidos({ pedidos, esAdmin, formatCLP, onActualizar, onPagar, onCancelar }) {
  return (
    <div className="mt-10">
      <div className="mb-4 flex items-center justify-between border-b border-tinta pb-2">
        <p className="font-display uppercase">{pedidos.length} pedidos</p>
        <Boton variante="texto" onClick={onActualizar} className="flex items-center gap-2 px-0"><RefreshCw size={16} /> Actualizar</Boton>
      </div>
      {pedidos.length === 0 ? (
        <p className="py-16 text-lg">{esAdmin ? 'Todavía no hay pedidos de clientes.' : 'Todavía no tienes pedidos. Cuando compres, aparecerán aquí con su estado de pago.'}</p>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {pedidos.map((o) => {
            const estado = ESTADOS[o.status] || { texto: o.status, estilo: 'border border-tinta' };
            return (
              <li key={o.id} className="flex flex-col bg-placa p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-display text-2xl font-bold uppercase leading-none">{o.orderNumber || `PED-${o.id}`}</h3>
                    {esAdmin && <p className="mt-1 text-sm text-gris">{o.customerEmail || o.user}</p>}
                  </div>
                  <span className={`px-2 py-0.5 font-display text-sm uppercase ${estado.estilo}`}>{estado.texto}</span>
                </div>
                <ul className="my-4 space-y-1 text-sm">
                  {(Array.isArray(o.items) ? o.items : []).map((it, i) => (
                    <li key={i} className="flex justify-between gap-2"><span>{it.name} x {it.qty}</span><span>{formatCLP(it.price * it.qty)}</span></li>
                  ))}
                </ul>
                <div className="mt-auto border-t border-tinta pt-3">
                  <span className="font-display text-2xl font-bold">{formatCLP(o.total)}</span>
                  {(o.status === 'PENDIENTE_PAGO' || o.status === 'PAGO_RECHAZADO') && (
                    <div className="mt-3 flex gap-2">
                      {o.status === 'PENDIENTE_PAGO' && !esAdmin && <Boton className="flex-1" onClick={() => onPagar(o)}>Reanudar pago</Boton>}
                      <Boton variante="secundario" className="flex-1" onClick={() => onCancelar(o)}>Cancelar pedido</Boton>
                    </div>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
