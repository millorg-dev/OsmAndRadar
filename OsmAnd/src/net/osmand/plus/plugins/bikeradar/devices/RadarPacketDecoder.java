package net.osmand.plus.plugins.bikeradar.devices;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import net.osmand.plus.plugins.bikeradar.RadarConfig;
import net.osmand.plus.plugins.bikeradar.RadarVehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decodes raw BLE notification bytes from a radar sensor into a list of RadarVehicles.
 *
 * Assumed packet format (Garmin Varia RTL compatible):
 * <pre>
 *   Byte 0          : number of threats (N), 0 = all clear
 *   For each threat (VEHICLE_ENTRY_BYTES bytes):
 *     Byte 0..1 BE  : threat ID (uint16, big-endian)
 *     Byte 2        : distance code  (0 = farthest, MAX_DISTANCE_CODE = closest)
 *     Byte 3..4 BE  : speed (uint16 big-endian, units of 0.01 m/s)
 * </pre>
 *
 * TODO: Verify format against actual Bryton Gardia BLE sniff (nRF Connect).
 *       Adjust constants in {@link RadarConfig} accordingly.
 *
 * This class has no Android dependencies and is fully unit-testable with plain JUnit.
 */
public final class RadarPacketDecoder {

    private RadarPacketDecoder() {}

    /**
     * Decodes a raw BLE notification payload into a list of {@link RadarVehicle} objects.
     *
     * @param bytes raw bytes from the BLE characteristic change notification
     * @return decoded vehicles; empty list if no threats or bytes are malformed
     */
    @NonNull
    public static List<RadarVehicle> decode(@NonNull byte[] bytes) {
        if (RadarConfig.PACKET_PARSER_MODE == RadarConfig.PacketParserMode.GARDIA_SAFE) {
            return decodeGardiaSafe(bytes);
        }
        return decodeLegacyCountFirst(bytes);
    }

    @NonNull
    private static List<RadarVehicle> decodeGardiaSafe(@NonNull byte[] bytes) {
        if (bytes.length < 1) {
            return Collections.emptyList();
        }
        int frameMarker = bytes[0] & 0xFF;

        // Observed on-device with com.brytonsport.gardia while no traffic:
        // 3000000000000000310000000000000000140300
        // Treat this frame family as all-clear until full Gardia protocol is verified.
        if (frameMarker == RadarConfig.GARDIA_FRAME_MARKER_STATUS) {
            return Collections.emptyList();
        }

        // Fallback to the generic parser for non-status frames.
        return decodeLegacyCountFirst(bytes);
    }

    @NonNull
    private static List<RadarVehicle> decodeLegacyCountFirst(@NonNull byte[] bytes) {
        if (bytes.length < 1) {
            return Collections.emptyList();
        }
        int count = bytes[0] & 0xFF;
        if (count == 0) {
            return Collections.emptyList();
        }
        int expectedLength = 1 + count * RadarConfig.VEHICLE_ENTRY_BYTES;
        if (bytes.length < expectedLength) {
            // Truncated packet – decode as many complete entries as possible
            count = (bytes.length - 1) / RadarConfig.VEHICLE_ENTRY_BYTES;
            if (count == 0) {
                return Collections.emptyList();
            }
        }

        List<RadarVehicle> vehicles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int offset = 1 + i * RadarConfig.VEHICLE_ENTRY_BYTES;
            int id = readUint16Be(bytes, offset);
            int distCode = bytes[offset + 2] & 0xFF;
            int rawSpeed = readUint16Be(bytes, offset + 3);

            float distanceMeters = distanceCodeToMeters(distCode);
            float speedKmh = rawSpeedToKmh(rawSpeed);

            vehicles.add(new RadarVehicle(id, distanceMeters, speedKmh));
        }
        return vehicles;
    }

    // -----------------------------------------------------------------------
    // Conversion helpers (package-private for unit tests)
    // -----------------------------------------------------------------------

    /**
     * Converts a raw distance code to meters.
     * Code 0 = farthest (~{@link RadarConfig#MAX_RADAR_DISTANCE_METERS}),
     * Code MAX_DISTANCE_CODE = closest (~0 m).
     */
    @VisibleForTesting
    static float distanceCodeToMeters(int code) {
        int clamped = Math.max(0, Math.min(code, RadarConfig.MAX_DISTANCE_CODE));
        // invert: higher code → closer → fewer meters
        return (RadarConfig.MAX_DISTANCE_CODE - clamped) * RadarConfig.DISTANCE_CODE_FACTOR;
    }

    /**
     * Converts raw speed units (0.01 m/s per unit) to km/h.
     */
    @VisibleForTesting
    static float rawSpeedToKmh(int rawSpeed) {
        return rawSpeed * RadarConfig.SPEED_UNIT_MS * 3.6f;
    }

    private static int readUint16Be(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}
