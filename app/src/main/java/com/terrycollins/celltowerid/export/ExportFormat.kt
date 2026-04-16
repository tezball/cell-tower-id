package com.terrycollins.celltowerid.export

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    GEOJSON("geojson", "application/geo+json"),
    KML("kml", "application/vnd.google-earth.kml+xml");
}
