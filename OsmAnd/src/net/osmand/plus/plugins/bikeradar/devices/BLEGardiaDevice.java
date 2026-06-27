package net.osmand.plus.plugins.bikeradar.devices;

import android.bluetooth.BluetoothAdapter;

import androidx.annotation.NonNull;

import net.osmand.plus.plugins.bikeradar.RadarConfig;
import net.osmand.plus.plugins.externalsensors.DeviceType;
import net.osmand.plus.plugins.externalsensors.devices.ble.BLEAbstractDevice;

import java.util.UUID;

/**
 * BLE device representing a Bryton Gardia R300L (or Garmin Varia RTL-compatible) rear radar.
 *
 * Service UUID: {@link RadarConfig#UUID_SERVICE_RADAR}
 * TODO: Replace with confirmed Bryton Gardia UUID after BLE sniffing.
 */
public class BLEGardiaDevice extends BLEAbstractDevice {

    public BLEGardiaDevice(@NonNull BluetoothAdapter bluetoothAdapter, @NonNull String deviceId) {
        super(bluetoothAdapter, deviceId);
        sensors.add(new GardiaRadarSensor(this));
    }

    @NonNull
    @Override
    public DeviceType getDeviceType() {
        return DeviceType.BLE_RADAR_GARDIA;
    }

    @NonNull
    public static UUID getServiceUUID() {
        return RadarConfig.UUID_SERVICE_RADAR;
    }
}
