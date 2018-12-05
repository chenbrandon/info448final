package edu.uw.s711258w.avidrunner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.*
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_home.*


class HomeActivity: AppCompatActivity() {
    private val TAG = "HomeActivity"
    private val RUN_DETAIL_FRAGMENT_TAG = "RunDetailFragment"

    private lateinit var adapter: RunDataAdapter

    private val mContext: Context = this@HomeActivity

    private val runDataList: MutableList<RunHistoryData>  = mutableListOf()

    private lateinit var tinydb: TinyDB




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        button_calendar.setOnClickListener {
            Log.v(TAG, "calendar clicked")
            startActivity(Intent(this, MainActivity::class.java))
        }
        button_music.setOnClickListener {
            Log.v(TAG, "music clicked")
            startActivity(Intent(this, PlaylistActivity::class.java))
        }
        button_run.setOnClickListener {
            Log.v(TAG, "run clicked")
            startActivity(Intent(this, MapsActivity::class.java))
        }

        tinydb = TinyDB(this)

        var listView = list_run_history
        listView.setOnItemClickListener { parent, view, position, id ->
            val data = adapter.getItem(position)

            val detailFragment: RunDetailFragment = RunDetailFragment.newInstance(data)

            supportFragmentManager.beginTransaction().
                replace(R.id.home_content_container, detailFragment)
                    .addToBackStack(null).
                commit()


//            val intent = Intent(this, MapsActivity::class.java)
//            startActivity(intent)
        }

        // Initialize adapter
        adapter = RunDataAdapter(mContext, R.layout.item_history, runDataList)

        // Set Adapter for listview
        (listView as AdapterView<Adapter>).adapter = adapter

        // Load run data
        getRunData()
    }

    // Gets the run data, render in list
    fun getRunData() {
        runDataList.clear()
        adapter.notifyDataSetChanged()


        var allRuns: ArrayList<Any> = tinydb.getListObject("allRuns", RunHistoryData::class.java)

        for (run in allRuns) {
            try {
                runDataList.add(run as RunHistoryData)
                adapter.notifyDataSetChanged()
            } catch (exception: Exception) {
                Log.v(TAG, "There was an exception converting the allRuns to RunHistory object")
            }
        }

        // using dummy data for now
//        val list = getDummyData()
//        for (item in list) {
//            runDataList.add(item)
//            adapter.notifyDataSetChanged()
//        }
    }

    // generates a list of dummy run data
    fun getDummyData(): List<RunHistoryData> {
        val result = mutableListOf<RunHistoryData>()
        for(i in 0..30) {
            val dummyGeoJson = "{\"type\": \"FeatureCollection\", \"features\": [{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.3078,47.655],[-122.3078,47.655]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} },{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.3078,47.655],[-122.3078,47.655],[-122.3078,47.67]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} },{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.3078,47.655],[-122.3078,47.655],[-122.3078,47.67],[-122.37,47.67]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} },{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.3078,47.655],[-122.3078,47.655],[-122.3078,47.67],[-122.37,47.67],[-122.3078,47.655]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} }]}\n"
//            val dummyGeoJson = "{\"type\": \"FeatureCollection\", \"features\": [{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.0,47.0],[-122.0,47.0]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} },{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.0,47.0],[-122.0,47.0],[-122.0,47.0]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} },{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.0,47.0],[-122.0,47.0],[-122.0,47.0],[-122.3078,46.655]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} },{ \"type\": \"Feature\", \"geometry\": { \"type\": \"LineString\", \"coordinates\": [ [-122.0,47.0],[-122.0,47.0],[-122.0,47.0],[-122.3078,46.655],[-122.3078,30.0]]},\"properties\": { \"color\": -10264223,\"width\": 25.0} }]}\n"
            result.add(RunHistoryData("runTime: $i", "rundate $i", "runpace $i", "miles $i", dummyGeoJson))
        }
        return result.toList()
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
}

open class RunDataAdapter(context: Context,
                          resource: Int,
                          runDataList: MutableList<RunHistoryData>): ArrayAdapter<RunHistoryData>(context, resource, runDataList){
    private val TAG: String = "RunDataAdapter"

    private lateinit var viewHolder: ViewHolder

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var inflatedView: View? = convertView
        if (inflatedView == null) {
            inflatedView = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        }

        viewHolder = ViewHolder(
            inflatedView!!.findViewById(R.id.text_history_date),
            inflatedView.findViewById(R.id.text_history_distance),
            inflatedView.findViewById(R.id.text_history_time)
        )

        inflatedView.tag = viewHolder


        // Get run data
        val runDataAtIndex: RunHistoryData = getItem(position)
        val runDate = runDataAtIndex.runDate
        val runDistance = runDataAtIndex.miles
        val runTime = runDataAtIndex.runTime
        val runPace = runDataAtIndex.milePace

        // Set view holder fields to data
        viewHolder.textViewRunDate.text = runDate
        viewHolder.textViewRunDistance.text = runDistance
        viewHolder.textViewRunTime.text = runTime


        return inflatedView
    }

    private data class ViewHolder @JvmOverloads constructor(
        var textViewRunDate: TextView,
        var textViewRunDistance: TextView,
        var textViewRunTime: TextView
    )
}

data class RunHistoryData(
        val runTime: String,
        val runDate: String,
        val milePace: String,
        val miles: String,
        val geoJSON: String
): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString()

    ) {
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RunHistoryData> {
        override fun createFromParcel(parcel: Parcel): RunHistoryData {
            return RunHistoryData(parcel)
        }

        override fun newArray(size: Int): Array<RunHistoryData?> {
            return arrayOfNulls(size)
        }
    }
}