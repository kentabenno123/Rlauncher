package app.olauncher.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.helper.Plug
import app.olauncher.helper.dpToPx

// Round smart plug buttons with "all off" in front, from margin to margin; shared by the home and the lock screen
class PlugButtons(private val layout: LinearLayout, private val viewModel: MainViewModel, owner: LifecycleOwner) {
    private val context = layout.context
    private val stroke = (1.5f * context.resources.displayMetrics.density).toInt()

    init {
        layout.isVisible = viewModel.plugs.isNotEmpty()
        if (viewModel.plugs.isNotEmpty()) addButtons()
        viewModel.plugStates.observe(owner) { showStates(it) }
    }

    private fun addButtons() {
        // "All off" on the far left: power icon in an outlined circle, in the theme's text color
        val textColor = TextView(context, null, 0, R.style.TextSmall).currentTextColor
        val allOff = ImageView(context).apply {
            setImageResource(R.drawable.ic_power)
            imageTintList = ColorStateList.valueOf(textColor)
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(stroke, textColor)
            }
            contentDescription = context.getString(R.string.plugs_all_off)
            setOnClickListener { viewModel.allPlugsOff() }
        }
        layout.addView(allOff, LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx()))
        viewModel.plugs.forEach { plug ->
            // Equal gaps between the circles, first and last sit on the edges
            layout.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            val button = TextView(context, null, 0, R.style.TextSmall).apply {
                text = plug.label
                textSize = 14f
                gravity = Gravity.CENTER
                includeFontPadding = false
                tag = plug
                contentDescription = plug.label
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                setOnClickListener { viewModel.togglePlug(plug) }
            }
            layout.addView(button, LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx()))
        }
    }

    // On: filled circle, letter in the contrasting color, full opacity. Off: outline at 50 %. Unknown: outline at 25 %
    private fun showStates(states: Map<String, Boolean>) {
        layout.children.filterIsInstance<TextView>().forEach { button ->
            val plug = button.tag as Plug
            val on = states[plug.id] == true
            val themeColors = textColorsOf(button)
            val color = themeColors.defaultColor
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(stroke, color)
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
}
