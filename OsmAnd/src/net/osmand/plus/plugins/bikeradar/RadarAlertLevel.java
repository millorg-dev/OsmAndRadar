package net.osmand.plus.plugins.bikeradar;

/**
 * Alert level derived from current radar state.
 *
 * CLEAR       – no vehicles detected behind the cyclist
 * APPROACHING – one or more vehicles detected (speed below threshold)
 * HIGH_SPEED  – at least one vehicle detected above the configurable speed threshold
 */
public enum RadarAlertLevel {
    CLEAR,
    APPROACHING,
    HIGH_SPEED
}
