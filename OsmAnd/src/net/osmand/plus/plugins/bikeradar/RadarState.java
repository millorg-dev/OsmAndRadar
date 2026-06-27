package net.osmand.plus.plugins.bikeradar;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the radar state at a point in time.
 */
public final class RadarState {

    /** Singleton for the all-clear state (no vehicles). */
    public static final RadarState CLEAR = new RadarState(Collections.emptyList(), RadarAlertLevel.CLEAR);

    private final List<RadarVehicle> vehicles;
    private final RadarAlertLevel alertLevel;

    public RadarState(@NonNull List<RadarVehicle> vehicles, @NonNull RadarAlertLevel alertLevel) {
        this.vehicles = Collections.unmodifiableList(vehicles);
        this.alertLevel = alertLevel;
    }

    /** Detected vehicles, ordered by distance (closest first). */
    @NonNull
    public List<RadarVehicle> getVehicles() {
        return vehicles;
    }

    /** Current alert level. */
    @NonNull
    public RadarAlertLevel getAlertLevel() {
        return alertLevel;
    }

    public boolean hasVehicles() {
        return !vehicles.isEmpty();
    }

    @Override
    public String toString() {
        return "RadarState{level=" + alertLevel + ", vehicles=" + vehicles + "}";
    }
}
