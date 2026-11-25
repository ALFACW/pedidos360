import { useState } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import { Boton, Campo, Modal, estiloCampo } from './ui';

const CATEGORIAS = ['Computación', 'Monitores', 'Accesorios', 'Audio', 'Mobiliario'];
const PRODUCTO_VACIO = { name: '', price: '', stock: '', category: 'Computación', icon: '📦' };
const USUARIO_VACIO = { name: '', email: '', role: 'CLIENTE', status: 'ACTIVO' };

function Tabla({ columnas, children }) {
  return (
    <div className="mt-6 overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          <tr className="border-b border-tinta font-display uppercase text-gris">
            {columnas.map((c) => <th key={c} className="py-2 pr-4 font-medium">{c}</th>)}
            <th className="py-2 text-right font-medium">Acciones</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-tinta/20">{children}</tbody>
      </table>
    </div>
  );
}

function Acciones({ nombre, onEditar, onEliminar }) {
  return (
    <td className="py-2 text-right whitespace-nowrap">
      <button onClick={onEditar} aria-label={`Editar ${nombre}`} className="inline-grid size-11 place-items-center cursor-pointer hover:bg-placa"><Pencil size={18} /></button>
      <button onClick={onEliminar} aria-label={`Eliminar ${nombre}`} className="inline-grid size-11 place-items-center cursor-pointer text-error hover:bg-placa"><Trash2 size={18} /></button>
    </td>
  );
}

/** CRUD de productos (ms-productos). El formulario vive aquí; App solo llama al API. */
export function AdminProductos({ productos, formatCLP, onGuardar, onEliminar }) {
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(PRODUCTO_VACIO);
  const abrir = (p) => { setForm(p ? { ...p } : PRODUCTO_VACIO); setEditando(p || {}); };
  const guardar = async (e) => {
    e.preventDefault();
    if (await onGuardar({ ...form, price: parseFloat(form.price), stock: parseInt(form.stock, 10) }, editando.id)) setEditando(null);
  };

  return (
    <div className="mt-10">
      <div className="flex items-center justify-between border-b border-tinta pb-2">
        <p className="font-display uppercase">{productos.length} productos</p>
        <Boton onClick={() => abrir(null)}>Nuevo producto</Boton>
      </div>
      <Tabla columnas={['', 'Producto', 'Categoría', 'Precio', 'Stock']}>
        {productos.map((p) => (
          <tr key={p.id}>
            <td className="py-2 pr-4 text-3xl" aria-hidden="true">{p.icon || '📦'}</td>
            <td className="py-2 pr-4 font-display text-lg uppercase">{p.name}</td>
            <td className="py-2 pr-4">{p.category}</td>
            <td className="py-2 pr-4 font-semibold">{formatCLP(p.price)}</td>
            <td className={`py-2 pr-4 ${p.stock <= 5 ? 'font-bold text-error' : ''}`}>{p.stock}</td>
            <Acciones nombre={p.name} onEditar={() => abrir(p)} onEliminar={() => onEliminar(p.id)} />
          </tr>
        ))}
      </Tabla>
      {editando && (
        <Modal titulo={editando.id ? 'Editar producto' : 'Nuevo producto'} onCerrar={() => setEditando(null)}>
          <form onSubmit={guardar} className="space-y-5">
            <Campo etiqueta="Nombre"><input required className={estiloCampo} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Campo>
            <div className="grid grid-cols-2 gap-4">
              <Campo etiqueta="Precio"><input required type="number" step="0.01" className={estiloCampo} value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} /></Campo>
              <Campo etiqueta="Stock"><input required type="number" className={estiloCampo} value={form.stock} onChange={(e) => setForm({ ...form, stock: e.target.value })} /></Campo>
            </div>
            <div className="grid grid-cols-[1fr_96px] gap-4">
              <Campo etiqueta="Categoría">
                <select className={estiloCampo} value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                  {CATEGORIAS.map((c) => <option key={c}>{c}</option>)}
                </select>
              </Campo>
              <Campo etiqueta="Ícono"><input className={estiloCampo} value={form.icon} onChange={(e) => setForm({ ...form, icon: e.target.value })} /></Campo>
            </div>
            <Boton type="submit" className="w-full">Guardar producto</Boton>
          </form>
        </Modal>
      )}
    </div>
  );
}

/** CRUD de usuarios (ms-usuarios). */
export function AdminUsuarios({ usuarios, onGuardar, onEliminar }) {
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(USUARIO_VACIO);
  const abrir = (u) => { setForm(u ? { name: u.name, email: u.email, role: u.role, status: u.status } : USUARIO_VACIO); setEditando(u || {}); };
  const guardar = async (e) => {
    e.preventDefault();
    if (await onGuardar(form, editando.id)) setEditando(null);
  };

  return (
    <div className="mt-10">
      <div className="flex items-center justify-between border-b border-tinta pb-2">
        <p className="font-display uppercase">{usuarios.length} usuarios</p>
        <Boton onClick={() => abrir(null)}>Nuevo usuario</Boton>
      </div>
      <Tabla columnas={['Nombre', 'Correo', 'Rol', 'Estado']}>
        {usuarios.map((u) => (
          <tr key={u.id}>
            <td className="py-3 pr-4 font-display text-lg uppercase">{u.name}</td>
            <td className="py-3 pr-4">{u.email}</td>
            <td className="py-3 pr-4"><span className={`px-2 py-0.5 font-display text-sm uppercase ${u.role === 'ADMIN' ? 'bg-tinta text-papel' : 'border border-tinta'}`}>{u.role}</span></td>
            <td className={`py-3 pr-4 ${u.status === 'ACTIVO' ? 'text-ok' : 'text-gris'}`}>{u.status}</td>
            <Acciones nombre={u.name} onEditar={() => abrir(u)} onEliminar={() => onEliminar(u.id)} />
          </tr>
        ))}
      </Tabla>
      {editando && (
        <Modal titulo={editando.id ? 'Editar usuario' : 'Nuevo usuario'} onCerrar={() => setEditando(null)}>
          <form onSubmit={guardar} className="space-y-5">
            <Campo etiqueta="Nombre completo"><input required className={estiloCampo} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Campo>
            <Campo etiqueta="Correo"><input required type="email" className={estiloCampo} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Campo>
            <div className="grid grid-cols-2 gap-4">
              <Campo etiqueta="Rol">
                <select className={estiloCampo} value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                  {['CLIENTE', 'VENDEDOR', 'ADMIN'].map((r) => <option key={r}>{r}</option>)}
                </select>
              </Campo>
              <Campo etiqueta="Estado">
                <select className={estiloCampo} value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                  {['ACTIVO', 'INACTIVO'].map((s) => <option key={s}>{s}</option>)}
                </select>
              </Campo>
            </div>
            <Boton type="submit" className="w-full">Guardar usuario</Boton>
          </form>
        </Modal>
      )}
    </div>
  );
}
