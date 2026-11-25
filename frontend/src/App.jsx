import { useMsal } from '@azure/msal-react';
import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { loginRequest } from './msalConfig';
import { Login } from './components/Login';
import { Cabecera } from './components/Cabecera';
import { TituloPagina } from './components/ui';
import { Tienda } from './components/Tienda';
import { Hero } from './components/Hero';
import { Carrito } from './components/Carrito';
import { Pedidos } from './components/Pedidos';
import { AdminProductos, AdminUsuarios } from './components/Admin';
import { Dashboard } from './components/Dashboard';
import { PagoWebpay, useRetornoWebpay } from './components/PagoWebpay';
import { DetalleProducto } from './components/DetalleProducto';
import { TokenModal } from './components/TokenModal';

const formatCLP = (valor) => new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(Number(valor) || 0);

const decodificarJwt = (token) => {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(decodeURIComponent(atob(base64).split('').map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')));
};

function App() {
  const { instance, accounts } = useMsal();
  const isAuthenticated = accounts.length > 0;
  // Cuenta elegida en el login (si hay varias en la sesión, no se usa simplemente la primera)
  const currentAccount = instance.getActiveAccount() || accounts[0];
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

  const [isAdmin, setIsAdmin] = useState(false);
  const [vista, setVista] = useState('tienda');
  const [products, setProducts] = useState([]);
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [cart, setCart] = useState([]);
  const [categoria, setCategoria] = useState('Todos');
  const [busqueda, setBusqueda] = useState('');
  const [procesando, setProcesando] = useState(false);
  const [aviso, setAviso] = useState(null);
  const [token, setToken] = useState(null);
  const [pedidoAPagar, setPedidoAPagar] = useState(null);
  const [detalle, setDetalle] = useState(null);
  const [interacciones, setInteracciones] = useState(0);

  const showAlert = (texto, tipo = 'ok') => {
    setAviso({ texto, tipo });
    setTimeout(() => setAviso(null), 4000);
  };

  // Access token de Entra ID emitido para el scope de la API
  const getAccessToken = async () => {
    if (!isAuthenticated) throw new Error('No has iniciado sesión');
    try {
      return (await instance.acquireTokenSilent({ account: currentAccount, scopes: loginRequest.scopes })).accessToken;
    } catch (err) {
      console.warn('acquireTokenSilent falló, se pide el token por popup:', err);
      return (await instance.acquireTokenPopup(loginRequest)).accessToken;
    }
  };

  const llamar = async (ruta, opciones = {}) => {
    const res = await fetch(`${apiBaseUrl}${ruta}`, {
      ...opciones,
      headers: { Authorization: `Bearer ${await getAccessToken()}`, 'Content-Type': 'application/json', ...opciones.headers },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.status === 204 ? null : res.json();
  };

  const handleLogin = async () => {
    try {
      const { account } = await instance.loginPopup({ ...loginRequest, prompt: 'select_account' });
      instance.setActiveAccount(account);
    } catch (err) {
      showAlert('No se pudo iniciar sesión: ' + (err.message || err), 'error');
    }
  };

  const handleLogout = () => {
    instance.logoutPopup();
    setCart([]);
    setOrders([]);
  };

  const abrirToken = async () => {
    try {
      const valor = await getAccessToken();
      setToken({ valor, claims: decodificarJwt(valor) });
    } catch (err) {
      showAlert('No se pudo obtener el token: ' + err.message, 'error');
    }
  };

  // ===================== DATOS =====================
  const loadProducts = () => llamar('/api/v1/products').then(setProducts).catch((e) => console.error('Productos:', e));
  const loadOrders = () => llamar('/api/v1/orders').then(setOrders).catch((e) => console.error('Pedidos:', e));
  const loadUsers = () => llamar('/api/v1/users').then(setUsers).catch((e) => console.error('Usuarios:', e));

  // El pago se confirma de forma asíncrona (RabbitMQ): se refrescan pedidos y stock hasta que llega el resultado
  const refrescarTrasPago = () => [0, 1500, 4000, 8000].forEach((ms) => setTimeout(() => { loadOrders(); loadProducts(); }, ms));

  useRetornoWebpay({
    activo: isAuthenticated,
    apiBaseUrl,
    getAccessToken,
    onResultado: (texto, tipo) => { showAlert(texto, tipo === 'success' ? 'ok' : 'error'); setVista('pedidos'); refrescarTrasPago(); },
  });

  // El backend decide quién es admin (rol Admin de Entra ID)
  useEffect(() => {
    if (!isAuthenticated) return;
    llamar('/api/v1/users/me').then((me) => setIsAdmin(Boolean(me.admin))).catch((e) => console.warn('Rol:', e));
    loadProducts();
    loadOrders();
  }, [isAuthenticated, currentAccount?.homeAccountId]);

  useEffect(() => {
    if (isAuthenticated && isAdmin) loadUsers();
  }, [isAuthenticated, isAdmin]);

  // ===================== ADMIN =====================
  const guardar = async (ruta, datos, id, recargar, texto) => {
    try {
      await llamar(id ? `${ruta}/${id}` : ruta, { method: id ? 'PUT' : 'POST', body: JSON.stringify(datos) });
      showAlert(texto);
      recargar();
      return true;
    } catch (err) {
      showAlert('No se pudo guardar: ' + err.message, 'error');
      return false;
    }
  };

  const eliminar = async (ruta, id, recargar, texto) => {
    if (!confirm('¿Seguro que quieres eliminarlo?')) return;
    try {
      await llamar(`${ruta}/${id}`, { method: 'DELETE' });
      showAlert(texto);
      recargar();
    } catch (err) {
      showAlert('No se pudo eliminar: ' + err.message, 'error');
    }
  };

  // ===================== TIENDA =====================
  // Detalle de producto: ms-productos publica la interacción VER en Kafka
  const verDetalle = async (id) => {
    try {
      setDetalle(await llamar(`/api/v1/products/${id}`));
      setInteracciones((n) => n + 1);
    } catch (err) {
      showAlert('No se pudo cargar el producto: ' + err.message, 'error');
    }
  };

  const addToCart = (product) => {
    // ms-carrito publica la interacción CARRITO en Kafka (si falla, la compra sigue igual)
    llamar('/api/v1/cart/items', { method: 'POST', body: JSON.stringify({ productoId: product.id, nombre: product.name, categoria: product.category }) })
      .then(() => setInteracciones((n) => n + 1))
      .catch((e) => console.warn('Interacción no registrada:', e));
    setCart((prev) => prev.some((i) => i.id === product.id)
      ? prev.map((i) => (i.id === product.id ? { ...i, qty: i.qty + 1 } : i))
      : [...prev, { ...product, qty: 1 }]);
    showAlert(`${product.name} agregado al carrito`);
  };

  const updateCartQty = (id, delta) =>
    setCart((prev) => prev.map((i) => (i.id === id ? { ...i, qty: i.qty + delta } : i)).filter((i) => i.qty > 0));

  const cartTotal = cart.reduce((suma, i) => suma + i.price * i.qty, 0);
  const unidades = cart.reduce((suma, i) => suma + i.qty, 0);

  const handleCheckout = async () => {
    try {
      setProcesando(true);
      const pedido = await llamar('/api/v1/orders', {
        method: 'POST',
        body: JSON.stringify({ items: cart.map(({ id, name, price, qty }) => ({ id, name, price, qty })), total: cartTotal, paymentMethod: 'WebPay' }),
      });
      setCart([]);
      setPedidoAPagar(pedido);
      loadOrders();
    } catch (err) {
      showAlert('No se pudo crear el pedido: ' + err.message, 'error');
    } finally {
      setProcesando(false);
    }
  };

  if (!isAuthenticated) return <Login onLogin={handleLogin} apiBaseUrl={apiBaseUrl} />;

  const titulos = {
    tienda: ['Tienda', categoria === 'Todos' ? 'Todo' : categoria],
    carrito: ['Carrito', unidades],
    pedidos: isAdmin ? ['Pedidos', 'Clientes'] : ['Mis', 'Pedidos'],
    productos: ['Admin', 'Productos'],
    usuarios: ['Admin', 'Usuarios'],
    dashboard: ['Dashboard', 'En vivo'],
  };
  const [seccion, detalleTitulo] = titulos[vista];

  return (
    <div className="min-h-screen">
      <Cabecera vista={vista} setVista={setVista} esAdmin={isAdmin} unidadesCarrito={unidades}
                cuenta={currentAccount} onToken={abrirToken} onSalir={handleLogout} />
      {vista === 'tienda' && <Hero onVerCategoria={setCategoria} />}

      <main className="mx-auto max-w-[1440px] px-4 pb-24 pt-10 sm:px-8">
        <TituloPagina key={`${vista}-${detalleTitulo}`} seccion={seccion} detalle={detalleTitulo} />

        {vista === 'tienda' && (
          <Tienda productos={products} categoria={categoria} setCategoria={setCategoria} busqueda={busqueda} setBusqueda={setBusqueda}
                  formatCLP={formatCLP} onVer={verDetalle} onAgregar={addToCart}
                  recomendacionesProps={{ apiBaseUrl, getAccessToken, actualizar: interacciones }} />
        )}
        {vista === 'carrito' && (
          <Carrito carrito={cart} total={cartTotal} formatCLP={formatCLP} onCambiar={updateCartQty}
                   onPagar={handleCheckout} procesando={procesando} onIrTienda={() => setVista('tienda')} />
        )}
        {vista === 'pedidos' && (
          <Pedidos pedidos={orders} esAdmin={isAdmin} formatCLP={formatCLP} onActualizar={loadOrders} onPagar={setPedidoAPagar} />
        )}
        {isAdmin && vista === 'productos' && (
          <AdminProductos productos={products} formatCLP={formatCLP}
                          onGuardar={(datos, id) => guardar('/api/v1/products', datos, id, loadProducts, 'Producto guardado')}
                          onEliminar={(id) => eliminar('/api/v1/products', id, loadProducts, 'Producto eliminado')} />
        )}
        {isAdmin && vista === 'usuarios' && (
          <AdminUsuarios usuarios={users}
                         onGuardar={(datos, id) => guardar('/api/v1/users', datos, id, loadUsers, 'Usuario guardado')}
                         onEliminar={(id) => eliminar('/api/v1/users', id, loadUsers, 'Usuario eliminado')} />
        )}
        {isAdmin && vista === 'dashboard' && (
          <Dashboard wsUrl={import.meta.env.VITE_DASHBOARD_WS_URL} apiBaseUrl={apiBaseUrl} getAccessToken={getAccessToken}
                     pedidos={orders} productos={products} formatCLP={formatCLP} onCompra={() => { loadOrders(); loadProducts(); }} />
        )}
      </main>

      {pedidoAPagar && (
        <PagoWebpay pedido={pedidoAPagar} apiBaseUrl={apiBaseUrl} getAccessToken={getAccessToken} formatCLP={formatCLP}
                    onCerrar={() => setPedidoAPagar(null)} />
      )}
      <AnimatePresence>
        {detalle && (
          <DetalleProducto key="detalle" producto={detalle} productos={products} formatCLP={formatCLP}
                           onAgregar={addToCart} onVer={verDetalle} onCerrar={() => setDetalle(null)} />
        )}
      </AnimatePresence>
      {token && (
        <TokenModal token={token.valor} claims={token.claims} apiBaseUrl={apiBaseUrl} onCerrar={() => setToken(null)}
                    onCopiar={() => { navigator.clipboard.writeText(token.valor); showAlert('Token copiado'); }} />
      )}

      <footer className="mx-auto max-w-[1440px] border-t border-tinta px-4 py-4 text-sm text-gris sm:px-8">
        Pedidos360. Fotografías de Unsplash (licencia Unsplash).
      </footer>

      <AnimatePresence>
        {aviso && (
          <motion.div role="status" initial={{ y: 40, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 40, opacity: 0 }}
                      className={`fixed bottom-4 left-4 right-4 z-50 max-w-md px-5 py-4 font-medium text-papel sm:right-auto ${aviso.tipo === 'error' ? 'bg-error' : 'bg-tinta'}`}>
            {aviso.texto}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default App;
