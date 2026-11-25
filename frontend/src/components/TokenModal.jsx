import { Boton, Modal } from './ui';

/** Access token de Entra ID para probar el API en Postman, con sus claims principales. */
export function TokenModal({ token, claims, apiBaseUrl, onCopiar, onCerrar }) {
  const filas = claims ? [
    ['aud (audience)', claims.aud],
    ['iss (issuer)', claims.iss],
    ['Usuario', claims.name || claims.preferred_username],
    ['Email', claims.preferred_username || claims.email],
    ['roles', (claims.roles || ['(sin rol: Cliente)']).join(', ')],
  ] : [];
  return (
    <Modal titulo="Token JWT" onCerrar={onCerrar} ancho="max-w-3xl">
      <textarea readOnly rows={4} value={token} aria-label="Access token" className="w-full bg-placa p-3 font-mono text-xs" />
      <Boton className="mt-3" onClick={onCopiar}>Copiar token</Boton>
      <dl className="mt-6 divide-y divide-tinta/20 border-y border-tinta">
        {filas.map(([k, v]) => (
          <div key={k} className="grid gap-1 py-2 sm:grid-cols-[160px_1fr]">
            <dt className="font-display uppercase text-gris">{k}</dt>
            <dd className="break-all font-mono text-sm">{v}</dd>
          </div>
        ))}
      </dl>
      <details className="mt-4">
        <summary className="cursor-pointer font-display uppercase">JSON completo</summary>
        <pre className="mt-2 overflow-x-auto bg-placa p-3 text-xs">{JSON.stringify(claims, null, 2)}</pre>
      </details>
      <p className="mt-6 text-sm">En Postman: Authorization, tipo Bearer Token, pega el token y llama por ejemplo a <code className="bg-placa px-1">{apiBaseUrl}/api/v1/products</code>.</p>
    </Modal>
  );
}
