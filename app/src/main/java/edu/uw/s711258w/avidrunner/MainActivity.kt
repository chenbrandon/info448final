package edu.uw.s711258w.avidrunner

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.provider.CalendarContract
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONException
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private val CALENDAR_REQUEST_CODE = 1
    private val timeList = ArrayList<String>()
    private val openTimes = ArrayList<String>()
    private var date = Calendar.getInstance().getTime()
    private val TAG = "MainActivity"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        findViewById<TextView>(R.id.location).setText("Location: " + prefs.getString("cityname", "No Location Specified"))


        //Dropdown selection for today or tomorrow
        val spinner: Spinner = findViewById(R.id.date_spinner)
        ArrayAdapter.createFromResource(
                this,
                R.array.days,
                android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner.adapter = adapter
        }
        spinner.onItemSelectedListener = this

    }


    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val calendar = Calendar.getInstance()
        val today = calendar.getTime()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = calendar.getTime()
        val selection = parent.getItemAtPosition(pos)
        if(selection == "Today") {
            date = today
            getDataFromCalendar(date)
            getWeatherData(date, prefs.getString("cityname", "No Location Specified")!!)
        } else {
            date = tomorrow
            getDataFromCalendar(date)
            getWeatherData(date, prefs.getString("cityname", "No Location Specified")!!)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

    data class ForecastData(val time: String, val tempf: String, val weatherdesc: String)

    fun getWeatherData(day: Date, city: String) {
        val dateformater = SimpleDateFormat("yyyy-MM-dd")
        val date = dateformater.format(day)
        val url: String = "https://api.worldweatheronline.com/premium/v1/weather.ashx?format=json" + "&q=" + city + "&date=" + date + "&tp=" + 1 + "&key=" + getString(R.string.API_KEY)

        val request = JsonObjectRequest(Request.Method.GET, url, null,
                Response.Listener { response ->
                    val forecastdata = ArrayList<ForecastData>()
                    try {
                        val data = response.getJSONObject("data")
                        val request = data.getJSONArray("weather")
                        val weather = request.getJSONObject(0)
                        val hourly = weather.getJSONArray("hourly")
                        for (i in 0..hourly.length()-1) {
                            val instance = hourly.getJSONObject(i)
                            val time = instance.getString("time")
                            val tempF = instance.getString("tempF")
                            val weather = instance.getJSONArray("weatherDesc")
                            val value = weather.getJSONObject(0)
                            val weatherDesc = value.getString("value")
                            val singleForecastData = ForecastData(time, tempF + " \u2109", weatherDesc)
                            forecastdata.add(singleForecastData)
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }


                    val recyclerView = findViewById<RecyclerView>(R.id.weather)
                    recyclerView.adapter = MyAdapter(forecastdata)
                    recyclerView.layoutManager = LinearLayoutManager(this, LinearLayout.VERTICAL,false)
                }, Response.ErrorListener {
            error -> Log.e(TAG, error.toString())})


        VolleyService.getInstance(this).add(request)

    }

    class MyAdapter(private val myDataset: ArrayList<ForecastData>) :
            RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        // Create new views (invoked by the layout manager)
        override fun onCreateViewHolder(parent: ViewGroup,
                                        viewType: Int): MyAdapter.MyViewHolder {
            // create a new view
            val textView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.weather_item, parent, false)
            return MyViewHolder(textView)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            // - get element from your dataset at this position
            // - replace the contents of the view with that element
            val item = myDataset.get(position)
            holder.time.text = item.time
            holder.temp.text = item.tempf
            holder.desc.text = item.weatherdesc
        }

        // Return the size of your dataset (invoked by the layout manager)
        override fun getItemCount() = myDataset.size

        class MyViewHolder(textView: View) : RecyclerView.ViewHolder(textView) {
            val time = textView.findViewById<TextView>(R.id.weatherTime)!!
            val temp = textView.findViewById<TextView>(R.id.weatherTemp)!!
            val desc = textView.findViewById<TextView>(R.id.weatherDesc)!!
        }
    }

    private class VolleyService
    private constructor(ctx: Context) { //private constructor; cannot instantiate directly

        companion object { //to hold the shared instances
            private var instance: VolleyService? = null //the single instance of this singleton

            //call this "factory" method to access the Singleton
            fun getInstance(ctx: Context): VolleyService {
                //only create the singleton if it doesn't exist yet
                if (instance == null) {
                    instance = VolleyService(ctx)
                }

                return instance as VolleyService //force casting
            }
        }

        //from Kotlin docs
        val requestQueue: RequestQueue by lazy { //instantiate once needed
            Volley.newRequestQueue(ctx.applicationContext) //return the context-based requestQueue
        }

        //convenience wrapper method
        fun <T> add(req: Request<T>) {
            requestQueue.add(req)
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.preferences -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun getDataFromCalendar(day: Date) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), CALENDAR_REQUEST_CODE)
        }
        timeList.clear()
        openTimes.clear()
        val resolver: ContentResolver  = getContentResolver()
        val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
        )
        val cursor = resolver.query(CalendarContract.Events.CONTENT_URI, projection, null, null, CalendarContract.Events.DTSTART)

        //Put in start time
        timeList.add("05:00 AM")
        while (cursor!!.moveToNext()) {
            val dtstart = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events.DTSTART))
            val dtend = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events.DTEND))
            val start = Date(dtstart)
            val end = Date(dtend)
            val dateformater = SimpleDateFormat("MM-dd-yyyy")
            val timeformater = SimpleDateFormat("hh:mm a")
            if (dateformater.format(day).equals(dateformater.format(start))) {
                timeList.add(timeformater.format(start))
                timeList.add(timeformater.format(end))
            }
        }
        //Put in end time
        timeList.add("10:00 PM")


        for(time in timeList.indices step(2)) {
            if(timeList[time] != timeList[time+1]) {
                openTimes.add(timeList[time] + " to " + timeList[time+1])
            }
        }
        val adapter = ArrayAdapter<String>(this, R.layout.list_item,
                R.id.txtItem, openTimes)
        val listView = findViewById<ListView>(R.id.times)
        listView.setAdapter(adapter)
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            CALENDAR_REQUEST_CODE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getDataFromCalendar(date)
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onSharedPreferenceChanged (sharedPreferences: SharedPreferences , key: String) {
        val city = sharedPreferences.getString(key, "No location specified")!!
        findViewById<TextView>(R.id.location).setText("Location: " + city)
        getWeatherData(date, city)
    }
}
