import { Boton, ImagenProducto, Modal } from './ui';

/** Detalle de un producto. Abrirlo llama a GET /products/{id}, que publica la interacción VER en Kafka. */
export function DetalleProducto({ producto, formatCLP, onAgregar, onCerrar }) {
  return (
    <Modal titulo={producto.name} onCerrar={onCerrar} ancho="max-w-2xl">
      <div className="grid gap-6 sm:grid-cols-2">
        <div className="aspect-square overflow-hidden bg-placa"><ImagenProducto producto={producto} tamanoEmoji="text-9xl" /></div>
        <div className="flex flex-col">
          <dl className="divide-y divide-tinta border-y border-tinta">
            {[['Categoría', producto.category], ['Precio', formatCLP(producto.price)], ['Disponibles', `${producto.stock} unidades`]].map(([k, v]) => (
              <div key={k} className="flex justify-between py-2">
                <dt className="font-display uppercase text-gris">{k}</dt>
                <dd className="font-semibold">{v}</dd>
              </div>
            ))}
          </dl>
          <Boton className="mt-auto w-full" onClick={() => { onAgregar(producto); onCerrar(); }}>Agregar al carrito</Boton>
        </div>
      </div>
    </Modal>
  );
}
