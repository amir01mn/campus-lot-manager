package io.github.amir01mn.parking.lot;

import io.github.amir01mn.parking.auth.CurrentUser;
import io.github.amir01mn.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager")
@Tag(name = "Manager: lots and spots", description = "Create lots and control lot/spot availability (MANAGER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ManagerLotController {

    private final LotManagementService lotManagementService;
    private final CurrentUser currentUser;

    public ManagerLotController(LotManagementService lotManagementService, CurrentUser currentUser) {
        this.lotManagementService = lotManagementService;
        this.currentUser = currentUser;
    }

    @PostMapping("/lots")
    @Operation(summary = "Create a lot",
            description = "Generates `capacity` spots (default 100) labelled L<lotId>-NNN with sensors SNS-L<lotId>-NNN.")
    @ApiResponse(responseCode = "201", description = "Lot created")
    @ApiResponse(responseCode = "409", description = "A lot with that name exists")
    public ResponseEntity<LotResponse> createLot(@Valid @RequestBody CreateLotRequest request) {
        LotResponse lot = lotManagementService.createLot(request);
        return ResponseEntity.created(URI.create("/api/lots/" + lot.id())).body(lot);
    }

    @PatchMapping("/lots/{id}")
    @Operation(summary = "Update a lot",
            description = "Enable/disable, rename or relocate. Disabling keeps existing bookings and reports how many "
                    + "upcoming ones are affected; spot flags are not changed.")
    public LotUpdateResponse updateLot(@PathVariable Long id, @Valid @RequestBody UpdateLotRequest request) {
        return lotManagementService.updateLot(id, request);
    }

    @PatchMapping("/spots/{id}")
    @Operation(summary = "Update a spot's manager flags",
            description = "Enable/disable or flag for maintenance (which alerts all managers). Existing bookings are "
                    + "kept; the response reports how many upcoming ones are affected.")
    public SpotUpdateResponse updateSpot(@PathVariable Long id, @Valid @RequestBody UpdateSpotRequest request) {
        return lotManagementService.updateSpot(currentUser.id(), id, request);
    }
}
