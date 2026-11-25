import { useState } from 'react';
import { motion } from 'motion/react';
import { Heart, Plus, Minus } from 'lucide-react';
import { Recomendaciones } from './Recomendaciones';
import { ImagenProducto, estiloCampo } from './ui';

function GrupoFiltro({ titulo, children }) {
  const [abierto, setAbierto] = useState(true);
  return (
    <div className="border-t border-tinta py-3">
      <button onClick={() => setAbierto(!abierto)} aria-expanded={abierto}
              className="flex min-h-8 w-full items-center justify-between font-display text-lg uppercase cursor-pointer">
        {titulo} {abierto ? <Minus size={16} /> : <Plus size={16} />}
      </button>
      {abierto && <div className="mt-2 space-y-1">{children}</div>}
    </div>
  );
}

export function PlacaProducto({ producto, formatCLP, onVer, onAgregar, indice = 0 }) {
  return (
    <motion.article
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.15 + indice * 0.05, duration: 0.4 }}
      className="group"
    >
      <button onClick={() => onVer(producto.id)} aria-label={`Ver detalle de ${producto.name}`}
              className="relative block aspect-square w-full overflow-hidden bg-placa cursor-pointer">
        <ImagenProducto producto={producto} tamanoEmoji="text-8xl" className="transition-transform duration-500 group-hover:scale-105" />
        <Heart size={18} className="absolute left-3 top-3 text-papel mix-blend-difference" aria-hidden="true" />
        {producto.stock <= 5 && <span className="absolute right-3 top-3 bg-tinta px-2 py-0.5 font-display text-sm uppercase text-papel">Últimas {producto.stock}</span>}
      </button>
      <div className="mt-3 flex items-start justify-between gap-3">
        <div>
          <h3 className="font-display text-lg uppercase leading-tight">{producto.name}</h3>
          <p className="font-semibold">{formatCLP(producto.price)}</p>
        </div>
        <button onClick={() => onAgregar(producto)} aria-label={`Agregar ${producto.name} al carrito`}
                className="grid size-11 shrink-0 place-items-center border border-tinta cursor-pointer transition-colors hover:bg-tinta hover:text-papel">
          <Plus size={20} />
        </button>
      </div>
    </motion.article>
  );
}

/** Catálogo: filtros en columna con reglas finas y grilla de placas, como una tienda editorial. */
export function Tienda({ productos, categoria, setCategoria, busqueda, setBusqueda, formatCLP, onVer, onAgregar, recomendacionesProps }) {
  const categorias = ['Todos', ...new Set(productos.map((p) => p.category))];
  const visibles = productos.filter((p) =>
    (categoria === 'Todos' || p.category === categoria) && p.name.toLowerCase().includes(busqueda.toLowerCase()));

  return (
    <div id="catalogo" className="mt-10 grid scroll-mt-40 gap-8 lg:grid-cols-[280px_1fr]">
      <aside aria-label="Filtros">
        <GrupoFiltro titulo="Buscar">
          <input type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)}
                 placeholder="Nombre del producto" aria-label="Buscar producto" className={estiloCampo} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Categoría">
          {categorias.map((c) => (
            <button key={c} onClick={() => setCategoria(c)} aria-pressed={categoria === c}
                    className={`flex min-h-8 w-full justify-between uppercase cursor-pointer underline-offset-4 hover:underline ${categoria === c ? 'font-bold underline' : ''}`}>
              {c}
              <span className="text-gris">({c === 'Todos' ? productos.length : productos.filter((p) => p.category === c).length})</span>
            </button>
          ))}
        </GrupoFiltro>
      </aside>

      <section>
        <Recomendaciones {...recomendacionesProps} productos={productos} formatCLP={formatCLP} onVer={onVer} />
        <p className="mb-4 border-b border-tinta pb-2 text-right font-display uppercase">{visibles.length} productos</p>
        {visibles.length === 0 ? (
          <p className="py-16 text-center text-lg">No hay productos con ese nombre en esta categoría. Prueba con otra búsqueda.</p>
        ) : (
          <div className="grid gap-x-4 gap-y-10 sm:grid-cols-2 xl:grid-cols-3">
            {visibles.map((p, i) => (
              <PlacaProducto key={p.id} producto={p} indice={i} formatCLP={formatCLP} onVer={onVer} onAgregar={onAgregar} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
