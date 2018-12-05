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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.content.pm.PackageManager
import android.view.View
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


class PlaylistActivity : AppCompatActivity() {

    private val TAG = "VT PLAYLISTACTIVITY"

    private val CLIENT_ID = "e576aca107ee4454a716f9ecf27edf1c"//getString(R.string.spotify_client_id)
    private val REDIRECT_URI = "edu.uw.s711258w.avidrunner://AuthenticationResponse"
    private lateinit var mSpotifyAppRemote: SpotifyAppRemote
    private lateinit var connectionListener: Connector.ConnectionListener
    //private var accessToken: String = "BQC0P5XP_oJFmcnEplIb13djEJnpnXYsNpVtM51lzYbdKj2nKylyDF1AqqzHhuKB9pibjmJRcqSWY14NNBmqZE59rWEhOFi5gfTDG9fZPsU1Rrb3o6Gx5VeglqWtOZ7PClUcpVTcrtZqJ8gwHzCTpp23skC6POMqSVjZihYHMwfxDRfkgLhHjWOPvMLZKshfhOnLLAw9dstGrXzNUQLOLT1mSgvbeaVhb64oOdWrJ3pL72yxxZM4Oaa-FUaLrgmMOIGscyY"
    private lateinit var accessToken: String
    private lateinit var spotifyService: SpotifyService
    private var sucessfullyConnected: Boolean = false
    private lateinit var currentUserPrivate: UserPrivate

    // Request code will be used to verify if result comes from the login activity. Can be set to any integer.
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
                Log.v(TAG, "Connected! Yay!")
                // Now you can start interacting with App Remote
                connected()
            }

            override fun onFailure(throwable: Throwable) {
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

    // TODO Fix, crashes on click
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

        getRecommendations()

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
            override fun success(user: UserPrivate , response: Response ) {
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

    // Returns recommendations for the user based on their preferences
    fun getRecommendations(): Recommendations? {
        Log.v(TAG, "Getting recommendations for the user...")
        val userPrefernces = getPreferences(Context.MODE_PRIVATE)
        val duration = userPrefernces.getString("playlist_length", "15")
        val limit = duration.toInt() / 3
        val defaultGenres = mutableSetOf<String>()
        defaultGenres.add("pop")
        val genres = userPrefernces.getStringSet("playlist_genre", defaultGenres)

        val preferences = mutableMapOf<String, Any>()
        preferences["seed_genres"] = defaultGenres //genres
        //preferences["market"] = "from_token"
        //
        preferences["limit"] = 30//limit

        var recommendations: Recommendations? = null
        spotifyService.getRecommendations(preferences, object: Callback<Recommendations> {
            override fun success(recs: Recommendations , response: Response ) {
                recommendations = recs
                Log.v(TAG,"Get recommendations success " + recs.seeds)
                val tracks = recs.tracks
                Log.v(TAG, "Tracks ${tracks.size}")
                for (track in tracks) {
                    Log.v(TAG, "Track album: ${track.album.name} name: ${track.name}")
                }
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Get user failure " + error.toString())
            }
        })
        return  recommendations
    }

    // Returns the album of the given id
    fun getAlbum(albumId: String): Album {
        lateinit var album: Album
        spotifyService.getAlbum(albumId, object: Callback<Album> {
            override fun success(result: Album , response: Response ) {
                album = result
                Log.v(TAG,"Album success " + album.name)
            }

            override fun failure(error: RetrofitError ) {
                Log.v(TAG, "Album failure " + error.toString())
            }
        })
        return album
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
                    Log.d(TAG, "Connected! Yay!")

                    // Now you can start interacting with App Remote
                    connected()

                }

                override fun onFailure(throwable: Throwable) {
                    Log.e(TAG, throwable.message, throwable)

                    // Something went wrong when attempting to connect! Handle errors here
                }
            })
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
