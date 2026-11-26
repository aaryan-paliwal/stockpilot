-- -----------------------------------------------------
-- V12: Multi-Tenant Supplier Isolation & Opening Stock Ledger
-- -----------------------------------------------------

-- 1. Ensure suppliers have a unique constraint on (id, user_id) so it can be referenced by composite foreign key
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_suppliers_id_user'
    ) THEN
        ALTER TABLE suppliers ADD CONSTRAINT uq_suppliers_id_user UNIQUE (id, user_id);
    END IF;
END $$;

-- 2. Drop the single-column foreign key fk_products_supplier and replace with composite foreign key (supplier_id, user_id)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_supplier'
    ) THEN
        ALTER TABLE products DROP CONSTRAINT fk_products_supplier;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_supplier_user'
    ) THEN
        ALTER TABLE products 
            ADD CONSTRAINT fk_products_supplier_user 
            FOREIGN KEY (supplier_id, user_id) 
            REFERENCES suppliers(id, user_id) 
            ON DELETE SET NULL;
    END IF;
END $$;

-- 3. Database-level trigger to guard stock_transactions as an immutable append-only ledger
-- Protects against accidental UPDATE or DELETE statements.
-- Operational Note: In administrative database migrations or disaster-recovery repairs,
-- this trigger can be temporarily disabled via:
--   ALTER TABLE stock_transactions DISABLE TRIGGER trg_stock_transactions_immutable;
CREATE OR REPLACE FUNCTION prevent_stock_transactions_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'stock_transactions table is an immutable inventory ledger: updates and deletes are strictly prohibited.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_stock_transactions_immutable ON stock_transactions;

CREATE TRIGGER trg_stock_transactions_immutable
BEFORE UPDATE OR DELETE ON stock_transactions
FOR EACH ROW
EXECUTE FUNCTION prevent_stock_transactions_mutation();

