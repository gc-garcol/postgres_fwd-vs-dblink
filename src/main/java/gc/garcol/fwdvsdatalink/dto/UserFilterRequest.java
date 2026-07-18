package gc.garcol.fwdvsdatalink.dto;

/**
 * Optional filters, combined with AND; each matches case-insensitively as a
 * "contains". Null means "no filter on this field".
 */
public record UserFilterRequest(
    String username,
    String email,
    String fullName,
    String phone
) {
}
