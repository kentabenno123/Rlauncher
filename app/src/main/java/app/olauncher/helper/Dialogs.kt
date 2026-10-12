package app.olauncher.helper

import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.linkUrl
import app.olauncher.databinding.DialogBaseBinding

/**
 * Shows a popup menu hanging off the end edge of this view.
 * [configure] can add or tweak items before the menu is shown.
 */
fun View.showPopupMenu(
    @MenuRes menuRes: Int = 0,
    configure: (Menu) -> Unit = {},
    onItemClick: (MenuItem) -> Unit,
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.END)
    if (menuRes != 0) popup.menuInflater.inflate(menuRes, popup.menu)
    configure(popup.menu)
    popup.setOnMenuItemClickListener { item ->
        onItemClick(item)
        true
    }
    popup.show()
    return popup
}

/**
 * App dialog: shows without bringing back a hidden status bar, and blurs the
 * screen behind it on Android 12+, fading blur and dialog out together on dismiss.
 */
class OlDialog(context: Context) : AlertDialog(context) {

    private var blur: WindowBlur? = null

    fun showRespectingStatusBar() {
        val window = window
        if (window == null || Prefs(context).showStatusBar) {
            show()
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        blur = window?.let { WindowBlur(it).apply { fadeIn() } }
    }

    override fun dismiss() {
        val blur = blur ?: return super.dismiss()
        this.blur = null
        blur.fadeOut { super.dismiss() }
    }
}

/**
 * Builds a dialog using the app's own layout: a title row with a close icon,
 * an optional [message] or custom [content], and a text [action] at the end.
 * [content] receives the container so the inflated view keeps its XML margins.
 */
fun Context.createDialog(
    @StringRes title: Int,
    @StringRes action: Int,
    @StringRes message: Int = 0,
    @StringRes neutral: Int = 0,
    onNeutral: () -> Unit = {},
    onAction: () -> Unit = {},
    content: ((ViewGroup) -> View)? = null,
): OlDialog {
    val dialog = OlDialog(this)
    val binding = DialogBaseBinding.inflate(LayoutInflater.from(dialog.context))
    binding.tvTitle.setText(title)
    binding.tvAction.setText(action)
    if (message != 0) {
        binding.tvMessage.setText(message)
        binding.tvMessage.isVisible = true
    }
    if (neutral != 0) {
        binding.tvNeutral.setText(neutral)
        binding.tvNeutral.isVisible = true
    }
    content?.let {
        binding.contentContainer.addView(it(binding.contentContainer))
        binding.contentContainer.isVisible = true
    }
    dialog.setView(binding.root)
    binding.ivClose.setOnClickListener { dialog.dismiss() }
    binding.tvNeutral.setOnClickListener {
        onNeutral()
        dialog.dismiss()
    }
    binding.tvAction.setOnClickListener {
        onAction()
        dialog.dismiss()
    }
    return dialog
}

/** Title with a close icon, a message and a single action. */
fun Context.showMessageDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
): OlDialog {
    val dialog = createDialog(title, action, message = message, onAction = onAction)
    dialog.showRespectingStatusBar()
    return dialog
}

// Adds a link entry (shortcut == null, asks for name + URL) or edits the URL of an existing one
@RequiresApi(Build.VERSION_CODES.O)
fun Context.showLinkDialog(shortcut: ShortcutInfo?, onSaved: () -> Unit) {
    val nameInput = EditText(this).apply {
        hint = getString(R.string.link_name)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        setText(shortcut?.shortLabel)
    }
    val urlInput = EditText(this).apply {
        hint = getString(R.string.link_url)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setText(shortcut?.intent?.dataString)
    }
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 0)
        if (shortcut == null) addView(nameInput)
        addView(urlInput)
    }
    AlertDialog.Builder(this)
        .setTitle(if (shortcut == null) R.string.add_link else R.string.link_url)
        .setView(layout)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val url = linkUrl(urlInput.text.toString())
            val name = nameInput.text.toString().trim()
                .ifEmpty { url?.toUri()?.host?.removePrefix("www.").orEmpty() }
            when {
                url == null -> showToast(R.string.invalid_link)
                saveLinkShortcut(shortcut?.id ?: "link-${System.currentTimeMillis()}", name, url) -> onSaved()
                else -> showToast(R.string.shortcut_pin_failed)
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
