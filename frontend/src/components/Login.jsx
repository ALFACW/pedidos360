import { useState } from 'react';
import { motion } from 'motion/react';
import { Logotipo } from './Cabecera';
import { Boton, Modal } from './ui';

/** Pantalla de entrada: el título gigante es lo memorable; el resto queda en silencio. */
export function Login({ onLogin, apiBaseUrl }) {
  const [estado, setEstado] = useState(null);

  const probarConexion = async () => {
    const consultar = (ruta) => fetch(`${apiBaseUrl}/api/v1/${ruta}/public`).then((r) => r.json()).catch((e) => ({ error: e.message }));
    const [pedidos, productos, usuarios] = await Promise.all(['orders', 'products', 'users'].map(consultar));
    setEstado({ pedidos, productos, usuarios });
  };

  return (
    <div className="mx-auto flex min-h-screen max-w-[1440px] flex-col justify-between px-4 py-6 sm:px-8">
      <Logotipo />
      <main>
        <motion.h1
          initial={{ opacity: 0, y: 32 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
          className="font-display text-7xl font-extrabold uppercase leading-[0.82] tracking-tight sm:text-9xl lg:text-[11rem]"
        >
          Tecnología/<br /><span className="text-gris">en tu puerta</span>
        </motion.h1>
        <p className="mt-6 max-w-md text-lg">Compra con tu cuenta Microsoft y paga con WebPay. Te avisamos por correo cuando tu pedido esté confirmado.</p>
        <Boton onClick={onLogin} className="mt-8 px-8 text-lg">Iniciar sesión con Microsoft</Boton>
      </main>
      <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-tinta pt-3 text-sm">
        <span>Pedidos360. Cloud Native en AWS.</span>
        <Boton variante="texto" onClick={probarConexion} className="px-0 text-sm">Probar conexión con el API Gateway</Boton>
      </footer>
      {estado && (
        <Modal titulo="Conexión con API Gateway" onCerrar={() => setEstado(null)}>
          <pre className="overflow-x-auto bg-placa p-4 text-xs">{JSON.stringify(estado, null, 2)}</pre>
        </Modal>
      )}
    </div>
  );
}
