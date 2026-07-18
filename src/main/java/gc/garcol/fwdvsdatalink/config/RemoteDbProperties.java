package gc.garcol.fwdvsdatalink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "remote-db")
public record RemoteDbProperties(
    String host,
    int port,
    String database,
    String username,
    String password,
    Dblink dblink,
    Fdw fdw
) {

    public record Dblink(String serverName) {
    }

    public record Fdw(String serverName, String localSchema, List<String> tables) {
    }

    /**
     * Values are inlined into DDL (server/user-mapping options do not support
     * bind parameters), so escape single quotes.
     */
    public static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
