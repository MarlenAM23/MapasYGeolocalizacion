@file:Suppress("DEPRECATION")

package com.example.mapa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mapa.API.Direcciones
import com.example.mapa.API.DireccionesApi
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay


class MainActivity : AppCompatActivity() {
    private lateinit var latitudeEditText: EditText
    private lateinit var longitudeEditText: EditText
    private lateinit var trazarButton: Button

    private var primerMarcador: Marker? = null
    private var segundoMarcador: Marker? = null
    private var puntoDeLlegada: GeoPoint? = null
    private var puntoDeSalida: GeoPoint? = null
    private var ruta = Polyline()
    private var colocar = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    var map: MapView? = null
    private lateinit var boton: Button

    private val direccionesApi: DireccionesApi by lazy {
        Direcciones.retrofitService
    }

    //your items
    var items = ArrayList<OverlayItem>()

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        val ctx: Context = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        setContentView(R.layout.activity_main)


        map = findViewById<View>(R.id.mapDraw) as MapView
        boton = findViewById(R.id.btnTrazar)
        map!!.setTileSource(TileSourceFactory.MAPNIK)

        latitudeEditText = findViewById(R.id.latitudeEditText)
        longitudeEditText = findViewById(R.id.longitudeEditText)
        trazarButton = findViewById(R.id.button)

        //Coloca un pin en las coordenadas dadas
        trazarButton.setOnClickListener {
            val latitude = latitudeEditText.text.toString().toDoubleOrNull()
            val longitude = longitudeEditText.text.toString().toDoubleOrNull()
            if (latitude != null && longitude != null) {
                puntoDeLlegada = GeoPoint(latitude, longitude)
                map?.overlays?.remove(segundoMarcador)
                segundoMarcador = Marker(map)
                segundoMarcador!!.position = GeoPoint(latitude,longitude)
                segundoMarcador?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
                segundoMarcador?.title = "Destino"
                map?.overlays?.add(segundoMarcador)

                ruta.setPoints(emptyList())
                map?.overlays?.remove(ruta)
                coords()
            }
        }

        val mapController = map!!.controller
        mapController.setZoom(19)
        puntoDeSalida = GeoPoint(0, 0)
        mapController.setCenter(puntoDeSalida)

        // Inicializa la variable de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configura el callback de ubicación
        var hasCenteredMap = false
        locationCallback = object : LocationCallback() {
            @Suppress("NAME_SHADOWING")
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation
                puntoDeSalida = GeoPoint(lastLocation!!.latitude, lastLocation.longitude)
                primerMarcador?.position = puntoDeSalida
                if (!hasCenteredMap) {
                    mapController.setCenter(puntoDeSalida)
                    hasCenteredMap = true
                }
                if (segundoMarcador != null && ruta.distance > 0) {
                    ruta.setPoints(emptyList())
                    map?.overlays?.remove(ruta)
                    coords()
                }
                map?.invalidate()
            }
        }

        // Configura la solicitud de ubicación
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        items.add(
            OverlayItem(
                "Titulo: ", "Descripción", GeoPoint(0.0, 0.0)
            )
        )

        primerMarcador = Marker(map)
        primerMarcador?.position = puntoDeSalida
        primerMarcador?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
        primerMarcador?.title = "UBICACION ACTUAL"
        map?.overlays?.add(primerMarcador)
        map?.invalidate()

        val mOverlay: ItemizedOverlayWithFocus<OverlayItem> = ItemizedOverlayWithFocus(
            items, object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem?> {
                override fun onItemSingleTapUp(index: Int, item: OverlayItem?): Boolean {
                    return true
                }

                override fun onItemLongPress(index: Int, item: OverlayItem?): Boolean {
                    return false
                }
            }, ctx
        )
        mOverlay.setFocusItemsOnTap(true)

        // Crear un nuevo Overlay para capturar eventos de toque
        val touchOverlay = object : Overlay() {

            private val gestureDetector =
                GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onLongPress(e: MotionEvent) {
                        // Obtener las coordenadas del punto donde se realizó la pulsación
                        if (colocar) {
                            puntoDeLlegada =
                                map?.projection!!.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint?
                            map?.overlays?.remove(segundoMarcador)
                            segundoMarcador = Marker(map)
                            segundoMarcador?.position = puntoDeLlegada
                            segundoMarcador?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
                            segundoMarcador?.title = "Destino"
                            map?.overlays?.add(segundoMarcador)

                            ruta.setPoints(emptyList())
                            map?.overlays?.remove(ruta)
                            coords()
                        }
                        // Redibujar el mapa para mostrar el nuevo marcador
                        map?.invalidate()
                    }
                })

            override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
                gestureDetector.onTouchEvent(event)
                return super.onTouchEvent(event, mapView)
            }
        }

        // Agregar el Overlay de eventos de toque al mapa
        map?.overlays?.add(touchOverlay)
        map?.overlays!!.add(mOverlay)

        var mensaje = true
        boton.setOnClickListener {
            if (colocar) {
                colocar = false
                boton.text = "Agregar Ubicacion"
                map?.setBuiltInZoomControls(true)
            } else {
                colocar = true
                boton.text = "Confirmar"
                map?.setBuiltInZoomControls(false)
                if (mensaje) {
                    Toast.makeText(
                        ctx, "Manten presionado para marcar ubicación.", Toast.LENGTH_LONG
                    ).show()
                    mensaje = false
                }
            }
        }

        //se agrega la funcionalidad de zoom y minimapa
        val compassOverlay = CompassOverlay(applicationContext, InternalCompassOrientationProvider(applicationContext), map)
        compassOverlay.enableCompass()
        map!!.overlays.add(compassOverlay)

        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled
        map!!.setMultiTouchControls(true)
        map!!.overlays.add(rotationGestureOverlay)

        mapController.setZoom(18)

        val dm : DisplayMetrics = this.resources.displayMetrics
        val scaleBarOverlay = ScaleBarOverlay(map)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10)
        map!!.overlays.add(scaleBarOverlay)

        val minimapOverlay = MinimapOverlay(this, map!!.tileRequestCompleteHandler)
        minimapOverlay.width = dm.widthPixels / 5
        minimapOverlay.height = dm.heightPixels / 5
        map!!.overlays.add(minimapOverlay)

        map!!.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map!!.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        map!!.onPause()
        stopLocationUpdates()
    }

    fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 0
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            0 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return
                } else {
                    checkPermissions()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun coords() {
        CoroutineScope(Dispatchers.IO).launch {
            val inicio = "${puntoDeSalida!!.longitude},${puntoDeSalida!!.latitude}"
            val final = "${puntoDeLlegada!!.longitude},${puntoDeLlegada!!.latitude}"
            val api = "5b3ce3597851110001cf624835945259895e4e9d885c351e926dda3a"
            val coordenadas = direccionesApi.getDirections(api, inicio, final)
            val features = coordenadas.features
            for (feature in features) {
                val geometry = feature.geometry
                val coordinates = geometry.coordinates

                for (coordenada in coordinates) {
                    val punto = GeoPoint(coordenada[1], coordenada[0])
                    ruta.addPoint(punto)
                }
                map?.overlays?.add(ruta)
            }
        }
    }
}