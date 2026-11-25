import { useMsal } from '@azure/msal-react';
import { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { loginRequest } from './msalConfig';
import { PagoWebpay, useRetornoWebpay } from './components/PagoWebpay';
import { Recomendaciones } from './components/Recomendaciones';
import { DetalleProducto } from './components/DetalleProducto';
import { Dashboard } from './components/Dashboard';

function App() {
  const { instance, accounts } = useMsal();
  const isAuthenticated = accounts.length > 0;
  // Cuenta elegida en el login (si hay varias en la sesión, no se usa simplemente la primera)
  const currentAccount = instance.getActiveAccount() || accounts[0];
  const [isAdmin, setIsAdmin] = useState((currentAccount?.idTokenClaims?.roles || []).includes('Admin'));

  // Navegación
  const [activeTab, setActiveTab] = useState('tienda'); // tienda | carrito | pedidos | productos | usuarios
  const [apiBaseUrl, setApiBaseUrl] = useState(import.meta.env.VITE_API_BASE_URL || 'https://t13j9mbqg7.execute-api.us-east-1.amazonaws.com/desarrollo');

  // Estados de datos
  const [products, setProducts] = useState([]);
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [cart, setCart] = useState([]);

  // Estados de carga y alertas
  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState(null); // { type: 'success' | 'error', text: '' }

  // Filtros de tienda
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('Todos');

  // Modales
  const [productModal, setProductModal] = useState({ open: false, mode: 'create', data: null });
  const [userModal, setUserModal] = useState({ open: false, mode: 'create', data: null });
  const [orderReceipt, setOrderReceipt] = useState(null);
  const [showHealthModal, setShowHealthModal] = useState(false);
  const [healthStatus, setHealthStatus] = useState(null);
  const [showTokenModal, setShowTokenModal] = useState(false);
  const [currentToken, setCurrentToken] = useState('');
  const [decodedToken, setDecodedToken] = useState(null);
  const [pedidoAPagar, setPedidoAPagar] = useState(null);
  const [detalle, setDetalle] = useState(null);
  const [interacciones, setInteracciones] = useState(0);

  // Formularios
  const formatCLP = (value) => new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(Number(value) || 0);

  const [productForm, setProductForm] = useState({ name: '', price: '', stock: '', category: 'Computación', icon: '📦' });
  const [userForm, setUserForm] = useState({ name: '', email: '', role: 'CLIENTE', status: 'ACTIVO' });
  const [paymentMethod, setPaymentMethod] = useState('Tarjeta de Crédito / Débito');

  // Notificación temporal
  const showAlert = (text, type = 'success') => {
    setAlert({ text, type });
    setTimeout(() => setAlert(null), 4000);
  };

  // Access token de Entra ID emitido para el scope de la API
  const getAccessToken = async () => {
    if (!isAuthenticated) throw new Error('No has iniciado sesión');
    try {
      const response = await instance.acquireTokenSilent({
        account: currentAccount,
        scopes: loginRequest.scopes,
      });
      return response.accessToken;
    } catch (err) {
      console.warn('acquireTokenSilent fallo, solicitando token por popup:', err);
      const popupResponse = await instance.acquireTokenPopup(loginRequest);
      return popupResponse.accessToken;
    }
  };

  // Iniciar / Cerrar sesión
  const handleLogin = async () => {
    try {
      setAlert(null);
      const { account } = await instance.loginPopup({ ...loginRequest, prompt: 'select_account' });
      instance.setActiveAccount(account);
      showAlert('¡Sesión iniciada con éxito en Microsoft Entra ID!');
    } catch (err) {
      showAlert('Error al iniciar sesión: ' + (err.message || err), 'error');
    }
  };

  const handleLogout = () => {
    instance.logoutPopup();
    setCart([]);
    setOrders([]);
    setProducts([]);
    setUsers([]);
  };

  // Abrir Modal de Inspección de Token JWT para Postman
  const handleOpenTokenModal = async () => {
    try {
      const token = await getAccessToken();
      setCurrentToken(token);
      try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(
          atob(base64)
            .split('')
            .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
            .join('')
        );
        setDecodedToken(JSON.parse(jsonPayload));
      } catch (e) {
        setDecodedToken(null);
      }
      setShowTokenModal(true);
    } catch (err) {
      showAlert('Error al obtener el token: ' + err.message, 'error');
    }
  };

  useRetornoWebpay({
    activo: isAuthenticated,
    apiBaseUrl,
    getAccessToken,
    onResultado: (texto, tipo) => { showAlert(texto, tipo); setActiveTab('pedidos'); loadOrders(); },
  });

  // El backend decide quién es admin (rol de Entra o ADMIN_EMAILS)
  useEffect(() => {
    if (!isAuthenticated) return;
    (async () => {
      try {
        const token = await getAccessToken();
        const usersApi = import.meta.env.VITE_USERS_API_BASE_URL || apiBaseUrl;
        const res = await fetch(`${usersApi}/api/v1/users/me`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (res.ok) setIsAdmin(Boolean((await res.json()).admin));
      } catch (e) {
        console.warn('No se pudo determinar el rol:', e);
      }
    })();
  }, [isAuthenticated, currentAccount?.homeAccountId, apiBaseUrl]);
  // Cargar datos cuando se autentica
  useEffect(() => {
    if (isAuthenticated) {
      loadProducts();
      loadOrders();
    }
  }, [isAuthenticated, currentAccount?.homeAccountId, apiBaseUrl]);

  // La lista de usuarios solo es accesible para Admin
  useEffect(() => {
    if (isAuthenticated && isAdmin) loadUsers();
  }, [isAuthenticated, isAdmin, apiBaseUrl]);

  // ===================== CRUD PRODUCTOS =====================
  const loadProducts = async () => {
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/v1/products`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data = await res.json();
        setProducts(data);
      }
    } catch (e) {
      console.error('Error cargando productos:', e);
    }
  };

  const handleSaveProduct = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const token = await getAccessToken();
      const isEdit = productModal.mode === 'edit';
      const url = isEdit ? `${apiBaseUrl}/api/v1/products/${productModal.data.id}` : `${apiBaseUrl}/api/v1/products`;
      const method = isEdit ? 'PUT' : 'POST';

      const payload = {
        name: productForm.name,
        price: parseFloat(productForm.price),
        stock: parseInt(productForm.stock),
        category: productForm.category,
        icon: productForm.icon || '📦',
      };

      const res = await fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showAlert(isEdit ? 'Producto actualizado correctamente' : 'Producto creado exitosamente');
      setProductModal({ open: false, mode: 'create', data: null });
      loadProducts();
    } catch (err) {
      showAlert('Error guardando producto: ' + err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteProduct = async (id) => {
    if (!confirm('¿Seguro que deseas eliminar este producto?')) return;
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/v1/products/${id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showAlert('Producto eliminado');
      loadProducts();
      // Eliminar del carrito si estaba
      setCart((prev) => prev.filter((item) => item.id !== id));
    } catch (err) {
      showAlert('Error eliminando producto: ' + err.message, 'error');
    }
  };

  // ===================== CRUD USUARIOS =====================
  const loadUsers = async () => {
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/v1/users`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data = await res.json();
        setUsers(data);
      }
    } catch (e) {
      console.error('Error cargando usuarios:', e);
    }
  };

  const handleSaveUser = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const token = await getAccessToken();
      const isEdit = userModal.mode === 'edit';
      const url = isEdit ? `${apiBaseUrl}/api/v1/users/${userModal.data.id}` : `${apiBaseUrl}/api/v1/users`;
      const method = isEdit ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(userForm),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showAlert(isEdit ? 'Usuario modificado' : 'Usuario creado con éxito');
      setUserModal({ open: false, mode: 'create', data: null });
      loadUsers();
    } catch (err) {
      showAlert('Error al guardar usuario: ' + err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteUser = async (id) => {
    if (!confirm('¿Seguro que deseas eliminar este usuario?')) return;
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/v1/users/${id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showAlert('Usuario eliminado');
      loadUsers();
    } catch (err) {
      showAlert('Error eliminando usuario: ' + err.message, 'error');
    }
  };

  // Detalle de producto: ms-productos publica la interacción VER en Kafka
  const verDetalle = async (id) => {
    try {
      const res = await fetch(`${apiBaseUrl}/api/v1/products/${id}`, {
        headers: { Authorization: `Bearer ${await getAccessToken()}` },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setDetalle(await res.json());
      setInteracciones((n) => n + 1);
    } catch (err) {
      showAlert('Error al cargar el producto: ' + err.message, 'error');
    }
  };

  // ===================== CARRITO & CHECKOUT =====================
  const addToCart = async (product) => {
    // ms-carrito publica la interacción CARRITO en Kafka (no bloquea la compra si falla)
    fetch(`${apiBaseUrl}/api/v1/cart/items`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${await getAccessToken()}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ productoId: product.id, nombre: product.name, categoria: product.category }),
    }).then(() => setInteracciones((n) => n + 1)).catch((e) => console.warn('Interacción no registrada:', e));
    setCart((prev) => {
      const existing = prev.find((i) => i.id === product.id);
      if (existing) {
        return prev.map((i) => (i.id === product.id ? { ...i, qty: i.qty + 1 } : i));
      }
      return [...prev, { ...product, qty: 1 }];
    });
    showAlert(`"${product.name}" añadido al carrito 🛒`);
  };

  const updateCartQty = (id, delta) => {
    setCart((prev) =>
      prev
        .map((item) => (item.id === id ? { ...item, qty: item.qty + delta } : item))
        .filter((item) => item.qty > 0)
    );
  };

  const cartTotal = cart.reduce((sum, item) => sum + item.price * item.qty, 0);

  const handleCheckout = async () => {
    if (cart.length === 0) return;
    try {
      setLoading(true);
      const token = await getAccessToken();

      const orderPayload = {
        items: cart.map((c) => ({ id: c.id, name: c.name, price: c.price, qty: c.qty })),
        total: cartTotal,
        paymentMethod: paymentMethod,
      };

      const res = await fetch(`${apiBaseUrl}/api/v1/orders`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(orderPayload),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const newOrder = await res.json();

      setCart([]);
      setPedidoAPagar(newOrder);
      loadOrders();
      showAlert(`Pedido ${newOrder.orderNumber} creado. Completa el pago con WebPay 💳`);
    } catch (err) {
      showAlert('Error al procesar la compra: ' + err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  // ===================== PEDIDOS =====================
  const loadOrders = async () => {
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/v1/orders`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data = await res.json();
        setOrders(data);
      }
    } catch (e) {
      console.error('Error cargando pedidos:', e);
    }
  };

  // ===================== CHEQUEO PÚBLICO =====================
  const checkHealth = async () => {
    try {
      setLoading(true);
      const [o, p, u] = await Promise.all([
        fetch(`${apiBaseUrl}/api/v1/orders/public`).then((r) => r.json()).catch((e) => ({ error: e.message })),
        fetch(`${apiBaseUrl}/api/v1/products/public`).then((r) => r.json()).catch((e) => ({ error: e.message })),
        fetch(`${apiBaseUrl}/api/v1/users/public`).then((r) => r.json()).catch((e) => ({ error: e.message })),
      ]);
      setHealthStatus({ orders: o, products: p, users: u });
      setShowHealthModal(true);
    } finally {
      setLoading(false);
    }
  };

  // Categorías disponibles
  const categories = ['Todos', ...new Set(products.map((p) => p.category))];

  // Productos filtrados
  const filteredProducts = products.filter((p) => {
    const matchesCategory = selectedCategory === 'Todos' || p.category === selectedCategory;
    const matchesSearch = p.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  // =========================================================================
  // RENDER: PANTALLA 1 - LOGIN AISLADO (Si NO está autenticado)
  // =========================================================================
  if (!isAuthenticated) {
    return (
      <div className="login-page">
        <div className="login-backdrop"></div>
        <div className="login-card">
          <div className="login-brand">
            <div className="login-icon">☁️</div>
            <h1>Pedidos360</h1>
            <p className="login-tagline">Plataforma Cloud Native E-Commerce</p>
          </div>

          <div className="login-features">
            <div className="feature-item">
              <span className="feat-icon">🛍️</span>
              <div>
                <strong>Tienda & Catálogo</strong>
                <small>Explora productos y simula compras en tiempo real</small>
              </div>
            </div>
            <div className="feature-item">
              <span className="feat-icon">⚡</span>
              <div>
                <strong>3 Microservicios</strong>
                <small>Spring Boot + AWS API Gateway + EC2</small>
              </div>
            </div>
            <div className="feature-item">
              <span className="feat-icon">🔒</span>
              <div>
                <strong>Seguridad OIDC / OAuth2</strong>
                <small>Autenticación protegida con Microsoft Entra ID</small>
              </div>
            </div>
          </div>

          <div className="login-actions">
            <button className="btn-azure-large" onClick={handleLogin}>
              <svg className="azure-svg" viewBox="0 0 24 24" width="22" height="22">
                <path fill="#0078D4" d="M11.4 24H0L8.6 0h11.2l-8.4 24z" />
                <path fill="#50E6FF" d="M14.6 15.6l-5.2 8.4h11.2l4-8.4h-10z" />
              </svg>
              Iniciar sesión con Azure
            </button>
          </div>

          <div className="login-footer">
            <button className="btn-link" onClick={checkHealth}>
              🔍 Probar conectividad con API Gateway (Público)
            </button>
          </div>
        </div>

        {/* Modal de Chequeo Público */}
        {showHealthModal && (
          <div className="modal-backdrop" onClick={() => setShowHealthModal(false)}>
            <div className="modal-box" onClick={(e) => e.stopPropagation()}>
              <h3>Estado de Conectividad con AWS API Gateway</h3>
              <pre className="code-box">{JSON.stringify(healthStatus, null, 2)}</pre>
              <button className="btn-secondary" onClick={() => setShowHealthModal(false)}>
                Cerrar
              </button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // RENDER: PANTALLA 2 - DASHBOARD E-COMMERCE (Autenticado)
  // =========================================================================
  return (
    <div className="ecommerce-app">
      {/* Barra de Notificación Flotante */}
      {alert && (
        <div className={`toast-notification ${alert.type}`}>
          {alert.type === 'success' ? '✅' : '⚠️'} {alert.text}
        </div>
      )}

      {/* NAVBAR SUPERIOR */}
      <header className="ecom-navbar">
        <div className="nav-left">
          <div className="nav-brand">
            <span className="brand-icon">☁️</span>
            <div>
              <h2>Pedidos360</h2>
              <span className="brand-sub">E-Commerce Cloud Native</span>
            </div>
          </div>

          {/* Menú de Módulos */}
          <nav className="nav-tabs">
            <button
              className={`nav-btn ${activeTab === 'tienda' ? 'active' : ''}`}
              onClick={() => setActiveTab('tienda')}
            >
              🛍️ Tienda
            </button>

            <button
              className={`nav-btn ${activeTab === 'carrito' ? 'active' : ''}`}
              onClick={() => setActiveTab('carrito')}
            >
              🛒 Carrito {cart.length > 0 && <span className="cart-badge">{cart.reduce((s, i) => s + i.qty, 0)}</span>}
            </button>

            <button
              className={`nav-btn ${activeTab === 'pedidos' ? 'active' : ''}`}
              onClick={() => {
                setActiveTab('pedidos');
                loadOrders();
              }}
            >
              📦 {isAdmin ? 'Pedidos de Clientes' : 'Mis Pedidos'} {orders.length > 0 && <span className="orders-badge">{orders.length}</span>}
            </button>

            {isAdmin && (

            <>

            <button
              className={`nav-btn ${activeTab === 'productos' ? 'active' : ''}`}
              onClick={() => {
                setActiveTab('productos');
                loadProducts();
              }}
            >
              🏷️ Productos (CRUD)
            </button>

            <button
              className={`nav-btn ${activeTab === 'usuarios' ? 'active' : ''}`}
              onClick={() => {
                setActiveTab('usuarios');
                loadUsers();
              }}
            >
              👥 Usuarios (CRUD)
            </button>

            <button className={`nav-btn ${activeTab === 'dashboard' ? 'active' : ''}`} onClick={() => setActiveTab('dashboard')}>
              📊 Dashboard
            </button>

            </>

            )}
          </nav>
        </div>

        {/* Perfil y Salir */}
        <div className="nav-right">
          <div className="user-profile-badge">
            <div className="avatar-circle">
              {(currentAccount?.name || currentAccount?.username || 'U').charAt(0).toUpperCase()}
            </div>
            <div className="user-info">
              <span className="user-name">{currentAccount?.name || currentAccount?.username}</span>
              <span className="user-tenant">Entra ID Verificado</span>
            </div>
          </div>
          <button className="btn-token" onClick={handleOpenTokenModal} title="Ver Token JWT para Postman">
            🔑 Ver Token JWT
          </button>
          <button className="btn-logout" onClick={handleLogout} title="Cerrar sesión">
            🚪 Salir
          </button>
        </div>
      </header>

      {/* CONTENIDO PRINCIPAL */}
      <motion.main key={activeTab} className="ecom-main" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        {/* ==================== VISTA 1: TIENDA / CATÁLOGO ==================== */}
        {activeTab === 'tienda' && (
          <div className="store-view">
            <div className="store-hero">
              <div>
                <h2>Catálogo de Productos</h2>
                <p>Selecciona los productos y simula compras conectadas al microservicio de pedidos.</p>
              </div>
              <div className="search-bar">
                <input
                  type="text"
                  placeholder="Buscar producto por nombre..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
            </div>

            {/* Filtros de Categoría */}
            <div className="category-filters">
              {categories.map((cat) => (
                <button
                  key={cat}
                  className={`cat-pill ${selectedCategory === cat ? 'active' : ''}`}
                  onClick={() => setSelectedCategory(cat)}
                >
                  {cat}
                </button>
              ))}
            </div>

            <Recomendaciones apiBaseUrl={apiBaseUrl} getAccessToken={getAccessToken} actualizar={interacciones} onVer={verDetalle} />

            {/* Grilla de Productos */}
            <div className="products-showcase">
              {filteredProducts.map((p, i) => (
                <motion.div key={p.id} className="store-product-card" initial={{ opacity: 0, y: 16 }}
                            animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}>
                  <div className="card-icon">{p.icon || '📦'}</div>
                  <span className="card-category">{p.category}</span>
                  <h3 className="card-title" style={{ cursor: 'pointer' }} onClick={() => verDetalle(p.id)}>{p.name}</h3>
                  <div className="card-footer">
                    <div>
                      <div className="card-price">{formatCLP(p.price)}</div>
                      <small className="card-stock">Stock: {p.stock} uds.</small>
                    </div>
                    <button className="btn-add-cart" onClick={() => addToCart(p)}>
                      + Añadir
                    </button>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>
        )}

        {/* ==================== VISTA 2: CARRITO & CHECKOUT ==================== */}
        {activeTab === 'carrito' && (
          <div className="cart-view">
            <h2>🛒 Carrito de Compras</h2>
            {cart.length === 0 ? (
              <div className="empty-cart-box">
                <span className="empty-icon">🛒</span>
                <h3>Tu carrito está vacío</h3>
                <p>Explora la tienda y añade artículos para simular tu compra.</p>
                <button className="btn-primary" onClick={() => setActiveTab('tienda')}>
                  Ir a la Tienda
                </button>
              </div>
            ) : (
              <div className="cart-layout">
                {/* Lista de Ítems */}
                <div className="cart-items-list">
                  {cart.map((item) => (
                    <div key={item.id} className="cart-item-row">
                      <div className="item-icon">{item.icon || '📦'}</div>
                      <div className="item-details">
                        <h4>{item.name}</h4>
                        <span className="item-cat">{item.category}</span>
                        <div className="item-unit-price">{formatCLP(item.price)} c/u</div>
                      </div>
                      <div className="item-qty-controls">
                        <button className="qty-btn" onClick={() => updateCartQty(item.id, -1)}>
                          -
                        </button>
                        <span className="qty-number">{item.qty}</span>
                        <button className="qty-btn" onClick={() => updateCartQty(item.id, 1)}>
                          +
                        </button>
                      </div>
                      <div className="item-subtotal">{formatCLP(item.price * item.qty)}</div>
                      <button className="btn-remove-item" onClick={() => updateCartQty(item.id, -item.qty)}>
                        ✕
                      </button>
                    </div>
                  ))}
                </div>

                {/* Resumen de Compra */}
                <div className="cart-summary-card">
                  <h3>Resumen del Pedido</h3>
                  <div className="summary-line">
                    <span>Subtotal</span>
                    <strong>{formatCLP(cartTotal)}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Envío Cloud Native</span>
                    <strong className="free-tag">GRATIS</strong>
                  </div>
                  <div className="summary-line">
                    <span>Impuestos incluidos</span>
                    <span>{formatCLP(0)}</span>
                  </div>
                  <hr />
                  <div className="summary-line total-line">
                    <span>Total a Pagar</span>
                    <strong>{formatCLP(cartTotal)}</strong>
                  </div>

                  <div className="payment-select-box">
                    <label>Método de Pago:</label>
                    <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
                      <option>Tarjeta de Crédito / Débito</option>
                      <option>Transferencia Bancaria Electrónica</option>
                      <option>Microsoft Pay (Simulado)</option>
                    </select>
                  </div>

                  <button className="btn-checkout" onClick={handleCheckout} disabled={loading}>
                    {loading ? 'Procesando en API Gateway...' : '🚀 Confirmar y Pagar con WebPay'}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ==================== VISTA 3: MIS PEDIDOS ==================== */}
        {activeTab === 'pedidos' && (
          <div className="orders-view">
            <div className="view-header">
              <div>
                <h2>📦 {isAdmin ? 'Pedidos de Clientes' : 'Historial de Pedidos'}</h2>
                <p>Pedidos registrados en el microservicio de órdenes tras simular compras en el carrito.</p>
              </div>
              <button className="btn-secondary" onClick={loadOrders}>
                🔄 Actualizar
              </button>
            </div>

            {orders.length === 0 ? (
              <p className="empty-box">{isAdmin ? 'Aún no hay pedidos de clientes.' : 'Aún no has realizado compras. Agrega productos al carrito y simula una compra.'}</p>
            ) : (
              <div className="orders-grid">
                {orders.map((o) => (
                  <div key={o.id} className="order-card">
                    <div className="order-card-header">
                      <div>
                        <strong>{o.orderNumber || `PED-${o.id}`}</strong>
                        <div className="order-date">{o.createdAt || 'Reciente'}</div>
                      </div>
                      <span className={`status-tag ${o.status?.toLowerCase()}`}>{o.status || 'CONFIRMADO'}</span>
                      {o.status === 'PENDIENTE_PAGO' && (
                        <button className="btn-primary" onClick={() => setPedidoAPagar(o)}>💳 Pagar</button>
                      )}
                    </div>

                    <div className="order-user-tag">👤 Cliente: {o.customerEmail || o.user}</div>

                    <div className="order-items-preview">
                      <strong>Artículos:</strong>
                      <ul>
                        {Array.isArray(o.items) ? (
                          o.items.map((it, idx) => (
                            <li key={idx}>
                              {it.name} x {it.qty} {it.price && `(${formatCLP(it.price * it.qty)})`}
                            </li>
                          ))
                        ) : (
                          <li>{JSON.stringify(o.items)}</li>
                        )}
                      </ul>
                    </div>

                    <div className="order-card-footer">
                      <span>Total Facturado:</span>
                      <strong>{formatCLP(o.total)}</strong>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ==================== VISTA 4: CRUD PRODUCTOS ==================== */}
        {isAdmin && activeTab === 'dashboard' && <Dashboard wsUrl={import.meta.env.VITE_DASHBOARD_WS_URL} />}

        {isAdmin && activeTab === 'productos' && (
          <div className="crud-view">
            <div className="view-header">
              <div>
                <h2>🏷️ Gestión de Productos (CRUD)</h2>
                <p>Crea, edita y elimina productos directamente en el microservicio `products`.</p>
              </div>
              <button
                className="btn-primary"
                onClick={() => {
                  setProductForm({ name: '', price: '', stock: '', category: 'Computación', icon: '📦' });
                  setProductModal({ open: true, mode: 'create', data: null });
                }}
              >
                + Nuevo Producto
              </button>
            </div>

            <div className="table-card">
              <table className="crud-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Icono</th>
                    <th>Nombre</th>
                    <th>Categoría</th>
                    <th>Precio</th>
                    <th>Stock</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((p) => (
                    <tr key={p.id}>
                      <td><strong>#{p.id}</strong></td>
                      <td className="center-icon">{p.icon || '📦'}</td>
                      <td><strong>{p.name}</strong></td>
                      <td><span className="pill-category">{p.category}</span></td>
                      <td className="price-text">{formatCLP(p.price)}</td>
                      <td><span className="stock-badge">{p.stock} uds.</span></td>
                      <td>
                        <div className="action-cell">
                          <button
                            className="btn-edit"
                            onClick={() => {
                              setProductForm({
                                name: p.name,
                                price: p.price,
                                stock: p.stock,
                                category: p.category,
                                icon: p.icon || '📦',
                              });
                              setProductModal({ open: true, mode: 'edit', data: p });
                            }}
                          >
                            ✏️ Editar
                          </button>
                          <button className="btn-delete" onClick={() => handleDeleteProduct(p.id)}>
                            🗑️
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ==================== VISTA 5: CRUD USUARIOS ==================== */}
        {isAdmin && activeTab === 'usuarios' && (
          <div className="crud-view">
            <div className="view-header">
              <div>
                <h2>👥 Gestión de Usuarios (CRUD)</h2>
                <p>Administra las cuentas de usuarios en el microservicio `users`.</p>
              </div>
              <button
                className="btn-primary"
                onClick={() => {
                  setUserForm({ name: '', email: '', role: 'CLIENTE', status: 'ACTIVO' });
                  setUserModal({ open: true, mode: 'create', data: null });
                }}
              >
                + Nuevo Usuario
              </button>
            </div>

            <div className="table-card">
              <table className="crud-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Nombre Completo</th>
                    <th>Correo Electrónico</th>
                    <th>Rol</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id}>
                      <td><strong>#{u.id}</strong></td>
                      <td><strong>{u.name}</strong></td>
                      <td><code>{u.email}</code></td>
                      <td><span className={`role-pill ${u.role?.toLowerCase()}`}>{u.role}</span></td>
                      <td><span className={`status-pill ${u.status?.toLowerCase()}`}>{u.status}</span></td>
                      <td>
                        <div className="action-cell">
                          <button
                            className="btn-edit"
                            onClick={() => {
                              setUserForm({
                                name: u.name,
                                email: u.email,
                                role: u.role,
                                status: u.status,
                              });
                              setUserModal({ open: true, mode: 'edit', data: u });
                            }}
                          >
                            ✏️ Editar
                          </button>
                          <button className="btn-delete" onClick={() => handleDeleteUser(u.id)}>
                            🗑️
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </motion.main>

      {/* ==================== MODAL PRODUCTO (CREAR / EDITAR) ==================== */}
      {pedidoAPagar && (
        <PagoWebpay pedido={pedidoAPagar} apiBaseUrl={apiBaseUrl} getAccessToken={getAccessToken} onCerrar={() => setPedidoAPagar(null)} />
      )}
      {detalle && (
        <DetalleProducto producto={detalle} formatCLP={formatCLP} onAgregar={addToCart} onCerrar={() => setDetalle(null)} />
      )}

      {productModal.open && (
        <div className="modal-backdrop" onClick={() => setProductModal({ open: false })}>
          <div className="modal-box" onClick={(e) => e.stopPropagation()}>
            <h3>{productModal.mode === 'edit' ? '✏️ Editar Producto' : '✨ Nuevo Producto'}</h3>
            <form onSubmit={handleSaveProduct}>
              <div className="form-group">
                <label>Nombre del Producto:</label>
                <input
                  required
                  type="text"
                  value={productForm.name}
                  onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                />
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Precio ($):</label>
                  <input
                    required
                    type="number"
                    step="0.01"
                    value={productForm.price}
                    onChange={(e) => setProductForm({ ...productForm, price: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>Stock:</label>
                  <input
                    required
                    type="number"
                    value={productForm.stock}
                    onChange={(e) => setProductForm({ ...productForm, stock: e.target.value })}
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Categoría:</label>
                  <select
                    value={productForm.category}
                    onChange={(e) => setProductForm({ ...productForm, category: e.target.value })}
                  >
                    <option>Computación</option>
                    <option>Monitores</option>
                    <option>Accesorios</option>
                    <option>Audio</option>
                    <option>Mobiliario</option>
                  </select>
                </div>
              </div>

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setProductModal({ open: false })}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary" disabled={loading}>
                  {loading ? 'Guardando...' : 'Guardar Producto'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================== MODAL USUARIO (CREAR / EDITAR) ==================== */}
      {userModal.open && (
        <div className="modal-backdrop" onClick={() => setUserModal({ open: false })}>
          <div className="modal-box" onClick={(e) => e.stopPropagation()}>
            <h3>{userModal.mode === 'edit' ? '✏️ Editar Usuario' : '👤 Nuevo Usuario'}</h3>
            <form onSubmit={handleSaveUser}>
              <div className="form-group">
                <label>Nombre Completo:</label>
                <input
                  required
                  type="text"
                  value={userForm.name}
                  onChange={(e) => setUserForm({ ...userForm, name: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Correo Electrónico:</label>
                <input
                  required
                  type="email"
                  value={userForm.email}
                  onChange={(e) => setUserForm({ ...userForm, email: e.target.value })}
                />
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Rol:</label>
                  <select
                    value={userForm.role}
                    onChange={(e) => setUserForm({ ...userForm, role: e.target.value })}
                  >
                    <option>CLIENTE</option>
                    <option>VENDEDOR</option>
                    <option>ADMIN</option>
                  </select>
                </div>
                <div className="form-group">
                  <label>Estado:</label>
                  <select
                    value={userForm.status}
                    onChange={(e) => setUserForm({ ...userForm, status: e.target.value })}
                  >
                    <option>ACTIVO</option>
                    <option>INACTIVO</option>
                  </select>
                </div>
              </div>

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setUserModal({ open: false })}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary" disabled={loading}>
                  {loading ? 'Guardando...' : 'Guardar Usuario'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================== MODAL COMPROBANTE DE COMPRA ==================== */}
      {orderReceipt && (
        <div className="modal-backdrop" onClick={() => setOrderReceipt(null)}>
          <div className="modal-box receipt-box" onClick={(e) => e.stopPropagation()}>
            <div className="receipt-header">
              <span className="receipt-check">🎉</span>
              <h3>¡Compra Simulada con Éxito!</h3>
              <p>El pedido fue registrado en el microservicio de Órdenes mediante AWS API Gateway.</p>
            </div>

            <div className="receipt-details">
              <p><strong>N° Pedido:</strong> <code>{orderReceipt.orderNumber}</code></p>
              <p><strong>Cliente:</strong> {orderReceipt.user}</p>
              <p><strong>Fecha:</strong> {orderReceipt.createdAt}</p>
              <p><strong>Total Pagado:</strong> <span className="receipt-total">{formatCLP(orderReceipt.total)}</span></p>
            </div>

            <div className="modal-actions">
              <button
                className="btn-primary"
                onClick={() => {
                  setOrderReceipt(null);
                  setActiveTab('pedidos');
                }}
              >
                Ver Mis Pedidos
              </button>
            </div>
          </div>
        </div>
      )}
      {/* ==================== MODAL INSPECCIÓN TOKEN JWT PARA POSTMAN ==================== */}
      {showTokenModal && (
        <div className="modal-backdrop" onClick={() => setShowTokenModal(false)}>
          <div className="modal-box token-modal" onClick={(e) => e.stopPropagation()}>
            <div className="view-header" style={{ marginBottom: '1rem' }}>
              <div>
                <h3>🔑 Token JWT (Microsoft Entra ID)</h3>
                <p>Usa este Bearer Token para probar las peticiones GET y POST en <strong>Postman</strong>.</p>
              </div>
              <button className="btn-secondary" onClick={() => setShowTokenModal(false)}>✕</button>
            </div>

            <div className="form-group">
              <label>Token Bearer Raw (Copia y pega en Postman):</label>
              <textarea
                readOnly
                className="token-textarea"
                rows="4"
                value={currentToken}
              />
              <button
                className="btn-primary copy-btn"
                onClick={() => {
                  navigator.clipboard.writeText(currentToken);
                  showAlert('¡Token copiado al portapapeles!');
                }}
              >
                📋 Copiar Token para Postman
              </button>
            </div>

            {decodedToken && (
              <div className="token-claims">
                <h4>Claims OIDC Verificadas:</h4>
                <div className="claims-grid">
                  <div><strong>aud (Audience):</strong> <code>{decodedToken.aud}</code></div>
                  <div><strong>iss (Issuer):</strong> <code>{decodedToken.iss}</code></div>
                  <div><strong>Usuario:</strong> <code>{decodedToken.name || decodedToken.preferred_username}</code></div>
                  <div><strong>Email:</strong> <code>{decodedToken.preferred_username || decodedToken.email}</code></div>
                  <div><strong>roles:</strong> <code>{(decodedToken.roles || ['(sin rol: Cliente)']).join(', ')}</code></div>
                </div>
                <details style={{ marginTop: '0.8rem' }}>
                  <summary style={{ cursor: 'pointer', color: 'var(--primary)', fontWeight: 600 }}>🔍 Ver JSON Completo Decodificado</summary>
                  <pre className="code-box" style={{ marginTop: '0.5rem' }}>{JSON.stringify(decodedToken, null, 2)}</pre>
                </details>
              </div>
            )}

            <div className="postman-help-box">
              <strong>💡 Cómo probarlo en Postman:</strong>
              <p><strong>1. Método:</strong> <code>POST</code> o <code>GET</code></p>
              <p><strong>2. URL:</strong> <code>{apiBaseUrl}/api/v1/products</code> (o <code>/api/v1/orders</code>)</p>
              <p><strong>3. En la pestaña Auth:</strong> Selecciona <strong>Bearer Token</strong> y pega el token de arriba.</p>
              <p><strong>4. En la pestaña Body (si es POST):</strong> Selecciona <strong>raw / JSON</strong> y envía: <code>{JSON.stringify({ name: "Monitor Postman", price: 199.99, stock: 10, category: "Monitores", icon: "🖥️" })}</code></p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
