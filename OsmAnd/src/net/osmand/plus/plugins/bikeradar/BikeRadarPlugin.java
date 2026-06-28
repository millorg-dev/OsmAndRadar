package net.osmand.plus.plugins.bikeradar;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.BuildConfig;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.plugins.OsmandPlugin;
import net.osmand.plus.plugins.bikeradar.ui.RadarStripView;
import net.osmand.plus.settings.backend.preferences.CommonPreference;

/**
 * OsmAnd plugin: Bike Rear Radar.
 *
 * Responsibilities:
 * - Register with OsmAnd plugin system
 * - Add {@link RadarStripView} as a transparent overlay on the map
 * - Receive {@link RadarState} updates from {@link GardiaRadarSensor} via static callback
 * - Provide user-configurable high-speed threshold setting
 *
 * Data flow:
 *   BLEGardiaDevice → GardiaRadarSensor → BikeRadarPlugin.radarStateListener → RadarStripView
 */
public class BikeRadarPlugin extends OsmandPlugin {

    public static final String ID = "net.osmand.bikeradar";

    /** Strip width in dp. */
    private static final int STRIP_WIDTH_DP = 48;

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------

    /** High-speed alert threshold in km/h. Vehicles above this speed turn the strip red. */
    public final CommonPreference<Float> HIGH_SPEED_THRESHOLD_KMH;

    /** Debug-only on-device packet trace for ride diagnostics without laptop. */
    public final CommonPreference<Boolean> DEBUG_TRACE_ENABLED;

    // -----------------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------------

    @Nullable
    private RadarStripView radarStripView;

    // -----------------------------------------------------------------------
    // Radar state listener (static so GardiaRadarSensor can call it without
    // a circular dependency on the plugin instance)
    // -----------------------------------------------------------------------

    public interface RadarStateListener {
        void onRadarStateChanged(@NonNull RadarState state);
    }

    @Nullable
    private static volatile RadarStateListener radarStateListener;

    @Nullable
    public static RadarStateListener getRadarStateListener() {
        return radarStateListener;
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public BikeRadarPlugin(@NonNull OsmandApplication app) {
        super(app);
        HIGH_SPEED_THRESHOLD_KMH = settings.registerFloatPreference(
                "bike_radar_high_speed_threshold_kmh",
                RadarConfig.DEFAULT_HIGH_SPEED_THRESHOLD_KMH).makeProfile();
        DEBUG_TRACE_ENABLED = settings.registerBooleanPreference(
            "bike_radar_debug_trace_enabled",
            BuildConfig.DEBUG).makeGlobal();
    }

    // -----------------------------------------------------------------------
    // OsmandPlugin overrides
    // -----------------------------------------------------------------------

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return app.getString(R.string.bike_radar_plugin_name);
    }

    @Override
    public CharSequence getDescription(boolean linksEnabled) {
        return app.getString(R.string.bike_radar_plugin_description);
    }

    @Override
    public int getLogoResourceId() {
        return R.drawable.ic_action_sensor_speed_outlined;
    }

    @Override
    public void mapActivityCreate(@NonNull MapActivity activity) {
        RadarDebugTrace.configure(app, DEBUG_TRACE_ENABLED.get());
        addRadarStripView(activity);
        registerRadarListener();
    }

    @Override
    public void mapActivityResume(@NonNull MapActivity activity) {
        RadarDebugTrace.configure(app, DEBUG_TRACE_ENABLED.get());
        addRadarStripView(activity);
        registerRadarListener();
    }

    @Override
    public void mapActivityPause(@NonNull MapActivity activity) {
        // Keep listener active in background so strip state is maintained
    }

    @Override
    public void mapActivityDestroy(@NonNull MapActivity activity) {
        radarStateListener = null;
        radarStripView = null;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void addRadarStripView(@NonNull MapActivity activity) {
        if (radarStripView != null) {
            return; // already added
        }
        View stripHostView = activity.findViewById(R.id.radar_strip_host);
        if (!(stripHostView instanceof FrameLayout)) {
            return; // layout not ready
        }
        FrameLayout stripHost = (FrameLayout) stripHostView;

        float density = activity.getResources().getDisplayMetrics().density;
        int stripWidthPx = Math.round(STRIP_WIDTH_DP * density);

        radarStripView = new RadarStripView(activity);
        radarStripView.setAlpha(0.85f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                stripWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.START | Gravity.TOP;

        stripHost.addView(radarStripView, params);
    }

    private void registerRadarListener() {
        RadarDebugTrace.configure(app, DEBUG_TRACE_ENABLED.get());
        radarStateListener = state -> {
            RadarDebugTrace.onState(state);
            RadarStripView view = radarStripView;
            if (view != null) {
                view.updateState(state);
            }
        };
    }

}
