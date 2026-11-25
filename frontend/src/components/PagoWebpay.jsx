import { useEffect, useState } from 'react';
import { Boton, Campo, Modal, estiloCampo } from './ui';

// Tarjeta de prueba de Transbank (ambiente de integración)
const TARJETA_PRUEBA = '4051885600446623';

/** Formulario de pago: registra el intento (Kafka, antifraude) y redirige a WebPay con un POST de token_ws. */
export function PagoWebpay({ pedido, apiBaseUrl, getAccessToken, formatCLP, onCerrar }) {
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
    <Modal titulo={`Pagar ${pedido.orderNumber}`} onCerrar={onCerrar}>
      <form onSubmit={pagar} className="space-y-5">
        <p className="flex items-baseline justify-between border-b border-tinta pb-3">
          <span className="font-display uppercase text-gris">Total</span>
          <span className="font-display text-4xl font-bold">{formatCLP(pedido.total)}</span>
        </p>
        <Campo etiqueta="Número de tarjeta">
          <input required inputMode="numeric" pattern="\d{16}" maxLength={16} value={numeroTarjeta} className={estiloCampo}
                 onChange={(e) => setNumeroTarjeta(e.target.value.replace(/\D/g, ''))} />
        </Campo>
        <p className="text-sm text-gris">Tarjeta de prueba de Transbank: {TARJETA_PRUEBA}. En WebPay usa CVV 123, RUT 11.111.111-1 y clave 123.</p>
        {error && <p role="alert" className="border-l-4 border-error bg-placa p-3 text-error">{error}</p>}
        <div className="flex gap-3">
          <Boton type="button" variante="secundario" onClick={onCerrar} className="flex-1">Pagar después</Boton>
          <Boton type="submit" disabled={enviando} className="flex-1">{enviando ? 'Conectando con WebPay...' : 'Pagar con WebPay'}</Boton>
        </div>
      </form>
    </Modal>
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
