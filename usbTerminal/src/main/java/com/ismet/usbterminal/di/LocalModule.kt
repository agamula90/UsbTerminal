package com.ismet.usbterminal.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import com.ismet.usb.UsbEmitter
import com.ismet.usbterminal.data.DirectoryType
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.io.PrintStream
import java.time.LocalDateTime
import java.util.concurrent.Executors
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    @UsbWriteDispatcher
    fun provideUsbWriteDispatcher(exceptionHandler: CoroutineExceptionHandler): CoroutineContext {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher() + exceptionHandler
    }

    @Provides
    @Singleton
    @CacheCo2ValuesDispatcher
    fun provideCacheC02ValuesDispatcher(exceptionHandler: CoroutineExceptionHandler): CoroutineContext {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher() + exceptionHandler
    }

    @Provides
    @Singleton
    fun provideExceptionHandler(): CoroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        throwable.printStackTrace(PrintStream(File(DirectoryType.CRASHES.getDirectory(), "crash_at_${LocalDateTime.now()}.txt")))
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