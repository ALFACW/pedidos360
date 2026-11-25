import { motion } from 'motion/react';
import { Boton } from './ui';

/** Portada de la tienda: foto a todo el ancho en blanco y negro y titular de campaña. */
export function Hero({ onVerCategoria }) {
  const irAlCatalogo = () => document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
  return (
    <section aria-label="Campaña" className="relative h-[80vh] min-h-[540px] overflow-hidden bg-tinta text-papel">
      <motion.img
        src="assets/hero.jpg"
        alt="Escritorio con monitor, notebook, teclado, mouse y audífonos"
        initial={{ scale: 1.12 }}
        animate={{ scale: 1 }}
        transition={{ duration: 1.6, ease: [0.22, 1, 0.36, 1] }}
        className="absolute inset-0 size-full object-cover grayscale"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-tinta via-tinta/40 to-transparent" />
      <div className="relative mx-auto flex h-full max-w-[1440px] flex-col justify-end px-4 pb-10 sm:px-8 sm:pb-14">
        <motion.h1
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
          className="font-display text-7xl font-extrabold uppercase leading-[0.82] tracking-tight sm:text-9xl lg:text-[12rem]"
        >
          Tu escritorio/<br />completo
        </motion.h1>
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.8, duration: 0.6 }}
                    className="mt-6 flex flex-wrap items-end justify-between gap-6">
          <p className="max-w-md text-lg">Monitores, teclados, audio y sillas con envío gratis. Pagas con WebPay y te avisamos por correo.</p>
          <div className="flex flex-wrap gap-3">
            <Boton variante="claro" onClick={irAlCatalogo} className="px-8 text-lg">Ver productos</Boton>
            <Boton variante="secundario" onClick={() => { onVerCategoria('Monitores'); irAlCatalogo(); }}
                   className="border-papel px-8 text-lg hover:bg-papel hover:text-tinta">Monitores</Boton>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
