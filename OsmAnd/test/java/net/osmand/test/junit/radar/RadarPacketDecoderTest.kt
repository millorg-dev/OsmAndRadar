package net.osmand.test.junit.radar

import net.osmand.plus.plugins.bikeradar.RadarConfig
import net.osmand.plus.plugins.bikeradar.devices.RadarPacketDecoder
import org.junit.Assert.*
import org.junit.Test

class RadarPacketDecoderTest {

    // -----------------------------------------------------------------------
    // decode() – structural tests
    // -----------------------------------------------------------------------

    @Test
    fun `empty byte array returns empty list`() {
        val result = RadarPacketDecoder.decode(byteArrayOf())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero threats in count byte returns empty list`() {
        val result = RadarPacketDecoder.decode(byteArrayOf(0x00))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `one vehicle decoded correctly`() {
        // count=1, id=1, distCode=32 (mid-range), speed=500 (= 5 m/s = 18 km/h)
        val bytes = byteArrayOf(
            0x01,              // count
            0x00, 0x01,        // id = 1 (BE uint16)
            32.toByte(),       // distCode
            0x01, 0xF4.toByte() // speed = 500 (BE uint16)
        )
        val result = RadarPacketDecoder.decode(bytes)
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)

        val expectedDist = RadarPacketDecoder.distanceCodeToMeters(32)
        assertEquals(expectedDist, result[0].distanceMeters, 0.01f)

        val expectedSpeed = RadarPacketDecoder.rawSpeedToKmh(500)
        assertEquals(expectedSpeed, result[0].relativeSpeedKmh, 0.01f)
    }

    @Test
    fun `two vehicles decoded correctly`() {
        val bytes = byteArrayOf(
            0x02,
            // vehicle 1: id=10, distCode=63 (closest), speed=2000
            0x00, 0x0A, 63.toByte(), 0x07, 0xD0.toByte(),
            // vehicle 2: id=20, distCode=0 (farthest), speed=100
            0x00, 0x14, 0x00, 0x00, 0x64
        )
        val result = RadarPacketDecoder.decode(bytes)
        assertEquals(2, result.size)
        assertEquals(10, result[0].id)
        assertEquals(20, result[1].id)
    }

    @Test
    fun `truncated packet decodes partial vehicles`() {
        // count=3 but only enough bytes for 1 vehicle
        val bytes = byteArrayOf(
            0x03,
            0x00, 0x01, 10.toByte(), 0x00, 0x32 // only 1 entry
        )
        val result = RadarPacketDecoder.decode(bytes)
        assertEquals(1, result.size) // should not crash
    }

    @Test
    fun `packet too short to contain count returns empty`() {
        // No bytes at all handled above; 1-byte packet with count=1 but no data
        val result = RadarPacketDecoder.decode(byteArrayOf(0x01))
        assertTrue(result.isEmpty())
    }

    // -----------------------------------------------------------------------
    // distanceCodeToMeters()
    // -----------------------------------------------------------------------

    @Test
    fun `distance code 0 maps to max distance`() {
        val dist = RadarPacketDecoder.distanceCodeToMeters(0)
        assertEquals(RadarConfig.MAX_RADAR_DISTANCE_METERS, dist, 0.1f)
    }

    @Test
    fun `distance code MAX maps to 0 meters`() {
        val dist = RadarPacketDecoder.distanceCodeToMeters(RadarConfig.MAX_DISTANCE_CODE)
        assertEquals(0.0f, dist, 0.1f)
    }

    @Test
    fun `distance code mid-range maps to half distance`() {
        val mid = RadarConfig.MAX_DISTANCE_CODE / 2
        val dist = RadarPacketDecoder.distanceCodeToMeters(mid)
        val expected = RadarConfig.MAX_RADAR_DISTANCE_METERS / 2
        assertEquals(expected, dist, 1.0f)
    }

    @Test
    fun `distance code above max is clamped`() {
        val dist = RadarPacketDecoder.distanceCodeToMeters(255)
        assertEquals(0.0f, dist, 0.1f)
    }

    // -----------------------------------------------------------------------
    // rawSpeedToKmh()
    // -----------------------------------------------------------------------

    @Test
    fun `speed 0 maps to 0 kmh`() {
        assertEquals(0.0f, RadarPacketDecoder.rawSpeedToKmh(0), 0.001f)
    }

    @Test
    fun `speed 1000 maps to 36 kmh`() {
        // 1000 * 0.01 m/s * 3.6 = 36 km/h
        assertEquals(36.0f, RadarPacketDecoder.rawSpeedToKmh(1000), 0.01f)
    }

    @Test
    fun `speed 1389 maps to approx 50 kmh`() {
        // 1389 * 0.01 * 3.6 = 50.004 km/h
        assertEquals(50.0f, RadarPacketDecoder.rawSpeedToKmh(1389), 0.1f)
    }
}
