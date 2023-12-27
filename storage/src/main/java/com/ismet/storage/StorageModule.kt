package com.ismet.storage

import android.os.Environment
import com.ismet.storage.legacyStorage.JavaDirectory
import com.ismet.storage.legacyStorage.JavaFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @BaseDirectory
    fun provideBaseDirectory() : String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    @Provides
    fun provideDirectory(
        @BaseDirectory baseDirectory: String
    ): Directory {
        return JavaDirectory(baseDirectory)
    }

    @Provides
    fun provideFile(
        @BaseDirectory baseDirectory: String
    ): File {
        return JavaFile(baseDirectory)
    }
}