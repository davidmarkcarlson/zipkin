package com.cashly.locations.api

import com.cashly.locations.LatLng
import com.cashly.locations.LocationQuery
import com.cashly.locations.LocationStore
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Caller-facing HTTP API for locations, implementing api/locations-api.yaml.
 *
 * The top of the stack. It depends only on the [LocationStore] seam and the JSON view
 * model — never on `vdbs`. It translates an HTTP request into a domain
 * [LocationQuery], asks the store, and renders the domain result as JSON. Point it at
 * a different [LocationStore] (e.g. a future bulk-loaded one) and this code does not
 * change.
 *
 * Install on a Ktor application that has ContentNegotiation(json) configured:
 * ```
 * routing { locationRoutes(store) }
 * ```
 */
fun Route.locationRoutes(store: LocationStore) {
    route("/api/locations") {
        // GET /api/locations?query=&lat=&lng=&radius=&limit=
        get {
            val query = try {
                call.request.queryParameters.toLocationQuery()
            } catch (e: InvalidQueryException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                return@get
            }
            call.respond(store.search(query).map { it.toJson() })
        }

        // GET /api/locations/{id}
        get("/{id}") {
            val id = call.parameters["id"]!!
            when (val location = store.get(id)) {
                null -> call.respond(HttpStatusCode.NotFound, "No location with id $id")
                else -> call.respond(location.toJson())
            }
        }
    }
}

/** Signals malformed client input; the route turns this into a 400. */
private class InvalidQueryException(message: String) : RuntimeException(message)

/**
 * Translate the HTTP query string into a domain [LocationQuery], keeping the store
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
