package net.osmand.plus.plugins.bikeradar.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

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

    private static final int MIN_ANIMATION_DURATION_MS = 120;
    private static final int MAX_ANIMATION_DURATION_MS = 360;
    private static final float MIN_MOVE_TO_ANIMATE_PX = 1.5f;

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
    private final Map<Integer, ValueAnimator> vehicleAnimators = new HashMap<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
            invalidate();
            return;
        }

        // Cancel animators for vehicles that disappeared
        for (Map.Entry<Integer, ValueAnimator> entry : vehicleAnimators.entrySet()) {
            boolean stillPresent = false;
            for (RadarVehicle v : state.getVehicles()) {
                if (v.getId() == entry.getKey()) {
                    stillPresent = true;
                    break;
                }
            }
            if (!stillPresent) {
                entry.getValue().cancel();
            }
        }

        // Animate each current vehicle to its target Y position
        for (RadarVehicle vehicle : state.getVehicles()) {
            float targetY = calculateVehicleY(vehicle.getDistanceMeters(),
                    RadarConfig.MAX_RADAR_DISTANCE_METERS, viewHeight);
            animateVehicleToY(vehicle.getId(), targetY);
        }

        // Remove positions for vehicles that are gone
        vehicleYPositions.entrySet().removeIf(e -> {
            for (RadarVehicle v : state.getVehicles()) {
                if (v.getId() == e.getKey()) return false;
            }
            return true;
        });
        vehicleAnimators.entrySet().removeIf(e -> {
            for (RadarVehicle v : state.getVehicles()) {
                if (v.getId() == e.getKey()) return false;
            }
            return true;
        });

        invalidate();
    }

    private void animateVehicleToY(int vehicleId, float targetY) {
        float startY = vehicleYPositions.getOrDefault(vehicleId, (float) getHeight());

        ValueAnimator existing = vehicleAnimators.get(vehicleId);
        if (existing != null) {
            Object current = existing.getAnimatedValue();
            if (current instanceof Float) {
                startY = (Float) current;
            }
            existing.cancel();
        }

        float delta = Math.abs(targetY - startY);
        if (delta < MIN_MOVE_TO_ANIMATE_PX) {
            vehicleYPositions.put(vehicleId, targetY);
            invalidate();
            return;
        }

        float viewHeight = Math.max(1f, getHeight());
        float normalizedDelta = Math.min(delta / viewHeight, 1f);
        int durationMs = (int) (MIN_ANIMATION_DURATION_MS
                + (MAX_ANIMATION_DURATION_MS - MIN_ANIMATION_DURATION_MS) * normalizedDelta);

        ValueAnimator animator = ValueAnimator.ofFloat(startY, targetY);
        animator.setDuration(durationMs);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            vehicleYPositions.put(vehicleId, (Float) animation.getAnimatedValue());
            invalidate();
        });
        vehicleAnimators.put(vehicleId, animator);
        animator.start();
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
        float iconWidth  = width * 0.7f;
        float iconHeight = iconWidth * 1.6f;
        float iconX = (width - iconWidth) / 2f;

        for (RadarVehicle vehicle : currentState.getVehicles()) {
            Float yPos = vehicleYPositions.get(vehicle.getId());
            if (yPos == null) {
                yPos = calculateVehicleY(vehicle.getDistanceMeters(),
                        RadarConfig.MAX_RADAR_DISTANCE_METERS, height);
            }
            // Center icon vertically around yPos
            float top = yPos - iconHeight / 2f;
            drawCarIcon(canvas, iconX, top, iconWidth, iconHeight);
        }
    }

    /**
     * Draws a simplified top-down car silhouette using Canvas primitives.
     * No external bitmap dependencies.
     */
    private void drawCarIcon(@NonNull Canvas canvas, float x, float top,
                             float w, float h) {
        // Car body
        RectF body = new RectF(x, top + h * 0.2f, x + w, top + h * 0.8f);
        canvas.drawRoundRect(body, w * 0.15f, w * 0.15f, vehiclePaint);

        // Roof (narrower rectangle in the middle)
        RectF roof = new RectF(x + w * 0.15f, top + h * 0.35f,
                               x + w * 0.85f, top + h * 0.65f);
        iconPaint.setColor(0x66000000);
        canvas.drawRoundRect(roof, w * 0.1f, w * 0.1f, iconPaint);
        iconPaint.setColor(Color.WHITE);

        // Front wheels
        float wheelW = w * 0.18f;
        float wheelH = h * 0.12f;
        canvas.drawOval(new RectF(x - wheelW * 0.3f, top + h * 0.22f,
                                  x + wheelW * 0.7f, top + h * 0.22f + wheelH), vehiclePaint);
        canvas.drawOval(new RectF(x + w - wheelW * 0.7f, top + h * 0.22f,
                                  x + w + wheelW * 0.3f, top + h * 0.22f + wheelH), vehiclePaint);

        // Rear wheels
        canvas.drawOval(new RectF(x - wheelW * 0.3f, top + h * 0.66f,
                                  x + wheelW * 0.7f, top + h * 0.66f + wheelH), vehiclePaint);
        canvas.drawOval(new RectF(x + w - wheelW * 0.7f, top + h * 0.66f,
                                  x + w + wheelW * 0.3f, top + h * 0.66f + wheelH), vehiclePaint);
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
