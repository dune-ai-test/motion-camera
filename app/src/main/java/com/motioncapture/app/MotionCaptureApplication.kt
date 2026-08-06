package com.motioncapture.app

import android.app.Application
import com.motioncapture.app.data.AppPreferences
import com.motioncapture.app.data.GalleryRepository

class MotionCaptureApplication : Application() {

    lateinit var preferences: AppPreferences
        private set

    lateinit var galleryRepository: GalleryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        galleryRepository = GalleryRepository(this)
    }
}
