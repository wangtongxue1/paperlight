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
 * Books are plain files, so they belong where the reader can actually find
 * them with the phone's own file manager — `MyBooks` at the root of shared
 * storage. Writing there needs 所有文件访问 on Android 11+ (scoped storage),
 * so the app's private folder is only a fallback for the time before that
 * one-time grant: the user's library is never hidden away by default.
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

    /**
     * `MyBooks` whenever it can be used, otherwise the private folder.
     *
     * A library that already lives in the private folder (imported before
     * the storage permission was granted) stays there until it is copied
     * over — resolving to an empty `MyBooks` instead would look like the
     * books had vanished.
     */
    fun current(context: Context): File {
        val pub = publicRoot()
        if (!hasPublicAccess(context)) return privateRoot(context)
        if (!pub.isDirectory && !pub.mkdirs()) return privateRoot(context)
        if (File(pub, "metadata.db").exists()) return pub
        // Nothing in MyBooks yet: a fresh install starts there, while an
        // existing private library keeps being read from where it is.
        return if (File(privateRoot(context), "metadata.db").exists()) privateRoot(context) else pub
    }

    fun isPublic(context: Context): Boolean =
        current(context).absolutePath == publicRoot().absolutePath

    /**
     * Copies the whole library to [target]. Existing files are never
     * overwritten, so running this twice is harmless. Returns the new root,
     * or null when the copy did not produce a usable library — the caller
     * then keeps using the old one.
     */
    fun moveTo(context: Context, target: File = publicRoot()): File? {
        val from = current(context)
        if (from.absolutePath == target.absolutePath) return target
        if (!target.isDirectory && !target.mkdirs()) return null
        runCatching { copyInto(from, target) }
        // The database is the library; without it the copy is not usable.
        if (File(from, "metadata.db").exists() && !File(target, "metadata.db").exists()) return null
        return target
    }

    private fun copyInto(from: File, to: File) {
        val children = from.listFiles() ?: return
        // `metadata.db` is what marks a copy as usable, so it goes last:
        // an interrupted copy then never looks like a complete library.
        val ordered = children.filter { it.name != "metadata.db" } +
            children.filter { it.name == "metadata.db" }
        for (child in ordered) {
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
