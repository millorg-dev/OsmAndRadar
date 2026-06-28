package net.osmand.plus.plugins.bikeradar;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.bikeradar.devices.RadarPacketDecoder;

import org.apache.commons.logging.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight on-device trace for radar packet diagnostics.
 *
 * Writes plain text to app-internal files so ride sessions can be analyzed later
 * without an attached laptop.
 */
public final class RadarDebugTrace {

    private static final Log LOG = PlatformUtil.getLog(RadarDebugTrace.class);

    private static final String TRACE_FILE_NAME = "bike_radar_trace.log";
    private static final long TRACE_ROTATE_BYTES = 512 * 1024;

    private static final Object LOCK = new Object();

    private static volatile File traceFile;
    private static volatile boolean enabled;

    private RadarDebugTrace() {
    }

    public static void configure(@NonNull OsmandApplication app, boolean enabledFlag) {
        synchronized (LOCK) {
            enabled = enabledFlag;
            traceFile = new File(app.getFilesDir(), TRACE_FILE_NAME);
            if (enabled) {
                appendLine("TRACE", "enabled path=" + traceFile.getAbsolutePath());
            }
        }
    }

    public static void onPacket(@NonNull byte[] bytes,
                                @NonNull List<RadarVehicle> vehicles,
                                @NonNull RadarState state) {
        if (!enabled) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("bytes=").append(bytes.length)
                .append(" hex=").append(RadarPacketDecoder.toHex(bytes))
                .append(" vehicles=").append(vehicles.size())
                .append(" level=").append(state.getAlertLevel().name());
        for (RadarVehicle vehicle : vehicles) {
            sb.append(" | id=").append(vehicle.getId())
                    .append(" d=").append(String.format(Locale.US, "%.1f", vehicle.getDistanceMeters()))
                    .append("m v=").append(String.format(Locale.US, "%.1f", vehicle.getRelativeSpeedKmh()))
                    .append("kmh");
        }
        appendLine("PACKET", sb.toString());
    }

    public static void onState(@NonNull RadarState state) {
        if (!enabled) {
            return;
        }
        appendLine("STATE", "level=" + state.getAlertLevel().name() + " vehicles=" + state.getVehicles().size());
    }

    private static void appendLine(@NonNull String tag, @NonNull String line) {
        synchronized (LOCK) {
            if (!enabled || traceFile == null) {
                return;
            }
            try {
                rotateIfNeeded(traceFile);
                try (FileWriter writer = new FileWriter(traceFile, true)) {
                    writer.write(timestamp() + " [" + tag + "] " + line + "\n");
                }
            } catch (IOException e) {
                LOG.error("Failed writing radar trace", e);
            }
        }
    }

    private static void rotateIfNeeded(@NonNull File file) {
        if (!file.exists() || file.length() < TRACE_ROTATE_BYTES) {
            return;
        }
        File backup = new File(file.getParentFile(), TRACE_FILE_NAME + ".old");
        if (backup.exists() && !backup.delete()) {
            LOG.warn("Failed deleting old backup radar trace");
        }
        if (!file.renameTo(backup)) {
            LOG.warn("Failed rotating radar trace");
        }
    }

    @NonNull
    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
