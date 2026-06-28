package net.osmand.plus.plugins.bikeradar.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import net.osmand.plus.plugins.bikeradar.RadarAlertLevel;
import net.osmand.plus.plugins.bikeradar.RadarConfig;
import net.osmand.plus.plugins.bikeradar.RadarState;
import net.osmand.plus.plugins.bikeradar.RadarVehicle;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom View: vertical radar strip drawn on the left edge of the map.
 *
 * Design:
 * - Narrow vertical bar (width ~48dp)
 * - Background color reflects alert level:
 *     GREEN  (#4CAF50) = CLEAR
 *     ORANGE (#FF9800) = APPROACHING
 *     RED    (#F44336) = HIGH_SPEED
 * - One car icon per detected vehicle, Y-position = distance
 *     Top  (Y=0)       = vehicle very close
 *     Bottom (Y=height) = vehicle far away
 * - Smooth Y-animation as vehicles approach
 */
public class RadarStripView extends View {

    // -----------------------------------------------------------------------
    // Alert level colors (Material Design palette)
    // -----------------------------------------------------------------------
    static final int COLOR_CLEAR       = 0xFF4CAF50; // Green 500
    static final int COLOR_APPROACHING = 0xFFFF9800; // Orange 500
    static final int COLOR_HIGH_SPEED  = 0xFFF44336; // Red 500
    static final int COLOR_BACKGROUND  = 0x88000000; // semi-transparent black overlay

    private static final long FRAME_INTERVAL_MS = 16L;
    private static final float POSITION_FOLLOW_FACTOR = 0.28f;
    private static final float SNAP_DISTANCE_PX = 0.6f;

    // -----------------------------------------------------------------------
    // Paint objects
    // -----------------------------------------------------------------------
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vehiclePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private RadarState currentState = RadarState.CLEAR;

    /** Animated Y positions per vehicle ID (pixels from top of view). */
    private final Map<Integer, Float> vehicleYPositions = new HashMap<>();
    private final Map<Integer, Float> vehicleTargetYPositions = new HashMap<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean frameAnimationRunning;
    private final Runnable frameAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            boolean hasMotion = false;

            for (Map.Entry<Integer, Float> entry : vehicleYPositions.entrySet()) {
                int vehicleId = entry.getKey();
                float currentY = entry.getValue();
                Float targetYObj = vehicleTargetYPositions.get(vehicleId);
                if (targetYObj == null) {
                    continue;
                }

                float targetY = targetYObj;
                float delta = targetY - currentY;
                if (Math.abs(delta) <= SNAP_DISTANCE_PX) {
                    if (currentY != targetY) {
                        entry.setValue(targetY);
                    }
                    continue;
                }

                float nextY = currentY + delta * POSITION_FOLLOW_FACTOR;
                entry.setValue(nextY);
                hasMotion = true;
            }

            if (hasMotion) {
                postInvalidateOnAnimation();
                mainHandler.postDelayed(this, FRAME_INTERVAL_MS);
            } else {
                frameAnimationRunning = false;
                postInvalidateOnAnimation();
            }
        }
    };

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public RadarStripView(@NonNull Context context) {
        super(context);
        init();
    }

    public RadarStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint.setStyle(Paint.Style.FILL);
        vehiclePaint.setStyle(Paint.Style.FILL);
        vehiclePaint.setColor(Color.WHITE);
        iconPaint.setStyle(Paint.Style.FILL);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setStrokeWidth(2f);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Update the radar state and trigger a redraw with animated vehicle transitions.
     * Thread-safe: posts to main thread if called from a background thread.
     */
    public void updateState(@NonNull RadarState state) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyState(state);
        } else {
            mainHandler.post(() -> applyState(state));
        }
    }

    // -----------------------------------------------------------------------
    // Internal state application
    // -----------------------------------------------------------------------

    private void applyState(@NonNull RadarState state) {
        currentState = state;

        float viewHeight = getHeight();
        if (viewHeight == 0) {
            // View not yet measured; just store state, onDraw will be called after layout
            postInvalidateOnAnimation();
            return;
        }

        // Update target Y position for each current vehicle.
        for (RadarVehicle vehicle : state.getVehicles()) {
            float targetY = calculateVehicleY(vehicle.getDistanceMeters(),
                    RadarConfig.MAX_RADAR_DISTANCE_METERS, viewHeight);
            int vehicleId = vehicle.getId();
            vehicleTargetYPositions.put(vehicleId, targetY);
            if (!vehicleYPositions.containsKey(vehicleId)) {
                vehicleYPositions.put(vehicleId, targetY);
            }
        }

        // Remove positions/targets for vehicles that are gone.
        vehicleYPositions.entrySet().removeIf(e -> {
            for (RadarVehicle v : state.getVehicles()) {
                if (v.getId() == e.getKey()) return false;
            }
            return true;
        });
        vehicleTargetYPositions.entrySet().removeIf(e -> {
            for (RadarVehicle v : state.getVehicles()) {
                if (v.getId() == e.getKey()) return false;
            }
            return true;
        });

        ensureFrameAnimationRunning();
        postInvalidateOnAnimation();
    }

    private void ensureFrameAnimationRunning() {
        if (!frameAnimationRunning) {
            frameAnimationRunning = true;
            mainHandler.post(frameAnimationRunnable);
        }
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width  = getWidth();
        int height = getHeight();

        // Strip background
        backgroundPaint.setColor(alertLevelToColor(currentState.getAlertLevel()));
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // Vehicle icons
        float iconWidth  = width * 0.58f;
        float iconHeight = iconWidth * 1.35f;
        float iconX = (width - iconWidth) / 2f;

        for (RadarVehicle vehicle : currentState.getVehicles()) {
            Float yPos = vehicleYPositions.get(vehicle.getId());
            if (yPos == null) {
                yPos = calculateVehicleY(vehicle.getDistanceMeters(),
                        RadarConfig.MAX_RADAR_DISTANCE_METERS, height);
            }
            // Center icon vertically around yPos
            float top = yPos - iconHeight / 2f;
            drawVehicleArrow(canvas, iconX, top, iconWidth, iconHeight);
        }

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mainHandler.removeCallbacks(frameAnimationRunnable);
        frameAnimationRunning = false;
    }

    /**
     * Draws a simple navigation-style arrow triangle.
     * Tip points up (towards decreasing distance / rider position).
     */
    private void drawVehicleArrow(@NonNull Canvas canvas, float x, float top,
                                  float w, float h) {
        float cx = x + w / 2f;

        Path arrow = new Path();
        arrow.moveTo(cx, top);
        arrow.lineTo(x + w, top + h);
        arrow.lineTo(x, top + h);
        arrow.close();

        canvas.drawPath(arrow, vehiclePaint);

        // Subtle center notch keeps shape readable on strong strip colors.
        iconPaint.setColor(0x66000000);
        Path notch = new Path();
        notch.moveTo(cx, top + h * 0.32f);
        notch.lineTo(cx + w * 0.12f, top + h * 0.72f);
        notch.lineTo(cx - w * 0.12f, top + h * 0.72f);
        notch.close();
        canvas.drawPath(notch, iconPaint);
        iconPaint.setColor(Color.WHITE);
    }

    // -----------------------------------------------------------------------
    // Static helpers (package-private for unit tests)
    // -----------------------------------------------------------------------

    /**
     * Maps a vehicle distance to a Y pixel position within the strip view.
     *
     * @param distanceMeters  distance in meters (0 = closest, maxDistance = farthest)
     * @param maxDistance     maximum detectable distance in meters
     * @param viewHeight      height of the view in pixels
     * @return Y in pixels: 0 = top (closest), viewHeight = bottom (farthest)
     */
    @VisibleForTesting
    public static float calculateVehicleY(float distanceMeters, float maxDistance, float viewHeight) {
        if (maxDistance <= 0) return viewHeight;
        float fraction = Math.max(0f, Math.min(distanceMeters / maxDistance, 1f));
        // distance=0 (near) → top (Y=0); distance=max (far) → bottom (Y=viewHeight)
        return fraction * viewHeight;
    }

    @VisibleForTesting
    public static int alertLevelToColor(@NonNull RadarAlertLevel level) {
        switch (level) {
            case HIGH_SPEED:  return COLOR_HIGH_SPEED;
            case APPROACHING: return COLOR_APPROACHING;
            default:          return COLOR_CLEAR;
        }
    }
}
