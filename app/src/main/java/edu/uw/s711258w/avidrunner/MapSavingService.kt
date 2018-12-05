package edu.uw.s711258w.avidrunner

import android.Manifest
import android.app.IntentService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*

class MapSavingService: IntentService("MapSavingService") {

    private val TAG = "MapSavingService"
    private lateinit var mHandler: Handler
    private val WRITE_REQUEST_CODE = 1
    private val DIRECTORY_FILES = ""


    override fun onCreate() {
        mHandler = Handler()
        super.onCreate()
    }

    override fun onHandleIntent(intent: Intent?) {
        val data = intent!!.getStringExtra("data")
        val fileName = "data.txt"

        val dir = this.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (!dir.exists()) {
            dir.mkdirs() //make Documents directory if doesn't otherwise exist << emulator workaround
        }

        val file = File(dir, fileName)
        if(file.exists()) {
            file.delete()
        }
        val out = PrintWriter(FileWriter(file, true))
        out.println(data)
        out.close()
    }
}