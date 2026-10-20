package app.olauncher.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.LifecycleOwner
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.HomeHeaderBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.weatherIconRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Battery line, date, clock, weather and the status row with LAB, Gemma and the tunnel;
// shared by the home screen and the lock screen
class HomeHeader(
    private val binding: HomeHeaderBinding,
    private val prefs: Prefs,
    viewModel: MainViewModel,
    owner: LifecycleOwner,
) {
    private val context = binding.root.context
    private val handler = Handler(Looper.getMainLooper())
    private val labBlink = ObjectAnimator.ofFloat(binding.labDot, View.ALPHA, 1f, 0.15f).apply {
        duration = 700
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
    }

    // shortcut: counts any VPN as the tunnel, WireGuard doesn't share which tunnel is up; fine with a single VPN app
    private val vpnNetworks = mutableSetOf<Network>()
    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { vpnNetworks.add(network); showVpnState() }
        }

        override fun onLost(network: Network) {
            handler.post { vpnNetworks.remove(network); showVpnState() }
        }
    }

    val vpnUp get() = vpnNetworks.isNotEmpty()

    init {
        viewModel.weather.observe(owner) { weather ->
            val temperature = binding.weatherTemperature
            temperature.isVisible = weather != null
            if (weather == null) return@observe
            temperature.text = context.getString(R.string.temperature, weather.temperature)
            val icon = weatherIconRes(weather.code)?.let { ContextCompat.getDrawable(context, it) }
            icon?.setBounds(0, 0, temperature.textSize.toInt(), temperature.textSize.toInt())
            temperature.setCompoundDrawablesRelative(null, icon, null, null)
            temperature.compoundDrawablePadding = 8.dpToPx()
            TextViewCompat.setCompoundDrawableTintList(temperature, temperature.textColors)
        }
        viewModel.labOk.observe(owner) { ok ->
            binding.labStatus.isVisible = ok != null
            binding.labDot.setTextColor(ContextCompat.getColor(context, if (ok == true) R.color.lab_ok else R.color.lab_down))
            labBlink.cancel()
            binding.labDot.alpha = 1f
            if (ok == false) labBlink.start()
            updateStatusRow()
        }
        viewModel.gemmaOk.observe(owner) { ok ->
            binding.gemmaStatus.isVisible = ok != null
            binding.gemmaDot.setTextColor(ContextCompat.getColor(context, if (ok == true) R.color.lab_ok else R.color.lab_down))
            updateStatusRow()
        }
        binding.vpnLayout.isVisible = BuildConfig.WIREGUARD_TUNNEL.isNotEmpty()
        updateStatusRow()
        if (BuildConfig.WIREGUARD_TUNNEL.isNotEmpty()) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            context.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(request, vpnCallback)
        }
    }

    // Date, its visibility setting and the battery line; the clock updates itself
    fun update() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)
        binding.date.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()).replace(".,", ",")
        // The thin line at the top replaces the battery level of the hidden status bar
        val battery = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        binding.batteryBar.isVisible = !prefs.showStatusBar && battery > 0
        binding.batteryBar.progress = battery
    }

    fun resume() = labBlink.resume()

    fun pause() = labBlink.pause() // no blinking while the screen isn't shown

    fun showVpnState() {
        binding.vpnSwitch.isChecked = vpnUp
        binding.vpnDot.setBackgroundResource(if (vpnUp) R.drawable.vpn_dot_on else R.drawable.vpn_dot_off)
    }

    fun destroy() {
        labBlink.cancel()
        handler.removeCallbacksAndMessages(null)
        if (BuildConfig.WIREGUARD_TUNNEL.isNotEmpty())
            context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(vpnCallback)
        vpnNetworks.clear()
    }

    // The row with its hairlines only shows when LAB, Gemma or the tunnel switch is configured
    private fun updateStatusRow() {
        binding.statusLayout.isVisible = binding.labStatus.isVisible || binding.gemmaStatus.isVisible || binding.vpnLayout.isVisible
    }

    companion object {
        // Keeps the battery line below the front camera when the hidden status bar frees that space
        fun keepBelowCutout(content: View) {
            ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
                view.updatePadding(top = maxOf(40.dpToPx(), cutout + 16.dpToPx()))
                insets
            }
        }
    }
}
