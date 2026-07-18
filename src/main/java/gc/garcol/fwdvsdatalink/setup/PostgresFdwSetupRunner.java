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

import java.util.List;

import static gc.garcol.fwdvsdatalink.config.RemoteDbProperties.quote;

/**
 * Provisions a postgres_fdw foreign server on postgres-main at startup and
 * imports the remote schema as foreign tables into a local schema.
 * Server, user mapping and imported tables are dropped and recreated on every
 * run, so any change to the remote host or account in application.yaml is
 * applied the next time the app starts.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class PostgresFdwSetupRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final RemoteDbProperties properties;
    private final SchemaProperties schemaProperties;

    @Override
    public void run(ApplicationArguments args) {
        String server = properties.fdw().serverName();
        String localSchema = properties.fdw().localSchema();

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
        log.info("[postgres_fdw] server '{}' ready, {}.account_details count = {}",
            server, localSchema, count);
    }
}
