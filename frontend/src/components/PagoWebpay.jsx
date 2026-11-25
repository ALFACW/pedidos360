import { useEffect, useState } from 'react';

// Tarjeta de prueba de Transbank (ambiente de integración)
const TARJETA_PRUEBA = '4051885600446623';

/** Formulario de pago: registra el intento (Kafka, antifraude) y redirige a WebPay con un POST de token_ws. */
export function PagoWebpay({ pedido, apiBaseUrl, getAccessToken, onCerrar }) {
  const [numeroTarjeta, setNumeroTarjeta] = useState(TARJETA_PRUEBA);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const pagar = async (e) => {
    e.preventDefault();
    setEnviando(true);
    setError('');
    try {
      const res = await fetch(`${apiBaseUrl}/api/v1/pagos/iniciar`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${await getAccessToken()}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ pedidoId: pedido.id, numeroTarjeta }),
      });
      if (res.status === 429) throw new Error('Tarjeta bloqueada por sospecha de fraude (más de 3 intentos en 10 segundos).');
      if (res.status === 409) throw new Error('El pago del pedido aún se está registrando. Intenta de nuevo en unos segundos.');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const { url, token } = await res.json();
      const form = document.createElement('form');
      form.method = 'POST';
      form.action = url;
      form.innerHTML = `<input type="hidden" name="token_ws" value="${token}">`;
      document.body.appendChild(form);
      form.submit();
    } catch (err) {
      setError(err.message);
      setEnviando(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onCerrar}>
      <div className="modal-box" onClick={(e) => e.stopPropagation()}>
        <h3>💳 Pagar pedido {pedido.orderNumber}</h3>
        <form onSubmit={pagar}>
          <div className="form-group">
            <label>Número de tarjeta:</label>
            <input required pattern="\d{16}" maxLength={16} value={numeroTarjeta}
                   onChange={(e) => setNumeroTarjeta(e.target.value.replace(/\D/g, ''))} />
            <small>Prueba Transbank: {TARJETA_PRUEBA} · CVV 123 · RUT 11.111.111-1 · clave 123</small>
          </div>
          {error && <p style={{ color: '#dc2626' }}>{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={onCerrar}>Pagar después</button>
            <button type="submit" className="btn-primary" disabled={enviando}>
              {enviando ? 'Conectando con WebPay...' : 'Pagar con WebPay'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/** Al volver de WebPay (?token_ws=... o ?TBK_TOKEN=... si se anuló) confirma el pago en ms-pagos. */
export function useRetornoWebpay({ activo, apiBaseUrl, getAccessToken, onResultado }) {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token_ws');
    if (!activo || (!token && !params.get('TBK_TOKEN'))) return;
    window.history.replaceState(null, '', window.location.pathname);
    if (!token) {
      onResultado('Pago anulado en WebPay', 'error');
      return;
    }
    (async () => {
      try {
        const res = await fetch(`${apiBaseUrl}/api/v1/pagos/confirmar`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${await getAccessToken()}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ token }),
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const { pedido, estado } = await res.json();
        onResultado(`Pedido ${pedido}: pago ${estado.toLowerCase()}`, estado === 'APROBADO' ? 'success' : 'error');
      } catch (err) {
        onResultado('No se pudo confirmar el pago: ' + err.message, 'error');
      }
    })();
  }, [activo]);
}
