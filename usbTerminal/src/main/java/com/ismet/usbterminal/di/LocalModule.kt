package com.ismet.usbterminal.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.ismet.usb.UsbEmitter
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    @AccessoryOperationDispatcher(operationType = "read")
    fun provideReadAccessoryDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    @Provides
    @Singleton
    @AccessoryOperationDispatcher(operationType = "write")
    fun provideWriteAccessoryDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    @Provides
    @Singleton
    @CacheAccessoryOutputOnMeasureDispatcher
    fun provideCacheAccessoryOutputOnMeasureDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideUsbEmitter() = UsbEmitter()

    @Provides
    @Singleton
    fun provideMoshi() = Moshi.Builder().build()
}