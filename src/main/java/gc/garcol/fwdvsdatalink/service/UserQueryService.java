package gc.garcol.fwdvsdatalink.service;

import gc.garcol.fwdvsdatalink.config.RemoteDbProperties;
import gc.garcol.fwdvsdatalink.config.SchemaProperties;
import gc.garcol.fwdvsdatalink.dto.UserFilterRequest;
import gc.garcol.fwdvsdatalink.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static gc.garcol.fwdvsdatalink.config.RemoteDbProperties.quote;

/**
 * Filters users by joining local users (main schema) with remote
 * account_details on external_id — once through the postgres_fdw foreign
 * tables, once through dblink.
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private static final RowMapper<UserResponse> MAPPER = (rs, rowNum) -> new UserResponse(
        rs.getLong("id"),
        rs.getObject("external_id", UUID.class),
        rs.getString("username"),
        rs.getString("email"),
        rs.getString("full_name"),
        rs.getString("address"),
        rs.getString("phone"),
        rs.getObject("created_at", OffsetDateTime.class));

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RemoteDbProperties remoteDbProperties;
    private final SchemaProperties schemaProperties;

    /**
     * postgres_fdw: one shared filter clause is enough — the planner pushes
     * the account_details conditions down to the remote server itself.
     */
    public List<UserResponse> filterViaFdw(UserFilterRequest request) {
        String sql = """
            SELECT u.id, u.external_id, u.username, u.email,
                   a.full_name, a.address, a.phone, u.created_at
            FROM %s.users u
            JOIN %s.account_details a ON a.external_id = u.external_id
            WHERE (cast(:username AS text) IS NULL OR u.username ILIKE '%%' || :username || '%%')
              AND (cast(:email AS text) IS NULL OR u.email ILIKE '%%' || :email || '%%')
              AND (cast(:fullName AS text) IS NULL OR a.full_name ILIKE '%%' || :fullName || '%%')
              AND (cast(:phone AS text) IS NULL OR a.phone ILIKE '%%' || :phone || '%%')
            ORDER BY u.id
            """.formatted(schemaProperties.main(), remoteDbProperties.fdw().localSchema());
        return jdbcTemplate.query(sql, localParams(request), MAPPER);
    }

    /**
     * dblink: no pushdown exists — the remote query runs exactly as written
     * and its full result set is shipped over. So the remote-column filters
     * (full_name, phone) are baked into the remote SQL itself to filter on
     * the remote server, and only the local-column filters (username, email)
     * are applied here after the join.
     */
    public List<UserResponse> filterViaDblink(UserFilterRequest request) {
        String sql = """
            SELECT u.id, u.external_id, u.username, u.email,
                   a.full_name, a.address, a.phone, u.created_at
            FROM %s.users u
            JOIN dblink(%s, %s)
                 AS a(external_id uuid, full_name text, address text, phone text)
                 ON a.external_id = u.external_id
            WHERE (cast(:username AS text) IS NULL OR u.username ILIKE '%%' || :username || '%%')
              AND (cast(:email AS text) IS NULL OR u.email ILIKE '%%' || :email || '%%')
            ORDER BY u.id
            """.formatted(schemaProperties.main(),
            quote(remoteDbProperties.dblink().serverName()),
            quote(remoteQuery(request)));
        return jdbcTemplate.query(sql, localParams(request), MAPPER);
    }

    /**
     * The SQL executed on the remote server via dblink. Values are escaped by
     * {@link RemoteDbProperties#quote}; the resulting literals are escaped a
     * second time when the whole query is quoted into the dblink() argument.
     */
    private String remoteQuery(UserFilterRequest request) {
        StringBuilder sql = new StringBuilder(
            "SELECT external_id, full_name, address, phone FROM %s.account_details WHERE true"
                .formatted(schemaProperties.remote()));
        if (request.fullName() != null) {
            sql.append(" AND full_name ILIKE ").append(quote("%" + request.fullName() + "%"));
        }
        if (request.phone() != null) {
            sql.append(" AND phone ILIKE ").append(quote("%" + request.phone() + "%"));
        }
        return sql.toString();
    }

    private static MapSqlParameterSource localParams(UserFilterRequest request) {
        return new MapSqlParameterSource()
            .addValue("username", request.username(), Types.VARCHAR)
            .addValue("email", request.email(), Types.VARCHAR)
            .addValue("fullName", request.fullName(), Types.VARCHAR)
            .addValue("phone", request.phone(), Types.VARCHAR);
    }
}
