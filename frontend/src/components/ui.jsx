import { motion } from 'motion/react';
import { X } from 'lucide-react';

const VARIANTES = {
  primario: 'bg-tinta text-papel hover:bg-tinta/80',
  secundario: 'border border-tinta hover:bg-tinta hover:text-papel',
  texto: 'underline-offset-4 hover:underline',
};

export function Boton({ variante = 'primario', className = '', ...props }) {
  return (
    <button
      className={`min-h-11 px-5 font-display text-base font-semibold uppercase tracking-wide transition-colors duration-200 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed ${VARIANTES[variante]} ${className}`}
      {...props}
    />
  );
}

export const estiloCampo = 'w-full min-h-11 border-b border-tinta bg-transparent px-1 text-base outline-none focus:border-b-2';

export function Campo({ etiqueta, children }) {
  return (
    <label className="block space-y-1">
      <span className="font-display text-sm font-semibold uppercase tracking-wide text-gris">{etiqueta}</span>
      {children}
    </label>
  );
}

/** Título gigante de cada vista, al estilo "SECCIÓN/DETALLE". */
export function TituloPagina({ seccion, detalle }) {
  return (
    <motion.h1
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
      className="font-display text-6xl font-extrabold uppercase leading-[0.85] tracking-tight sm:text-8xl lg:text-9xl"
    >
      {seccion}/<span className="text-gris">{detalle}</span>
    </motion.h1>
  );
}

export function Modal({ titulo, onCerrar, children, ancho = 'max-w-lg' }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-tinta/60 p-4" onClick={onCerrar}>
      <motion.div
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className={`w-full ${ancho} max-h-[90vh] overflow-y-auto bg-papel p-6 sm:p-8`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-6 flex items-start justify-between gap-4 border-b border-tinta pb-3">
          <h2 className="font-display text-3xl font-bold uppercase leading-none">{titulo}</h2>
          <button onClick={onCerrar} aria-label="Cerrar" className="grid size-11 place-items-center cursor-pointer hover:bg-placa">
            <X size={20} />
          </button>
        </div>
        {children}
      </motion.div>
    </div>
  );
}
