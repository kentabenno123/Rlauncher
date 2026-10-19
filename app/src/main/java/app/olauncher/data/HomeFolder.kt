package app.olauncher.data

// One app or pinned shortcut inside a home screen folder
data class FolderApp(
    val label: String,
    val packageName: String,
    val activityClassName: String,
    val user: String,
    val shortcutId: String,
)

// Control characters never occur in app labels or package names, so they are safe separators
private const val FIELD_SEPARATOR = "\u001F"
private const val ENTRY_SEPARATOR = "\u001E"

fun encodeFolder(apps: List<FolderApp>): String =
    apps.joinToString(ENTRY_SEPARATOR) {
        listOf(it.label, it.packageName, it.activityClassName, it.user, it.shortcutId).joinToString(FIELD_SEPARATOR)
    }

// Moves one entry, e.g. a folder app dragged onto another one
fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

fun decodeFolder(value: String): List<FolderApp> =
    value.split(ENTRY_SEPARATOR).mapNotNull { entry ->
        entry.split(FIELD_SEPARATOR).takeIf { it.size == 5 }?.let { FolderApp(it[0], it[1], it[2], it[3], it[4]) }
    }
