-- =====================================================================
-- PEDIDOS360 - ESQUEMA DE BASE DE DATOS RELACIONAL (POSTGRESQL - AWS RDS)
-- Base de datos: pedidos360db
-- =====================================================================

CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    category VARCHAR(80) NOT NULL,
    icon VARCHAR(20) DEFAULT '📦',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'CLIENTE',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    username VARCHAR(150) NOT NULL,
    items JSONB NOT NULL,
    total NUMERIC(10, 2) NOT NULL,
    payment_method VARCHAR(100) DEFAULT 'Tarjeta de Crédito / Débito',
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMADO',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- DATOS INICIALES (SEMILLA)
-- =====================================================================

INSERT INTO products (name, price, stock, category, icon) VALUES
('MacBook Pro M3 Max 16"', 2499.99, 12, 'Computación', '💻'),
('Monitor Dell UltraSharp 32" 4K', 799.50, 25, 'Monitores', '🖥️'),
('Teclado Mecánico Keychron Q1 Pro', 189.90, 40, 'Accesorios', '⌨️'),
('Mouse Logitech MX Master 3S', 99.90, 50, 'Accesorios', '🖱️'),
('Auriculares Sony WH-1000XM5 ANC', 349.00, 30, 'Audio', '🎧'),
('Silla Ergonómica Herman Miller Aeron', 1250.00, 8, 'Mobiliario', '🪑')
ON CONFLICT DO NOTHING;

INSERT INTO users (name, email, role, status) VALUES
('Diego Carrillo', 'diego.carrilo@josecamposar.onmicrosoft.com', 'ADMIN', 'ACTIVO'),
('Administrador Pedidos360', 'admin@pedidos360.internal', 'ADMIN', 'ACTIVO'),
('Vendedor Tienda', 'vendedor@pedidos360.internal', 'VENDEDOR', 'ACTIVO'),
('Cliente Frecuente', 'cliente@pedidos360.internal', 'CLIENTE', 'ACTIVO')
ON CONFLICT (email) DO NOTHING;

INSERT INTO orders (order_number, username, items, total, payment_method, status) VALUES
('PED-101', 'diego.carrilo@josecamposar.onmicrosoft.com', '[{"name":"Teclado Mecánico Keychron Q1 Pro","qty":1,"price":189.90}]'::jsonb, 189.90, 'Tarjeta de Crédito / Débito', 'CONFIRMADO'),
('PED-102', 'diego.carrilo@josecamposar.onmicrosoft.com', '[{"name":"Mouse Logitech MX Master 3S","qty":2,"price":99.90}]'::jsonb, 199.80, 'Transferencia Bancaria Electrónica', 'CONFIRMADO')
ON CONFLICT (order_number) DO NOTHING;
