import { KeyRound, LogOut } from 'lucide-react';

export function Logotipo() {
  return (
    <div className="font-display text-xl font-extrabold uppercase leading-[0.9] tracking-tight">
      Pedidos<br />360/<br />Tienda
    </div>
  );
}

function Pestana({ activa, onClick, children, contador }) {
  return (
    <button
      onClick={onClick}
      aria-current={activa ? 'page' : undefined}
      className={`flex min-h-8 items-center gap-1.5 text-left font-display text-lg uppercase leading-tight cursor-pointer underline-offset-4 hover:underline ${activa ? 'font-bold underline decoration-2' : 'font-medium'}`}
    >
      {children}
      {contador > 0 && <span className="grid size-5 place-items-center rounded-full bg-tinta text-xs text-papel">{contador}</span>}
    </button>
  );
}

/** Cabecera editorial: logotipo apilado, navegación en columnas de texto y acciones de sesión. */
export function Cabecera({ vista, setVista, esAdmin, unidadesCarrito, cuenta, onToken, onSalir }) {
  const ir = (v) => () => setVista(v);
  return (
    <header className="sticky top-0 z-40 border-b border-tinta bg-papel">
      <div className="mx-auto flex max-w-[1440px] flex-wrap items-start justify-between gap-6 px-4 py-4 sm:px-8">
        <Logotipo />
        <nav aria-label="Principal" className="grid grid-cols-2 gap-x-10 gap-y-0.5 sm:grid-cols-[auto_auto]">
          <Pestana activa={vista === 'tienda'} onClick={ir('tienda')}>Tienda</Pestana>
          {esAdmin && <Pestana activa={vista === 'productos'} onClick={ir('productos')}>Productos</Pestana>}
          <Pestana activa={vista === 'carrito'} onClick={ir('carrito')} contador={unidadesCarrito}>Carrito</Pestana>
          {esAdmin && <Pestana activa={vista === 'usuarios'} onClick={ir('usuarios')}>Usuarios</Pestana>}
          <Pestana activa={vista === 'pedidos'} onClick={ir('pedidos')}>{esAdmin ? 'Pedidos de clientes' : 'Mis pedidos'}</Pestana>
          {esAdmin && (
            <Pestana activa={vista === 'dashboard'} onClick={ir('dashboard')}>
              <span className="size-2 rounded-full bg-acento" aria-hidden="true" /> Dashboard
            </Pestana>
          )}
        </nav>
        <div className="flex items-center gap-1">
          <div className="mr-3 hidden text-right leading-tight sm:block">
            <div className="text-sm font-semibold">{cuenta?.name || cuenta?.username}</div>
            <div className="text-xs text-gris">{esAdmin ? 'Administrador' : 'Cliente'}</div>
          </div>
          <button onClick={onToken} aria-label="Ver token JWT" title="Ver token JWT" className="grid size-11 place-items-center cursor-pointer hover:bg-placa">
            <KeyRound size={20} />
          </button>
          <button onClick={onSalir} aria-label="Cerrar sesión" title="Cerrar sesión" className="grid size-11 place-items-center cursor-pointer hover:bg-placa">
            <LogOut size={20} />
          </button>
        </div>
      </div>
    </header>
  );
}
