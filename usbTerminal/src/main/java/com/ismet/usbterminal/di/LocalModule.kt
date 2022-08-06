package com.ismet.usbterminal.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.ismet.usb.UsbEmitter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @AccessoryOperationDispatcher(operationType = "read")
    fun provideReadAccessoryDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    @Provides
    @AccessoryOperationDispatcher(operationType = "write")
    fun provideWriteAccessoryDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    @Provides
    @CacheAccessoryOutputOnMeasureDispatcher
    fun provideCacheAccessoryOutputOnMeasureDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    @Provides
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    fun provideUsbEmitter() = UsbEmitter()
}