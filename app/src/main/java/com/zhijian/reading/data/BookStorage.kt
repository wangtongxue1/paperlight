package com.zhijian.reading.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Where the library folder lives.
 *
 * Books are plain files, so they should be somewhere the reader can actually
 * find them with the phone's own file manager — `MyBooks` at the root of
 * shared storage. Writing there needs 所有文件访问 on Android 11+ (scoped
 * storage), so that is an explicit opt-in: until the permission is granted
 * the library stays in the app's private folder, which always works.
 */
object BookStorage {

    const val FOLDER = "MyBooks"

    /** `/storage/emulated/0/MyBooks`. */
    fun publicRoot(): File = File(Environment.getExternalStorageDirectory(), FOLDER)

    /** `files/Library` — invisible to the user, but needs no permission. */
    fun privateRoot(context: Context): File = File(context.filesDir, "Library")

    fun hasPublicAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** The folder in use right now: the chosen one when it is reachable. */
    fun current(context: Context, settings: Settings): File {
        val chosen = settings.libraryPath ?: return privateRoot(context)
        val dir = File(chosen)
        if (dir.isDirectory || dir.mkdirs()) return dir
        return privateRoot(context)
    }

    fun isPublic(context: Context, settings: Settings): Boolean =
        settings.libraryPath == publicRoot().absolutePath

    /**
     * Copies the whole library to [target] and remembers it there. Existing
     * files are never overwritten, so running this twice is harmless.
     * Returns the new root, or null when the copy did not produce a usable
     * library — the caller then keeps using the old one.
     */
    fun moveTo(context: Context, settings: Settings, target: File = publicRoot()): File? {
        val from = current(context, settings)
        if (from.absolutePath == target.absolutePath) return target
        if (!target.isDirectory && !target.mkdirs()) return null
        runCatching { copyInto(from, target) }
        // The database is the library; without it the copy is not usable.
        if (File(from, "metadata.db").exists() && !File(target, "metadata.db").exists()) return null
        settings.libraryPath = target.absolutePath
        return target
    }

    fun reset(settings: Settings) {
        settings.libraryPath = null
    }

    private fun copyInto(from: File, to: File) {
        val children = from.listFiles() ?: return
        for (child in children) {
            val dest = File(to, child.name)
            if (child.isDirectory) {
                dest.mkdirs()
                copyInto(child, dest)
            } else if (!dest.exists()) {
                child.copyTo(dest, overwrite = false)
            }
        }
    }
}
