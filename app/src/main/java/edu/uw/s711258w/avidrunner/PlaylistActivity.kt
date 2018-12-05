package edu.uw.s711258w.avidrunner

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import com.spotify.sdk.android.authentication.LoginActivity.REQUEST_CODE
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.content.pm.PackageManager
import android.graphics.Bitmap
import kaaes.spotify.webapi.android.SpotifyService
import retrofit.RequestInterceptor.RequestFacade
import retrofit.RequestInterceptor
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.models.Album
import kaaes.spotify.webapi.android.models.Recommendations
import kaaes.spotify.webapi.android.models.UserPrivate
import kotlinx.android.synthetic.main.activity_playlist.*
import retrofit.Callback
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Response
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.v4.util.LruCache
import android.view.*
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.playlist_item.view.*
import org.w3c.dom.Text
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import android.widget.ImageView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.*
import com.spotify.android.appremote.api.UserApi
import com.spotify.protocol.client.CallResult
import org.json.JSONException
import com.android.volley.toolbox.*

class PlaylistActivity : AppCompatActivity() {

    private val TAG = "VT PLAYLISTACTIVITY"

    private val CLIENT_ID = "e576aca107ee4454a716f9ecf27edf1c"//getString(R.string.spotify_client_id)
    private val REDIRECT_URI = "edu.uw.s711258w.avidrunner://AuthenticationResponse"
    private lateinit var mSpotifyAppRemote: SpotifyAppRemote
    private lateinit var connectionListener: Connector.ConnectionListener
    //private var accessToken: String = "BQC0P5XP_oJFmcnEplIb13djEJnpnXYsNpVtM51lzYbdKj2nKylyDF1AqqzHhuKB9pibjmJRcqSWY14NNBmqZE59rWEhOFi5gfTDG9fZPsU1Rrb3o6Gx5VeglqWtOZ7PClUcpVTcrtZqJ8gwHzCTpp23skC6POMqSVjZihYHMwfxDRfkgLhHjWOPvMLZKshfhOnLLAw9dstGrXzNUQLOLT1mSgvbeaVhb64oOdWrJ3pL72yxxZM4Oaa-FUaLrgmMOIGscyY"
    private lateinit var accessToken: String
    private lateinit var userApi: UserApi
    private lateinit var spotifyService: SpotifyService
    private var sucessfullyConnected: Boolean = false // to remote spotify
    private lateinit var currentUserPrivate: UserPrivate
    private val playlistTracks =  mutableListOf<Track>()
    private val sTracks =  mutableListOf<kaaes.spotify.webapi.android.models.Track>()

    // Request code will be used to verify if result comes from the login activity
    private val LOGIN_REQUEST_CODE = 1337
    private val LOGOUT_REQUEST_CODE = 5

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
        //val recyclerView = playlist_list
        playlist_list.layoutManager = LinearLayoutManager(this)
        playlist_list.adapter = RecyclerViewAdapter(this, sTracks)
        //playlist_list.adapter = PlaylistRecyclerViewAdapter(this, playlistTracks)
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

    // Handles the click for Change Playlist button
    // sends user to settings activity where they can change the options for the playlist
    fun handleChangePlaylist(v: View) {
        Log.v(TAG, "Change Playlist button clicked, sending to Settings...")
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // Handles the click for Generate Playlist button
    // Generates a playlist based on the users Playlist Options preferences
    fun handleGeneratePlaylist(v: View) {
        Log.v(TAG, "Generate Playlist button clicked, creating a playlist...")
        // TODO generate a playlist
    }

    // Log out of spotify in our app
    fun logOut(v: View) {
        //AuthenticationClient.openLoginActivity(this, )
    }

    // What to do when connected to remote Spotify app
    private fun connected() {
        Log.v(TAG, "Connected to remote Spotify app!")
        sucessfullyConnected = true
        userApi = mSpotifyAppRemote.userApi
        subscribeToPlayerState()
        createSpotifyService()
        //playlist_activity_layout.visibility = View.VISIBLE
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
        //getRecommendations()

        playlist_activity_layout.visibility = View.VISIBLE
        /*
        val restAdapter = RestAdapter.Builder()
            .setEndpoint(SpotifyApi.SPOTIFY_WEB_API_ENDPOINT)
            //.setRequestInterceptor { request ->
                //request.addHeader("Authorization", "Bearer $accessToken")
            //}
            .setRequestInterceptor( object: RequestInterceptor {
                override fun intercept(request: RequestFacade) {
                    request.addHeader("Authorization", "Bearer $accessToken")
                }
            })
            .build()
        spotifyService = restAdapter.create(SpotifyService::class.java)
        Log.v(TAG, "Successfully created Spotify Service!")
        //sucessfullyConnected = true
        val id = mSpotifyAppRemote.userApi.subscribeToUserStatus().requestId
        Log.v(TAG, "ID: $id")
        //signed_in_user.text = spotifyService.getMe().display_name

        //signed_in_user.text = spotifyService.me.display_name
        //Log.v(TAG, "${spotifyService.getMe().display_name}")
        */
    }

    // Returns the current user logged in
    fun getUser() {
        spotifyService.getMe(object: Callback<UserPrivate> {
            override fun success(user: UserPrivate , response: retrofit.client.Response) {
                currentUserPrivate = user
                Log.v(TAG,"Get user success " + user.display_name)
                signed_in_user.text = user.display_name
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Get user failure " + error.toString())
            }
        })
    }


    fun getUserPlaylist() {

    }

    // Get a work out genre playlist
    fun getWorkOutPlaylist() {
        val preferences = mutableMapOf<String, Any>()
        preferences["seed_genres"] = "work-out,pop,edm"
        //preferences["market"] = "from_token"
        preferences["limit"] = 15
        preferences["target_energy"] = 0.85
        //preferences["target_valence"] = 0.9
        preferences["min_popularity"] = 85
        //tempo BPM
        //valence 0.0-1.0

        spotifyService.getRecommendations(preferences, object: Callback<Recommendations> {
            override fun success(recommendations: Recommendations , response: retrofit.client.Response) {
                Log.v(TAG,"Get recommendations success " + recommendations.seeds)
                val tracks = recommendations.tracks
                Log.v(TAG, "Tracks ${tracks.size}")

                // clear the current playlist
                //sTracks.clear()

                for (track in tracks) {
                    Log.v(TAG, "   SONG: ${track.name}   ARTISTS:${track.artists.size}   ALBUM: ${track.album.name}   POPULARITY: ${track.popularity}   URI:${track.uri}")
                    val id = track.id
                    //val url = "https://api.spotify.com/v1$id"
                    //val spotifyTrack = mSpotifyAppRemote.imagesApi.getImage(track.album.images.first().url)
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

        // assign values from the NewsArticle objects to the proper fields
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.imageView.setErrorImageResId(R.mipmap.ic_default_song_image)
            holder.imageView.setDefaultImageResId(R.mipmap.ic_default_song_image)
            holder.imageView.setImageUrl(item.album.images.first().url, VolleyService.getInstance(this.parentActivity).imageLoader)
            holder.songView.text = item.name
            holder.artistView.text = item.artists.first().name
            val duration = (item.duration_ms.toDouble() / 1000).toInt()
            val minutes = (duration / 60).toString()
            var seconds = (duration%60).toString()
            if (seconds.length == 1) {seconds = "0$seconds"}
            val time = "$minutes:$seconds"
            holder.songLengthView.text = time//duration.toString()
            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

    }

    // Recycler view adapter for using a Spotify Track
    class PlaylistRecyclerViewAdapter(private val parentActivity: PlaylistActivity,
                                      private val values: MutableList<Track>) :
        RecyclerView.Adapter<PlaylistRecyclerViewAdapter.ViewHolder>() {

        private val onClickListener: View.OnClickListener

        init {
            onClickListener = View.OnClickListener { v ->
                Log.v("RecyclerViewAdapter", "Song clicked")
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

        // assign values from the NewsArticle objects to the proper fields
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.imageView.setErrorImageResId(R.mipmap.ic_default_song_image)
            holder.imageView.setDefaultImageResId(R.mipmap.ic_default_song_image)
            holder.imageView.setImageUrl(item.imageUri.toString(), VolleyService.getInstance(this.parentActivity).imageLoader)
            holder.songView.text = item.name
            holder.artistView.text = item.artist.toString()
            holder.songLengthView.text = item.duration.toDouble().toString()
            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

    }

    // Returns recommendations for the user based on their preferences
    fun getRecommendations(): Recommendations? {
        Log.v(TAG, "Getting recommendations for the user...")
        val userPrefernces = getPreferences(Context.MODE_PRIVATE)
        //userPrefernces = this.getSharedPreferences( "PlaylistOptions",Context.MODE_PRIVATE)
        Log.v(TAG, "Genre pref exists: ${userPrefernces.contains("playlist_genre")}")
        // Default preferences
        val defaultGenres = mutableSetOf<String>()
        defaultGenres.add("work-out")
        defaultGenres.add("pop")
        val defaultDuration = "20"

        // User preferences values
        val genres = userPrefernces.getStringSet("playlist_genre", defaultGenres)
        Log.v(TAG, "User genres: ${genres}")
        val duration = userPrefernces.getString("playlist_length", defaultDuration)
        Log.v(TAG, "User length: $duration")

        // For the request
        val limit = duration!!.toInt() / 3

        val preferences = mutableMapOf<String, Any>()
        preferences["seed_genres"] = defaultGenres.toList().toString()
        //preferences["market"] = "from_token"
        //
        // preferences["limit"] = limit

        var recommendations: Recommendations? = null
        spotifyService.getRecommendations(preferences, object: Callback<Recommendations> {
            override fun success(recs: Recommendations , response: retrofit.client.Response) {
                recommendations = recs
                Log.v(TAG,"Get recommendations success " + recs.seeds)
                val tracks = recs.tracks
                Log.v(TAG, "Tracks ${tracks.size}")
                for (track in tracks) {
                    Log.v(TAG, "Track album: ${track.album.name} name: ${track.name}")
                }
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Get recommendations failure " + error.toString())
            }
        })
        return  recommendations
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

    // Returns the track of the given id SpotifyService
    fun getTrackKaaes(trackId: String): kaaes.spotify.webapi.android.models.Track {
        lateinit var track: kaaes.spotify.webapi.android.models.Track
        spotifyService.getTrack(trackId, object: Callback<kaaes.spotify.webapi.android.models.Track> {
            override fun success(result: kaaes.spotify.webapi.android.models.Track, response: retrofit.client.Response) {
                track = result
                Log.v(TAG,"Album success " + track.name)
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Album failure " + error.toString())
            }
        })
        return track
    }

    // TODO finish
    fun getPlaylist() {
        Log.v(TAG, "Getting playlists...")

    }

    //TODO change the playlist, it's random
    // Play a playlist
    fun playPlaylist() {
        mSpotifyAppRemote.playerApi.play("spotify:user:spotify:playlist:37i9dQZF1DX2sUQwD7tbmL")
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

    // Authorize the user using the built-in auth flow
    fun builtInAuthFlow() {
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams,
            object : Connector.ConnectionListener {

                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    mSpotifyAppRemote = spotifyAppRemote
                    Log.v(TAG, "Connected! Yay!")

                    // Now you can start interacting with App Remote
                    connected()

                }

                override fun onFailure(throwable: Throwable) {
                    Log.v(TAG, throwable.message, throwable)

                    // Something went wrong when attempting to connect! Handle errors here
                }
            })
    }

    //A class to manage the Volley requestQueue as a singleton
    private class VolleyService private constructor(ctx: Context) { //private constructor; cannot instantiate directly
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
