package io.github.amir01mn.parking.sensor;

import io.github.amir01mn.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensors")
@Tag(name = "Sensors", description = "Occupancy readings from spot sensors (X-Sensor-Key, not JWT)")
@SecurityRequirement(name = OpenApiConfig.SENSOR_KEY)
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @PostMapping("/readings")
    @Operation(summary = "Submit a batch of sensor readings",
            description = "1 to 500 readings. Readings older than a spot's last reading are ignored; unknown sensor "
                    + "ids are reported back. A spot turning occupied with no confirmed booking covering now raises "
                    + "an unauthorized-parking alert for managers.")
    @ApiResponse(responseCode = "200", description = "Batch processed")
    @ApiResponse(responseCode = "400", description = "Invalid batch (empty, more than 500 readings, missing fields)")
    @ApiResponse(responseCode = "401", description = "Missing or wrong X-Sensor-Key")
    public SensorIngestResult readings(@Valid @RequestBody SensorReadingsRequest request) {
        return sensorService.ingest(request.readings());
    }
}
