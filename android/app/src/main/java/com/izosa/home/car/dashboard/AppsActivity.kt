package com.izosa.home.car.dashboard

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Grid of every launchable app, for everything that isn't on the home screen bar. */
class AppsActivity : AppCompatActivity() {

    private lateinit var grid: GridView

    // Swiping down closes the drawer, but only once the grid is scrolled to the top, so it
    // never fights with scrolling through the apps.
    private val swipe by lazy {
        VerticalSwipe(this) { up -> if (!up && !grid.canScrollVertically(-1)) close() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        grid = findViewById(R.id.appGrid)
        grid.adapter = AppAdapter(apps)
        grid.setOnItemClickListener { _, _, position, _ ->
            val info = apps[position].activityInfo
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(info.packageName, info.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.homeButton).setOnClickListener { close() }
        findViewById<View>(R.id.recentsButton).setOnClickListener { showRecents() }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            SettingsLock.unlock(this) { startActivity(Intent(this, SettingsActivity::class.java)) }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        swipe.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = close()

    /** Opens the system recent-apps screen, or the setting that enables it the first time. */
    private fun showRecents() {
        if (RecentsService.showRecents()) return
        Toast.makeText(this, R.string.hint_enable_recents, Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun close() {
        finish()
        overridePendingTransition(R.anim.stay, R.anim.slide_out_bottom)
    }

    private inner class AppAdapter(private val apps: List<ResolveInfo>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(app.loadIcon(packageManager))
            view.findViewById<TextView>(R.id.label).text = app.loadLabel(packageManager)
            return view
        }
    }
}
