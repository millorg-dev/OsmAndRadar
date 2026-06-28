package net.osmand.plus.plugins.bikeradar.devices;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.plugins.externalsensors.devices.AbstractDevice.DeviceListener;
import net.osmand.plus.plugins.externalsensors.devices.DeviceConnectionResult;
import net.osmand.plus.plugins.externalsensors.devices.DeviceConnectionState;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline test device that behaves like a BLE Gardia inside the existing external-sensor stack.
 *
 * The only simulated boundary is the BLE ingress itself: replay frames are injected directly into
 * {@link GardiaRadarSensor#handleRadarPayload(byte[])} so everything downstream uses the real app path.
 */
public class FakeBLEGardiaDevice extends BLEGardiaDevice {

    private static final long CONNECT_DELAY_MS = 250L;
    private static final long FRAME_PERIOD_MS = 100L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ReplayScenario> replayScenarios;

    private int replayIndex;
    private int scenarioIndex;
    private boolean replayRunning;

    private final Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            if (getCurrentState() != DeviceConnectionState.CONNECTING) {
                return;
            }
            setCurrentState(DeviceConnectionState.CONNECTED);
            rssi = -42;
            batteryLevel = 87;
            for (DeviceListener listener : listeners) {
                listener.onDeviceConnect(FakeBLEGardiaDevice.this, DeviceConnectionResult.SUCCESS, null);
            }
            startReplay();
        }
    };

    private final Runnable replayRunnable = new Runnable() {
        @Override
        public void run() {
            if (!replayRunning || getCurrentState() != DeviceConnectionState.CONNECTED || replayScenarios.isEmpty()) {
                return;
            }
            ReplayScenario scenario = getCurrentScenario();
            if (scenario.frames.isEmpty()) {
                return;
            }
            getRadarSensor().handleRadarPayload(scenario.frames.get(replayIndex));
            replayIndex = (replayIndex + 1) % scenario.frames.size();
            handler.postDelayed(this, FRAME_PERIOD_MS);
        }
    };

    public FakeBLEGardiaDevice(@NonNull OsmandApplication app,
                               @NonNull BluetoothAdapter bluetoothAdapter,
                               @NonNull String deviceId) {
        super(bluetoothAdapter, deviceId);
        replayScenarios = buildReplayScenarios(app);
    }

    @Override
    public boolean connect(@NonNull Context context, @Nullable Activity activity) {
        if (isConnected() || isConnecting()) {
            return true;
        }
        stopReplay();
        setCurrentState(DeviceConnectionState.CONNECTING);
        for (DeviceListener listener : listeners) {
            listener.onDeviceConnecting(this);
        }
        handler.postDelayed(connectRunnable, CONNECT_DELAY_MS);
        return true;
    }

    @Override
    public boolean disconnect() {
        handler.removeCallbacks(connectRunnable);
        stopReplay();
        setCurrentState(DeviceConnectionState.DISCONNECTED);
        return true;
    }

    private void startReplay() {
        replayIndex = 0;
        replayRunning = true;
        handler.removeCallbacks(replayRunnable);
        handler.post(replayRunnable);
    }

    private void stopReplay() {
        replayRunning = false;
        handler.removeCallbacks(replayRunnable);
    }

    @NonNull
    public String getCurrentScenarioName() {
        return getCurrentScenario().name;
    }

    public void selectNextScenario() {
        scenarioIndex = (scenarioIndex + 1) % replayScenarios.size();
        restartReplayIfNeeded();
    }

    private void restartReplayIfNeeded() {
        replayIndex = 0;
        if (getCurrentState() == DeviceConnectionState.CONNECTED) {
            stopReplay();
            startReplay();
        }
    }

    @NonNull
    private ReplayScenario getCurrentScenario() {
        return replayScenarios.get(scenarioIndex);
    }

    @NonNull
    private static List<ReplayScenario> buildReplayScenarios(@NonNull OsmandApplication app) {
        List<ReplayScenario> scenarios = new ArrayList<>();
        scenarios.add(new ReplayScenario(
                app.getString(R.string.bike_radar_scenario_clear),
                frames(
                        "3000000000000000310000000000000000140300",
                        "3000000000000000310000000000000000140300",
                        "3000000000000000310000000000000000140300"
                )));
        scenarios.add(new ReplayScenario(
                app.getString(R.string.bike_radar_scenario_single_car),
                frames(
                        "3000000000000000310000000000000000140300",
                        "0100010802B6",
                        "0100010C02B6",
                        "0100011002B6",
                        "0100011402B6",
                        "0100011802B6",
                        "0100011C02B6",
                        "0100012002B6",
                        "0100012402B6",
                        "0100012802B6",
                        "0100012C02B6",
                        "0100013002B6",
                        "0100013402B6",
                        "0100013802B6",
                        "3000000000000000310000000000000000140300"
                )));
        scenarios.add(new ReplayScenario(
                app.getString(R.string.bike_radar_scenario_two_cars),
                frames(
                        "0200011202B600020A0798",
                        "0200011602B600020E0798",
                        "0200011A02B60002120798",
                        "0200011E079800021602B6",
                        "02000122079800021A02B6",
                        "02000126079800021E02B6",
                        "0100012A0798",
                        "3000000000000000310000000000000000140300"
                )));
        scenarios.add(new ReplayScenario(
                app.getString(R.string.bike_radar_scenario_fast_car),
                frames(
                        "3000000000000000310000000000000000140300",
                        "010001200798",
                        "010001240798",
                        "010001280798",
                        "0100012C0798",
                        "010001300798",
                        "010001340798",
                        "010001380798",
                        "0100013C0798",
                        "3000000000000000310000000000000000140300"
                )));
        return scenarios;
    }

    @NonNull
    private static List<byte[]> frames(@NonNull String... values) {
        List<byte[]> frames = new ArrayList<>();
        for (String value : values) {
            frames.add(hex(value));
        }
        return frames;
    }

    @NonNull
    private static byte[] hex(@NonNull String value) {
        int length = value.length();
        byte[] out = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int hi = Character.digit(value.charAt(i), 16);
            int lo = Character.digit(value.charAt(i + 1), 16);
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static final class ReplayScenario {
        private final String name;
        private final List<byte[]> frames;

        private ReplayScenario(@NonNull String name, @NonNull List<byte[]> frames) {
            this.name = name;
            this.frames = frames;
        }
    }
}
