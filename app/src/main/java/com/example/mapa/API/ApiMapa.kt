package com.example.mapa.API

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val BASE_URL = "https://api.openrouteservice.org"

private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

private val retrofit =
    Retrofit.Builder().addConverterFactory(MoshiConverterFactory.create(moshi)).baseUrl(BASE_URL)
        .build()


interface DireccionesApi {
    @GET("v2/directionsfoot-walking")
    suspend fun getDirections(
        @Query("api_key") apiKey: String,
        @Query("start") inicio: String,
        @Query("end") final: String
    ): Coordenadas
}


object Direcciones {
    val retrofitService: DireccionesApi by lazy {
        retrofit.create(DireccionesApi::class.java)
    }
}