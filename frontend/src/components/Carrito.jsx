import { Minus, Plus, X } from 'lucide-react';
import { Boton } from './ui';

export function Carrito({ carrito, total, formatCLP, onCambiar, onPagar, procesando, onIrTienda }) {
  if (carrito.length === 0) {
    return (
      <div className="mt-16 max-w-md">
        <p className="text-lg">Tu carrito está vacío. Agrega productos desde la tienda para comprarlos con WebPay.</p>
        <Boton className="mt-6" onClick={onIrTienda}>Ir a la tienda</Boton>
      </div>
    );
  }
  return (
    <div className="mt-10 grid gap-10 lg:grid-cols-[1fr_380px]">
      <ul className="divide-y divide-tinta border-y border-tinta">
        {carrito.map((item) => (
          <li key={item.id} className="grid grid-cols-[72px_1fr_auto] items-center gap-4 py-4 sm:grid-cols-[96px_1fr_auto_auto]">
            <div className="grid aspect-square place-items-center bg-placa text-4xl" aria-hidden="true">{item.icon || '📦'}</div>
            <div>
              <h3 className="font-display text-xl uppercase leading-tight">{item.name}</h3>
              <p className="text-sm text-gris">{item.category}. {formatCLP(item.price)} c/u</p>
            </div>
            <div className="flex items-center border border-tinta">
              <button onClick={() => onCambiar(item.id, -1)} aria-label={`Quitar una unidad de ${item.name}`} className="grid size-11 place-items-center cursor-pointer hover:bg-placa"><Minus size={16} /></button>
              <span className="w-8 text-center font-semibold" aria-live="polite">{item.qty}</span>
              <button onClick={() => onCambiar(item.id, 1)} aria-label={`Agregar una unidad de ${item.name}`} className="grid size-11 place-items-center cursor-pointer hover:bg-placa"><Plus size={16} /></button>
            </div>
            <div className="col-span-3 flex items-center justify-end gap-2 sm:col-span-1">
              <span className="font-semibold">{formatCLP(item.price * item.qty)}</span>
              <button onClick={() => onCambiar(item.id, -item.qty)} aria-label={`Eliminar ${item.name} del carrito`} className="grid size-11 place-items-center cursor-pointer hover:bg-placa"><X size={18} /></button>
            </div>
          </li>
        ))}
      </ul>
      <aside className="h-fit bg-placa p-6 lg:sticky lg:top-40">
        <h2 className="font-display text-2xl font-bold uppercase">Resumen</h2>
        <dl className="mt-4 space-y-2">
          <div className="flex justify-between"><dt>Subtotal</dt><dd>{formatCLP(total)}</dd></div>
          <div className="flex justify-between"><dt>Envío</dt><dd>Gratis</dd></div>
          <div className="flex justify-between border-t border-tinta pt-3 font-display text-3xl font-bold uppercase"><dt>Total</dt><dd>{formatCLP(total)}</dd></div>
        </dl>
        <Boton className="mt-6 w-full" onClick={onPagar} disabled={procesando}>{procesando ? 'Creando pedido...' : 'Pagar con WebPay'}</Boton>
        <p className="mt-3 text-sm text-gris">Se crea el pedido y te llevamos a WebPay para completar el pago.</p>
      </aside>
    </div>
  );
}
