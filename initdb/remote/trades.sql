-- Matched trades between two account_details users; maker_id/taker_id
-- reference account_details.external_id. Seeded by trades_seed.sql.
CREATE TABLE IF NOT EXISTS postgres_remote.trades (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL,
    maker_id UUID NOT NULL,
    taker_id UUID NOT NULL,
    trading_pair TEXT NOT NULL,
    matched_base_quantity NUMERIC(30, 8) NOT NULL,
    matched_quote_quantity NUMERIC(30, 8) NOT NULL,
    price NUMERIC(30, 8) NOT NULL,
    matched_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trades_maker_time ON postgres_remote.trades (maker_id, matched_time);
CREATE INDEX IF NOT EXISTS idx_trades_taker_time ON postgres_remote.trades (taker_id, matched_time);
