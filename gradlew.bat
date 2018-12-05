package edu.uw.s711258w.sunspotter

import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.util.LruCache
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.*
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.*
import org.json.JSONException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mAdapter: ForecastAdapter
    private lateinit var mLayoutManager: RecyclerView.LayoutManager



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val searchField = findViewById<EditText>(R.id.plain_text_input)
        val searchButton = findViewById<Button>(R.id.findSun)
        searchButton.setOnClickListener {
            val url: String = "api.openweathermap.org/data/2.5/forecast?format=json&units=imperial"  + "&q=" + searchField + getString(R.string.OPEN_WEATHER_MAP_API_KEY)

            val request = JsonObjectRequest(Request.Method.GET, url, null,
                    Response.Listener {response ->
                        val data = ArrayList<ForecastData>()
                        try {
                            val results = response.getJSONArray("list")
                            for (i in 0 until results.length()) {
                                val row = ForecastData()
                                val instance = results.getJSONObject(i)

                                val icon = instance.getString("weather.icon") + "@"
                                val drawableId: Int = getResources().getIdentifie