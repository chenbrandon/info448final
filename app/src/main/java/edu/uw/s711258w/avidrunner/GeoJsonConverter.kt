package edu.uw.s711258w.avidrunner

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import org.json.JSONException
import org.json.JSONObject


/**
 * Code for converting Polylines into GeoJSON formatted Strings (and back)
 * @author Joel Ross
 * @author Kyungmin Lee
 * @author Zico Deng
 * @version Nov 2018
 */


/**
 * Returns a GeoJSON String representing the given list of Polylines. Style information
 * (width and color) is included as properties.
 * @param lines A list of Polylines
 * @return A GeoJSON FeatureCollection of LineStrings
 */
fun convertToGeoJson(lines: List<Polyline>):String {
    var builder = StringBuilder().apply {
        append("{\"type\": \"FeatureCollection\", " + "\"features\": [")
        for (line in lines) {
            append(
                "{ \"type\": \"Feature\", " + //new feature for each line

                        "\"geometry\": { \"type\": \"LineString\", " + "\"coordinates\": [ "
            )
            for (point in line.points) { //add points
                append("[" + point.longitude + "," + point.latitude + "],") //invert lat/lng for GeoJSON

            }
            deleteCharAt(this.length - 1) //remove trailing comma
            append("]},") //end geometry
            append("\"properties\": { ")
            append("\"color\": " + line.color + ",") //color property
            append("\"width\": " + line.width) //width property
            append("} },") //end properties/feature
        }
        deleteCharAt(this.length - 1) //remove trailing comma
        append("]}") //end json
    }
    return builder.toString()
}

/**
 * Returns a list of PolylineOptions (for creating Polylines) from a GeoJSON string.
 * Color of the lines should be stores in "properties.color" for each geometry.
 * @param geojson A String of a GeoJSON FeatureCollection of LineStrings
 * @return A list of PolylineOptions representing those LineStrings
 * @throws JSONException if error parsing the GeoJSON String
 */
@Throws(JSONException::class)
fun convertFromGeoJson(geojson: String): List<PolylineOptions> {

    val polyLineList = mutableListOf<PolylineOptions>()

    val featuresArray = JSONObject(geojson).getJSONArray("features")

    //loop through features, creating an options for each line.
    for (i in 0 until featuresArray.length()) {
        val featureObj = featuresArray.getJSONObject(i)

        //get LatLng coordinates
        val coordinates = featureObj.getJSONObject("geometry").getJSONArray("coordinates")
        val startCoord = coordinates.getJSONArray(0)
        val endCoord = coordinates.getJSONArray(1)
        val start = LatLng(startCoord.getDouble(1), startCoord.getDouble(0))
        val end = LatLng(endCoord.getDouble(1), endCoord.getDouble(0))

        //get polyline properties
        val properties = featureObj.getJSONObject("properties")
        val color = properties.getInt("color")
        val width = properties.getDouble("width").toFloat()

        //define the polyline
        val line = PolylineOptions()
            .add(start)
            .add(end)
            .color(color)
            .width(width)
        polyLineList.add(line)
    }
    return polyLineList
}