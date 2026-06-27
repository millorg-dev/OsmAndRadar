package net.osmand.plus.plugins.bikeradar;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure business logic for deriving alert level and building RadarState from a vehicle list.
 * No Android dependencies – fully unit-testable with plain JUnit.
 */
public final class RadarAlertCalculator {

    private RadarAlertCalculator() {}

    /**
     * Determines the alert level for a given list of vehicles and speed threshold.
     *
     * Rules (in priority order):
     *  1. No vehicles → CLEAR
     *  2. Any vehicle with relativeSpeedKmh >= thresholdKmh → HIGH_SPEED
     *  3. Otherwise → APPROACHING
     *
     * @param vehicles       detected vehicles (may be empty)
     * @param thresholdKmh   speed limit in km/h above which HIGH_SPEED is triggered
     * @return alert level
     */
    @NonNull
    public static RadarAlertLevel calculate(@NonNull List<RadarVehicle> vehicles, float thresholdKmh) {
        if (vehicles.isEmpty()) {
            return RadarAlertLevel.CLEAR;
        }
        for (RadarVehicle v : vehicles) {
            if (v.getRelativeSpeedKmh() >= thresholdKmh) {
                return RadarAlertLevel.HIGH_SPEED;
            }
        }
        return RadarAlertLevel.APPROACHING;
    }

    /**
     * Builds a complete {@link RadarState} from a raw vehicle list.
     * Vehicles are sorted by distance (closest first) for consistent strip rendering.
     *
     * @param vehicles     detected vehicles (may be empty)
     * @param thresholdKmh high-speed threshold in km/h
     * @return immutable RadarState
     */
    @NonNull
    public static RadarState buildState(@NonNull List<RadarVehicle> vehicles, float thresholdKmh) {
        if (vehicles.isEmpty()) {
            return RadarState.CLEAR;
        }
        List<RadarVehicle> sorted = new ArrayList<>(vehicles);
        Collections.sort(sorted, Comparator.comparingDouble(RadarVehicle::getDistanceMeters));
        RadarAlertLevel level = calculate(sorted, thresholdKmh);
        return new RadarState(sorted, level);
    }
}
