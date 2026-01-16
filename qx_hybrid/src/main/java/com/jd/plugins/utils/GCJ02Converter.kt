package com.jd.plugins.utils

/**
 * WGS84 ↔ GCJ02 坐标转换（火星坐标）
 */

/**
 * 优化：精准WGS84→GCJ02坐标系转换（标准算法，解决偏移问题）
 */
object GCJ02Converter {
    private const val PI = 3.1415926535897932384626
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    /**
     * WGS84转GCJ02（火星坐标系）
     */
    fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        var latitude = lat
        var longitude = lng
        if (outOfChina(latitude, longitude)) {
            return Pair(latitude, longitude)
        }
        var dLat = transformLat(longitude - 105.0, latitude - 35.0)
        var dLng = transformLng(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * PI
        var magic = Math.sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI)
        val mgLat = latitude + dLat
        val mgLng = longitude + dLng
        return Pair(mgLat, mgLng)
    }

    /**
     * 判断是否在国内，不在国内不做转换
     */
    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}