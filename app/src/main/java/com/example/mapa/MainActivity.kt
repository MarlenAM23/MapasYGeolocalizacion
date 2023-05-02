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

    //Declaración de las variables necesarias.
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

    //Inicializa el objeto con la propiedad de retrofit
    private val direccionesApi: DireccionesApi by lazy {
        Direcciones.retrofitService
    }

    //Items
    var items = ArrayList<OverlayItem>()

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Mandamos llamar nuestra funcion para revisar permisos
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
                segundoMarcador!!.position = GeoPoint(latitude, longitude)
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

        // Crear un objeto de tipo Overlay para manejar los eventos táctiles en el mapa
        val touchOverlay = object : Overlay() {
            // Crear un objeto GestureDetector para detectar el evento de toque largo en el mapa
            private val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                // Acción a realizar cuando se detecte un toque largo
                override fun onLongPress(e: MotionEvent) {
                    if (colocar) {
                        // Obtener las coordenadas del punto donde se realizó la pulsación
                        puntoDeLlegada = map?.projection!!.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint?
                        // Eliminar el segundo marcador existente (si lo hay)
                        map?.overlays?.remove(segundoMarcador)
                        // Crear un nuevo marcador en las coordenadas del destino seleccionado
                        segundoMarcador = Marker(map)
                        segundoMarcador?.position = puntoDeLlegada
                        segundoMarcador?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
                        segundoMarcador?.title = "Destino"
                        // Agregar el nuevo marcador al mapa
                        map?.overlays?.add(segundoMarcador)

                        // Limpiar la ruta existente y obtener nuevas coordenadas para la nueva ruta
                        ruta.setPoints(emptyList())
                        map?.overlays?.remove(ruta)
                        coords()
                    }
                    // Redibujar el mapa para mostrar el nuevo marcador (y ruta)
                    map?.invalidate()
                }
            })

            // Este evento se llama cada que hay un toque en el mapa
            override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
                // Pasar el evento al objeto GestureDetector para detectar si se trata de un toque largo
                gestureDetector.onTouchEvent(event)
                return super.onTouchEvent(event, mapView)
            }
        }

        // Se agregan los eventos de toques en el mapa.
        map?.overlays?.add(touchOverlay)
        map?.overlays!!.add(mOverlay)

        //Se intercalan las funcionalidades del boton de trazar ruta.
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

        // Se crea una superposición de la brújula en el mapa, que se habilita para que la dirección del norte esté en la parte superior
        val compassOverlay = CompassOverlay(
            applicationContext, InternalCompassOrientationProvider(applicationContext), map
        )
        compassOverlay.enableCompass()
        map!!.overlays.add(compassOverlay)

        // Se crea una superposición de gestos de rotación, que permite al usuario girar el mapa utilizando gestos de rotación de dos dedos
        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled
        map!!.setMultiTouchControls(true)
        map!!.overlays.add(rotationGestureOverlay)

        // Se establece un nivel de zoom para el mapa
        mapController.setZoom(18)

        // Se crea una superposición de barra de escala, que muestra la escala del mapa en términos de distancia en metros y millas
        val dm: DisplayMetrics = this.resources.displayMetrics
        val scaleBarOverlay = ScaleBarOverlay(map)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10)
        map!!.overlays.add(scaleBarOverlay)

        // Se crea una superposición de minimapa, que muestra una vista general del mapa completo y permite al usuario navegar a diferentes áreas del mapa
        val minimapOverlay = MinimapOverlay(this, map!!.tileRequestCompleteHandler)
        minimapOverlay.width = dm.widthPixels / 5
        minimapOverlay.height = dm.heightPixels / 5
        map!!.overlays.add(minimapOverlay)

        // Se invalida el mapa para que los cambios realizados en las superposiciones se reflejen en el mapa
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

    private fun checkPermissions() {
        // Verifica si se tiene el permiso de ubicación fina o GPS.
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            // Verifica si se tiene el permiso de ubicación gruesa (datos y wifi para GPS).
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||

            // Verifica si se tiene el permiso de escritura en el almacenamiento externo.
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Si uno o más de los permisos necesarios no se tienen, se pide al usuario que los conceda
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 0
            )
        }
    }

    // Esta es una función que se llama automáticamente cuando el usuario responde a la solicitud de permiso
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Si el código de solicitud es 0, significa que se está evaluando la respuesta de la solicitud de permiso.
        when (requestCode) {
            0 -> {
                // Si grantResults[0] es igual a PackageManager.PERMISSION_GRANTED, significa que el permiso fue concedido
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
        // Se solicita la actualización de la ubicación con el cliente de ubicación fusionado
        fusedLocationClient.requestLocationUpdates(
            locationRequest, // Se pasa la solicitud de ubicación definida anteriormente
            locationCallback, // Se define el callback que manejará las actualizaciones de ubicación
            Looper.getMainLooper() // Se especifica el looper principal para recibir actualizaciones en el hilo principal
        )
    }

    private fun stopLocationUpdates() {
        // Se remueven las actualizaciones de ubicación con el cliente de ubicación fusionado
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    fun coords() {
        // Creamos un nuevo scope para trabajar con corrutinas y enviamos el trabajo a un hilo en segundo plano
        CoroutineScope(Dispatchers.IO).launch {
            // Obtenemos las coordenadas de inicio y final
            val inicio = "${puntoDeSalida!!.longitude},${puntoDeSalida!!.latitude}"
            val final = "${puntoDeLlegada!!.longitude},${puntoDeLlegada!!.latitude}"
            val api = "5b3ce3597851110001cf624835945259895e4e9d885c351e926dda3a"
            // Realizamos la solicitud a la API con la función getDirections, definida en la interfaz DireccionesApi
            val coordenadas = direccionesApi.getDirections(api, inicio, final)
            // Obtenemos la lista de features de las coordenadas devueltas por la API
            val features = coordenadas.features
            for (feature in features) {
                val geometry = feature.geometry
                val coordinates = geometry.coordinates
                for (coordenada in coordinates) {
                    // Creamos un nuevo punto GeoPoint con las coordenadas obtenidas
                    val punto = GeoPoint(coordenada[1], coordenada[0])
                    // Añadimos el punto a la ruta
                    ruta.addPoint(punto)
                }
                // Añadimos la ruta al mapa
                map?.overlays?.add(ruta)
            }
        }
    }

}