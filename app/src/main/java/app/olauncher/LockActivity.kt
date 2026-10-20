package app.olauncher

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

// Prototype: own screen over the lock screen that asks the system right away to unlock,
// so on a Pixel only the fingerprint symbol should appear on top of it
class LockActivity : AppCompatActivity() {

    private var unlockRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_lock)
        // Fallback when the automatic request was cancelled: a tap asks again
        findViewById<View>(R.id.lockRoot).setOnClickListener { requestUnlock() }
    }

    override fun onResume() {
        super.onResume()
        unlockRequested = false
        window.decorView.post { requestUnlockOnce() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) requestUnlockOnce()
    }

    // Only while the display is on; a request with the screen off would be wasted
    private fun requestUnlockOnce() {
        if (unlockRequested || !getSystemService(PowerManager::class.java).isInteractive) return
        unlockRequested = true
        requestUnlock()
    }

    private fun requestUnlock() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finish()
        })
    }
}
