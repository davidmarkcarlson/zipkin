package com.cashly.locations.adapter.http

import com.cashly.locations.application.GetLocation
import com.cashly.locations.application.SearchLocations
import com.cashly.locations.domain.LatLng
import com.cashly.locations.domain.LocationQuery
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Inbound HTTP adapter for locations, implementing api/locations-api.yaml.
 *
 * Its only job is to translate the HTTP edge to and from the application core: parse
 * the request into a domain [LocationQuery] / id, invoke the appropriate use case,
 * and render the domain result as JSON. It holds no application logic and knows
 * nothing about vdbs — it depends on the [SearchLocations] / [GetLocation] use cases
 * and the JSON view model, nothing else.
 *
 * Install on a Ktor application that has ContentNegotiation(json) configured:
 * ```
 * routing { locationRoutes(searchLocations, getLocation) }
 * ```
 */
fun Route.locationRoutes(
    searchLocations: SearchLocations,
    getLocation: GetLocation,
) {
    route("/api/locations") {
        // GET /api/locations?query=&lat=&lng=&radius=&limit=
        get {
            val query = try {
                call.request.queryParameters.toLocationQuery()
            } catch (e: InvalidQueryException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                return@get
            }
            call.respond(searchLocations(query).map { it.toJson() })
        }

        // GET /api/locations/{id}
        get("/{id}") {
            val id = call.parameters["id"]!!
            when (val location = getLocation(id)) {
                null -> call.respond(HttpStatusCode.NotFound, "No location with id $id")
                else -> call.respond(location.toJson())
            }
        }
    }
}

/** Signals malformed client input; the route turns this into a 400. */
private class InvalidQueryException(message: String) : RuntimeException(message)

/**
 * Translate the HTTP query string into a domain [LocationQuery], keeping the use case
 * insulated from raw, possibly-malformed input.
 */
private fun Parameters.toLocationQuery(): LocationQuery =
    LocationQuery(
        text = this["query"]?.trim()?.ifEmpty { null },
        near = parseNear(),
        radiusMeters = intParam("radius"),
        limit = intParam("limit") ?: LocationQuery.DEFAULT_LIMIT,
    )

private fun Parameters.parseNear(): LatLng? {
    val lat = doubleParam("lat")
    val lng = doubleParam("lng")
    return when {
        lat != null && lng != null -> LatLng(lat, lng)
        lat == null && lng == null -> null
        else -> throw InvalidQueryException("lat and lng must be provided together")
    }
}

private fun Parameters.intParam(name: String): Int? =
    this[name]?.let { it.toIntOrNull() ?: throw InvalidQueryException("Invalid value for $name: $it") }

private fun Parameters.doubleParam(name: String): Double? =
    this[name]?.let { it.toDoubleOrNull() ?: throw InvalidQueryException("Invalid value for $name: $it") }
