package net.osmand.plus.plugins.bikeradar;

import androidx.annotation.NonNull;

/**
 * Thread-safe runtime status for on-map radar diagnostics.
 *
 * This feeds the compact debug overlay shown in {@code RadarStripView}
 * so rides can be debugged without logcat access.
 */
public final class RadarLiveDebugStatus {

    private static boolean pluginActive;
    private static boolean connecting;
    private static boolean connected;

    private static long lastConnectMs;
    private static long lastDisconnectMs;
    private static long lastPacketMs;
    private static long lastStateMs;

    private static int packetCount;
    private static int stateCount;
    private static int lastPacketBytes;
    private static int lastVehicleCount;

    @NonNull
    private static RadarAlertLevel lastAlertLevel = RadarAlertLevel.CLEAR;

    private RadarLiveDebugStatus() {
    }

    public static synchronized void onPluginActive(boolean active) {
        pluginActive = active;
    }

    public static synchronized void onDeviceConnecting() {
        connecting = true;
        connected = false;
    }

    public static synchronized void onDeviceConnected() {
        connecting = false;
        connected = true;
        lastConnectMs = System.currentTimeMillis();
    }

    public static synchronized void onDeviceDisconnected() {
        connecting = false;
        connected = false;
        lastDisconnectMs = System.currentTimeMillis();
    }

    public static synchronized void onPacket(int bytes, @NonNull RadarState state) {
        packetCount++;
        lastPacketMs = System.currentTimeMillis();
        lastPacketBytes = bytes;
        lastVehicleCount = state.getVehicles().size();
        lastAlertLevel = state.getAlertLevel();
    }

    public static synchronized void onState(@NonNull RadarState state) {
        stateCount++;
        lastStateMs = System.currentTimeMillis();
        lastVehicleCount = state.getVehicles().size();
        lastAlertLevel = state.getAlertLevel();
    }

    @NonNull
    public static synchronized Snapshot snapshot() {
        return new Snapshot(
                pluginActive,
                connecting,
                connected,
                lastConnectMs,
                lastDisconnectMs,
                lastPacketMs,
                lastStateMs,
                packetCount,
                stateCount,
                lastPacketBytes,
                lastVehicleCount,
                lastAlertLevel
        );
    }

    public static final class Snapshot {
        public final boolean pluginActive;
        public final boolean connecting;
        public final boolean connected;
        public final long lastConnectMs;
        public final long lastDisconnectMs;
        public final long lastPacketMs;
        public final long lastStateMs;
        public final int packetCount;
        public final int stateCount;
        public final int lastPacketBytes;
        public final int lastVehicleCount;
        @NonNull
        public final RadarAlertLevel lastAlertLevel;

        private Snapshot(boolean pluginActive,
                         boolean connecting,
                         boolean connected,
                         long lastConnectMs,
                         long lastDisconnectMs,
                         long lastPacketMs,
                         long lastStateMs,
                         int packetCount,
                         int stateCount,
                         int lastPacketBytes,
                         int lastVehicleCount,
                         @NonNull RadarAlertLevel lastAlertLevel) {
            this.pluginActive = pluginActive;
            this.connecting = connecting;
            this.connected = connected;
            this.lastConnectMs = lastConnectMs;
            this.lastDisconnectMs = lastDisconnectMs;
            this.lastPacketMs = lastPacketMs;
            this.lastStateMs = lastStateMs;
            this.packetCount = packetCount;
            this.stateCount = stateCount;
            this.lastPacketBytes = lastPacketBytes;
            this.lastVehicleCount = lastVehicleCount;
            this.lastAlertLevel = lastAlertLevel;
        }
    }
}