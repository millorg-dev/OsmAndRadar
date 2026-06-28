package net.osmand.plus.plugins.externalsensors

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.bikeradar.RadarConfig
import net.osmand.plus.plugins.bikeradar.devices.FakeBLEGardiaDevice
import net.osmand.plus.plugins.externalsensors.devices.AbstractDevice
import net.osmand.plus.plugins.externalsensors.devices.ble.BLEAbstractDevice
import net.osmand.plus.plugins.externalsensors.devices.ble.BLEOBDDevice
import net.osmand.plus.settings.backend.preferences.CommonPreferenceProvider

class ExternalSensorsDeviceHelper(
	private val externalSensorsPlugin: ExternalSensorsPlugin,
	app: OsmandApplication,
	preferenceProvider: CommonPreferenceProvider<String>) : DevicesHelper(app, preferenceProvider) {

	private val application = app

	override fun addFoundBLEDevice(device: BLEAbstractDevice) {
		val isOBDDevice = device is BLEOBDDevice
		if (!isOBDDevice) {
			devices[device.deviceId] = device
		}
	}

	override fun onDevicePaired(device: AbstractDevice<*>) {
		super.onDevicePaired(device)
		externalSensorsPlugin.onDevicePaired(device)
	}

	fun createFakeRadarDevice(): AbstractDevice<*>? {
		val existing = devices[RadarConfig.DEBUG_FAKE_DEVICE_ID]
		if (existing != null) {
			return existing
		}
		val adapter = bluetoothAdapter ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
		val device = FakeBLEGardiaDevice(application, adapter, RadarConfig.DEBUG_FAKE_DEVICE_ID)
		device.setDeviceName(application.getString(R.string.bike_radar_simulated_device_name))
		devices[device.deviceId] = device
		return device
	}
}