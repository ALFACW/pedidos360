import { useMsal } from '@azure/msal-react';
import { useState } from 'react';
import { loginRequest } from './msalConfig';

function App() {
  const { instance, accounts } = useMsal();
  const [activeTab, setActiveTab] = useState('orders');
  const [orders, setOrders] = useState(null);
  const [productsData, setProductsData] = useState(null);
  const [userData, setUserData] = useState(null);
  const [publicResponse, setPublicResponse] = useState(null);

  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [tokenPreview, setTokenPreview] = useState('');

  const defaultApiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  const [apiBaseUrl, setApiBaseUrl] = useState(defaultApiBase);

  const isAuthenticated = accounts.length > 0;
  const currentAccount = accounts[0];

  const handleLogin = async () => {
    try {
      setError('');
      const result = await instance.loginPopup(loginRequest);
      console.log('Login OK:', result);
      if (result.accessToken) {
        setTokenPreview(result.accessToken.substring(0, 30) + '...');
      }
    } catch (err) {
      setError('Error al iniciar sesión con Azure Entra ID: ' + (err.message || err));
      console.error(err);
    }
  };

  const handleLogout = () => {
    instance.logoutPopup();
    setOrders(null);
    setProductsData(null);
    setUserData(null);
    setPublicResponse(null);
    setTokenPreview('');
  };

  const getAccessToken = async () => {
    if (!isAuthenticated) {
      throw new Error('Primero debes iniciar sesión con Azure Entra ID.');
    }
    const tokenResponse = await instance.acquireTokenSilent({
      account: currentAccount,
      scopes: loginRequest.scopes,
    });
    setTokenPreview(tokenResponse.accessToken.substring(0, 35) + '...');
    return tokenResponse.accessToken;
  };

  // 1. Consultar Microservicio de Pedidos (Orders)
  const handleFetchOrders = async () => {
    setLoading(true);
    setError('');
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/orders`, {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      const data = await res.json();
      setOrders(data);
      setActiveTab('orders');
    } catch (err) {
      setError(`Error al consultar Orders (${apiBaseUrl}/api/orders): ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // 2. Consultar Microservicio de Productos (Products)
  const handleFetchProducts = async () => {
    setLoading(true);
    setError('');
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/products`, {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      const data = await res.json();
      setProductsData(data);
      setActiveTab('products');
    } catch (err) {
      setError(`Error al consultar Products (${apiBaseUrl}/api/products): ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // 3. Consultar Microservicio de Usuarios (Users)
  const handleFetchUsers = async () => {
    setLoading(true);
    setError('');
    try {
      const token = await getAccessToken();
      const res = await fetch(`${apiBaseUrl}/api/users/me`, {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      const data = await res.json();
      setUserData(data);
      setActiveTab('users');
    } catch (err) {
      setError(`Error al consultar Users (${apiBaseUrl}/api/users/me): ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // 4. Probar Endpoint Público (sin token)
  const handleFetchPublic = async (serviceName) => {
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${apiBaseUrl}/api/${serviceName}/public`);
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      const data = await res.json();
      setPublicResponse({ service: serviceName, data });
      setActiveTab('public');
    } catch (err) {
      setError(`Error en endpoint público /api/${serviceName}/public: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-layout">
      <header className="navbar">
        <div className="nav-brand">
          <span className="logo-icon">☁️</span>
          <div>
            <h1>Pedidos360 - Cloud Native</h1>
            <span className="subtitle">Arquitectura: React + Azure Entra ID + AWS API Gateway + 3 Microservicios</span>
          </div>
        </div>

        <div className="nav-auth">
          {!isAuthenticated ? (
            <button className="btn-azure" onClick={handleLogin}>
              <svg className="azure-icon" viewBox="0 0 24 24" width="18" height="18">
                <path fill="#0078D4" d="M11.4 24H0L8.6 0h11.2l-8.4 24z"/>
                <path fill="#50E6FF" d="M14.6 15.6l-5.2 8.4h11.2l4-8.4h-10z"/>
              </svg>
              Iniciar sesión con Azure
            </button>
          ) : (
            <div className="auth-profile">
              <span className="user-badge" title={currentAccount?.username}>
                👤 {currentAccount?.name || currentAccount?.username}
              </span>
              <button className="btn-secondary" onClick={handleLogout}>
                Cerrar sesión
              </button>
            </div>
          )}
        </div>
      </header>

      <main className="container">
        {/* Configuración de Endpoint Base */}
        <section className="card config-card">
          <div className="config-header">
            <h3>🔗 Configuración de API Gateway / Backend</h3>
            <span className="badge-gateway">
              {apiBaseUrl.includes('amazonaws.com') ? 'AWS API Gateway' : 'Localhost / Directo'}
            </span>
          </div>
          <div className="gateway-input-group">
            <label htmlFor="apiBase">URL Base del API:</label>
            <input
              id="apiBase"
              type="text"
              value={apiBaseUrl}
              onChange={(e) => setApiBaseUrl(e.target.value)}
              placeholder="https://xxx.execute-api.us-east-1.amazonaws.com/desarrollo"
            />
          </div>
          {tokenPreview && (
            <div className="token-preview">
              <small><strong>Token JWT activo:</strong> <code>{tokenPreview}</code></small>
            </div>
          )}
        </section>

        {error && (
          <div className="alert alert-error">
            <strong>⚠️ Atención:</strong> {error}
          </div>
        )}

        {/* Panel de Operaciones */}
        <section className="card actions-card">
          <h3>⚡ Microservicios Disponibles (Protegidos con JWT de Azure)</h3>
          <p className="hint">
            Haz clic en cada botón para solicitar datos al microservicio correspondiente a través de la ruta del API Gateway.
          </p>

          <div className="action-buttons">
            <button
              className={`btn-service ${activeTab === 'orders' ? 'active' : ''}`}
              onClick={handleFetchOrders}
              disabled={loading || !isAuthenticated}
            >
              📦 1. Pedidos (Orders)
              <small>/api/orders</small>
            </button>

            <button
              className={`btn-service ${activeTab === 'products' ? 'active' : ''}`}
              onClick={handleFetchProducts}
              disabled={loading || !isAuthenticated}
            >
              🏷️ 2. Productos (Products)
              <small>/api/products</small>
            </button>

            <button
              className={`btn-service ${activeTab === 'users' ? 'active' : ''}`}
              onClick={handleFetchUsers}
              disabled={loading || !isAuthenticated}
            >
              👥 3. Usuarios (Users)
              <small>/api/users/me</small>
            </button>
          </div>

          <div className="public-actions">
            <span>Pruebas de conectividad pública (sin JWT):</span>
            <button className="btn-outline" onClick={() => handleFetchPublic('orders')} disabled={loading}>
              /api/orders/public
            </button>
            <button className="btn-outline" onClick={() => handleFetchPublic('products')} disabled={loading}>
              /api/products/public
            </button>
            <button className="btn-outline" onClick={() => handleFetchPublic('users')} disabled={loading}>
              /api/users/public
            </button>
          </div>
        </section>

        {/* Resultados */}
        <section className="card results-card">
          <div className="tabs">
            <button
              className={`tab ${activeTab === 'orders' ? 'active' : ''}`}
              onClick={() => setActiveTab('orders')}
            >
              Pedidos ({orders ? (Array.isArray(orders) ? orders.length : '1') : 0})
            </button>
            <button
              className={`tab ${activeTab === 'products' ? 'active' : ''}`}
              onClick={() => setActiveTab('products')}
            >
              Productos ({productsData?.products?.length || 0})
            </button>
            <button
              className={`tab ${activeTab === 'users' ? 'active' : ''}`}
              onClick={() => setActiveTab('users')}
            >
              Perfil Usuario ({userData ? '1' : 0})
            </button>
            <button
              className={`tab ${activeTab === 'public' ? 'active' : ''}`}
              onClick={() => setActiveTab('public')}
            >
              Respuesta Pública
            </button>
          </div>

          <div className="tab-content">
            {loading && <div className="loader">Consultando microservicio...</div>}

            {/* TAB: ORDERS */}
            {activeTab === 'orders' && !loading && (
              <div>
                {!orders ? (
                  <p className="empty-msg">Inicia sesión y presiona "1. Pedidos (Orders)" para ver tus pedidos.</p>
                ) : (
                  <div className="table-responsive">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>ID</th>
                          <th>Artículo</th>
                          <th>Cantidad</th>
                          <th>Usuario asignado</th>
                        </tr>
                      </thead>
                      <tbody>
                        {Array.isArray(orders) ? (
                          orders.map((o, idx) => (
                            <tr key={idx}>
                              <td><strong>#{o.id}</strong></td>
                              <td>{o.item}</td>
                              <td><span className="badge-qty">{o.qty}</span></td>
                              <td><code>{o.user}</code></td>
                            </tr>
                          ))
                        ) : (
                          <tr><td colSpan="4"><pre>{JSON.stringify(orders, null, 2)}</pre></td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {/* TAB: PRODUCTS */}
            {activeTab === 'products' && !loading && (
              <div>
                {!productsData ? (
                  <p className="empty-msg">Inicia sesión y presiona "2. Productos (Products)" para cargar el catálogo.</p>
                ) : (
                  <div>
                    <div className="service-meta">
                      <span>👤 Solicitado por: <strong>{productsData.consultedBy}</strong></span>
                      <span>📊 Total productos: <strong>{productsData.totalProducts}</strong></span>
                    </div>
                    <div className="products-grid">
                      {productsData.products?.map((p) => (
                        <div key={p.id} className="product-card">
                          <h4>{p.name}</h4>
                          <p className="price">${p.price?.toFixed(2)}</p>
                          <span className="stock">Stock disponible: {p.stock}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* TAB: USERS */}
            {activeTab === 'users' && !loading && (
              <div>
                {!userData ? (
                  <p className="empty-msg">Inicia sesión y presiona "3. Usuarios (Users)" para ver los claims de identidad.</p>
                ) : (
                  <div className="profile-details">
                    <p><strong>Nombre:</strong> {userData.name || 'No especificado'}</p>
                    <p><strong>Usuario / Email:</strong> {userData.preferred_username || userData.email || 'N/A'}</p>
                    <p><strong>Subject (Azure Sub ID):</strong> <code>{userData.subject}</code></p>
                    <p><strong>Emisor (Issuer):</strong> <code>{userData.issuer}</code></p>
                    <p><strong>Expira:</strong> {userData.expiresAt}</p>
                  </div>
                )}
              </div>
            )}

            {/* TAB: PUBLIC */}
            {activeTab === 'public' && !loading && (
              <div>
                {!publicResponse ? (
                  <p className="empty-msg">Selecciona una de las pruebas públicas sin token para verificar conectividad.</p>
                ) : (
                  <div className="json-box">
                    <h4>Respuesta de: <code>/api/{publicResponse.service}/public</code></h4>
                    <pre>{JSON.stringify(publicResponse.data, null, 2)}</pre>
                  </div>
                )}
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
