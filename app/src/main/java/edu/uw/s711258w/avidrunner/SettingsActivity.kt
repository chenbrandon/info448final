package edu.uw.s711258w.avidrunner


import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceFragment
import android.view.MenuItem

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSupportActionBar()!!.setDisplayHomeAsUpEnabled(true)
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
    }

    class SettingsFragment: PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            addPreferencesFromResource(R.xml.preferences)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.getItemId()
        if (id == android.R.id.home) {
            this.finish()
        }
        return super.onOptionsItemSelected(item)
    }

}