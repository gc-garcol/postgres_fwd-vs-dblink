package gc.garcol.fwdvsdatalink.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user from postgres_main joined with its account details from
 * postgres_remote via external_id.
 */
public record UserResponse(
    long id,
    UUID externalId,
    String username,
    String email,
    String fullName,
    String address,
    String phone,
    OffsetDateTime createdAt
) {
}
