package gc.garcol.fwdvsdatalink.service;

import gc.garcol.fwdvsdatalink.config.RemoteDbProperties;
import gc.garcol.fwdvsdatalink.config.SchemaProperties;
import gc.garcol.fwdvsdatalink.dto.TradeAggregateRequest;
import gc.garcol.fwdvsdatalink.dto.TradeAggregateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

import static gc.garcol.fwdvsdatalink.config.RemoteDbProperties.quote;

/**
 * Aggregates remote trades per user, day and trading pair, joining local
 * users (main schema) with remote trades on external_id — once through the
 * postgres_fdw foreign table, once through dblink. A user participates in a
 * trade as maker or taker; both sides count towards their buckets.
 *
 * <p>Day buckets are pinned to UTC on both paths: a bare
 * {@code matched_time::date} would use the session time zone, which is the
 * JVM zone for the local JDBC session but the remote server default for the
 * dblink session, making the two endpoints disagree around midnight.
 */
@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private static final RowMapper<TradeAggregateResponse> MAPPER = (rs, rowNum) -> new TradeAggregateResponse(
        rs.getLong("user_id"),
        rs.getString("username"),
        rs.getObject("trade_date", LocalDate.class),
        rs.getString("trading_pair"),
        rs.getLong("trade_count"),
        rs.getBigDecimal("total_base_quantity"),
        rs.getBigDecimal("total_quote_quantity"),
        rs.getBigDecimal("avg_price"));

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RemoteDbProperties remoteDbProperties;
    private final SchemaProperties schemaProperties;

    /**
     * postgres_fdw: the trades scan (with the trading_pair/matched_time
     * conditions) is pushed down to the remote server, but the join with the
     * local users table and the aggregation run locally.
     */
    public List<TradeAggregateResponse> aggregateViaFdw(TradeAggregateRequest request) {
        String sql = """
            SELECT u.id AS user_id, u.username,
                   (t.matched_time AT TIME ZONE 'UTC')::date AS trade_date,
                   t.trading_pair,
                   count(*) AS trade_count,
                   sum(t.matched_base_quantity) AS total_base_quantity,
                   sum(t.matched_quote_quantity) AS total_quote_quantity,
                   round(avg(t.price), 8) AS avg_price
            FROM %s.users u
            JOIN %s.trades t ON u.external_id IN (t.maker_id, t.taker_id)
            WHERE (cast(:username AS text) IS NULL OR u.username ILIKE '%%' || :username || '%%')
              AND (cast(:tradingPair AS text) IS NULL OR t.trading_pair = :tradingPair)
              AND (cast(:fromDate AS date) IS NULL OR (t.matched_time AT TIME ZONE 'UTC') >= cast(:fromDate AS date))
              AND (cast(:toDate AS date) IS NULL OR (t.matched_time AT TIME ZONE 'UTC') < cast(:toDate AS date) + 1)
            GROUP BY u.id, u.username, trade_date, t.trading_pair
            ORDER BY u.id, trade_date, t.trading_pair
            """.formatted(schemaProperties.main(), remoteDbProperties.fdw().localSchema());
        return jdbcTemplate.query(sql, localParams(request), MAPPER);
    }

    /**
     * dblink: no pushdown exists, so the aggregation itself is baked into the
     * remote SQL — only the small per-user/day/pair buckets travel back, and
     * they are joined with local users here. Remote-column filters
     * (trading_pair, dates) are inlined remotely; the username filter is
     * local-only and applied after the join.
     */
    public List<TradeAggregateResponse> aggregateViaDblink(TradeAggregateRequest request) {
        String sql = """
            SELECT u.id AS user_id, u.username,
                   a.trade_date, a.trading_pair, a.trade_count,
                   a.total_base_quantity, a.total_quote_quantity, a.avg_price
            FROM %s.users u
            JOIN dblink(%s, %s)
                 AS a(user_id uuid, trade_date date, trading_pair text, trade_count bigint,
                      total_base_quantity numeric, total_quote_quantity numeric, avg_price numeric)
                 ON a.user_id = u.external_id
            WHERE (cast(:username AS text) IS NULL OR u.username ILIKE '%%' || :username || '%%')
            ORDER BY u.id, a.trade_date, a.trading_pair
            """.formatted(schemaProperties.main(),
            quote(remoteDbProperties.dblink().serverName()),
            quote(remoteAggregateQuery(request)));
        return jdbcTemplate.query(sql, localParams(request), MAPPER);
    }

    /**
     * The aggregation SQL executed on the remote server via dblink. Each trade
     * is expanded to its maker and taker rows before grouping so both sides
     * count for their user. Values are escaped by
     * {@link RemoteDbProperties#quote}; the resulting literals are escaped a
     * second time when the whole query is quoted into the dblink() argument.
     */
    private String remoteAggregateQuery(TradeAggregateRequest request) {
        StringBuilder sql = new StringBuilder("""
            SELECT user_id, (matched_time AT TIME ZONE 'UTC')::date AS trade_date, trading_pair,
                   count(*) AS trade_count,
                   sum(matched_base_quantity) AS total_base_quantity,
                   sum(matched_quote_quantity) AS total_quote_quantity,
                   round(avg(price), 8) AS avg_price
            FROM (
                SELECT maker_id AS user_id, trading_pair, matched_time,
                       matched_base_quantity, matched_quote_quantity, price
                FROM %s.trades
                UNION ALL
                SELECT taker_id, trading_pair, matched_time,
                       matched_base_quantity, matched_quote_quantity, price
                FROM %s.trades
            ) t
            WHERE true""".formatted(schemaProperties.remote(), schemaProperties.remote()));
        if (request.tradingPair() != null) {
            sql.append(" AND trading_pair = ").append(quote(request.tradingPair()));
        }
        if (request.fromDate() != null) {
            sql.append(" AND (matched_time AT TIME ZONE 'UTC') >= date ").append(quote(request.fromDate().toString()));
        }
        if (request.toDate() != null) {
            sql.append(" AND (matched_time AT TIME ZONE 'UTC') < date ").append(quote(request.toDate().toString())).append(" + 1");
        }
        sql.append(" GROUP BY user_id, trade_date, trading_pair");
        return sql.toString();
    }

    private static MapSqlParameterSource localParams(TradeAggregateRequest request) {
        return new MapSqlParameterSource()
            .addValue("username", request.username(), Types.VARCHAR)
            .addValue("tradingPair", request.tradingPair(), Types.VARCHAR)
            .addValue("fromDate", request.fromDate(), Types.DATE)
            .addValue("toDate", request.toDate(), Types.DATE);
    }
}
