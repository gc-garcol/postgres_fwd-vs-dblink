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

import static gc.garcol.fwdvsdatalink.config.RemoteDbProperties.quote;

/**
 * Provisions a named dblink foreign server on postgres-main at startup.
 * The server and user mapping are dropped and recreated on every run, so any
 * change to the remote host or account in application.yaml is applied the
 * next time the app starts.
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class DblinkSetupRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final RemoteDbProperties properties;
    private final SchemaProperties schemaProperties;

    @Override
    public void run(ApplicationArguments args) {
        String server = properties.dblink().serverName();

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
        log.info("[dblink] server '{}' ready, remote account_details count = {}", server, count);
    }
}
