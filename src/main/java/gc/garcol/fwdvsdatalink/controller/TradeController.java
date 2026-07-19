package gc.garcol.fwdvsdatalink.controller;

import gc.garcol.fwdvsdatalink.dto.TradeAggregateRequest;
import gc.garcol.fwdvsdatalink.dto.TradeAggregateResponse;
import gc.garcol.fwdvsdatalink.service.TradeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@Tag(name = "Trades", description = "Aggregate remote trades per user, day and trading pair")
public class TradeController {

    private final TradeQueryService tradeQueryService;

    @Operation(summary = "Aggregate trades via postgres_fdw",
        description = "Joins main users with the remote trades foreign table and aggregates locally")
    @PostMapping("/aggregate/fdw")
    public List<TradeAggregateResponse> aggregateViaFdw(@RequestBody TradeAggregateRequest request) {
        return tradeQueryService.aggregateViaFdw(request);
    }

    @Operation(summary = "Aggregate trades via dblink",
        description = "Aggregates on the remote server through dblink, then joins the buckets with main users")
    @PostMapping("/aggregate/dblink")
    public List<TradeAggregateResponse> aggregateViaDblink(@RequestBody TradeAggregateRequest request) {
        return tradeQueryService.aggregateViaDblink(request);
    }
}
