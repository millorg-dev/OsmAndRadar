package net.osmand.plus.plugins.bikeradar.devices;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.plugins.bikeradar.RadarConfig;
import net.osmand.plus.plugins.bikeradar.RadarLiveDebugStatus;
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

    protected final GardiaRadarSensor radarSensor;

    public BLEGardiaDevice(@NonNull BluetoothAdapter bluetoothAdapter, @NonNull String deviceId) {
        super(bluetoothAdapter, deviceId);
        radarSensor = new GardiaRadarSensor(this);
        sensors.add(radarSensor);
    }

    @NonNull
    public GardiaRadarSensor getRadarSensor() {
        return radarSensor;
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

    @Override
    protected void onGattConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            RadarLiveDebugStatus.onDeviceConnected();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            RadarLiveDebugStatus.onDeviceDisconnected();
        }
        super.onGattConnectionStateChange(gatt, status, newState);
    }

    @Override
    public boolean connect(@NonNull android.content.Context context, @Nullable android.app.Activity activity) {
        RadarLiveDebugStatus.onDeviceConnecting();
        return super.connect(context, activity);
    }

    @Override
    public boolean disconnect() {
        RadarLiveDebugStatus.onDeviceDisconnected();
        return super.disconnect();
    }
}
