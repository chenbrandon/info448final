package edu.uw.s711258w.avidrunner

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.provider.CalendarContract
import android.provider.UserDictionary
import android.support.v4.content.FileProvider
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.ShareActionProvider
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private val CALENDAR_REQUEST_CODE = 1
    private val timeList = ArrayList<String>()
    private val openTimes = ArrayList<String>()
    private var cityName = ""
    private lateinit var date: Date

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



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

    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        val calendar = Calendar.getInstance()
        val today = calendar.getTime()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = calendar.getTime()
        val selection = parent.getItemAtPosition(pos)
        if(selection == "Today") {
            date = today
            getDataFromCalendar(date)
            getWeatherData(date)
        } else {
            date = tomorrow
            getDataFromCalendar(date)
            getWeatherData(date)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

    fun getWeatherData(day: Date) {

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
            R.id.playlist -> {
                startActivity(Intent(this, PlaylistActivity::class.java))
                true
            }
            R.id.start_run -> {
                startActivity(Intent(this, MapsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun getDataFromCalendar(day: Date) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), CALENDAR_REQUEST_CODE);
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
        while (cursor.moveToNext()) {
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
}
