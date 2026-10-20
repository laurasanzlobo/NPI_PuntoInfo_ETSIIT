package com.example.npi_puntoinfo_etsiit.ui.components

import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.npi_puntoinfo_etsiit.outdoor.GeoPoint
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorDestination
import com.example.npi_puntoinfo_etsiit.outdoor.WalkingRoute
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

enum class OutdoorMapStatus { LOADING, READY, ERROR }

@Composable
fun OutdoorMap(
    destinations: List<OutdoorDestination>,
    selected: OutdoorDestination?,
    position: GeoPoint?,
    route: WalkingRoute?,
    recenter: Int,
    onSelect: (String) -> Unit,
    onStatus: (OutdoorMapStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val select by rememberUpdatedState(onSelect)
    val status by rememberUpdatedState(onStatus)
    val controller = remember(context) {
        OutdoorMapController(context, { select(it) }, { status(it) })
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_RESUME -> controller.resume()
                Lifecycle.Event.ON_PAUSE -> controller.pause()
                Lifecycle.Event.ON_STOP -> controller.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.resume()
        onDispose {
            lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }
    AndroidView(
        factory = { controller.view },
        modifier = modifier,
        update = { controller.render(destinations, selected, position, route, recenter) }
    )
}

private data class MapContent(
    val destinations: List<OutdoorDestination>, val selected: OutdoorDestination?,
    val position: GeoPoint?, val route: WalkingRoute?, val recenter: Int
)

/** Capas GeoJSON modernas: no usa las antiguas anotaciones Marker/Polyline. */
private class OutdoorMapController(
    context: Context,
    private val onSelect: (String) -> Unit,
    private val onStatus: (OutdoorMapStatus) -> Unit
) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var content: MapContent? = null
    private var displayed: MapContent? = null
    private var started = false
    private var resumed = false
    private var destroyed = false
    private val density = context.resources.displayMetrics.density

    init {
        MapLibre.getInstance(context.applicationContext)
        view = MapView(context)
        view.onCreate(null)
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateMap() }
        view.addOnDidFailLoadingMapListener {
            if (!destroyed) onStatus(OutdoorMapStatus.ERROR)
        }
        view.getMapAsync { loadedMap ->
            if (!destroyed) {
                map = loadedMap
                loadedMap.uiSettings.apply {
                    isAttributionEnabled = true
                    isCompassEnabled = true
                    isRotateGesturesEnabled = false
                }
                loadedMap.addOnMapClickListener { location ->
                    val pixel = loadedMap.projection.toScreenLocation(location)
                    val radius = 22 * density
                    val feature = loadedMap.queryRenderedFeatures(
                        RectF(pixel.x - radius, pixel.y - radius, pixel.x + radius, pixel.y + radius),
                        "t4-destinations"
                    ).firstOrNull()
                    feature?.getStringProperty("id")?.let(onSelect)
                    feature != null
                }
                loadedMap.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                    if (!destroyed) {
                        style.addSource(GeoJsonSource("t4-route", FeatureCollection.fromFeatures(emptyArray<Feature>())))
                        style.addLayer(LineLayer("t4-route-line", "t4-route").withProperties(
                            lineColor("#0066CC"), lineWidth(5f), lineOpacity(0.9f)))
                        style.addSource(GeoJsonSource("t4-destinations-source", FeatureCollection.fromFeatures(emptyArray<Feature>())))
                        style.addLayer(CircleLayer("t4-destinations", "t4-destinations-source").withProperties(
                            circleColor(get("color")), circleRadius(get("radius")),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                        style.addSource(GeoJsonSource("t4-position", FeatureCollection.fromFeatures(emptyArray<Feature>())))
                        style.addLayer(CircleLayer("t4-position-dot", "t4-position").withProperties(
                            circleColor("#0066CC"), circleRadius(7f),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                        displayed = null
                        updateMap()
                        onStatus(OutdoorMapStatus.READY)
                    }
                }
            }
        }
    }

    fun render(destinations: List<OutdoorDestination>, selected: OutdoorDestination?, position: GeoPoint?, route: WalkingRoute?, recenter: Int) {
        content = MapContent(destinations, selected, position, route, recenter)
        updateMap()
    }

    private fun updateMap() {
        if (destroyed) return
        if (view.width == 0 || view.height == 0) return
        val current = content ?: return
        if (current == displayed) return
        val map = map ?: return
        val style = map.style?.takeIf { it.isFullyLoaded } ?: return
        val features = current.destinations.map { destination ->
            Feature.fromGeometry(destination.point.toPoint()).apply {
                addStringProperty("id", destination.id)
                addStringProperty("color", if (destination == current.selected) "#C41230" else "#687787")
                addNumberProperty("radius", if (destination == current.selected) 11 else 8)
            }
        }
        style.getSourceAs<GeoJsonSource>("t4-destinations-source")?.setGeoJson(FeatureCollection.fromFeatures(features))
        style.getSourceAs<GeoJsonSource>("t4-position")?.setGeoJson(FeatureCollection.fromFeatures(
            current.position?.let { listOf(Feature.fromGeometry(it.toPoint())) }.orEmpty()))
        style.getSourceAs<GeoJsonSource>("t4-route")?.setGeoJson(FeatureCollection.fromFeatures(
            current.route?.let { listOf(Feature.fromGeometry(LineString.fromLngLats(it.points.map { point -> point.toPoint() }))) }.orEmpty()))
        val fitPoints = current.route?.points.orEmpty() + listOfNotNull(current.position, current.selected?.point)
        val points = (fitPoints.ifEmpty { current.destinations.map { it.point } }).distinct()
        if (points.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first().toLatLng(), 16.0))
        } else if (points.size > 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.Builder().includes(points.map { it.toLatLng() }).build(), (36 * density).toInt()))
            // Evita un zoom extremo si GPS y destino están prácticamente en el mismo punto.
            if (map.cameraPosition.zoom > 17) map.moveCamera(CameraUpdateFactory.zoomTo(17.0))
        }
        displayed = current
    }

    fun start() { if (!destroyed && !started) { view.onStart(); started = true } }
    fun resume() { if (!destroyed && !resumed) { view.onResume(); resumed = true } }
    fun pause() { if (resumed) { view.onPause(); resumed = false } }
    fun stop() { pause(); if (started) { view.onStop(); started = false } }
    fun destroy() { if (!destroyed) { stop(); destroyed = true; view.onDestroy() } }
}

private fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)
