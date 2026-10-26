package io.github.amir01mn.parking.user;

import io.github.amir01mn.parking.common.PageResponse;
import io.github.amir01mn.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/managers")
@Tag(name = "Admin: managers", description = "Manager accounts (SUPER_MANAGER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminController {

    private final ManagerAccountService managerAccountService;

    public AdminController(ManagerAccountService managerAccountService) {
        this.managerAccountService = managerAccountService;
    }

    @PostMapping
    @Operation(summary = "Create a manager",
            description = "Returns the new account and a generated password, shown only in this response.")
    @ApiResponse(responseCode = "201", description = "Manager created")
    @ApiResponse(responseCode = "409", description = "E-mail already registered")
    public ResponseEntity<CreatedManagerResponse> create(@Valid @RequestBody CreateManagerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(managerAccountService.createManager(request));
    }

    @GetMapping
    @Operation(summary = "List managers", description = "Accounts with role MANAGER, sorted by name.")
    public PageResponse<UserProfileResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return managerAccountService.listManagers(page, size);
    }
}
