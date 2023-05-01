package com.example.mapa.API

data class Coordenadas(
    val type: String, val features: List<Feature>
)

data class Feature(
    val bbox: List<Double>, val type: String, val properties: Properties, val geometry: Geometry
)

data class Properties(
    val segments: List<Segment>, val summary: Summary, val way_points: List<Int>
)

data class Segment(
    val distance: Double, val duration: Double, val steps: List<Step>
)

data class Step(
    val distance: Double,
    val duration: Double,
    val type: Int,
    val instruction: String,
    val name: String,
    val way_points: List<Int>
)

data class Summary(
    val distance: Double, val duration: Double
)

data class Geometry(
    val coordinates: List<List<Double>>, val type: String
)
