package com.motioncapture.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

enum class Source { MEDIA_STORE, APP_PRIVATE }

data class GalleryItem(
    val uri: Uri,
    val dateTaken: Long,
    val source: Source,
)

class GalleryRepository(private val context: Context) {

    private val privateDir: File
        get() = File(context.filesDir, FOLDER_NAME)

    suspend fun todayCount(): Int = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDaySeconds = cal.timeInMillis / 1000L

        var count = mediaStoreCount(startOfDaySeconds)
        privateDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(PREFIX) && file.lastModified() >= cal.timeInMillis) {
                count++
            }
        }
        count
    }

    suspend fun galleryItems(): List<GalleryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<GalleryItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf("$PREFIX%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol) * 1000L
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                items += GalleryItem(uri, date, Source.MEDIA_STORE)
            }
        }

        privateDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                items += GalleryItem(Uri.fromFile(file), file.lastModified(), Source.APP_PRIVATE)
            }
        }

        items.sortedByDescending { it.dateTaken }
    }

    suspend fun latestItem(): GalleryItem? = galleryItems().firstOrNull()

    private fun mediaStoreCount(startOfDaySeconds: Long): Int {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND " +
            "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf("$PREFIX%", startOfDaySeconds.toString()),
            null,
        )?.use { cursor -> return cursor.count }
        return 0
    }

    companion object {
        const val PREFIX = "MotionCapture_"
        const val FOLDER_NAME = "MotionCapture"
    }
}
