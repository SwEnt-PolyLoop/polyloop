// Made with Claude.

package com.swent.polyloop.model.location

/**
 * A point on the map, in degrees: [latitude] from -90 to 90 and [longitude] from -180 to 180. It
 * does not depend on Firebase, so that ViewModels and screens can use it.
 */
data class MapPoint(val latitude: Double, val longitude: Double)
