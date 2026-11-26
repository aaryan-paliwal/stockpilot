-- -----------------------------------------------------
-- V11: Enhance Core Inventory Domain (Products & Ledger)
-- -----------------------------------------------------

-- 1. Enhance products table with SKU, descriptions, pricing, supplier, status, and quantity checks
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS sku VARCHAR(64),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS uom VARCHAR(20) NOT NULL DEFAULT 'PCS',
    ADD COLUMN IF NOT EXISTS cost_price NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS selling_price NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS status INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS supplier_id BIGINT;

-- Backfill SKU for any existing products to prevent null violations
UPDATE products
SET sku = 'SKU-' || LPAD(id::text, 6, '0')
WHERE sku IS NULL;

-- Enforce NOT NULL on sku after backfill
ALTER TABLE products
    ALTER COLUMN sku SET NOT NULL;

-- Add constraints on products
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_products_sku_user'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT uq_products_sku_user UNIQUE (sku, user_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_supplier'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_qty_non_negative'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_qty_non_negative CHECK (qty >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_reorder_non_negative'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_reorder_non_negative CHECK (reorder_point >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_cost_price_non_negative'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_cost_price_non_negative CHECK (cost_price >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_selling_price_non_negative'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_selling_price_non_negative CHECK (selling_price >= 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_products_sku_user ON products (sku, user_id);
CREATE INDEX IF NOT EXISTS idx_products_supplier_id ON products (supplier_id);

-- 2. Enhance stock_transactions table as immutable inventory ledger
ALTER TABLE stock_transactions
    ALTER COLUMN type TYPE VARCHAR(20),
    ADD COLUMN IF NOT EXISTS previous_qty INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS resulting_qty INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reason_reference VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_stock_tx_previous_qty_non_negative'
    ) THEN
        ALTER TABLE stock_transactions ADD CONSTRAINT chk_stock_tx_previous_qty_non_negative CHECK (previous_qty >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_stock_tx_resulting_qty_non_negative'
    ) THEN
        ALTER TABLE stock_transactions ADD CONSTRAINT chk_stock_tx_resulting_qty_non_negative CHECK (resulting_qty >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_stock_tx_qty_non_negative'
    ) THEN
        ALTER TABLE stock_transactions ADD CONSTRAINT chk_stock_tx_qty_non_negative CHECK (qty >= 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stock_tx_user_product ON stock_transactions (user_id, product_id, created_at DESC);

