package edu.uw.s711258w.avidrunner

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.content.pm.PackageManager
import android.graphics.Bitmap
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.SpotifyApi
import kotlinx.android.synthetic.main.activity_playlist.*
import retrofit.Callback
import retrofit.RetrofitError
import android.support.v4.util.LruCache
import android.view.*
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.playlist_item.view.*
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.*
import com.spotify.android.appremote.api.UserApi
import org.json.JSONException
import kaaes.spotify.webapi.android.models.*
import java.util.*

class PlaylistActivity : AppCompatActivity() {

    private val TAG = "VT PLAYLISTACTIVITY"

    private val CLIENT_ID = "e576aca107ee4454a716f9ecf27edf1c"
    private val REDIRECT_URI = "edu.uw.s711258w.avidrunner://AuthenticationResponse"
    private lateinit var mSpotifyAppRemote: SpotifyAppRemote
    private lateinit var connectionListener: Connector.ConnectionListener
    private lateinit var accessToken: String
    private lateinit var userApi: UserApi
    private lateinit var spotifyService: SpotifyService
    private var sucessfullyConnected: Boolean = false // to remote spotify
    private lateinit var currentUserPrivate: UserPrivate
    private lateinit var userDisplayName: String
    private val sTracks =  mutableListOf<kaaes.spotify.webapi.android.models.Track>()
    private val topArtists = mutableListOf<String>()
    private var playlistCreated = false
    private lateinit var playlistGenerated: Playlist

    // Request code will be used to verify if result comes from the login activity
    private val LOGIN_REQUEST_CODE = 1337

    /** Prompts the user to install the Spotify app if not found on their device
     *  and to authorize our app by signing into their Spotify account **/
    override fun onStart() {
        super.onStart()
        // Callback for when our app successfully connects remotely to the spotify app
        connectionListener = object : Connector.ConnectionListener {

            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                mSpotifyAppRemote = spotifyAppRemote
                Log.v(TAG, "Connected to Spotify!")
                // Now you can start interacting with App Remote
                connected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.v(TAG, "Could not connect to Spotify...")
                Log.v(TAG, throwable.message, throwable)

                // Something went wrong when attempting to connect! Handle errors here
            }
        }

        // checks if Spotify is installed on the device, if not opens google play store
        if (!checkSpotifyInstalled()) {
            installSpotifyApp()
        } else {
            // best practice for authentication
            singleSignOnAuth()
        }
    }

    /** Get user authorization with a single sign-on, best practice
     *  Requires the application's fingerprint to be registered
     *  For when the application needs a token with multiple authorization scopes **/
    fun singleSignOnAuth() {
        val scopes: Array<String> = resources.getStringArray(R.array.scopes)
        val  builder = AuthenticationRequest.Builder(CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URI)
        builder.setScopes(scopes)
        // ShowDialog will display the current Spotify user logged in on the device and allow the user to log out
        builder.setShowDialog(true)
        val request = builder.build()
        // Opens an activity for the user to log into their Spotify account
        AuthenticationClient.openLoginActivity(this, LOGIN_REQUEST_CODE, request)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        // Check if result comes from the correct activity
        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = AuthenticationClient.getResponse(resultCode, intent)

            when (response.type) {
                // Response was successful and contains auth token
                AuthenticationResponse.Type.TOKEN -> {
                    // Handle successful response
                    Log.v(TAG, "Successfully connected!")
                    // Set the access token
                    accessToken = response.accessToken
                    Log.v(TAG, "Access token: $accessToken")
                    val params: ConnectionParams = ConnectionParams.Builder(CLIENT_ID).setRedirectUri(REDIRECT_URI).build()
                    SpotifyAppRemote.connect(this, params, connectionListener)
                }

                // Auth flow returned an error
                AuthenticationResponse.Type.ERROR -> {
                    // Handle error response
                    Log.v(TAG, "Error! Did not connect!")
                    Toast.makeText(this, "Error connecting to Spotify", Toast.LENGTH_LONG).show()
                }
                // Most likely auth flow was cancelled
                // Handle other cases
                else -> super.onActivityResult(requestCode, resultCode, intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        // Set up the recyclerview to display the generated playlist
        playlist_list.layoutManager = LinearLayoutManager(this)
        playlist_list.adapter = RecyclerViewAdapter(this, sTracks)
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

    // disconnect from the remote spotify app
    override fun onStop() {
        super.onStop()
        if (sucessfullyConnected) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote)
        }
    }

    // Handles the click for Generate Playlist button
    // Generates a random playlist
    fun handleRegeneratePlaylist(v: View) {
        Log.v(TAG, "Regenerate Playlist button clicked, creating a new playlist...")
        getUserTopTracksArtists()
    }

    // Handles the click for Add Playlist button
    fun handleAddPlaylist(v: View) {
        Log.v(TAG, "Add Playlist button clicked, adding playlist to Spotify account...")

        //if (!playlistCreated) {
            //createPlaylist("Avid Runner")
            //playlistCreated = true
        //}
        //if (playlistGenerated.) {
            val parameters = mutableMapOf<String, Any>()
            // array of track uris
            val body = mutableMapOf<String, Any>()
            val uriArray = arrayListOf<String>()
            for (track in sTracks) {
                Log.v(TAG, "track uri ${track.uri}")
                uriArray.add(track.uri)
            }
            parameters["uris"] = uriArray.toList()
            body["uris"] = uriArray

            spotifyService.addTracksToPlaylist(
                currentUserPrivate.id,
                playlistGenerated.id,
                parameters,
                body,
                object : Callback<Pager<PlaylistTrack>> {
                    override fun success(playlistTrack: Pager<PlaylistTrack>, response: retrofit.client.Response) {
                        Log.v(TAG, "Add to playlist success ${playlistTrack.total}")
                        //playlistTrack.items
                    }

                    override fun failure(error: RetrofitError) {
                        Log.v(TAG, "Add to playlist failure " + error.toString())
                    }
                })
        //}
    }

    // creates a playlist in Spotify account with the given name
    fun createPlaylist(name: String) {
        val parameters = mutableMapOf<String, Any>()
        parameters["name"] = name
        parameters["description"] = "Running playlist"
        //val playlistReturned = spotifyService.createPlaylist(name, parameters)
        spotifyService.createPlaylist(currentUserPrivate.id, parameters, object: Callback<Playlist> {
            override fun success(playlist: Playlist , response: retrofit.client.Response) {
                Log.v(TAG,"Create playlist success ${playlist.name}")
                Log.v(TAG,"Create playlist success ${playlist.id}")
                Log.v(TAG,"Create playlist success ${playlist.uri}")
                playlistGenerated = playlist
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Create playlist failure " + error.toString())
            }
        })
    }


    // What to do when connected to remote Spotify app
    private fun connected() {
        Log.v(TAG, "Connected to remote Spotify app!")
        sucessfullyConnected = true
        userApi = mSpotifyAppRemote.userApi
        subscribeToPlayerState()
        createSpotifyService()
    }

    // Creates a Spotify service with access token from successfully connecting to remote spotify app
    // From kaaes library
    fun createSpotifyService() {
        Log.v(TAG, "Creating Spotify Service...")

        val api = SpotifyApi()
        api.setAccessToken(accessToken) // set authorization
        spotifyService = api.service    // set the service reference

        getUser()
        getWorkOutPlaylist()
    }

    // Returns the current user logged in
    fun getUser() {
        spotifyService.getMe(object: Callback<UserPrivate> {
            override fun success(user: UserPrivate , response: retrofit.client.Response) {
                currentUserPrivate = user
                Log.v(TAG,"Get user success " + user.display_name)
                signed_in_user.text = user.display_name
                getUserTopArtists()
                userDisplayName = user.display_name
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Get user failure " + error.toString())
            }
        })
    }

    // create a personalized playlist based on the users top artists
    fun getUserTopTracksArtists() {
        var result = ""
        for (item in topArtists) {
            if (result.isNotEmpty()) {
                result = result + ",$item"
            } else {
                result = result + item
            }
        }
        Log.v(TAG, "Artists Result: $result")

        val preferences = mutableMapOf<String, Any>()
        preferences["limit"] = 15
        preferences["seed_genres"] = result
        getTrackRecommendations(preferences)
    }

    // sets the top artists for the current user, gets the genres
    fun getUserTopArtists() {
        // For requesting the top artists
        val parameters = mutableMapOf<String, Any>()
        parameters["limit"] = 10
        parameters["time_range"] = "short_term" // the last 4 weeks

        var validCount = 0

        spotifyService.getTopArtists(parameters, object: Callback<Pager<kaaes.spotify.webapi.android.models.Artist>> {
            override fun success(artistsPager: Pager<kaaes.spotify.webapi.android.models.Artist> , response: retrofit.client.Response) {
                Log.v(TAG,"Get top artists success ")
                val artists = artistsPager.items
                Log.v(TAG, "Top Artists count: ${artists.size}")
                topArtists.clear()
                for (artist in artists) {
                    val genres = artist.genres
                    for (genre in genres) {
                        Log.v(TAG, "${artist.name} genre $genre")
                        if (spotifyGenres.contains(genre) && !topArtists.contains(genre) && validCount < 5 ) {
                            Log.v(TAG,"   VALID adding genre $genre")
                            topArtists.add(genre)
                            validCount++
                        }
                    }
                }
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Get user top artists failure " + error.toString())
            }
        })
    }

    // Get a work out genre playlist
    fun getWorkOutPlaylist() {
        val preferences = mutableMapOf<String, Any>()
        preferences["seed_genres"] = "work-out,pop,edm"

        val random = Random()
        preferences["market"] = "from_token"
        preferences["limit"] = 15
        val targetEnergy = (random.nextInt(85-70)+ 70).toDouble() / 100
        val targetValence = (random.nextInt(85-70)+ 70).toDouble() / 100
        preferences["target_energy"] = targetEnergy
        preferences["target_valence"] = targetValence
        //tempo BPM
        //valence 0.0-1.0

        getTrackRecommendations(preferences)

    }

    fun getTrackRecommendations(preferences: MutableMap<String, Any>) {
        spotifyService.getRecommendations(preferences, object: Callback<Recommendations> {
            override fun success(recommendations: Recommendations , response: retrofit.client.Response) {
                Log.v(TAG,"Get recommendations success " + recommendations.seeds)
                val tracks = recommendations.tracks
                Log.v(TAG, "Tracks ${tracks.size}")

                // clear the current playlist
                sTracks.clear()

                for (track in tracks) {
                    Log.v(TAG, "SONG: ${track.name} ARTISTS:${track.artists.size} ALBUM: ${track.album.name} POPULARITY: ${track.popularity} URI:${track.uri}")
                    sTracks.add(track)
                    playlist_list.adapter!!.notifyDataSetChanged()
                }
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Get recommendations failure " + error.toString())
            }
        })
    }

    fun getTrack(trackid: String) {
        val url = "https://api.spotify.com/v1$trackid"
        Log.v("VTest ListActivity", url)
        var uri = ""
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            com.android.volley.Response.Listener { response ->
                try {
                    Log.v(TAG, "GETTING REQUEST")
                    //uri = response.getJSONObject()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }, com.android.volley.Response.ErrorListener { error ->
                error(error.toString())
            })
        VolleyService.getInstance(this).add(request)
    }

    // Recycler view adapter for using a Track from Kaaes, Android Spotify Web API
    class RecyclerViewAdapter(private val parentActivity: PlaylistActivity,
                              private val values: MutableList<kaaes.spotify.webapi.android.models.Track>) :
        RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

        private val TAG = "VTRecyclerViewAdapter"
        private val onClickListener: View.OnClickListener

        init {
            // when a song is clicked
            onClickListener = View.OnClickListener { v ->
                Log.v(TAG, "Song clicked")
                val track = v.tag as kaaes.spotify.webapi.android.models.Track
                val trackUri = track.uri
                Log.v(TAG, "Track Uri: $trackUri")

            }
        }

        // Set of instance variables
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: NetworkImageView = view.playlist_item_image
            val songView: TextView = view.playlist_item_song
            val artistView: TextView = view.playlist_item_artist
            val songLengthView: TextView = view.playlist_item_length
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.playlist_item, parent, false)
            return ViewHolder(view)
        }

        // assign values from the Track objects to the proper fields
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            // set default and error image
            holder.imageView.setErrorImageResId(R.mipmap.ic_default_song_image)
            holder.imageView.setDefaultImageResId(R.mipmap.ic_default_song_image)

            // set the image from the track
            holder.imageView.setImageUrl(item.album.images.first().url, VolleyService.getInstance(this.parentActivity).imageLoader)

            // set the song name
            holder.songView.text = item.name

            // set the artist to the first artict listed
            holder.artistView.text = item.artists.first().name

            // convert song duration in milliseconds to minutes
            val duration = (item.duration_ms.toDouble() / 1000).toInt()
            val minutes = (duration / 60).toString()
            var seconds = (duration%60).toString()
            if (seconds.length == 1) {seconds = "0$seconds"}
            // format to 0:00
            val time = "$minutes:$seconds"
            // set the length of the song
            holder.songLengthView.text = time

            // set on item click
            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size
    }

    // Returns the album of the given id
    fun getAlbum(albumId: String): Album {
        lateinit var album: Album
        spotifyService.getAlbum(albumId, object: Callback<Album> {
            override fun success(result: Album , response: retrofit.client.Response) {
                album = result
                Log.v(TAG,"Album success " + album.name)
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Album failure " + error.toString())
            }
        })
        return album
    }

    // Subscribe to PlayerState, get the song that's playing
    fun subscribeToPlayerState() {
        mSpotifyAppRemote.playerApi
            .subscribeToPlayerState()
            .setEventCallback { playerState ->
                val track = playerState.track
                if (track != null) {
                    Log.d(TAG, track.name + " by " + track.artist.name)
                }
            }
    }

    // Returns true if the Spotify app is installed on the device, false otherwise
    fun checkSpotifyInstalled(): Boolean {
        val pm = packageManager
        var isSpotifyInstalled: Boolean
        try {
            pm.getPackageInfo("com.spotify.music", 0)
            isSpotifyInstalled = true
        } catch (e: PackageManager.NameNotFoundException) {
            isSpotifyInstalled = false
        }
        return isSpotifyInstalled
    }

    // Opens the google play store to the Spotify app if it's not installed on the device
    fun installSpotifyApp() {
        val appPackageName = "com.spotify.music"
        val referrer = "adjust_campaign=$packageName&adjust_tracker=ndjczk&utm_source=adjust_preinstall"

        try {
            val uri = Uri.parse("market://details")
                .buildUpon()
                .appendQueryParameter("id", appPackageName)
                .appendQueryParameter("referrer", referrer)
                .build()
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (ignored: android.content.ActivityNotFoundException) {
            val uri = Uri.parse("https://play.google.com/store/apps/details")
                .buildUpon()
                .appendQueryParameter("id", appPackageName)
                .appendQueryParameter("referrer", referrer)
                .build()
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

    }

    //A class to manage the Volley requestQueue as a singleton
    private class VolleyService private constructor(ctx: Context) {
        companion object {
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
        val requestQueue: RequestQueue by lazy {
            Volley.newRequestQueue(ctx.applicationContext) //return the context-based requestQueue
        }

        //from Kotlin docs
        val imageLoader: ImageLoader by lazy {
            ImageLoader(requestQueue,
                object : ImageLoader.ImageCache {
                    private val cache = LruCache<String, Bitmap>(20)
                    override fun getBitmap(url: String): Bitmap? {
                        return cache.get(url)
                    }
                    override fun putBitmap(url: String, bitmap: Bitmap) {
                        cache.put(url, bitmap)
                    }
                })
        }

        //convenience wrapper method
        fun <T> add(req: Request<T>) {
            requestQueue.add(req)
        }

    }

    private val spotifyGenres = arrayOf(
        "acoustic",
        "afrobeat",
        "alt-rock",
        "alternative",
        "ambient",
        "anime",
        "black-metal",
        "bluegrass",
        "blues",
        "bossanova",
        "brazil",
        "breakbeat",
        "british",
        "cantopop",
        "chicago-house",
        "children",
        "chill",
        "classical",
        "club",
        "comedy",
        "country",
        "dance",
        "dancehall",
        "death-metal",
        "deep-house",
        "detroit-techno",
        "disco",
        "disney",
        "drum-and-bass",
        "dub",
        "dubstep",
        "edm",
        "electro",
        "electronic",
        "emo",
        "folk",
        "forro",
        "french",
        "funk",
        "garage",
        "german",
        "gospel",
        "goth",
        "grindcore",
        "groove",
        "grunge",
        "guitar",
        "happy",
        "hard-rock",
        "hardcore",
        "hardstyle",
        "heavy-metal",
        "hip-hop",
        "holidays",
        "honky-tonk",
        "house",
        "idm",
        "indian",
        "indie",
        "indie-pop",
        "industrial",
        "iranian",
        "j-dance",
        "j-idol",
        "j-pop",
        "j-rock",
        "jazz",
        "k-pop",
        "kids",
        "latin",
        "latino",
        "malay",
        "mandopop",
        "metal",
        "metal-misc",
        "metalcore",
        "minimal-techno",
        "movies",
        "mpb",
        "new-age",
        "new-release",
        "opera",
        "pagode",
        "party",
        "philippines-opm",
        "piano",
        "pop",
        "pop-film",
        "post-dubstep",
        "power-pop",
        "progressive-house",
        "psych-rock",
        "punk",
        "punk-rock",
        "r-n-b",
        "rainy-day",
        "reggae",
        "reggaeton",
        "road-trip",
        "rock",
        "rock-n-roll",
        "rockabilly",
        "romance",
        "sad",
        "salsa",
        "samba",
        "sertanejo",
        "show-tunes",
        "singer-songwriter",
        "ska",
        "sleep",
        "songwriter",
        "soul",
        "soundtracks",
        "spanish",
        "study",
        "summer",
        "swedish",
        "synth-pop",
        "tango",
        "techno",
        "trance",
        "trip-hop",
        "turkish",
        "work-out",
        "world-music"
    )
}
