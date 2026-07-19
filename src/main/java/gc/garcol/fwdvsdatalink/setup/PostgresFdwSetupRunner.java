package gc.garcol.fwdvsdatalink.setup;

import gc.garcol.fwdvsdatalink.config.RemoteDbProperties;
import gc.garcol.fwdvsdatalink.config.SchemaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static gc.garcol.fwdvsdatalink.config.RemoteDbProperties.quote;

/**
 * Provisions a postgres_fdw foreign server on postgres-main at startup and
 * imports the remote schema as foreign tables into a local schema.
 * The applied remote config is persisted in system_configs; on later runs the
 * server, user mapping and imported tables are only dropped and recreated when
 * the config in application.yaml differs from the stored one (or the server is
 * missing), and the new config is stored afterwards.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class PostgresFdwSetupRunner implements ApplicationRunner {

    static final String CONFIG_KEY = "remote-db.fdw";

    private final JdbcTemplate jdbcTemplate;
    private final RemoteDbProperties properties;
    private final SchemaProperties schemaProperties;
    private final SystemConfigStore systemConfigStore;

    // Postgres DDL is transactional, so drop/create/import and the
    // system_configs update commit atomically; any failure rolls all back.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String server = properties.fdw().serverName();
        String localSchema = properties.fdw().localSchema();

        Map<String, String> config = new LinkedHashMap<>();
        config.put(CONFIG_KEY + ".host", properties.host());
        config.put(CONFIG_KEY + ".port", String.valueOf(properties.port()));
        config.put(CONFIG_KEY + ".database", properties.database());
        config.put(CONFIG_KEY + ".username", properties.username());
        config.put(CONFIG_KEY + ".password", properties.password());
        config.put(CONFIG_KEY + ".server-name", server);
        config.put(CONFIG_KEY + ".local-schema", localSchema);
        config.put(CONFIG_KEY + ".remote-schema", schemaProperties.remote());
        config.put(CONFIG_KEY + ".tables", String.valueOf(properties.fdw().tables()));
        Map<String, String> stored = systemConfigStore.findByPrefix(CONFIG_KEY + ".");
        if (systemConfigStore.foreignServerExists(server) && config.equals(stored)) {
            log.info("[postgres_fdw] server '{}' unchanged, skipping setup", server);
            return;
        }
        String changes = SystemConfigStore.describeChanges(stored, config);
        log.info("[postgres_fdw] provisioning server '{}'{}", server,
            changes.isEmpty() ? " (config unchanged, server missing)" : ", config changes: " + changes);

        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgres_fdw SCHEMA " + schemaProperties.main());
        // Dropping the server cascades to its user mapping and the previously
        // imported foreign tables, so config changes fully apply on restart.
        jdbcTemplate.execute("DROP SERVER IF EXISTS " + server + " CASCADE");
        jdbcTemplate.execute("""
            CREATE SERVER %s FOREIGN DATA WRAPPER postgres_fdw
            OPTIONS (host %s, port %s, dbname %s)
            """.formatted(server,
            quote(properties.host()),
            quote(String.valueOf(properties.port())),
            quote(properties.database())));
        jdbcTemplate.execute("""
            CREATE USER MAPPING FOR CURRENT_USER SERVER %s
            OPTIONS (user %s, password %s)
            """.formatted(server,
            quote(properties.username()),
            quote(properties.password())));

        // Only the configured tables are imported; when no tables are
        // configured, the whole remote schema is imported.
        List<String> tables = properties.fdw().tables();
        String limitTo = tables == null || tables.isEmpty()
            ? ""
            : " LIMIT TO (%s)".formatted(String.join(", ", tables));
        jdbcTemplate.execute("IMPORT FOREIGN SCHEMA %s%s FROM SERVER %s INTO %s"
            .formatted(schemaProperties.remote(), limitTo, server, localSchema));

        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + localSchema + ".account_details", Long.class);
        systemConfigStore.saveAll(CONFIG_KEY + ".", config);
        log.info("[postgres_fdw] server '{}' ready, {}.account_details count = {}",
            server, localSchema, count);
    }
}
