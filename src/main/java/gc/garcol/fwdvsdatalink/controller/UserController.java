package gc.garcol.fwdvsdatalink.controller;

import gc.garcol.fwdvsdatalink.dto.UserFilterRequest;
import gc.garcol.fwdvsdatalink.dto.UserResponse;
import gc.garcol.fwdvsdatalink.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Filter users joined with remote account details on external_id")
public class UserController {

    private final UserQueryService userQueryService;

    @Operation(summary = "Filter users via postgres_fdw",
        description = "Joins main users with remote account_details through imported foreign tables")
    @PostMapping("/filter/fdw")
    public List<UserResponse> filterViaFdw(@RequestBody UserFilterRequest request) {
        return userQueryService.filterViaFdw(request);
    }

    @Operation(summary = "Filter users via dblink",
        description = "Joins main users with remote account_details through a dblink query")
    @PostMapping("/filter/dblink")
    public List<UserResponse> filterViaDblink(@RequestBody UserFilterRequest request) {
        return userQueryService.filterViaDblink(request);
    }
}
