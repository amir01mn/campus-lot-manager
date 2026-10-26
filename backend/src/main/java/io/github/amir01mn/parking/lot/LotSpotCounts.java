package io.github.amir01mn.parking.lot;

/**
 * Aggregated spot counts for one lot (JPQL constructor projection).
 *
 * @param enabledAndServiceable spots that are enabled and need no maintenance (bookable if the lot is enabled)
 */
public record LotSpotCounts(Long lotId, Long total, Long enabledAndServiceable, Long occupied) {

    public static LotSpotCounts empty(Long lotId) {
        return new LotSpotCounts(lotId, 0L, 0L, 0L);
    }
}
