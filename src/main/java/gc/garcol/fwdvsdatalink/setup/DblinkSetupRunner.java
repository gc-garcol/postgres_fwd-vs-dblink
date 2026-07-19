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
import java.util.Map;

import static gc.garcol.fwdvsdatalink.config.RemoteDbProperties.quote;

/**
 * Provisions a named dblink foreign server on postgres-main at startup.
 * The applied remote config is persisted in system_configs; on later runs the
 * server and user mapping are only dropped and recreated when the config in
 * application.yaml differs from the stored one (or the server is missing),
 * and the new config is stored afterwards.
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class DblinkSetupRunner implements ApplicationRunner {

    static final String CONFIG_KEY = "remote-db.dblink";

    private final JdbcTemplate jdbcTemplate;
    private final RemoteDbProperties properties;
    private final SchemaProperties schemaProperties;
    private final SystemConfigStore systemConfigStore;

    // Postgres DDL is transactional, so drop/create/user-mapping and the
    // system_configs update commit atomically; any failure rolls all back.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String server = properties.dblink().serverName();

        Map<String, String> config = new LinkedHashMap<>();
        config.put(CONFIG_KEY + ".host", properties.host());
        config.put(CONFIG_KEY + ".port", String.valueOf(properties.port()));
        config.put(CONFIG_KEY + ".database", properties.database());
        config.put(CONFIG_KEY + ".username", properties.username());
        config.put(CONFIG_KEY + ".password", properties.password());
        config.put(CONFIG_KEY + ".server-name", server);
        Map<String, String> stored = systemConfigStore.findByPrefix(CONFIG_KEY + ".");
        if (systemConfigStore.foreignServerExists(server) && config.equals(stored)) {
            log.info("[dblink] server '{}' unchanged, skipping setup", server);
            return;
        }
        String changes = SystemConfigStore.describeChanges(stored, config);
        log.info("[dblink] provisioning server '{}'{}", server,
            changes.isEmpty() ? " (config unchanged, server missing)" : ", config changes: " + changes);

        // Install into the main schema so dblink() resolves via the session
        // search_path.
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS dblink SCHEMA " + schemaProperties.main());
        jdbcTemplate.execute("DROP SERVER IF EXISTS " + server + " CASCADE");
        jdbcTemplate.execute("""
            CREATE SERVER %s FOREIGN DATA WRAPPER dblink_fdw
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

        Long count = jdbcTemplate.queryForObject("""
            SELECT cnt FROM dblink(%s, 'SELECT count(*) FROM %s.account_details')
            AS t(cnt bigint)
            """.formatted(quote(server), schemaProperties.remote()), Long.class);
        systemConfigStore.saveAll(CONFIG_KEY + ".", config);
        log.info("[dblink] server '{}' ready, remote account_details count = {}", server, count);
    }
}
