package app.olauncher.ui

import android.Manifest
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RotateDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.FolderApp
import app.olauncher.data.moved
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getChangedAppTheme
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.Plug
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showTextInputDialog
import app.olauncher.helper.showToast
import app.olauncher.helper.weatherIconRes
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Tag of an app shown inside an expanded home folder
    private data class FolderItem(val location: Int, val index: Int)

    private val expandedFolders = mutableSetOf<Int>()
    private lateinit var labBlink: ObjectAnimator
    private val statusHandler = Handler(Looper.getMainLooper())

    // Keeps LAB and Gemma current while the home screen stays open
    private val statusRefresh = object : Runnable {
        override fun run() {
            viewModel.refreshLabStatus()
            viewModel.refreshPlugs()
            statusHandler.postDelayed(this, 30_000)
        }
    }
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshWeather(force = true) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        labBlink = ObjectAnimator.ofFloat(binding.labDot, View.ALPHA, 1f, 0.15f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        initPlugButtons()
        view.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)
        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen(false)
        viewModel.isOlauncherDefault()
        val locationGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!locationGranted && !prefs.locationPermissionAsked) {
            prefs.locationPermissionAsked = true
            locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        viewModel.refreshWeather()
        statusRefresh.run()
        labBlink.resume()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    override fun onPause() {
        labBlink.pause() // no blinking while an app is in front
        statusHandler.removeCallbacks(statusRefresh)
        // Folders are always closed when you come back, without a fade-out on return
        binding.homeAppsLayout.layoutTransition = null
        expandedFolders.clear()
        removeFolderApps()
        super.onPause()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                (view.tag as? FolderItem)?.let {
                    folderAppClicked(it)
                    return
                }
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        (view.tag as? FolderItem)?.let {
            startFolderAppDrag(view, it)
            return true
        }
        val slot = view.tag?.toString()?.toIntOrNull()
        if (slot != null && prefs.getFolder(slot) != null) {
            showFolderMenu(view, slot)
            return true
        }
        when (view.id) {
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1, prefs.appName1.isNotEmpty(), true)
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2, prefs.appName2.isNotEmpty(), true)
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3, prefs.appName3.isNotEmpty(), true)
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4, prefs.appName4.isNotEmpty(), true)
            R.id.homeApp5 -> showAppList(Constants.FLAG_SET_HOME_APP_5, prefs.appName5.isNotEmpty(), true)
            R.id.homeApp6 -> showAppList(Constants.FLAG_SET_HOME_APP_6, prefs.appName6.isNotEmpty(), true)
            R.id.homeApp7 -> showAppList(Constants.FLAG_SET_HOME_APP_7, prefs.appName7.isNotEmpty(), true)
            R.id.homeApp8 -> showAppList(Constants.FLAG_SET_HOME_APP_8, prefs.appName8.isNotEmpty(), true)
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        viewModel.weather.observe(viewLifecycleOwner) { weather ->
            val temperature = binding.weatherTemperature
            temperature.isVisible = weather != null
            if (weather == null) return@observe
            temperature.text = getString(R.string.temperature, weather.temperature)
            val icon = weatherIconRes(weather.code)?.let { ContextCompat.getDrawable(requireContext(), it) }
            icon?.setBounds(0, 0, (temperature.textSize * 0.7f).toInt(), (temperature.textSize * 0.7f).toInt())
            temperature.setCompoundDrawablesRelative(icon, null, null, null)
            temperature.compoundDrawablePadding = 10.dpToPx()
            TextViewCompat.setCompoundDrawableTintList(temperature, temperature.textColors)
        }
        viewModel.labOk.observe(viewLifecycleOwner) { ok ->
            binding.labStatus.isVisible = ok != null
            binding.labDot.setTextColor(ContextCompat.getColor(requireContext(), if (ok == true) R.color.lab_ok else R.color.lab_down))
            labBlink.cancel()
            binding.labDot.alpha = 1f
            if (ok == false) labBlink.start()
        }
        viewModel.plugStates.observe(viewLifecycleOwner) { showPlugStates(it) }
        viewModel.gemmaOk.observe(viewLifecycleOwner) { ok ->
            binding.gemmaStatus.isVisible = ok != null
            binding.gemmaDot.setTextColor(ContextCompat.getColor(requireContext(), if (ok == true) R.color.lab_ok else R.color.lab_down))
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)

        // These fire only on d-pad/keyboard events; touch is consumed by ViewSwipeTouchListener
        binding.homeApp1.setOnClickListener(this)
        binding.homeApp2.setOnClickListener(this)
        binding.homeApp3.setOnClickListener(this)
        binding.homeApp4.setOnClickListener(this)
        binding.homeApp5.setOnClickListener(this)
        binding.homeApp6.setOnClickListener(this)
        binding.homeApp7.setOnClickListener(this)
        binding.homeApp8.setOnClickListener(this)
        binding.homeApp1.setOnLongClickListener(this)
        binding.homeApp2.setOnLongClickListener(this)
        binding.homeApp3.setOnLongClickListener(this)
        binding.homeApp4.setOnLongClickListener(this)
        binding.homeApp5.setOnLongClickListener(this)
        binding.homeApp6.setOnLongClickListener(this)
        binding.homeApp7.setOnLongClickListener(this)
        binding.homeApp8.setOnLongClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.clockDateLayout.gravity = horizontalGravity
        // Full-width home apps make the whole row tappable, not just the label
        val appWidth = if (prefs.extendHomeAppsArea) ViewGroup.LayoutParams.MATCH_PARENT
        else ViewGroup.LayoutParams.WRAP_CONTENT
        with(binding) { listOf(homeApp1, homeApp2, homeApp3, homeApp4, homeApp5, homeApp6, homeApp7, homeApp8) }
            .forEach {
                it.gravity = horizontalGravity
                it.updateLayoutParams { width = appWidth }
            }
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

//        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date.text = dateText.replace(".,", ",")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        removeFolderApps()
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        binding.homeApp1.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp1, prefs.appName1, prefs.appPackage1, prefs.appUser1, prefs.isShortcut1, prefs.shortcutId1)) {
            prefs.appName1 = ""
            prefs.appPackage1 = ""
        }
        if (homeAppsNum == 1) return

        binding.homeApp2.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp2, prefs.appName2, prefs.appPackage2, prefs.appUser2, prefs.isShortcut2, prefs.shortcutId2)) {
            prefs.appName2 = ""
            prefs.appPackage2 = ""
        }
        if (homeAppsNum == 2) return

        binding.homeApp3.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp3, prefs.appName3, prefs.appPackage3, prefs.appUser3, prefs.isShortcut3, prefs.shortcutId3)) {
            prefs.appName3 = ""
            prefs.appPackage3 = ""
        }
        if (homeAppsNum == 3) return

        binding.homeApp4.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp4, prefs.appName4, prefs.appPackage4, prefs.appUser4, prefs.isShortcut4, prefs.shortcutId4)) {
            prefs.appName4 = ""
            prefs.appPackage4 = ""
        }
        if (homeAppsNum == 4) return

        binding.homeApp5.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp5, prefs.appName5, prefs.appPackage5, prefs.appUser5, prefs.isShortcut5, prefs.shortcutId5)) {
            prefs.appName5 = ""
            prefs.appPackage5 = ""
        }
        if (homeAppsNum == 5) return

        binding.homeApp6.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp6, prefs.appName6, prefs.appPackage6, prefs.appUser6, prefs.isShortcut6, prefs.shortcutId6)) {
            prefs.appName6 = ""
            prefs.appPackage6 = ""
        }
        if (homeAppsNum == 6) return

        binding.homeApp7.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp7, prefs.appName7, prefs.appPackage7, prefs.appUser7, prefs.isShortcut7, prefs.shortcutId7)) {
            prefs.appName7 = ""
            prefs.appPackage7 = ""
        }
        if (homeAppsNum == 7) return

        binding.homeApp8.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp8, prefs.appName8, prefs.appPackage8, prefs.appUser8, prefs.isShortcut8, prefs.shortcutId8)) {
            prefs.appName8 = ""
            prefs.appPackage8 = ""
        }
    }

    private fun setHomeAppText(
        textView: TextView,
        appName: String,
        packageName: String,
        userString: String,
        isShortcut: Boolean,
        shortcutId: String?,
    ): Boolean {
        val location = textView.tag.toString().toInt()
        prefs.getFolder(location)?.let { apps ->
            setFolderLabel(textView, location, appName)
            if (location in expandedFolders) showFolderApps(textView, location, apps)
            return true
        }

        // Get user handle for the app/shortcut
        val userHandle = getUserHandleFromString(requireContext(), userString)

        // If it's a shortcut, verify it still exists
        if (isShortcut) {
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Query for the specific shortcut
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                // Check if our shortcut still exists
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    textView.text = appName
                    return true
                }
                textView.text = ""
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                textView.text = ""
                return false
            }
        }

        // Regular app check
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            return true
        }
        textView.text = ""
        return false
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun homeAppClicked(location: Int) {
        val folderApps = prefs.getFolder(location)
        if (folderApps != null) {
            toggleFolder(location, folderApps)
            return
        }
        launchAppOrShortcut(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
    }

    // Folder name followed by a chevron that points right when closed and turns down when open
    private fun setFolderLabel(textView: TextView, location: Int, name: String, animate: Boolean = false) {
        val size = (textView.textSize * 0.6f).toInt()
        val chevron = RotateDrawable().apply {
            drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_chevron)?.mutate()?.apply {
                setTint(textView.currentTextColor)
            }
            toDegrees = 90f
            setBounds(0, 0, size, size)
        }
        val target = if (location in expandedFolders) 10_000 else 0
        chevron.level = if (animate) 10_000 - target else target
        val align = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER
        else DynamicDrawableSpan.ALIGN_BASELINE
        textView.text = SpannableStringBuilder(name).append(" ").append("›", ImageSpan(chevron, align), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (animate) ObjectAnimator.ofInt(chevron, "level", chevron.level, target).apply {
            duration = 200
            addUpdateListener { textView.invalidate() }
        }.start()
    }

    private fun toggleFolder(location: Int, apps: List<FolderApp>) {
        val layout = binding.homeAppsLayout
        val folderView = layout.findViewWithTag<TextView>(location.toString()) ?: return
        // Set on first use only, so the home apps don't fade in every time the screen is built
        if (layout.layoutTransition == null) layout.layoutTransition = LayoutTransition().apply { setDuration(200) }
        if (expandedFolders.remove(location)) removeFolderApps(location)
        else {
            expandedFolders.add(location)
            showFolderApps(folderView, location, apps)
        }
        setFolderLabel(folderView, location, prefs.getAppName(location), animate = true)
    }

    // Removes the shown apps of one folder, or of all folders
    private fun removeFolderApps(location: Int? = null) {
        val layout = binding.homeAppsLayout
        layout.children.filter { (it.tag as? FolderItem)?.let { item -> location == null || item.location == location } == true }
            .toList()
            .forEach { layout.removeView(it) }
    }

    private fun initPlugButtons() {
        binding.plugsLayout.isVisible = viewModel.plugs.isNotEmpty()
        if (viewModel.plugs.isEmpty()) return
        // "All off" on the far left: power icon in an outlined circle, in the theme's text color
        val textColor = TextView(requireContext(), null, 0, R.style.TextSmall).currentTextColor
        val allOff = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_power)
            imageTintList = ColorStateList.valueOf(textColor)
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((1.5f * resources.displayMetrics.density).toInt(), textColor)
            }
            contentDescription = getString(R.string.plugs_all_off)
            setOnClickListener { viewModel.allPlugsOff() }
        }
        binding.plugsLayout.addView(allOff, LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()))
        viewModel.plugs.forEach { plug ->
            // Equal gaps between the circles, first and last sit on the edges
            binding.plugsLayout.addView(Space(requireContext()), LinearLayout.LayoutParams(0, 0, 1f))
            val button = TextView(requireContext(), null, 0, R.style.TextSmall).apply {
                text = plug.label
                textSize = 12f
                gravity = Gravity.CENTER
                includeFontPadding = false
                tag = plug
                contentDescription = plug.label
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                setOnClickListener { viewModel.togglePlug(plug) }
            }
            binding.plugsLayout.addView(button, LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()))
        }
    }

    // On: filled circle, letter in the contrasting color, full opacity. Off: outline at 50 %. Unknown: outline at 25 %
    private fun showPlugStates(states: Map<String, Boolean>) {
        binding.plugsLayout.children.filterIsInstance<TextView>().forEach { button ->
            val plug = button.tag as Plug
            val on = states[plug.id] == true
            val themeColors = textColorsOf(button)
            val color = themeColors.defaultColor
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((1.5f * resources.displayMetrics.density).toInt(), color)
                setColor(if (on) color else Color.TRANSPARENT)
            }
            val contrast = if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
            button.setTextColor(if (on) ColorStateList.valueOf(contrast) else themeColors)
            button.alpha = when (states[plug.id]) {
                true -> 1f
                false -> 0.5f
                null -> 0.25f
            }
        }
    }

    // The theme's text colors, also after the button showed the contrasting "on" color
    private fun textColorsOf(button: TextView): ColorStateList =
        button.getTag(R.id.plugsLayout) as? ColorStateList
            ?: button.textColors.also { button.setTag(R.id.plugsLayout, it) }

    // Lists the folder's apps indented right below the folder entry
    private fun showFolderApps(folderView: TextView, location: Int, apps: List<FolderApp>) {
        val layout = binding.homeAppsLayout
        val indent = 24.dpToPx()
        val alignment = prefs.homeAlignment
        apps.forEachIndexed { index, app ->
            val appView = TextView(requireContext(), null, 0, R.style.TextLarge).apply {
                text = app.label
                tag = FolderItem(location, index)
                setOnDragListener(folderAppDragListener)
                gravity = alignment
                setPaddingRelative(
                    if (alignment == Gravity.START) indent else 0,
                    folderView.paddingTop,
                    if (alignment == Gravity.END) indent else 0,
                    folderView.paddingBottom,
                )
                setOnTouchListener(getViewSwipeTouchListener(context, this))
            }
            layout.addView(
                appView,
                layout.indexOfChild(folderView) + 1 + index,
                ViewGroup.LayoutParams(folderView.layoutParams.width, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    private fun folderAppClicked(item: FolderItem) {
        val app = prefs.getFolder(item.location)?.getOrNull(item.index) ?: return
        launchAppOrShortcut(
            appName = app.label,
            packageName = app.packageName,
            activityClassName = app.activityClassName.ifEmpty { null },
            shortcutId = app.shortcutId,
            isShortcut = app.shortcutId.isNotEmpty(),
            userString = app.user
        )
    }

    private fun showFolderMenu(view: View, location: Int) {
        view.showPopupMenu(configure = { menu ->
            menu.add(0, R.string.folder_add_app, 0, R.string.folder_add_app)
            menu.add(0, R.string.rename, 1, R.string.rename)
            menu.add(0, R.string.folder_dissolve, 2, R.string.folder_dissolve)
        }) { item ->
            when (item.itemId) {
                R.string.folder_add_app ->
                    showAppList(Constants.FLAG_ADD_TO_FOLDER + location, includeHiddenApps = true)

                R.string.rename -> requireContext().showTextInputDialog(R.string.rename, prefs.getAppName(location)) {
                    prefs.setAppName(location, it)
                    populateHomeScreen(false)
                }

                R.string.folder_dissolve -> {
                    prefs.setFolder(location, null)
                    prefs.setAppName(location, "")
                    expandedFolders.remove(location)
                    populateHomeScreen(false)
                }
            }
        }
    }

    // Still holding when the long press fires: drag onto another app of the same folder to reorder,
    // or release in place for the menu. Already released (isPressed is cleared on ACTION_UP): menu right away
    private fun startFolderAppDrag(view: View, item: FolderItem) {
        val dragging = view.isPressed && view.startDragAndDrop(null, View.DragShadowBuilder(view), item, 0)
        if (!dragging) showFolderAppMenu(view, item)
    }

    private val folderAppDragListener = View.OnDragListener { target, event ->
        val dragged = event.localState as? FolderItem
        val targetItem = target.tag as? FolderItem
        if (dragged == null || targetItem == null || dragged.location != targetItem.location) return@OnDragListener false
        when (event.action) {
            DragEvent.ACTION_DRAG_ENTERED -> if (targetItem != dragged) target.alpha = 0.4f
            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> target.alpha = 1f
            DragEvent.ACTION_DROP -> {
                target.alpha = 1f
                if (targetItem == dragged) showFolderAppMenu(target, dragged)
                else prefs.getFolder(dragged.location)?.let { apps ->
                    prefs.setFolder(dragged.location, apps.moved(dragged.index, targetItem.index))
                    populateHomeScreen(false)
                }
            }
        }
        true
    }

    private fun showFolderAppMenu(view: View, item: FolderItem) {
        view.showPopupMenu(configure = { menu ->
            menu.add(0, R.string.rename, 0, R.string.rename)
            menu.add(0, R.string.folder_remove_app, 1, R.string.folder_remove_app)
        }) { menuItem ->
            val apps = prefs.getFolder(item.location) ?: return@showPopupMenu
            val app = apps.getOrNull(item.index) ?: return@showPopupMenu
            when (menuItem.itemId) {
                // Only the label shown in the folder changes, the app keeps its own name
                R.string.rename -> requireContext().showTextInputDialog(R.string.rename, app.label) { label ->
                    prefs.setFolder(item.location, apps.map { if (it === app) it.copy(label = label) else it })
                    populateHomeScreen(false)
                }

                R.string.folder_remove_app -> {
                    prefs.setFolder(item.location, apps.filterIndexed { index, _ -> index != item.index })
                    populateHomeScreen(false)
                }
            }
        }
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeRight,
            packageName = prefs.appPackageSwipeRight,
            activityClassName = prefs.appActivityClassNameRight,
            shortcutId = prefs.shortcutIdSwipeRight,
            isShortcut = prefs.isShortcutSwipeRight,
            userString = prefs.appUserSwipeRight,
            fallback = { openDialerApp(requireContext()) }
        )
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeLeft,
            packageName = prefs.appPackageSwipeLeft,
            activityClassName = prefs.appActivityClassNameSwipeLeft,
            shortcutId = prefs.shortcutIdSwipeLeft,
            isShortcut = prefs.isShortcutSwipeLeft,
            userString = prefs.appUserSwipeLeft,
            fallback = { openCameraApp(requireContext()) }
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            e.printStackTrace()
        }
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
        }
    }

    private fun showStatusBar() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    // A swipe from the top edge only shows the status bar briefly, then it hides again by itself
    private fun hideStatusBar() {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.statusBars())
            }
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    // Hides the status bar again when home gets the focus back, e.g. after the notification shade or a dialog
    private val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus && isResumed && !prefs.showStatusBar) hideStatusBar()
    }

    private fun changeAppTheme() {
        if (prefs.dailyWallpaper.not()) return
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.dailyWallpaper) {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                expandNotificationDrawer(requireContext())
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (!prefs.lockModeOn) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    binding.lock.performClick()
                else
                    lockPhone()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                expandNotificationDrawer(requireContext())
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    override fun onDestroyView() {
        labBlink.cancel()
        binding.root.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
        super.onDestroyView()
        _binding = null
    }
}