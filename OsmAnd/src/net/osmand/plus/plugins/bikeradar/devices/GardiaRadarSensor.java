package net.osmand.plus.plugins.bikeradar.devices;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.plugins.bikeradar.BikeRadarPlugin;
import net.osmand.plus.plugins.bikeradar.RadarAlertCalculator;
import net.osmand.plus.plugins.bikeradar.RadarConfig;
import net.osmand.plus.plugins.bikeradar.RadarState;
import net.osmand.plus.plugins.bikeradar.RadarVehicle;
import net.osmand.plus.plugins.externalsensors.devices.sensors.AbstractSensor;
import net.osmand.plus.plugins.externalsensors.devices.sensors.SensorData;
import net.osmand.plus.plugins.externalsensors.devices.sensors.SensorDataField;
import net.osmand.plus.plugins.externalsensors.devices.sensors.SensorWidgetDataField;
import net.osmand.plus.plugins.externalsensors.devices.sensors.SensorWidgetDataFieldType;
import net.osmand.plus.plugins.externalsensors.devices.sensors.ble.BLEAbstractSensor;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * BLE sensor implementation for rear radar devices (Bryton Gardia / Garmin Varia-compatible).
 *
 * Subscribes to the radar measurement characteristic, decodes incoming packets via
 * {@link RadarPacketDecoder}, and notifies {@link BikeRadarPlugin} with the new {@link RadarState}.
 */
public class GardiaRadarSensor extends BLEAbstractSensor {

    /** Immutable SensorData wrapper carrying the latest RadarState. */
    public static class RadarSensorData implements SensorData {

        private final RadarState state;

        RadarSensorData(@NonNull RadarState state) {
            this.state = state;
        }

        @NonNull
        public RadarState getRadarState() {
            return state;
        }

        @NonNull
        @Override
        public List<SensorDataField> getDataFields() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<SensorDataField> getExtraDataFields() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public List<SensorWidgetDataField> getWidgetFields() {
            return null;
        }
    }

    // -----------------------------------------------------------------------

    private volatile RadarState lastState = RadarState.CLEAR;
    private float highSpeedThresholdKmh = RadarConfig.DEFAULT_HIGH_SPEED_THRESHOLD_KMH;

    public GardiaRadarSensor(@NonNull BLEGardiaDevice device) {
        super(device, device.getDeviceId() + "_radar");
    }

    /** Update the high-speed threshold (km/h). Call from plugin settings. */
    public void setHighSpeedThresholdKmh(float thresholdKmh) {
        this.highSpeedThresholdKmh = thresholdKmh;
    }

    @NonNull
    @Override
    public UUID getRequestedCharacteristicUUID() {
        return RadarConfig.UUID_CHAR_RADAR_MEASUREMENT;
    }

    @NonNull
    @Override
    public String getName() {
        return "Rear Radar";
    }

    @NonNull
    @Override
    public List<SensorWidgetDataFieldType> getSupportedWidgetDataFieldTypes() {
        // Radar data is rendered via RadarStripView, not a text widget
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public List<SensorData> getLastSensorDataList() {
        return Collections.singletonList(new RadarSensorData(lastState));
    }

    @Override
    public void writeSensorDataToJson(@NonNull JSONObject json,
                                      @NonNull SensorWidgetDataFieldType widgetDataFieldType) {
        // Radar data is not written to track recordings (live-view only)
    }

    // -----------------------------------------------------------------------
    // BLE callbacks
    // -----------------------------------------------------------------------

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                     @NonNull BluetoothGattCharacteristic characteristic,
                                     int status) {
        // Radar uses notifications only; read is not used
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                        @NonNull BluetoothGattCharacteristic characteristic) {
        if (!getRequestedCharacteristicUUID().equals(characteristic.getUuid())) {
            return;
        }
        byte[] bytes = characteristic.getValue();
        if (bytes == null) {
            return;
        }
        List<RadarVehicle> vehicles = RadarPacketDecoder.decode(bytes);
        RadarState newState = RadarAlertCalculator.buildState(vehicles, highSpeedThresholdKmh);
        lastState = newState;
        lastTimeDifferentValue = System.currentTimeMillis();

        // Notify the plugin via static listener (avoids circular dependency)
        BikeRadarPlugin.RadarStateListener listener = BikeRadarPlugin.getRadarStateListener();
        if (listener != null) {
            listener.onRadarStateChanged(newState);
        }

        // Also fire through the standard device listener chain so the External Sensors
        // plugin knows data arrived (keeps connection health checks happy)
        device.fireSensorDataEvent(this, new RadarSensorData(newState));
    }
}
