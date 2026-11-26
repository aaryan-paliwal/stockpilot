-- -----------------------------------------------------
-- V14: Warehouse Transfer Status Constraint and Lifecycle Timestamps
-- -----------------------------------------------------

-- 1. Add check constraint on status column for warehouse_transfers
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_transfers_status'
    ) THEN
        ALTER TABLE warehouse_transfers
            ADD CONSTRAINT chk_transfers_status
            CHECK (status IN ('INITIATED', 'APPROVED', 'COMPLETED', 'CANCELLED'));
    END IF;
END $$;

-- 2. Add approved_at and cancelled_at timestamp columns to warehouse_transfers
ALTER TABLE warehouse_transfers
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE;

