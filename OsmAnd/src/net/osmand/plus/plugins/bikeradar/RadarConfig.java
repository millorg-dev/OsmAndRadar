package net.osmand.plus.plugins.bikeradar;

import java.util.UUID;

/**
 * Central configuration for the Bike Radar plugin.
 *
 * BLE UUIDs: defaulting to Garmin Varia RTL-documented values (publicly reverse-engineered).
 * TODO: Update SERVICE_RADAR / CHAR_RADAR_MEASUREMENT with Bryton Gardia-specific UUIDs
 *       once confirmed via BLE sniffer (nRF Connect app on the real device).
 *
 * Packet format: RadarPacketDecoder documents the assumed format.
 * TODO: Verify / adjust decode() after BLE sniffing with the real Gardia.
 */
public final class RadarConfig {

    public enum PacketParserMode {
        /**
         * Generic parser where byte[0] is the vehicle count and each entry is 5 bytes.
         * This matches the initial reverse-engineered assumption.
         */
        LEGACY_COUNT_FIRST,

        /**
         * Bryton Gardia safe mode.
         * Observed "idle/all-clear" notifications start with frame marker 0x30.
         * Until the full frame layout is verified, these frames are treated as all-clear.
         */
        GARDIA_SAFE
    }

    private RadarConfig() {}

    // -----------------------------------------------------------------------
    // BLE Service & Characteristic UUIDs
    // -----------------------------------------------------------------------

    /**
     * Primary radar BLE service UUID.
     * Default: Garmin Varia RTL compatible (widely documented open-source).
     * Update this constant after BLE sniffing the Bryton Gardia.
     */
    public static final String SERVICE_RADAR = "6A4E2200-667B-11E3-949A-0800200C9A66";
    public static final UUID UUID_SERVICE_RADAR = UUID.fromString(SERVICE_RADAR);

    /**
     * Radar measurement notification characteristic UUID.
     * Update after BLE sniffing the Bryton Gardia.
     */
    public static final String CHAR_RADAR_MEASUREMENT = "6A4E2201-667B-11E3-949A-0800200C9A66";
    public static final UUID UUID_CHAR_RADAR_MEASUREMENT = UUID.fromString(CHAR_RADAR_MEASUREMENT);

    // -----------------------------------------------------------------------
    // Device discovery
    // -----------------------------------------------------------------------

    /** Device name prefix used as fallback when service UUID is not advertised. */
    public static final String DEVICE_NAME_PREFIX_GARDIA = "Gardia";
    public static final String DEVICE_NAME_PREFIX_VARIA = "Varia";

    // -----------------------------------------------------------------------
    // Alert thresholds
    // -----------------------------------------------------------------------

    /** Speed (km/h) above which the strip turns RED instead of orange. User-configurable. */
    public static final float DEFAULT_HIGH_SPEED_THRESHOLD_KMH = 50.0f;

    // -----------------------------------------------------------------------
    // Radar range
    // -----------------------------------------------------------------------

    /** Maximum detection range in meters used for Y-position mapping in the strip view. */
    public static final float MAX_RADAR_DISTANCE_METERS = 150.0f;

    // -----------------------------------------------------------------------
    // Packet decoder constants (adjust if BLE sniffing reveals different format)
    // -----------------------------------------------------------------------

    /**
     * Number of bytes per vehicle entry in the notification payload.
     * Format: [id_hi, id_lo, dist_code, speed_hi, speed_lo]
     */
    public static final int VEHICLE_ENTRY_BYTES = 5;

    /**
     * Conversion factor from distance code (byte 2 of vehicle entry) to meters.
     * Code range 0..63, where 63 = closest, 0 = farthest (~150 m).
     * distanceMeters = (MAX_DISTANCE_CODE - code) * DISTANCE_CODE_FACTOR
     */
    public static final int MAX_DISTANCE_CODE = 63;
    public static final float DISTANCE_CODE_FACTOR = MAX_RADAR_DISTANCE_METERS / MAX_DISTANCE_CODE;

    /**
     * Speed unit in m/s per raw unit (bytes 3-4 of vehicle entry, big-endian uint16).
     * 1 unit = 0.01 m/s → multiply by 3.6 for km/h.
     */
    public static final float SPEED_UNIT_MS = 0.01f;

    // -----------------------------------------------------------------------
    // Parser mode and observed Gardia frame markers
    // -----------------------------------------------------------------------

    /**
     * Active parser mode.
     * Default uses a conservative Gardia-safe mode to avoid false positives.
     */
    public static final PacketParserMode PACKET_PARSER_MODE = PacketParserMode.GARDIA_SAFE;

    /** Observed recurring Gardia frame marker for radar status notifications. */
    public static final int GARDIA_FRAME_MARKER_STATUS = 0x30;
}
