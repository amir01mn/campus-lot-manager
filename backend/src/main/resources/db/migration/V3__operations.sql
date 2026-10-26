-- Phase 3: operations (sensor alerts, overstay detection).

-- When the current continuous occupancy began (set on an unoccupied -> occupied reading, cleared when the spot
-- becomes free). Lets the overstay job tell "the same car stayed past its booking" from "a new car arrived".
ALTER TABLE parking_spots ADD COLUMN occupied_since timestamptz;

-- Set once when an overstay alert has been raised for the booking, so the job alerts at most once per booking.
ALTER TABLE bookings ADD COLUMN overstay_alerted_at timestamptz;

-- Overstay candidates: ended, not cancelled, not yet alerted.
CREATE INDEX ix_bookings_overstay_candidates ON bookings (end_time)
    WHERE overstay_alerted_at IS NULL AND status <> 'CANCELLED';
