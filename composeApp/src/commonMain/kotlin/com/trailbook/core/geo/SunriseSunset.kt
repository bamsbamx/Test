package com.trailbook.core.geo

import kotlin.math.*

data class SunTimes(val sunriseEpochMillis: Long?, val sunsetEpochMillis: Long?, val polarDay: Boolean, val polarNight: Boolean)

object SunriseSunset {
    fun compute(latitude: Double, longitude: Double, dateUtcMillis: Long, utcOffsetHours: Double = 0.0): SunTimes {
        val dayMillis = 86400000L
        val dayStart = dateUtcMillis - (dateUtcMillis % dayMillis) + (utcOffsetHours * 3600_000).toLong()
        val julianDay = dayStart / 86400000.0 + 2440587.5 + 0.5 - utcOffsetHours / 24.0
        val n = julianDay - 2451545.0 + 0.0008 - longitude / 360.0
        fun events(zenithDeg: Double): Pair<Double, Double> {
            var sunriseJ = Double.NaN
            var sunsetJ = Double.NaN
            for (i in 0..1) {
                val offset = if (i == 0) 0.0 else 0.5
                val nn = n + offset
                val m = (0.9856 * nn) - 3.289
                val l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
                var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l)))) % 360.0
                if (ra < 0) ra += 360.0
                val lQ = (l / 90).toInt() * 90
                val raQ = (ra / 90).toInt() * 90
                ra += (lQ - raQ)
                ra /= 15.0
                val sinDec = 0.39782 * sin(Math.toRadians(l))
                val cosDec = cos(asin(sinDec))
                val cosH = (cos(Math.toRadians(zenithDeg)) - (sinDec * sin(Math.toRadians(latitude)))) /
                    (cosDec * cos(Math.toRadians(latitude)))
                if (cosH > 1.0) continue
                if (cosH < -1.0) continue
                val h = if (i == 0) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
                val localMeanTime = h / 15.0 + ra - (0.06571 * nn) - 6.622
                val utcTime = (localMeanTime - longitude / 15.0) % 24.0
                val j = nn + utcTime / 24.0
                if (i == 0) sunriseJ = j else sunsetJ = j
            }
            return sunriseJ to sunsetJ
        }

        val (sr, ss) = events(90.833)
        fun toMillis(j: Double): Long = ((j - 2440587.5) * 86400000.0).toLong()
        val sunrise = if (sr.isNaN()) null else toMillis(sr)
        val sunset = if (ss.isNaN()) null else toMillis(ss)
        if (sunrise == null && sunset == null) {
            val sinDec = 0.39782 * sin(Math.toRadians(n * 0.9856 - 3.289 + 282.634))
            val decl = Math.toDegrees(asin(sinDec))
            val polarDay = ((decl - latitude) * (if (latitude >= 0) 1.0 else -1.0)) > 0
            return SunTimes(null, null, polarDay, !polarDay)
        }
        return SunTimes(sunrise, sunset, false, false)
    }
}
