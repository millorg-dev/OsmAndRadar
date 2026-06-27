package net.osmand.test.junit.radar

import net.osmand.plus.plugins.bikeradar.RadarAlertLevel
import net.osmand.plus.plugins.bikeradar.ui.RadarStripView
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarStripViewTest {

    // -----------------------------------------------------------------------
    // calculateVehicleY()
    // -----------------------------------------------------------------------

    @Test
    fun `vehicle at 0m maps to Y=0 (top)`() {
        val y = RadarStripView.calculateVehicleY(0f, 150f, 800f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `vehicle at max distance maps to bottom`() {
        val y = RadarStripView.calculateVehicleY(150f, 150f, 800f)
        assertEquals(800f, y, 0.01f)
    }

    @Test
    fun `vehicle at half distance maps to mid-height`() {
        val y = RadarStripView.calculateVehicleY(75f, 150f, 800f)
        assertEquals(400f, y, 0.01f)
    }

    @Test
    fun `distance beyond max is clamped to bottom`() {
        val y = RadarStripView.calculateVehicleY(300f, 150f, 800f)
        assertEquals(800f, y, 0.01f)
    }

    @Test
    fun `negative distance is clamped to top`() {
        val y = RadarStripView.calculateVehicleY(-10f, 150f, 800f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `zero viewHeight returns 0`() {
        val y = RadarStripView.calculateVehicleY(50f, 150f, 0f)
        assertEquals(0f, y, 0.01f)
    }

    // -----------------------------------------------------------------------
    // alertLevelToColor()
    // -----------------------------------------------------------------------

    @Test
    fun `CLEAR maps to green color`() {
        assertEquals(RadarStripView.COLOR_CLEAR,
            RadarStripView.alertLevelToColor(RadarAlertLevel.CLEAR))
    }

    @Test
    fun `APPROACHING maps to orange color`() {
        assertEquals(RadarStripView.COLOR_APPROACHING,
            RadarStripView.alertLevelToColor(RadarAlertLevel.APPROACHING))
    }

    @Test
    fun `HIGH_SPEED maps to red color`() {
        assertEquals(RadarStripView.COLOR_HIGH_SPEED,
            RadarStripView.alertLevelToColor(RadarAlertLevel.HIGH_SPEED))
    }
}
