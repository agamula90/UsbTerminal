package com.ismet.storage.legacyStorage

import com.ismet.storage.BaseDirectory
import com.ismet.storage.File
import java.io.IOException
import java.io.PrintStream
import javax.inject.Inject

class JavaFile @Inject constructor(
    @BaseDirectory private val baseDirectory: String,
): File {

    private val sourceFile = java.io.File(baseDirectory)

    override fun exists(relativePath: String): Boolean {
        val targetFile = relativePath.getRelativeFile()
        return targetFile.isFile && targetFile.exists()
    }

    private fun String.getRelativeFile() = java.io.File(sourceFile, this)

    override fun create(relativePath: String): Boolean {
        return try {
            relativePath.getRelativeFile().createNewFile()
            true
        } catch (e: IOException) {
            false
        }
    }

    override fun read(relativePath: String): String {
        return relativePath.getRelativeFile().readText()
    }

    override fun write(relativePath: String, content: String) {
        relativePath.getRelativeFile().writeText(content)
    }

    override fun append(relativePath: String, content: String) {
        relativePath.getRelativeFile().appendText(content)
    }

    override fun delete(relativePath: String): Boolean {
        val targetFile = relativePath.getRelativeFile()
        return targetFile.delete()
    }

    override fun print(relativePath: String): PrintStream? {
        val targetFile = relativePath.getRelativeFile()
        return PrintStream(targetFile.absolutePath)
    }
}