package gc.garcol.fwdvsdatalink.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One aggregation bucket: all trades a user participated in (as maker or
 * taker) on one day for one trading pair.
 */
public record TradeAggregateResponse(
    long userId,
    String username,
    LocalDate tradeDate,
    String tradingPair,
    long tradeCount,
    BigDecimal totalBaseQuantity,
    BigDecimal totalQuoteQuantity,
    BigDecimal avgPrice
) {
}
