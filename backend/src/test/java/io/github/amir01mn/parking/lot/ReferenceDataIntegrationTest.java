package io.github.amir01mn.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.amir01mn.parking.support.AbstractIntegrationTest;
import io.github.amir01mn.parking.user.DirectoryEntry;
import io.github.amir01mn.parking.user.UniversityDirectoryRepository;
import io.github.amir01mn.parking.user.UserType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ReferenceDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ParkingLotRepository lotRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private UniversityDirectoryRepository directoryRepository;

    @Test
    void threeSeededLotsWithExpectedCapacities() {
        assertThat(lotRepository.findAll())
                .extracting(ParkingLot::getName)
                .containsExactlyInAnyOrder("Lot A — Keele Campus North", "Lot B — Stong Pond", "Lot C — Glendon");

        assertThat(spotCount("Lot A — Keele Campus North")).isEqualTo(40);
        assertThat(spotCount("Lot B — Stong Pond")).isEqualTo(60);
        assertThat(spotCount("Lot C — Glendon")).isEqualTo(30);
    }

    @Test
    void seededSpotsAreBookableWithLabelsAndSensorIds() {
        ParkingLot lotA = lotRepository.findByName("Lot A — Keele Campus North").orElseThrow();
        List<ParkingSpot> spots = spotRepository.findByLotIdOrderByLabelAsc(lotA.getId());

        assertThat(spots.get(0).getLabel()).isEqualTo("A-001");
        assertThat(spots.get(spots.size() - 1).getLabel()).isEqualTo("A-040");
        assertThat(spots).allMatch(ParkingSpot::isBookable).noneMatch(ParkingSpot::isOccupied);
        assertThat(spotRepository.findBySensorId("SNS-B-060"))
                .get().extracting(ParkingSpot::getLabel).isEqualTo("B-060");
    }

    @Test
    void universityDirectoryIsSeeded() {
        assertThat(directoryRepository.count()).isEqualTo(20);
        assertThat(directoryRepository.findByEmail("faculty.demo@yorku.ca"))
                .get().extracting(DirectoryEntry::getUserType).isEqualTo(UserType.FACULTY);
        assertThat(directoryRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    private long spotCount(String lotName) {
        return spotRepository.countByLotId(lotRepository.findByName(lotName).orElseThrow().getId());
    }
}
