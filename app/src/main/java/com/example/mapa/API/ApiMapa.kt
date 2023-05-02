package com.example.mapa.API

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Define la URL base de la API.
private const val BASE_URL = "https://api.openrouteservice.org"

// Crea una instancia de Moshi con un adaptador de Kotlin para procesar JSON.
private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

// Crea una instancia de Retrofit utilizando la URL base y el convertidor Moshi.
private val retrofit =
    Retrofit.Builder().addConverterFactory(MoshiConverterFactory.create(moshi)).baseUrl(BASE_URL)
        .build()

interface DireccionesApi {
    // Define la ruta de la petición HTTP
    @GET("v2/directionsfoot-walking")
    // Define el método para obtener las coordenadas de las direcciones utilizando la anotación @Query para agregar parámetros a la petición HTTP.
    suspend fun getDirections(
        @Query("api_key") apiKey: String,
        @Query("start") inicio: String,
        @Query("end") final: String
    ): Coordenadas // El método devuelve un objeto de tipo Coordenadas.
}

// Crea un objeto que provee una instancia de la interfaz DireccionesApi utilizando Retrofit.
object Direcciones {
    val retrofitService: DireccionesApi by lazy {
        // Se crea una instancia de la interfaz DireccionesApi utilizando Retrofit.
        retrofit.create(DireccionesApi::class.java)
    }
}