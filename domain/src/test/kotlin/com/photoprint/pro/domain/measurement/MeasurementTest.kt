package com.photoprint.pro.domain.measurement

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementTest {
    @Test
    fun `unit conversions round trip`() {
        assertEquals(25.4, LengthUnit.INCH.toMm(1.0), 1e-12)
        assertEquals(35.0, LengthUnit.CM.toMm(3.5), 1e-12)
        assertEquals(2.0, LengthUnit.INCH.fromMm(50.8), 1e-12)
        assertEquals(10.0, LengthUnit.CM.fromMm(100.0), 1e-12)
        for (u in LengthUnit.entries) assertEquals(123.456, u.toMm(u.fromMm(123.456)), 1e-9)
    }

    @Test
    fun `points and mils`() {
        assertEquals(72.0, Measurement.mmToPoints(25.4), 1e-12)
        assertEquals(1000.0, Measurement.mmToMils(25.4), 1e-12)
        assertEquals(100.0, Measurement.pointsToMm(Measurement.mmToPoints(100.0)), 1e-12)
        assertEquals(100.0, Measurement.milsToMm(Measurement.mmToMils(100.0)), 1e-12)
    }
}
