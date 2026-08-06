package com.motioncapture.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
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
    val isVideo: Boolean = false,
)

class GalleryRepository(private val context: Context) {

    private val privateDir: File
        get() = File(context.filesDir, FOLDER_NAME)

    private val videoPrivateDir: File
        get() = File(context.filesDir, VIDEO_FOLDER_NAME)

    private val videoExternalDir: File?
        get() = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.let { File(it, VIDEO_FOLDER_NAME) }

    suspend fun todayCount(): Int = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDaySeconds = cal.timeInMillis / 1000L

        var count = mediaStoreCount(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            startOfDaySeconds,
        )
        count += mediaStoreCount(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            startOfDaySeconds,
        )
        count += filesTodayCount(privateDir, cal.timeInMillis)
        count += filesTodayCount(videoPrivateDir, cal.timeInMillis)
        videoExternalDir?.let { count += filesTodayCount(it, cal.timeInMillis) }
        count
    }

    suspend fun galleryItems(): List<GalleryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<GalleryItem>()

        items += queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
        items += queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        items += filesToItems(privateDir, isVideo = false)
        items += filesToItems(videoPrivateDir, isVideo = true)
        videoExternalDir?.let { items += filesToItems(it, isVideo = true) }

        items.sortedByDescending { it.dateTaken }
    }

    suspend fun latestItem(): GalleryItem? = galleryItems().firstOrNull()

    private fun queryMediaStore(collection: Uri, isVideo: Boolean): List<GalleryItem> {
        val result = mutableListOf<GalleryItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        context.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf("$PREFIX%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol) * 1000L
                result += GalleryItem(
                    uri = ContentUris.withAppendedId(collection, id),
                    dateTaken = date,
                    source = Source.MEDIA_STORE,
                    isVideo = isVideo,
                )
            }
        }
        return result
    }

    private fun filesToItems(dir: File, isVideo: Boolean): List<GalleryItem> {
        val result = mutableListOf<GalleryItem>()
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                result += GalleryItem(
                    uri = Uri.fromFile(file),
                    dateTaken = file.lastModified(),
                    source = Source.APP_PRIVATE,
                    isVideo = isVideo,
                )
            }
        }
        return result
    }

    private fun filesTodayCount(dir: File, startOfDayMillis: Long): Int {
        return dir.listFiles()?.count {
            it.isFile && it.name.startsWith(PREFIX) && it.lastModified() >= startOfDayMillis
        } ?: 0
    }

    private fun mediaStoreCount(collection: Uri, startOfDaySeconds: Long): Int {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND " +
            "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
        context.contentResolver.query(
            collection,
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
        const val VIDEO_FOLDER_NAME = "MotionCaptureVideos"
    }
}
