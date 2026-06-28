package net.osmand.plus.plugins.bikeradar.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;

import net.osmand.plus.plugins.bikeradar.RadarLiveDebugStatus;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Small diagnostics overlay rendered on top of the map.
 */
public class RadarDebugOverlayView extends View {

    private static final long REFRESH_MS = 1000L;
    private static final long DATA_STALE_MS = 3000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panelRect = new RectF();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            postInvalidateOnAnimation();
            mainHandler.postDelayed(this, REFRESH_MS);
        }
    };

    public RadarDebugOverlayView(@NonNull Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        panelPaint.setColor(0xB0000000);
        panelPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(13f * density);
        textPaint.setStyle(Paint.Style.FILL);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mainHandler.post(refreshRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mainHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int width = Math.round(220f * density);
        int height = Math.round(130f * density);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        RadarLiveDebugStatus.Snapshot snapshot = RadarLiveDebugStatus.snapshot();
        long now = System.currentTimeMillis();

        String conn;
        if (snapshot.connected) {
            conn = "CONNECTED";
        } else if (snapshot.connecting) {
            conn = "CONNECTING";
        } else {
            conn = "DISCONNECTED";
        }

        long packetAgeMs = snapshot.lastPacketMs > 0 ? (now - snapshot.lastPacketMs) : Long.MAX_VALUE;
        String data = packetAgeMs <= DATA_STALE_MS ? "OK" : "STALE";
        String packetAge = snapshot.lastPacketMs > 0
                ? String.format(Locale.US, "%.1fs", packetAgeMs / 1000f)
                : "n/a";

        String[] lines = new String[] {
                "PLUG " + (snapshot.pluginActive ? "ON" : "OFF"),
                "BLE  " + conn,
                "DATA " + data + " age=" + packetAge,
                "PKT  " + snapshot.packetCount + " ST " + snapshot.stateCount,
                "LAST " + snapshot.lastPacketBytes + "B veh=" + snapshot.lastVehicleCount,
                "ALRT " + snapshot.lastAlertLevel,
                "CONN " + formatTime(snapshot.lastConnectMs)
        };

        panelRect.set(0f, 0f, getWidth(), getHeight());
        float density = getResources().getDisplayMetrics().density;
        canvas.drawRoundRect(panelRect, 8f * density, 8f * density, panelPaint);

        float lineHeight = textPaint.getTextSize() + (2f * density);
        float y = 18f * density;
        for (String line : lines) {
            canvas.drawText(line, 10f * density, y, textPaint);
            y += lineHeight;
        }
    }

    @NonNull
    private String formatTime(long timeMs) {
        if (timeMs <= 0) {
            return "--:--:--";
        }
        synchronized (timeFormat) {
            return timeFormat.format(timeMs);
        }
    }
}