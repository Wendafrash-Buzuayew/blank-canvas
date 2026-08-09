-- Flyway Migration V1: Initial Multi-Tenant Schema for QRServe Platform

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. MERCHANTS TABLE
CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(50) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address TEXT NOT NULL,
    logo_url VARCHAR(500),
    category VARCHAR(50) NOT NULL DEFAULT 'RESTAURANT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchants_slug ON merchants(slug);

-- 2. BRANCHES TABLE
CREATE TABLE IF NOT EXISTS branches (
    id BIGSERIAL PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    address TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_branches_merchant_id ON branches(merchant_id);

-- 3. TABLES (DINING TABLES)
CREATE TABLE IF NOT EXISTS tables (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    table_number VARCHAR(50) NOT NULL,
    capacity INT NOT NULL DEFAULT 4,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, OCCUPIED, RESERVED
    qr_token VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_branch_table UNIQUE (branch_id, table_number)
);

CREATE INDEX idx_tables_branch_id ON tables(branch_id);
CREATE INDEX idx_tables_merchant_id ON tables(merchant_id);
CREATE INDEX idx_tables_qr_token ON tables(qr_token);

-- 4. CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_merchant_id ON categories(merchant_id);

-- 5. PRODUCTS TABLE
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    image VARCHAR(500),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    preparation_time INT NOT NULL DEFAULT 15, -- minutes
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_merchant_id ON products(merchant_id);
CREATE INDEX idx_products_category_id ON products(category_id);

-- 6. ORDERS TABLE
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES tables(id) ON DELETE CASCADE,
    customer_name VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, PREPARING, READY, DELIVERED, PAID, CANCELLED
    total_amount NUMERIC(12, 2) NOT NULL,
    note TEXT,
    payment_method VARCHAR(50) DEFAULT 'PAY_AT_COUNTER',
    payment_status VARCHAR(30) DEFAULT 'UNPAID',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_merchant_id ON orders(merchant_id);
CREATE INDEX idx_orders_branch_id ON orders(branch_id);
CREATE INDEX idx_orders_table_id ON orders(table_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- 7. ORDER ITEMS TABLE
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL,
    notes TEXT
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- 8. USERS TABLE (RBAC)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID REFERENCES merchants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL, -- SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER, WAITER, KITCHEN, CASHIER, CUSTOMER
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_merchant_id ON users(merchant_id);
CREATE INDEX idx_users_role ON users(role);

-- SEED INITIAL SYSTEM DATA (DEMO)
INSERT INTO merchants (id, name, slug, phone, city, address, logo_url, category)
VALUES 
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Sunrise Coffee & Bakery', 'sunrise', '+251911111111', 'Addis Ababa', 'Bole Atlas', 'https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=400&q=80', 'COFFEE_SHOP'),
('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'La Piazza Italian Restaurant', 'lapiazza', '+251922222222', 'Addis Ababa', 'Kazanchis', 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=400&q=80', 'RESTAURANT');

INSERT INTO branches (id, merchant_id, name, phone, address)
VALUES 
(1, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Main Bole Branch', '+251911111111', 'Bole Road, Next to Edna Mall'),
(2, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Kazanchis Branch', '+251922222222', 'Main Street, Kazanchis');

INSERT INTO tables (id, branch_id, merchant_id, table_number, capacity, status, qr_token)
VALUES
(1, 1, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'T-01', 2, 'AVAILABLE', 'qr-token-sunrise-1-1'),
(2, 1, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'T-02', 4, 'OCCUPIED', 'qr-token-sunrise-1-2'),
(15, 1, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'T-15', 4, 'AVAILABLE', 'qr-token-sunrise-1-15');

-- Default Super Admin User (password: password)
INSERT INTO users (id, merchant_id, name, email, password_hash, role)
VALUES
('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', NULL, 'Super Administrator', 'admin@hotel.com', '$2a$10$eD7gE7Kz5hZ6Yh8Y3r8B6e5n7Q6F1K9P3L5M2N8O1P4Q7R0S3T6U9', 'SUPER_ADMIN'),
('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Sunrise Manager', 'owner@sunrise.com', '$2a$10$eD7gE7Kz5hZ6Yh8Y3r8B6e5n7Q6F1K9P3L5M2N8O1P4Q7R0S3T6U9', 'MERCHANT_OWNER');
