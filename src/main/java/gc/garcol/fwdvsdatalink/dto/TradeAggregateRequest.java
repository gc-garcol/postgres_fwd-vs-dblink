package gc.garcol.fwdvsdatalink.dto;

import java.time.LocalDate;

/**
 * Optional filters for trade aggregation, combined with AND. Null means
 * "no filter on this field". Dates are inclusive and match on the day of
 * matched_time.
 */
public record TradeAggregateRequest(
    String username,
    String tradingPair,
    LocalDate fromDate,
    LocalDate toDate
) {
}
