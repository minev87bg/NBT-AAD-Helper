package com.nbtaad.editor.data

import android.content.Context
import java.io.File

/**
 * App-private storage, one folder per car, named after the VIN:
 *   files/cars/<VIN>/nbt-aad.env.fromcar   file as last read from the head unit
 *   files/cars/<VIN>/nbt-aad.env.draft     unfinished session (edited, not yet uploaded)
 *   files/cars/<VIN>/nbt-aad.env.uploaded  last file successfully uploaded
 */
class Storage(ctx: Context) {
    private val root = File(ctx.filesDir, "cars")

    private fun dir(carId: String): File {
        val safe = carId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, safe).apply { mkdirs() }
    }

    private fun file(carId: String, name: String) = File(dir(carId), name)

    fun saveFromCar(carId: String, text: String) = file(carId, "nbt-aad.env.fromcar").writeText(text)

    fun saveDraft(carId: String, text: String) = file(carId, "nbt-aad.env.draft").writeText(text)

    fun hasDraft(carId: String): Boolean = file(carId, "nbt-aad.env.draft").exists()

    fun readDraft(carId: String): String? =
        file(carId, "nbt-aad.env.draft").takeIf { it.exists() }?.readText()

    fun clearDraft(carId: String) {
        file(carId, "nbt-aad.env.draft").delete()
    }

    fun saveUploaded(carId: String, text: String) = file(carId, "nbt-aad.env.uploaded").writeText(text)
}
