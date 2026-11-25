/** Detalle de un producto. Abrirlo llama a GET /products/{id}, que publica la interacción VER en Kafka. */
export function DetalleProducto({ producto, formatCLP, onAgregar, onCerrar }) {
  return (
    <div className="modal-backdrop" onClick={onCerrar}>
      <div className="modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="card-icon">{producto.icon || '📦'}</div>
        <span className="card-category">{producto.category}</span>
        <h3>{producto.name}</h3>
        <p>{formatCLP(producto.price)} · Stock: {producto.stock} uds.</p>
        <div className="modal-actions">
          <button className="btn-secondary" onClick={onCerrar}>Cerrar</button>
          <button className="btn-primary" onClick={() => { onAgregar(producto); onCerrar(); }}>+ Añadir al carrito</button>
        </div>
      </div>
    </div>
  );
}
