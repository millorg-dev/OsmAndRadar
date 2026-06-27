package net.osmand.test.junit.radar

import net.osmand.plus.plugins.bikeradar.RadarAlertCalculator
import net.osmand.plus.plugins.bikeradar.RadarAlertLevel
import net.osmand.plus.plugins.bikeradar.RadarVehicle
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarAlertCalculatorTest {

    private val threshold = 50.0f

    // --- calculate() ---

    @Test
    fun `empty list returns CLEAR`() {
        val level = RadarAlertCalculator.calculate(emptyList(), threshold)
        assertEquals(RadarAlertLevel.CLEAR, level)
    }

    @Test
    fun `single vehicle below threshold returns APPROACHING`() {
        val vehicles = listOf(vehicle(speed = 40.0f))
        val level = RadarAlertCalculator.calculate(vehicles, threshold)
        assertEquals(RadarAlertLevel.APPROACHING, level)
    }

    @Test
    fun `single vehicle at exact threshold returns HIGH_SPEED`() {
        val vehicles = listOf(vehicle(speed = 50.0f))
        val level = RadarAlertCalculator.calculate(vehicles, threshold)
        assertEquals(RadarAlertLevel.HIGH_SPEED, level)
    }

    @Test
    fun `single vehicle above threshold returns HIGH_SPEED`() {
        val vehicles = listOf(vehicle(speed = 80.0f))
        val level = RadarAlertCalculator.calculate(vehicles, threshold)
        assertEquals(RadarAlertLevel.HIGH_SPEED, level)
    }

    @Test
    fun `multiple vehicles all below threshold returns APPROACHING`() {
        val vehicles = listOf(vehicle(speed = 30.0f), vehicle(id = 2, speed = 49.9f))
        val level = RadarAlertCalculator.calculate(vehicles, threshold)
        assertEquals(RadarAlertLevel.APPROACHING, level)
    }

    @Test
    fun `one fast vehicle among slow ones returns HIGH_SPEED`() {
        val vehicles = listOf(vehicle(speed = 20.0f), vehicle(id = 2, speed = 75.0f))
        val level = RadarAlertCalculator.calculate(vehicles, threshold)
        assertEquals(RadarAlertLevel.HIGH_SPEED, level)
    }

    @Test
    fun `custom threshold respected`() {
        val vehicles = listOf(vehicle(speed = 40.0f))
        assertEquals(RadarAlertLevel.APPROACHING, RadarAlertCalculator.calculate(vehicles, 50.0f))
        assertEquals(RadarAlertLevel.HIGH_SPEED,  RadarAlertCalculator.calculate(vehicles, 30.0f))
    }

    // --- buildState() ---

    @Test
    fun `buildState empty returns CLEAR singleton`() {
        val state = RadarAlertCalculator.buildState(emptyList(), threshold)
        assertEquals(RadarAlertLevel.CLEAR, state.alertLevel)
        assertEquals(0, state.vehicles.size)
    }

    @Test
    fun `buildState sorts vehicles by distance ascending`() {
        val vehicles = listOf(
            vehicle(id = 1, dist = 100.0f, speed = 30.0f),
            vehicle(id = 2, dist = 30.0f,  speed = 20.0f),
            vehicle(id = 3, dist = 60.0f,  speed = 25.0f)
        )
        val state = RadarAlertCalculator.buildState(vehicles, threshold)
        assertEquals(listOf(30.0f, 60.0f, 100.0f), state.vehicles.map { it.distanceMeters })
    }

    @Test
    fun `buildState derives correct HIGH_SPEED level`() {
        val vehicles = listOf(vehicle(speed = 90.0f))
        val state = RadarAlertCalculator.buildState(vehicles, threshold)
        assertEquals(RadarAlertLevel.HIGH_SPEED, state.alertLevel)
    }

    // --- helpers ---

    private fun vehicle(id: Int = 1, dist: Float = 50.0f, speed: Float = 30.0f) =
        RadarVehicle(id, dist, speed)
}
