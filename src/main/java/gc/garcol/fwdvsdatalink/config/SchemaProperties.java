package gc.garcol.fwdvsdatalink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schema")
public record SchemaProperties(
    String main,
    String remote
) {
}
