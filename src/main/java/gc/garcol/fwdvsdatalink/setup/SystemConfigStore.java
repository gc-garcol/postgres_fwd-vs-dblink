package gc.garcol.fwdvsdatalink.setup;

import gc.garcol.fwdvsdatalink.config.SchemaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Key/value store backed by the system_configs table on postgres-main
 * (created by initdb/main/init.sql). Each config field is stored as its own
 * row, e.g. remote-db.fdw.host, remote-db.fdw.port.
 * Setup runners persist the remote connection info they last applied here,
 * so on the next startup they can skip provisioning when nothing changed
 * and recreate the foreign server / user mapping only when the config differs.
 */
@Component
@RequiredArgsConstructor
public class SystemConfigStore {

    private final JdbcTemplate jdbcTemplate;
    private final SchemaProperties schemaProperties;

    /**
     * All rows whose config_key starts with the given prefix, as key -> value.
     */
    public Map<String, String> findByPrefix(String prefix) {
        Map<String, String> values = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT config_key, config_value FROM %s.system_configs WHERE config_key LIKE ? ORDER BY config_key"
                .formatted(schemaProperties.main()),
            rs -> {
                values.put(rs.getString(1), rs.getString(2));
            }, prefix + "%");
        return values;
    }

    /**
     * Replaces all rows under the prefix with the given values, so keys that
     * are no longer produced (e.g. after a rename) do not linger.
     */
    public void saveAll(String prefix, Map<String, String> values) {
        jdbcTemplate.update(
            "DELETE FROM %s.system_configs WHERE config_key LIKE ?".formatted(schemaProperties.main()),
            prefix + "%");
        values.forEach((key, value) -> jdbcTemplate.update(
            "INSERT INTO %s.system_configs (config_key, config_value) VALUES (?, ?)"
                .formatted(schemaProperties.main()),
            key, value));
    }

    /**
     * Human-readable diff between the stored and the desired config, for
     * logging why a runner recreates its foreign server. Password values are
     * never included, only the fact that the password changed.
     */
    public static String describeChanges(Map<String, String> stored, Map<String, String> desired) {
        TreeSet<String> keys = new TreeSet<>(stored.keySet());
        keys.addAll(desired.keySet());
        StringJoiner changes = new StringJoiner(", ");
        for (String key : keys) {
            String before = stored.get(key);
            String after = desired.get(key);
            if (Objects.equals(before, after)) {
                continue;
            }
            if (key.endsWith(".password")) {
                changes.add(key + ": <changed>");
            } else {
                changes.add(key + ": '" + before + "' -> '" + after + "'");
            }
        }
        return changes.toString();
    }

    /**
     * True when the foreign server (dblink or postgres_fdw) currently exists,
     * guarding against a stale system_configs row after the server was dropped
     * manually.
     */
    public boolean foreignServerExists(String serverName) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_foreign_server WHERE srvname = ?", Long.class, serverName);
        return count != null && count > 0;
    }
}
