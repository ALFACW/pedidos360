

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
    image_url VARCHAR(300),
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
    customer_email VARCHAR(254),
    items JSONB NOT NULL,
    total NUMERIC(10, 2) NOT NULL,
    payment_method VARCHAR(100) DEFAULT 'Tarjeta de Crédito / Débito',
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMADO',
    payment_status VARCHAR(30),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_email VARCHAR(254);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS owner_id VARCHAR(100);

CREATE TABLE IF NOT EXISTS order_outbox (
    event_id VARCHAR(120) PRIMARY KEY,
    order_id INTEGER NOT NULL UNIQUE REFERENCES orders(id),
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_outbox_pending
    ON order_outbox (created_at)
    WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS payment_inbox (
    event_id VARCHAR(120) PRIMARY KEY,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    event_id VARCHAR(120) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    customer VARCHAR(150) NOT NULL,
    customer_email VARCHAR(254),
    result VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS customer_email VARCHAR(254);

-- =====================================================================
-- DATOS INICIALES (SEMILLA)
-- =====================================================================

-- Fotos de Unsplash (licencia Unsplash), servidas por el frontend en assets/
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url VARCHAR(300);

INSERT INTO products (name, price, stock, category, icon, image_url) VALUES
('MacBook Pro M3 Max 16"', 2499.99, 12, 'Computación', '💻', 'assets/producto-1.jpg'),
('Monitor Dell UltraSharp 32" 4K', 799.50, 25, 'Monitores', '🖥️', 'assets/producto-2.jpg'),
('Teclado Mecánico Keychron Q1 Pro', 189.90, 40, 'Accesorios', '⌨️', 'assets/producto-3.jpg'),
('Mouse Logitech MX Master 3S', 99.90, 50, 'Accesorios', '🖱️', 'assets/producto-4.jpg'),
('Auriculares Sony WH-1000XM5 ANC', 349.00, 30, 'Audio', '🎧', 'assets/producto-5.jpg'),
('Silla Ergonómica Herman Miller Aeron', 1250.00, 8, 'Mobiliario', '🪑', 'assets/producto-6.jpg')
ON CONFLICT DO NOTHING;

INSERT INTO users (name, email, role, status) VALUES
('Diego Carrillo', 'diego.carrilo@josecamposar.onmicrosoft.com', 'CLIENTE', 'ACTIVO'),
('Diego Carrillo (Admin)', 'di.carrillow@duocuc.cl', 'ADMIN', 'ACTIVO'),
('Administrador Pedidos360', 'admin@pedidos360.internal', 'ADMIN', 'ACTIVO'),
('Vendedor Tienda', 'vendedor@pedidos360.internal', 'VENDEDOR', 'ACTIVO'),
('Cliente Frecuente', 'cliente@pedidos360.internal', 'CLIENTE', 'ACTIVO')
ON CONFLICT (email) DO NOTHING;

INSERT INTO orders (order_number, username, items, total, payment_method, status) VALUES
('PED-101', 'diego.carrilo@josecamposar.onmicrosoft.com', '[{"name":"Teclado Mecánico Keychron Q1 Pro","qty":1,"price":189.90}]'::jsonb, 189.90, 'Tarjeta de Crédito / Débito', 'CONFIRMADO'),
('PED-102', 'diego.carrilo@josecamposar.onmicrosoft.com', '[{"name":"Mouse Logitech MX Master 3S","qty":2,"price":99.90}]'::jsonb, 199.80, 'Transferencia Bancaria Electrónica', 'CONFIRMADO')
ON CONFLICT (order_number) DO NOTHING;
