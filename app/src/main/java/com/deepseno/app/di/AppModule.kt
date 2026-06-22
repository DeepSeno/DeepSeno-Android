package com.enmooy.deepseno.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.enmooy.deepseno.data.local.DeepSenoDatabase
import com.enmooy.deepseno.data.local.dao.CacheDao
import com.enmooy.deepseno.data.local.dao.CaptureItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DeepSenoDatabase =
        Room.databaseBuilder(context, DeepSenoDatabase::class.java, "deepseno.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCaptureItemDao(db: DeepSenoDatabase): CaptureItemDao = db.captureItemDao()

    @Provides
    fun provideCacheDao(db: DeepSenoDatabase): CacheDao = db.cacheDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("deepseno_prefs", Context.MODE_PRIVATE)
}
