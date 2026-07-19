-- Seeds 2000 random trades over the last 30 days among the ten
-- account_details users (external_id ...0001 to ...0010), maker always
-- different from taker. Safe to re-run: inserts only when trades is empty.
--
-- Runs automatically on a fresh postgres-remote volume. To apply to an
-- already-initialized container:
--   docker exec -i postgres-remote psql -U username -d postgres_remote < initdb/remote/trades_seed.sql
INSERT INTO postgres_remote.trades
    (order_id, maker_id, taker_id, trading_pair,
     matched_base_quantity, matched_quote_quantity, price, matched_time)
SELECT
    gen_random_uuid(),
    ('00000000-0000-0000-0000-0000000000' || lpad(s.maker::text, 2, '0'))::uuid,
    ('00000000-0000-0000-0000-0000000000' || lpad((((s.maker - 1 + s.step) % 10) + 1)::text, 2, '0'))::uuid,
    s.pair,
    s.base_qty,
    round(s.base_qty * p.price, 8),
    p.price,
    s.matched_time
FROM (
    SELECT
        (1 + floor(random() * 10))::int AS maker,
        -- step 1..9 guarantees taker differs from maker
        (1 + floor(random() * 9))::int  AS step,
        (ARRAY['BTC/USDT', 'ETH/USDT', 'SOL/USDT'])[1 + floor(random() * 3)::int] AS pair,
        round((0.001 + random() * 2)::numeric, 8) AS base_qty,
        random() AS jitter,
        now() - random() * interval '30 days' AS matched_time
    FROM generate_series(1, 2000)
) s
CROSS JOIN LATERAL (
    SELECT round((CASE s.pair
                      WHEN 'BTC/USDT' THEN 60000
                      WHEN 'ETH/USDT' THEN 3000
                      ELSE 150
                  END * (0.95 + 0.1 * s.jitter))::numeric, 8) AS price
) p
WHERE NOT EXISTS (SELECT 1 FROM postgres_remote.trades);
