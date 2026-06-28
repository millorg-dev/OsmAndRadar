package net.osmand.plus.plugins.externalsensors.dialogs

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.plugins.PluginsHelper
import net.osmand.plus.plugins.bikeradar.BikeRadarPlugin
import net.osmand.plus.plugins.bikeradar.RadarConfig
import net.osmand.plus.plugins.bikeradar.devices.BLEGardiaDevice
import net.osmand.plus.plugins.bikeradar.devices.FakeBLEGardiaDevice
import net.osmand.plus.plugins.externalsensors.adapters.ChangeableCharacteristicsAdapter
import net.osmand.plus.plugins.externalsensors.adapters.DeviceCharacteristicsAdapter
import net.osmand.plus.plugins.externalsensors.devices.AbstractDevice
import net.osmand.plus.plugins.externalsensors.devices.AbstractDevice.BATTERY_UNKNOWN_LEVEL_VALUE
import net.osmand.plus.plugins.externalsensors.devices.AbstractDevice.DeviceListener
import net.osmand.plus.plugins.externalsensors.devices.DeviceConnectionResult
import net.osmand.plus.plugins.externalsensors.devices.ble.BLEAbstractDevice
import net.osmand.plus.plugins.externalsensors.devices.sensors.AbstractSensor
import net.osmand.plus.plugins.externalsensors.devices.sensors.DeviceChangeableProperty
import net.osmand.plus.plugins.externalsensors.devices.sensors.DeviceChangeableProperty.NAME
import net.osmand.plus.plugins.externalsensors.devices.sensors.SensorData
import net.osmand.plus.plugins.externalsensors.devices.sensors.SensorDataField
import net.osmand.plus.plugins.externalsensors.dialogs.EditDevicePropertyDialog.OnSaveSensorPropertyCallback
import net.osmand.plus.plugins.externalsensors.dialogs.ForgetDeviceDialog.Companion.showInstance
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.utils.ColorUtilities
import net.osmand.plus.utils.UiUtilities

class ExternalDeviceDetailsFragment : ExternalDevicesBaseFragment(), DeviceListener,
	ForgetDeviceDialog.ForgetDeviceListener,
	OnSaveSensorPropertyCallback, ChangeableCharacteristicsAdapter.OnPropertyClickedListener {
	companion object {
		const val TAG: String = "ExternalSensorDetailsFragment"
		const val DEVICE_ID_KEY = "DEVICE_ID"
		private const val RADAR_THRESHOLD_MIN_KMH = 20
		private const val RADAR_THRESHOLD_MAX_KMH = 70

		fun showInstance(manager: FragmentManager, device: AbstractDevice<out AbstractSensor>) {
			if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
				val fragment = ExternalDeviceDetailsFragment()
				val arguments = Bundle()
				arguments.putString(DEVICE_ID_KEY, device.deviceId)
				fragment.arguments = arguments
				manager.beginTransaction()
					.replace(R.id.fragmentContainer, fragment, TAG)
					.addToBackStack(null)
					.commitAllowingStateLoss()
			}
		}
	}

	lateinit var device: AbstractDevice<out AbstractSensor>
	private var connectionState: TextView? = null
	private var batteryLevel: TextView? = null
	private var batteryLevelContentView: View? = null
	private var progress: View? = null
	private var receivedDataView: RecyclerView? = null
	private var changeablePropertiesView: RecyclerView? = null
	private lateinit var receivedDataAdapter: DeviceCharacteristicsAdapter
	private var changeableCharacteristicsAdapter: ChangeableCharacteristicsAdapter? = null
	private var deviceNamePropertyButton: View? = null
	private var deviceNameProperty: TextView? = null
	private var deviceNameHeader: TextView? = null
	private var forgetButton: View? = null
	private var forgetButtonText: TextView? = null
	private var forgetButtonIcon: ImageView? = null
	private var radarScenarioContainer: View? = null
	private var radarScenarioName: TextView? = null
	private var radarScenarioNextButton: View? = null
	private var radarThresholdContainer: View? = null
	private var radarThresholdValue: TextView? = null
	private var radarThresholdSlider: SeekBar? = null
	private var bikeRadarPlugin: BikeRadarPlugin? = null
	private var updatingRadarThresholdUi = false

	override fun getLayoutId(): Int {
		return R.layout.fragment_external_device_details
	}

	override fun getElevation(): Float {
		return 0f
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val deviceId = arguments?.getString(DEVICE_ID_KEY)
			?: throw IllegalArgumentException("DEVICE_ID in not defined")
		val foundDevice = plugin.getDevice(deviceId)
		if (foundDevice != null) {
			device = foundDevice
		} else {
			getParentFragmentManager().popBackStack()
		}
	}

	override fun setupToolbar(view: View) {
		view.findViewById<ImageButton>(R.id.close_button).apply {
			setOnClickListener {
				requireActivity().onBackPressed()
			}
			setImageResource(AndroidUtils.getNavigationIconResId(context))
		}
	}

	override fun setupUI(view: View) {
		super.setupUI(view)
		deviceNameHeader = view.findViewById(R.id.device_name)
		connectionState = view.findViewById(R.id.connection_state)
		batteryLevel = view.findViewById(R.id.battery_level)
		progress = view.findViewById(R.id.progress_bar)
		batteryLevelContentView = view.findViewById(R.id.battery_level_container)
		deviceNameProperty = view.findViewById(R.id.property_name)
		deviceNamePropertyButton = view.findViewById(R.id.name_property_container)
		radarScenarioContainer = view.findViewById(R.id.radar_scenario_container)
		radarScenarioName = view.findViewById(R.id.radar_scenario_name)
		radarScenarioNextButton = view.findViewById(R.id.radar_scenario_next_button)
		radarThresholdContainer = view.findViewById(R.id.radar_threshold_container)
		radarThresholdValue = view.findViewById(R.id.radar_threshold_value)
		radarThresholdSlider = view.findViewById(R.id.radar_threshold_slider)
		bikeRadarPlugin = PluginsHelper.getPlugin(BikeRadarPlugin::class.java)
		forgetButton = view.findViewById(R.id.forget_device_container)
		forgetButtonText = view.findViewById(R.id.forget_btn)
		forgetButtonIcon = view.findViewById(R.id.forget_icon)
		forgetButton?.setOnClickListener { onForgetDevice() }
		deviceNamePropertyButton?.setOnClickListener { onRenameDevice() }
		radarScenarioNextButton?.setOnClickListener { selectNextRadarScenario() }
		radarThresholdSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				val value = sliderProgressToThreshold(progress)
				updateRadarThresholdValueText(value)
				if (fromUser && !updatingRadarThresholdUi) {
					applyRadarThreshold(value)
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

			override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
		})
		updateConnectedState(view)
		updateButtonState(view)
		updateProperties()
		updateRadarScenarioUi()
		updateRadarThresholdUi()
		val connectionTypeContentView: View = view.findViewById(R.id.connection_type_container)
		val connectionTypeTextView: TextView = view.findViewById(R.id.connection_type)
		var connectionType = getConnectionTypeName()
		connectionTypeTextView.text = connectionType
		var connectionRes = R.string.external_device_details_connection
		connectionTypeContentView.contentDescription =
			"${app.getString(connectionRes)} $connectionType"
		receivedDataAdapter = DeviceCharacteristicsAdapter(app, nightMode)
		changeableCharacteristicsAdapter =
			ChangeableCharacteristicsAdapter(app, nightMode, device, this)
		receivedDataView = view.findViewById(R.id.received_data)
		receivedDataView!!.adapter = receivedDataAdapter
		changeablePropertiesView = view.findViewById(R.id.changeable_properties)
		changeablePropertiesView!!.adapter = changeableCharacteristicsAdapter
		changeableCharacteristicsAdapter?.notifyDataSetChanged()
	}

	private fun getAntText() = app.getString(R.string.external_device_ant)

	private fun getBleText() = app.getString(R.string.external_device_ble)

	private fun isBle(): Boolean {
		return device is BLEAbstractDevice
	}

	private fun updateConnectedState(view: View) {
		val isConnected = device.isConnected
		val rssi = device.rssi
		val signalLevelIcon = if (!isConnected) {
			app.uiUtilities.getIcon(R.drawable.ic_action_signal_not_found, nightMode)
		} else if (rssi > -50) {
			AppCompatResources.getDrawable(app, R.drawable.ic_action_signal_high)
		} else if (rssi > -70) {
			AppCompatResources.getDrawable(app, R.drawable.ic_action_signal_middle)
		} else {
			AppCompatResources.getDrawable(app, R.drawable.ic_action_signal_low)
		}
		val connectedTextId =
			if (isConnected) R.string.external_device_connected else R.string.external_device_disconnected

		connectionState?.text = app.getString(
			R.string.ltr_or_rtl_combine_via_comma,
			app.getString(connectedTextId),
			getConnectionTypeName()
		)
		connectionState?.setCompoundDrawablesWithIntrinsicBounds(signalLevelIcon, null, null, null);

		var batteryLevelValue = device.batteryLevel.toString()
		batteryLevel?.text =
			if (device.hasBatteryLevel()) batteryLevelValue else app.getString(R.string.n_a)
		if (device.batteryLevel == BATTERY_UNKNOWN_LEVEL_VALUE) {
			batteryLevelValue = app.getString(R.string.res_unknown)
		}
		val strRes = R.string.external_device_details_battery
		batteryLevelContentView?.contentDescription = "${app.getString(strRes)} $batteryLevelValue"
		val widgetIcon: ImageView = view.findViewById(R.id.widget_icon)
		val deviceType = device.deviceType
		deviceType.let {
			widgetIcon.background = ContextCompat.getDrawable(
				requireActivity(),
				if (isConnected) {
					if (nightMode) R.drawable.bg_widget_type_icon_dark else R.drawable.bg_widget_type_icon_light
				} else {
					if (nightMode) R.drawable.bg_widget_type_disconnected_icon_dark else R.drawable.bg_widget_type_disconnected_icon_light
				})
			if (!isConnected) {
				widgetIcon.setImageDrawable(getContentIcon(it.disconnectedIconId))
			} else {
				widgetIcon.setImageResource(if (nightMode) it.nightIconId else it.dayIconId)
			}
		}
	}

	private fun getConnectionTypeName() = if (isBle()) getBleText() else getAntText()

	private fun updateConnectedState() {
		view?.let { updateConnectedState(it) }
	}

	private fun updateButtonState() {
		view?.let { updateButtonState(it) }
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun updateProperties() {
		val deviceName = plugin.getDeviceName(device)
		deviceNameProperty?.text = deviceName
		deviceNameHeader?.text = deviceName
		changeableCharacteristicsAdapter?.notifyDataSetChanged()
	}

	private fun updateRadarScenarioUi() {
		val fakeDevice = device as? FakeBLEGardiaDevice
		AndroidUiHelper.updateVisibility(radarScenarioContainer, fakeDevice != null)
		if (fakeDevice != null) {
			radarScenarioName?.text = fakeDevice.currentScenarioName
		}
	}

	private fun updateRadarThresholdUi() {
		val isRadarDevice = device is BLEGardiaDevice
		val hasRadarPlugin = bikeRadarPlugin != null
		AndroidUiHelper.updateVisibility(radarThresholdContainer, isRadarDevice && hasRadarPlugin)
		if (!isRadarDevice || !hasRadarPlugin) {
			return
		}

		val threshold = getCurrentRadarThreshold()
		updatingRadarThresholdUi = true
		radarThresholdSlider?.max = RADAR_THRESHOLD_MAX_KMH - RADAR_THRESHOLD_MIN_KMH
		radarThresholdSlider?.progress = thresholdToSliderProgress(threshold)
		updateRadarThresholdValueText(threshold)
		updatingRadarThresholdUi = false
	}

	private fun updateRadarThresholdValueText(valueKmh: Int) {
		radarThresholdValue?.text = getString(R.string.bike_radar_threshold_value_kmh, valueKmh)
	}

	private fun getCurrentRadarThreshold(): Int {
		val pluginThreshold = bikeRadarPlugin?.HIGH_SPEED_THRESHOLD_KMH?.get()
			?: RadarConfig.DEFAULT_HIGH_SPEED_THRESHOLD_KMH
		return clampRadarThreshold(pluginThreshold.toInt())
	}

	private fun thresholdToSliderProgress(valueKmh: Int): Int {
		return clampRadarThreshold(valueKmh) - RADAR_THRESHOLD_MIN_KMH
	}

	private fun sliderProgressToThreshold(progress: Int): Int {
		return clampRadarThreshold(RADAR_THRESHOLD_MIN_KMH + progress)
	}

	private fun clampRadarThreshold(valueKmh: Int): Int {
		return valueKmh.coerceIn(RADAR_THRESHOLD_MIN_KMH, RADAR_THRESHOLD_MAX_KMH)
	}

	private fun applyRadarThreshold(valueKmh: Int) {
		val plugin = bikeRadarPlugin ?: return
		plugin.HIGH_SPEED_THRESHOLD_KMH.set(valueKmh.toFloat())
		this.plugin.applyRadarThresholdToKnownDevices()
	}

	private fun selectNextRadarScenario() {
		val fakeDevice = device as? FakeBLEGardiaDevice ?: return
		fakeDevice.selectNextScenario()
		updateRadarScenarioUi()
	}

	private fun updateButtonState(view: View) {
		val pairButtonText = view.findViewById<TextView>(R.id.button_text)
		val pairButton = view.findViewById<View>(R.id.pair_btn)
		val pairButtonContainer = view.findViewById<View>(R.id.button_container)
		val connectedStateBtnTextColor = ColorUtilities.getButtonSecondaryTextColorId(nightMode)
		val disconnectedStateBtnTextColor =
			if (nightMode) R.color.dlg_btn_primary_text_dark else R.color.dlg_btn_primary_text_light
		val unpairedStateBtnTextColor =
			if (nightMode) R.color.dlg_btn_primary_text_dark else R.color.dlg_btn_primary_text_light

		val connectedStateBtnBgColorLight = R.drawable.dlg_btn_secondary_light
		val connectedStateBtnBgColorDark = R.drawable.dlg_btn_secondary_dark

		val disconnectedStateBtnBgColorLight = R.drawable.dlg_btn_primary_light
		val disconnectedStateBtnBgColorDark = R.drawable.dlg_btn_primary_dark

		val connectingStateBtnBgColorLight = R.color.active_color_secondary_light
		val connectingStateBtnBgColorDark = R.color.active_color_secondary_dark

		val unpairedStateBtnBgColorLight = R.color.osmand_live_active
		val unpairedStateBtnBgColorDark = R.color.osmand_live_active

		val lightResId: Int
		val darkResId: Int
		var pairBtnTextColorId = 0
		var pairBtnTextId = 0
		var isConnecting = false
		var forgetDeviceTextColor = R.color.deletion_color_warning
		var forgetDeviceIconColor = R.color.deletion_color_warning
		val isDevicePaired = plugin.isDevicePaired(device)
		forgetButton?.isEnabled = isDevicePaired
		deviceNamePropertyButton?.isEnabled = isDevicePaired
		if (!isDevicePaired) {
			lightResId = unpairedStateBtnBgColorLight
			darkResId = unpairedStateBtnBgColorDark
			pairBtnTextColorId = unpairedStateBtnTextColor
			pairBtnTextId = R.string.external_device_details_pair
			pairButton.setOnClickListener { pairDevice() }
			forgetDeviceTextColor =
				if (nightMode) R.color.text_color_tertiary_dark else R.color.text_color_tertiary_light
			forgetDeviceIconColor =
				if (nightMode) R.color.icon_color_secondary_dark else R.color.icon_color_secondary_light
		} else if (device.isConnected) {
			lightResId = connectedStateBtnBgColorLight
			darkResId = connectedStateBtnBgColorDark
			pairBtnTextColorId = connectedStateBtnTextColor
			pairBtnTextId = R.string.external_device_details_disconnect
			pairButton.setOnClickListener { disconnectDevice() }
		} else if (device.isConnecting) {
			lightResId = connectingStateBtnBgColorLight
			darkResId = connectingStateBtnBgColorDark
			pairButton.setOnClickListener(null)
			isConnecting = true
		} else {
			lightResId = disconnectedStateBtnBgColorLight
			darkResId = disconnectedStateBtnBgColorDark
			pairBtnTextColorId = disconnectedStateBtnTextColor
			pairBtnTextId = R.string.external_device_details_connect
			pairButton.setOnClickListener { connectDevice() }
		}
		forgetButtonText?.setTextColor(ContextCompat.getColorStateList(app, forgetDeviceTextColor))
		forgetButtonIcon?.setImageDrawable(
			app.uiUtilities.getIcon(
				R.drawable.ic_action_sensor_remove,
				forgetDeviceIconColor))
		view.post {
			AndroidUtils.setBackground(
				app,
				pairButtonContainer,
				nightMode,
				R.drawable.ripple_solid_light,
				R.drawable.ripple_solid_dark
			)
			AndroidUtils.setBackground(
				app,
				pairButton,
				nightMode,
				lightResId,
				darkResId
			)
			if (pairBtnTextId != 0) {
				pairButtonText.text = getString(pairBtnTextId)
			}
			if (pairBtnTextColorId != 0) {
				val colorStateList = ContextCompat.getColorStateList(app, pairBtnTextColorId)
				pairButtonText.setTextColor(colorStateList)
			}
			progress?.visibility = if (isConnecting) View.VISIBLE else View.GONE
			pairButtonText.visibility = if (isConnecting) View.GONE else View.VISIBLE
		}
	}

	private fun onRenameDevice() {
		EditDevicePropertyDialog.showInstance(requireActivity(), this, device, NAME)
	}

	private fun onForgetDevice() {
		showInstance(requireActivity().supportFragmentManager, this, device.deviceId)
	}

	override fun onResume() {
		super.onResume()
		device.addListener(this)
		updateConnectedState()
		updateButtonState()
		updateProperties()
		updateRadarScenarioUi()
		updateRadarThresholdUi()
	}

	override fun onPause() {
		super.onPause()
		device.removeListener(this)
	}

	private fun pairDevice() {
		plugin.pairDevice(device)
		updateButtonState()
		connectDevice()
	}

	private fun connectDevice() {
		plugin.connectDevice(activity, device)
	}

	private fun disconnectDevice() {
		plugin.disconnectDevice(device)
	}

	override fun onDeviceConnecting(device: AbstractDevice<*>) {
		app.runInUIThread {
			updateButtonState()
		}
	}

	override fun onDeviceConnect(
		device: AbstractDevice<out AbstractSensor>,
		result: DeviceConnectionResult,
		error: String?) {
		app.runInUIThread {
			updateConnectedState()
			updateButtonState()
			updateRadarScenarioUi()
			updateRadarThresholdUi()
		}
	}

	override fun onDeviceDisconnect(device: AbstractDevice<out AbstractSensor>) {
		app.runInUIThread {
			updateConnectedState()
			updateButtonState()
			updateRadarScenarioUi()
			updateRadarThresholdUi()
		}
	}

	override fun onSensorData(sensor: AbstractSensor, data: SensorData) {
		app.runInUIThread {
			updateConnectedState()
			val dataFields = ArrayList<SensorDataField>()
			for (sensor in device.sensors) {
				val sensorDataList = sensor.lastSensorDataList
				sensorDataList?.let {
					for (sensorData in sensorDataList) {
						sensorData?.let {
							dataFields.addAll(sensorData.dataFields)
						}
					}
				}
			}
			receivedDataAdapter.setItems(dataFields)
			if (device.isBatteryLow) {
				app.showShortToastMessage(R.string.external_device_low_battery)
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		connectionState = null
		batteryLevel = null
		receivedDataView = null
		changeablePropertiesView = null
		radarScenarioContainer = null
		radarScenarioName = null
		radarScenarioNextButton = null
		radarThresholdContainer = null
		radarThresholdValue = null
		radarThresholdSlider = null
		bikeRadarPlugin = null
	}

	@ColorRes
	override fun getStatusBarColorId(): Int {
		AndroidUiHelper.setStatusBarContentColor(view, nightMode)
		return if (nightMode) R.color.status_bar_main_dark else R.color.activity_background_color_light
	}

	override fun getContentStatusBarNightMode(): Boolean {
		return nightMode
	}

	override fun onForgetSensorConfirmed(device: AbstractDevice<out AbstractSensor>) {
		plugin.unpairDevice(device)
		updateButtonState()
	}

	override fun changeSensorPropertyValue(
		sensorId: String,
		property: DeviceChangeableProperty,
		newValue: String) {
		plugin.setDeviceProperty(device, property, newValue)
		updateProperties()
	}

	override fun onPropertyClicked(property: DeviceChangeableProperty) {
		EditDevicePropertyDialog.showInstance(requireActivity(), this, device, property)
	}
}