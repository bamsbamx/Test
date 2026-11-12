package com.trailbook.core.format

import kotlin.math.abs

object NumFormat {
    fun fixed(value: Double, decimals: Int): String {
        val neg = value < 0
        val scaled = (abs(value) * pow10(decimals)) + 0.5
        val intPart = (scaled.toLong() / pow10(decimals)).toLong()
        var frac = (scaled.toLong() % pow10(decimals)).toLong()
        val s = if (decimals == 0) {
            intPart.toString()
        } else {
            intPart.toString() + "." + frac.toString().padStart(decimals, '0')
        }
        return if (neg && (intPart != 0L || frac != 0L)) "-$s" else s
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }

    fun padStartNumber(value: Long, width: Int): String = value.toString().padStart(width, '0')
}

object Str {
    fun fmt(pattern: String, vararg args: Any?): String {
        val sb = StringBuilder()
        var argIdx = 0
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c != '%') { sb.append(c); i++; continue }
            var j = i + 1
            var zeroPad = false
            if (j < pattern.length && pattern[j] == '0') { zeroPad = true; j++ }
            var width = 0
            while (j < pattern.length && pattern[j].isDigit()) { width = width * 10 + (pattern[j] - '0'); j++ }
            var precision = -1
            if (j < pattern.length && pattern[j] == '.') {
                j++
                precision = 0
                while (j < pattern.length && pattern[j].isDigit()) { precision = precision * 10 + (pattern[j] - '0'); j++ }
            }
            if (j >= pattern.length) { sb.append(c); break }
            val spec = pattern[j]
            val arg = args.getOrNull(argIdx)
            when (spec) {
                '%' -> sb.append('%')
                's' -> { sb.append(arg.toString()); argIdx++ }
                'd' -> {
                    val n = (arg as? Number)?.toLong() ?: 0L
                    val s = n.toString()
                    if (width > s.length) sb.append("0".repeat(width - s.length))
                    sb.append(s); argIdx++
                }
                'X' -> {
                    val n = (arg as? Number)?.toInt() ?: 0
                    val s = n.toString(16).uppercase()
                    if (width > s.length) sb.append("0".repeat(width - s.length))
                    sb.append(s); argIdx++
                }
                'f' -> {
                    val decimals = if (precision >= 0) precision else 6
                    val d = (arg as? Number)?.toDouble() ?: 0.0
                    val s = NumFormat.fixed(d, decimals)
                    if (width > s.length) sb.append(if (zeroPad) "0".repeat(width - s.length) else " ".repeat(width - s.length))
                    sb.append(s); argIdx++
                }
                else -> sb.append(spec)
            }
            i = j + 1
        }
        return sb.toString()
    }
}
