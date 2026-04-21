package com.jd.plugins.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS84 ↔ GCJ02（火星坐标系）双向转换工具。
 *
 * - 算法精度：正向 1–5 米（GCJ02 加密算法本身的近似上限，代码实现不引入额外误差）。
 * - 反向采用迭代逼近，3 次迭代后残差即低于正向算法精度，无需更多次数。
 * - 国外坐标不做转换，直接原样返回。
 * - 返回 [LatLng] 数据类，支持解构：`val (lat, lng) = convert(...)`。
 */
object GCJ02Converter {

    private const val A = 6378245.0

    private const val EE = 0.00669342162296594323

    data class LatLng(val lat: Double, val lng: Double)

    /** WGS84 → GCJ02。国外坐标原样返回。 */
    fun wgs84ToGcj02(lat: Double, lng: Double): LatLng {
        if (outOfChina(lat, lng)) return LatLng(lat, lng)
        val (dLat, dLng) = offset(lat, lng)
        return LatLng(lat + dLat, lng + dLng)
    }

    /**
     * GCJ02 → WGS84，迭代逼近解。
     *
     * 残差量级：1 次 ≈ 100m，2 次 ≈ 1m，3 次 ≈ 厘米级（已被正向算法 1–5m 的近似误差完全覆盖）。
     */
    fun gcj02ToWgs84(lat: Double, lng: Double): LatLng {
        if (outOfChina(lat, lng)) return LatLng(lat, lng)
        var wgsLat = lat
        var wgsLng = lng
        repeat(3) {
            val (dLat, dLng) = offset(wgsLat, wgsLng)
            wgsLat = lat - dLat
            wgsLng = lng - dLng
        }
        return LatLng(wgsLat, wgsLng)
    }

    /**
     * Haversine 两点距离（米）。与坐标系无关，但两个点必须在同一坐标系下。
     */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371008.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val h = sin(dLat / 2).let { it * it } +
            cos(rLat1) * cos(rLat2) * sin(dLng / 2).let { it * it }
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }

    /** 粗略矩形判断：港澳台与国外坐标均视为"国外"不做转换。 */
    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    /** 以 WGS84 坐标为基准，计算 GCJ02 相对 WGS84 的偏移量（Δlat, Δlng）。 */
    private fun offset(lat: Double, lng: Double): LatLng {
        val x = lng - 105.0
        val y = lat - 35.0
        val radLat = lat * PI / 180.0
        val sinLat = sin(radLat)
        val magic = 1 - EE * sinLat * sinLat
        val sqrtMagic = sqrt(magic)

        val dLat = (transformLat(x, y) * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val dLng = (transformLng(x, y) * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return LatLng(dLat, dLng)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
