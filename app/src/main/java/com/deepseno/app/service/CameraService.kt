package com.enmooy.deepseno.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createImageFile(): File {
        val fileName = "deepseno-photo-${System.currentTimeMillis() / 1000}-${(1000..9999).random()}.jpg"
        return File(context.cacheDir, fileName)
    }

    fun getCacheDir(): File = context.cacheDir
}
