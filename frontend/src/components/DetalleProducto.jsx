import { CreditCard, ShieldCheck, Truck } from 'lucide-react';
import { motion } from 'motion/react';
import { Boton, ImagenProducto, PanelLateral } from './ui';

const BENEFICIOS = [
  [Truck, 'Envío gratis', 'Despacho en 24 a 48 horas hábiles'],
  [CreditCard, 'Pago con WebPay', 'Débito y crédito, en ambiente seguro de Transbank'],
  [ShieldCheck, 'Compra protegida', 'Pedidos y pagos validados con tu cuenta Microsoft'],
];

/** Vista previa del producto. Abrirla llama a GET /products/{id}, que publica la interacción VER en Kafka. */
export function DetalleProducto({ producto, productos, formatCLP, onAgregar, onVer, onCerrar }) {
  const relacionados = productos.filter((p) => p.category === producto.category && p.id !== producto.id).slice(0, 3);
  const ultimas = producto.stock <= 5;
  return (
    <PanelLateral titulo={producto.name} onCerrar={onCerrar}>
      <div className="aspect-[4/3] shrink-0 overflow-hidden bg-placa">
        <motion.div key={producto.id} className="size-full" initial={{ scale: 1.08 }} animate={{ scale: 1 }} transition={{ duration: 0.8 }}>
          <ImagenProducto producto={producto} tamanoEmoji="text-[10rem]" />
        </motion.div>
      </div>

      <div className="flex flex-1 flex-col p-6 sm:p-10">
        <p className="font-display text-lg uppercase text-gris">{producto.category}</p>
        <h2 className="font-display text-5xl font-extrabold uppercase leading-[0.9] sm:text-6xl">{producto.name}</h2>
        <div className="mt-6 flex items-end justify-between border-y border-tinta py-4">
          <span className="font-display text-5xl font-bold">{formatCLP(producto.price)}</span>
          <span className={`font-display text-lg uppercase ${ultimas ? 'text-error' : 'text-ok'}`}>
            {ultimas ? `Últimas ${producto.stock} unidades` : `${producto.stock} disponibles`}
          </span>
        </div>

        <ul className="mt-6 space-y-4">
          {BENEFICIOS.map(([Icono, titulo, detalle]) => (
            <li key={titulo} className="flex gap-4">
              <Icono size={24} className="mt-0.5 shrink-0" aria-hidden="true" />
              <span><strong className="block">{titulo}</strong><span className="text-gris">{detalle}</span></span>
            </li>
          ))}
        </ul>

        {relacionados.length > 0 && (
          <section className="mt-10">
            <h3 className="border-b border-tinta pb-2 font-display text-xl font-bold uppercase">Otros de {producto.category}</h3>
            <div className="mt-3 grid grid-cols-3 gap-3">
              {relacionados.map((p) => (
                <button key={p.id} onClick={() => onVer(p.id)} className="text-left cursor-pointer group">
                  <div className="aspect-square overflow-hidden bg-placa">
                    <ImagenProducto producto={p} tamanoEmoji="text-4xl" className="transition-transform duration-300 group-hover:scale-105" />
                  </div>
                  <span className="mt-1 block font-display text-sm uppercase leading-tight">{p.name}</span>
                </button>
              ))}
            </div>
          </section>
        )}

        <div className="sticky bottom-0 -mx-6 mt-auto bg-papel px-6 pt-6 sm:-mx-10 sm:px-10">
          <Boton className="w-full py-4 text-xl" onClick={() => { onAgregar(producto); onCerrar(); }}>Agregar al carrito</Boton>
        </div>
      </div>
    </PanelLateral>
  );
}
