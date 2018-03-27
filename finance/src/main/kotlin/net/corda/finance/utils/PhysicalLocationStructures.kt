package net.corda.finance.utils

import net.corda.annotations.serialization.CordaSerializable
import java.util.*

data class ScreenCoordinate(val screenX: Double, val screenY: Double)

/** A latitude/longitude pair. */
@CordaSerializable
data class WorldCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90..90)
        require(longitude in -180..180)
    }

    /**
     * Convert to screen coordinates using the Mercator projection. You should have a world map image that
     * you know the precise extents of for this function to work.
     *
     * Note that no world map ever has latitude extents of -90 to 90 because at these extremes the mapping tends
     * to infinity. Google Maps, for example, uses a square map image, and square maps yield latitude extents
     * of 85.0511 to -85.0511 = arctan(sinh(π)).
     */
    @Suppress("unused") // Used from the visualiser GUI.
    fun project(screenWidth: Double, screenHeight: Double, topLatitude: Double, bottomLatitude: Double,
                leftLongitude: Double, rightLongitude: Double): ScreenCoordinate {
        require(latitude in bottomLatitude..topLatitude)
        require(longitude in leftLongitude..rightLongitude)

        fun deg2rad(deg: Double) = deg * Math.PI / 180.0
        val leftLngRad = deg2rad(leftLongitude)
        val rightLngRad = deg2rad(rightLongitude)
        fun longitudeToScreenX(lng: Double) = screenWidth * (deg2rad(lng) - leftLngRad) / (rightLngRad - leftLngRad)
        fun screenYRelative(latDeg: Double) = Math.log(Math.tan(latDeg / 360.0 * Math.PI + Math.PI / 4))
        val topLatRel = screenYRelative(topLatitude)
        val bottomLatRel = screenYRelative(bottomLatitude)
        fun latitudeToScreenY(lat: Double) = screenHeight * (screenYRelative(lat) - topLatRel) / (bottomLatRel - topLatRel)
        return ScreenCoordinate(longitudeToScreenX(longitude), latitudeToScreenY(latitude))
    }
}

/**
 * A labelled [WorldCoordinate], where the label is human meaningful. For example, the name of the nearest city.
 * Labels should not refer to non-landmarks, for example, they should not contain the names of organisations.
 * The [countryCode] field is a two letter ISO country code.
 */
@CordaSerializable
data class WorldMapLocation(val coordinate: WorldCoordinate, val description: String, val countryCode: String)

/**
 * A simple lookup table of city names to their coordinates. Lookups are case insensitive.
 */
object CityDatabase {
    private val matcher = Regex("^([a-zA-Z- ]*) \\((..)\\)$")
    private val caseInsensitiveLookups = HashMap<String, WorldMapLocation>()
    val cityMap = HashMap<String, WorldMapLocation>()

    init {
        javaClass.getResourceAsStream("cities.txt").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith("#")) continue
                val (name, lng, lat) = line.split('\t')
                val matchResult = matcher.matchEntire(name) ?: throw Exception("Could not parse line: $line")
                val (city, country) = matchResult.destructured
                val location = WorldMapLocation(WorldCoordinate(lat.toDouble(), lng.toDouble()), city, country)
                caseInsensitiveLookups[city.toLowerCase()] = location
                cityMap[city] = location
            }
        }
    }

    operator fun get(name: String) = caseInsensitiveLookups[name.toLowerCase()]
}
