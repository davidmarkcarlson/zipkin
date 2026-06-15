package com.cashly.locations.application

import com.cashly.locations.domain.LatLng
import com.cashly.locations.domain.Location
import com.cashly.locations.domain.LocationQuery
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the search use case in isolation — no HTTP, no vdbs — against a fake
 * [LocationProvider]. This is where the application rule (bounding the result limit)
 * lives, and it can be verified without any adapter, which is the point of the layer.
 */
class SearchLocationsTest {

    /** Records the query it was handed and returns a fixed list. */
    private class FakeProvider(private val results: List<Location>) : LocationProvider {
        var lastQuery: LocationQuery? = null
        override suspend fun search(query: LocationQuery): List<Location> {
            lastQuery = query
            return results
        }
        override suspend fun get(id: String): Location? = results.firstOrNull { it.id == id }
    }

    private fun locations(n: Int): List<Location> =
        (1..n).map { Location("id$it", "name$it", "addr$it", LatLng(0.0, 0.0)) }

    @Test
    fun `bounds the requested limit and trims the results`() = runBlocking {
        val provider = FakeProvider(locations(5))
        val search = SearchLocations(provider)

        val results = search(LocationQuery(limit = 2))

        assertEquals(2, results.size)
        assertEquals(2, provider.lastQuery?.limit, "use case should pass the bounded limit to the provider")
    }

    @Test
    fun `caps the limit at MAX_LIMIT`() = runBlocking {
        val provider = FakeProvider(locations(1))
        SearchLocations(provider)(LocationQuery(limit = 10_000))

        assertEquals(LocationQuery.MAX_LIMIT, provider.lastQuery?.limit)
    }

    @Test
    fun `raises a non-positive limit to at least one`() = runBlocking {
        val provider = FakeProvider(locations(1))
        SearchLocations(provider)(LocationQuery(limit = 0))

        assertEquals(1, provider.lastQuery?.limit)
    }
}
