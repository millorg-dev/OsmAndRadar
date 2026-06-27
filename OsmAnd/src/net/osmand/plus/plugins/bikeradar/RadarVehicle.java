package net.osmand.plus.plugins.bikeradar;

/**
 * A single vehicle detected by the radar behind the cyclist.
 */
public final class RadarVehicle {

    private final int id;
    private final float distanceMeters;
    private final float relativeSpeedKmh;

    public RadarVehicle(int id, float distanceMeters, float relativeSpeedKmh) {
        this.id = id;
        this.distanceMeters = distanceMeters;
        this.relativeSpeedKmh = relativeSpeedKmh;
    }

    /** Unique threat ID assigned by the radar hardware. */
    public int getId() {
        return id;
    }

    /** Distance behind the cyclist in meters (0 = at position, positive = behind). */
    public float getDistanceMeters() {
        return distanceMeters;
    }

    /** Relative closing speed in km/h (positive = approaching). */
    public float getRelativeSpeedKmh() {
        return relativeSpeedKmh;
    }

    @Override
    public String toString() {
        return "RadarVehicle{id=" + id + ", dist=" + distanceMeters + "m, speed=" + relativeSpeedKmh + "km/h}";
    }
}
