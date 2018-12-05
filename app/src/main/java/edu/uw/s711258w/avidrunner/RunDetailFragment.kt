package edu.uw.s711258w.avidrunner

import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.android.synthetic.main.fragment_run_detail.*
import kotlinx.android.synthetic.main.fragment_run_detail.view.*

class RunDetailFragment: Fragment(), OnMapReadyCallback {
    private val TAG = "RunDetailFragment"
    private lateinit var mMap: GoogleMap
    private lateinit var runRoute: String
    private var lines: List<PolylineOptions>? = null

    companion object {
        val RUN_PARCEL_KEY = "run_parcel"

        fun newInstance(run: RunHistoryData): RunDetailFragment {
            val args = Bundle().apply {
                putParcelable(RUN_PARCEL_KEY, run)
            }
            val fragment = RunDetailFragment().apply {
                arguments = args
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.v(TAG, "try to inflate")
        val rootView = inflater.inflate(R.layout.fragment_run_detail, container, false)

        Log.v(TAG, "inside oncreate")
        arguments?.let {
            val runData = it.getParcelable<RunHistoryData>(RUN_PARCEL_KEY)
            runData?.let {
                Log.v(TAG, "rundata date was ${runData.runDate}")
                rootView.text_detail_date.text = "Date: ${runData.runDate}"
                rootView.text_detail_distance.text = "Distance: ${runData.miles}"
                rootView.text_detail_time.text = "Time: ${runData.runTime}"
                rootView.text_detail_pace.text = "Pace: ${runData.milePace}"
            }
            Log.v(TAG, "Route data is ${runData.geoJSON}")
            runRoute = runData.geoJSON
            if (runRoute != null && !runRoute.equals("")) {
                try {
                    lines = convertFromGeoJson(runRoute)
                } catch (exception: Exception) {
                    Log.v(TAG, "The exception was: ${exception.localizedMessage}")
                }
            }
        }
        val mf = childFragmentManager.findFragmentById(R.id.detail_map) as SupportMapFragment
        if (mf != null) {
            mf.retainInstance = true
            mf.getMapAsync(this)
        }
        return rootView
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.v(TAG, "map is ready")

        val mgh = LatLng(47.655, -122.3078)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(mgh))
        mMap.setMaxZoomPreference(40.0f)
        mMap.setMinZoomPreference(15.0f)
        mMap.uiSettings.isZoomControlsEnabled = true
        if (lines == null) {
            return
        }
        for(currLine in lines!!) {
            Log.v(TAG, "${currLine.width}")
            Log.v(TAG, currLine.points.toString())
            mMap.addPolyline(currLine)

        }
        showPolyLines()

    }

    fun showPolyLines() {

        for (currLine in lines!!) {
            val first: LatLng = currLine.points.get(0)
            for (point in currLine.points) {
                Log.v(TAG, "the point is: ${point.latitude}, ${point.longitude}")
            }
            mMap.addPolyline(currLine)
        }
    }
}