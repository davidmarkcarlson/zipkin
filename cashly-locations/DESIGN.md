# Locations backend — design (first pass)

**Goal.** Serve a `locations` API (search + lookup) whose first data source is
cashtie's **vdbs** service, structured so we can later swap vdbs for data we bulk load
and serve ourselves **without changing the API or any caller**.

**Status.** Design proposal. The Kotlin below is *illustrative* — it shows the shape
of each layer and the boundaries; it is not a buildable module. Stack assumed: Kotlin
+ coroutines, Ktor (server routing + HTTP client), kotlinx.serialization.

**Contract is pre-existing.** The locations endpoints and `Location` schema already
live in the repo's `api/openapi.yaml`. That spec is the source of truth; this design
does **not** introduce a new one. The HTTP adapter implements those endpoints and the
domain/JSON types mirror that `Location` schema. Field names shown below are
placeholders until reconciled with the actual schema — see *Reconcile with the
existing spec* at the end.

---

## Architecture

Ports & adapters (hexagonal). The request flows inbound-adapter → use case → port →
outbound-adapter, and dependencies point inward only.

```
HTTP caller
    │  api/openapi.yaml   (existing — the contract a caller sees)
    ▼
adapter.http   LocationRoutes              inbound (HTTP) adapter — translation only
    ▼  calls a use case
application    SearchLocations / GetLocation   the core: application rules
    ▼  calls out through a port
application    LocationProvider (interface)    outbound port            ◄── the seam
    ▲  implemented by
adapter.vdbs   VdbsLocationProvider          outbound (vdbs) adapter
    │  domain query → vdbs request, vdbs JSON → domain
    ▼
cashtie / vdbs
```

| Layer | Role | Depends on |
|---|---|---|
| `domain` | Entities: `Location`, `LatLng`, `LocationQuery` | nothing |
| `application` | Use cases (`SearchLocations`, `GetLocation`) + outbound port (`LocationProvider`) | `domain` |
| `adapter.http` | Inbound HTTP adapter: routes + JSON view model | `application`, `domain` |
| `adapter.vdbs` | Outbound vdbs adapter: provider, client, wire model, mapper | `application`, `domain` |

The key property: **vdbs is an implementation detail behind the `LocationProvider`
port.** Everything vdbs-specific (wire model, HTTP client, fault type, mapper) is
Kotlin `internal`, so the core cannot reference a vdbs type even by accident — the
compiler enforces the boundary, not convention.

---

## The contract (existing `api/openapi.yaml`)

The caller-facing API already exists in `api/openapi.yaml`: the locations endpoints
and the `Location` schema. This design conforms to it rather than defining anything
new. The HTTP adapter implements those endpoints; the domain `Location` and the JSON
view model are shaped to match that schema.

The endpoints/fields used in the snippets below — `GET /api/locations` (search) and
`GET /api/locations/{id}` (lookup), and `Location { id, name, address, position{lat,lng},
categories }` — are placeholders standing in for whatever the spec actually declares.
Where they differ, the spec wins; only the domain model and the two mapping points
(`Location.toJson`, `VdbsPlace.toLocation`) change. See *Reconcile with the existing
spec*.

---

## Layers, in code

### domain — pure, dependency-free

```kotlin
data class LatLng(val lat: Double, val lng: Double)

data class Location(
    val id: String,
    val name: String,
    val address: String,
    val position: LatLng,
    val categories: List<String> = emptyList(),
)

data class LocationQuery(
    val text: String? = null,
    val near: LatLng? = null,
    val radiusMeters: Int? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object { const val DEFAULT_LIMIT = 25; const val MAX_LIMIT = 100 }
}
```

### application — use cases + the outbound port

The port is owned by the core; adapters implement it.

```kotlin
interface LocationProvider {                       // outbound port (the seam)
    suspend fun search(query: LocationQuery): List<Location>
    suspend fun get(id: String): Location?
}

class SearchLocations(private val locations: LocationProvider) {
    // Application rule lives here, not in any adapter: bound the result limit so it
    // holds for *any* backend and is testable with no HTTP and no vdbs.
    suspend operator fun invoke(query: LocationQuery): List<Location> {
        val limit = query.limit.coerceIn(1, LocationQuery.MAX_LIMIT)
        return locations.search(query.copy(limit = limit)).take(limit)
    }
}

class GetLocation(private val locations: LocationProvider) {
    suspend operator fun invoke(id: String): Location? = locations.get(id)
}
```

### adapter.http — inbound, translation only

Depends on the use cases, never on the port or vdbs. Parses the HTTP edge into a
`LocationQuery` / id, invokes a use case, renders domain → JSON. Bad input → `400`.

```kotlin
fun Route.locationRoutes(searchLocations: SearchLocations, getLocation: GetLocation) {
    route("/api/locations") {
        get {
            val query = call.request.queryParameters.toLocationQuery()   // 400 on bad input
            call.respond(searchLocations(query).map { it.toJson() })
        }
        get("/{id}") {
            when (val location = getLocation(call.parameters["id"]!!)) {
                null -> call.respond(HttpStatusCode.NotFound, "No location with id …")
                else -> call.respond(location.toJson())
            }
        }
    }
}
// JsonLocation/JsonLatLng are @Serializable view models local to this adapter, so the
// wire contract can evolve independently of the domain model.
```

### adapter.vdbs — outbound, vdbs-specific (all `internal`)

```kotlin
// wire model — internal; nothing outside the module knows vdbs' field names
@Serializable internal data class VdbsSearchResponse(val results: List<VdbsPlace> = emptyList())
@Serializable internal data class VdbsPlace(
    @SerialName("place_id") val placeId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("formatted_address") val formattedAddress: String = "",
    val lat: Double, val lon: Double,
    val categories: List<String> = emptyList(),
)

internal fun VdbsPlace.toLocation() = Location(           // the single mapping point
    id = placeId, name = displayName, address = formattedAddress,
    position = LatLng(lat, lon), categories = categories,
)

class VdbsLocationProvider internal constructor(private val client: VdbsClient) : LocationProvider {
    constructor(http: HttpClient, baseUrl: String, apiKey: String) : this(VdbsClient(http, baseUrl, apiKey))

    override suspend fun search(q: LocationQuery): List<Location> =
        client.search(q.text, q.near?.lat, q.near?.lng, q.radiusMeters, q.limit)
            .results.map { it.toLocation() }            // pure translation, no rules

    override suspend fun get(id: String): Location? = client.lookup(id)?.toLocation()
}
// VdbsClient holds the Ktor HttpClient, vdbs URLs and auth, and 404→null. Also internal.
```

### wiring — the single swap point

```kotlin
val http = vdbsHttpClient()                                  // shared Ktor client
val provider: LocationProvider =
    VdbsLocationProvider(http, baseUrl = "https://vdbs.cashtie.com", apiKey = env("VDBS_API_KEY"))

val search = SearchLocations(provider)
val get = GetLocation(provider)

routing { locationRoutes(search, get) }                      // + ContentNegotiation(json)
```

---

## Translation at each boundary

| Boundary | From | To |
|---|---|---|
| HTTP → domain (`adapter.http`) | `query`,`lat`/`lng`,`radius`,`limit` | `LocationQuery` (bad numbers/`lat` w/o `lng` → 400) |
| domain → HTTP JSON (`adapter.http`) | `Location` | `JsonLocation { …, position{lat,lng} }` |
| domain → vdbs (`adapter.vdbs`) | `LocationQuery` | `?q=&lat=&lon=&radius=&limit=&api_key=` |
| vdbs → domain (`adapter.vdbs`) | `VdbsPlace { place_id, display_name, lon … }` | `Location` |

---

## Swapping vdbs out later

`LocationProvider` is the seam. To serve our own bulk-loaded data:

1. Add `class BulkLocationProvider(...) : LocationProvider` in its own adapter package.
2. Build it in wiring instead of `VdbsLocationProvider`.

No change to the use cases, `locationRoutes`, the domain model, or the OpenAPI spec.

---

## Reconcile with the existing spec

This design has to be aligned to `api/openapi.yaml` before it's final. Concretely,
the spec drives these and nothing else moves:

- **Domain `Location` / `LatLng`** mirror the spec's `Location` schema (field names,
  which are required vs optional, how position is represented — nested object vs flat
  `lat`/`lng`/`latitude`/`longitude`).
- **`adapter.http`**: route paths, the search query parameter names, and the success
  status codes match the spec's operations. `JsonLocation` *is* the spec's response
  schema (ideally generated from it rather than hand-written).
- **`VdbsPlace.toLocation`**: the one place that bridges vdbs' field names to the
  spec-shaped domain model.

The layering itself (adapter → use case → port → vdbs) does not change with the spec.

> Could not read `api/openapi.yaml` from here — it's in the cashly repo, which isn't
> in this session's scope. Paste the locations operations + `Location` schema (or add
> that repo to the session) and I'll fix the domain model and the two mapping points
> to match exactly.

## Assumptions about vdbs (confirm)

- **vdbs contract.** Assumed `GET /v1/places/search?q=&lat=&lon=&radius=&limit=&api_key=`
  returning `{ "results": [ { place_id, display_name, formatted_address, lat, lon,
  categories } ] }`, and `GET /v1/places/{id}` returning a bare place (404 when
  absent). Confined to `VdbsClient` + the wire model; decoding ignores unknown fields.
- **Auth.** Assumed an `api_key` query parameter; a header/signed request would be
  confined to `VdbsClient`.

## Out of scope for this first pass

Caching / rate-limiting in front of vdbs, paging, retry/timeout/circuit-breaking on
the client, auth on the public endpoints, and richer domain fields (hours, phone, …).
Each slots into an existing layer without reshaping the design.
