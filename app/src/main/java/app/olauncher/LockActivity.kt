package app.olauncher

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Prototype: own screen over the lock screen. The Pixel keeps listening for the fingerprint while
// it is shown, so it unlocks directly; a tap opens the PIN entry as a fallback
class LockActivity : AppCompatActivity() {

    private val unlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_lock)
        findViewById<View>(R.id.lockRoot).setOnClickListener { showPinEntry() }
        ContextCompat.registerReceiver(this, unlockedReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // Screen turned on again before the phone locked: nothing to show
    override fun onResume() {
        super.onResume()
        if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) finish()
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
        unregisterReceiver(unlockedReceiver)
        super.onDestroy()
    }
}
