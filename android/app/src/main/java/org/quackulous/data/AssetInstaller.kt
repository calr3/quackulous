package org.quackulous.data

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * Copies the engine's bundled data (assets/quackle) to app storage, where the engine reads it by path. Runs again only
 * after the app is installed or updated. The dictionary the user builds lives alongside it in lexica/ and is kept.
 */
object AssetInstaller {
    private const val ROOT = "quackle"

    fun install(context: Context): File {
        val target = File(context.filesDir, ROOT)
        val stamp = File(target, ".installed")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.exists() && stamp.readText() == version) return target

        copy(context.assets, ROOT, target)
        stamp.writeText(version)
        return target
    }

    private fun copy(assets: AssetManager, path: String, target: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        target.mkdirs()
        for (child in children) copy(assets, "$path/$child", File(target, child))
    }
}
