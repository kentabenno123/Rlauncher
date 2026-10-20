package app.olauncher

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityLockBinding
import app.olauncher.ui.HomeHeader

// Prototype: own screen over the lock screen with the home screen's header. The Pixel keeps listening
// for the fingerprint while it is shown, so it unlocks directly; a tap opens the PIN entry as a fallback
class LockActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var header: HomeHeader

    private val unlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    // Same text size as the home screen
    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (!prefs.showStatusBar) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        header = HomeHeader(binding.header, prefs, viewModel, this)
        HomeHeader.keepBelowCutout(binding.lockRoot)
        // The tunnel is only shown here, switching it needs the unlocked phone
        binding.header.vpnSwitch.isEnabled = false
        binding.header.vpnSwitch.alpha = 0.4f
        binding.lockRoot.setOnClickListener { showPinEntry() }
        ContextCompat.registerReceiver(this, unlockedReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        // Screen turned on again before the phone locked: nothing to show
        if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            finish()
            return
        }
        header.update()
        header.resume()
        viewModel.refreshWeather()
        viewModel.refreshLabStatus()
    }

    override fun onPause() {
        header.pause()
        super.onPause()
    }

    private fun showPinEntry() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finish()
        })
    }

    override fun onDestroy() {
        header.destroy()
        unregisterReceiver(unlockedReceiver)
        super.onDestroy()
    }
}
