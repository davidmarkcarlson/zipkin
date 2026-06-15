package com.cashly.locations.adapter.vdbs

import com.cashly.locations.domain.LatLng
import com.cashly.locations.domain.LocationQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the vdbs outbound adapter through the [com.cashly.locations.application.LocationProvider]
 * port, with a Ktor [MockEngine] standing in for vdbs. Verifies both directions of the
 * translation: domain query -> vdbs request, and vdbs response -> domain locations.
 */
class VdbsLocationProviderTest {

    private val searchBody = """
        {"results":[
          {"place_id":"abc","display_name":"Blue Bottle","formatted_address":"1 Main St",
           "lat":37.7,"lon":-122.4,"categories":["cafe","coffee"]},
          {"place_id":"def","display_name":"Philz","formatted_address":"2 Market St",
           "lat":37.8,"lon":-122.5,"categories":[]}
        ]}
    """.trimIndent()

    private val placeBody = """
        {"place_id":"abc","display_name":"Blue Bottle","formatted_address":"1 Main St",
         "lat":37.7,"lon":-122.4,"categories":["cafe"]}
    """.trimIndent()

    /** Builds a provider whose vdbs is a MockEngine. Captures the last requested URL. */
    private class Fixture(reply: (url: String) -> Pair<HttpStatusCode, String>) {
        var lastUrl: String = ""
        val provider: VdbsLocationProvider

        init {
            val engine = MockEngine { request ->
                lastUrl = request.url.toString()
                val (status, body) = reply(lastUrl)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val http = HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            provider = VdbsLocationProvider(http, "https://vdbs.test", "secret")
        }
    }

    private fun ok(body: String): (String) -> Pair<HttpStatusCode, String> =
        { HttpStatusCode.OK to body }

    @Test
    fun `transforms vdbs search results into domain locations`() = runBlocking {
        val results = Fixture(ok(searchBody)).provider.search(LocationQuery(text = "coffee"))

        assertEquals(2, results.size)
        val first = results.first()
        assertEquals("abc", first.id)
        assertEquals("Blue Bottle", first.name)
        assertEquals("1 Main St", first.address)
        assertEquals(LatLng(37.7, -122.4), first.position)
        assertEquals(listOf("cafe", "coffee"), first.categories)
    }

    @Test
    fun `translates a domain query into vdbs request params, including auth`() = runBlocking {
        val fixture = Fixture(ok(searchBody))
        fixture.provider.search(
            LocationQuery(
                text = "coffee",
                near = LatLng(37.7, -122.4),
                radiusMeters = 500,
                limit = 10,
            ),
        )

        val url = fixture.lastUrl
        assertTrue("/v1/places/search" in url, url)
        assertTrue("q=coffee" in url, url)
        assertTrue("lat=37.7" in url, url)
        assertTrue("lon=-122.4" in url, url)
        assertTrue("radius=500" in url, url)
        assertTrue("limit=10" in url, url)
        assertTrue("api_key=secret" in url, url)
    }

    @Test
    fun `looks up a single location by id`() = runBlocking {
        val location = Fixture(ok(placeBody)).provider.get("abc")
        assertEquals("Blue Bottle", location?.name)
    }

    @Test
    fun `returns null when vdbs has no such location`() = runBlocking {
        val fixture = Fixture { HttpStatusCode.NotFound to "not found" }
        assertNull(fixture.provider.get("missing"))
    }
}
