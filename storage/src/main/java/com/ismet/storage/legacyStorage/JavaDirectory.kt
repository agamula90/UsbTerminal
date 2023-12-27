package com.ismet.storage.legacyStorage

import com.ismet.storage.BaseDirectory
import com.ismet.storage.Directory
import java.io.File
import javax.inject.Inject

class JavaDirectory @Inject constructor(
    @BaseDirectory private val baseDirectory: String,
): Directory {
    private val sourceFile = File(baseDirectory)

    override fun exists(relativePath: String): Boolean {
        val targetFile = relativePath.getRelativeFile()
        return targetFile.isDirectory && targetFile.exists()
    }

    private fun String.getRelativeFile() = File(sourceFile, this)

    override fun create(relativePath: String): Boolean {
        val targetFile = relativePath.getRelativeFile()
        return targetFile.mkdir()
    }

    override fun createRecursively(relativePath: String): Boolean {
        val targetFile = relativePath.getRelativeFile()
        return targetFile.mkdirs()
    }

    override fun listSubDirectoryPaths(relativePath: String): List<String> {
        val targetFile = relativePath.getRelativeFile()
        val directories = targetFile.listFiles { d -> d.isDirectory }
        return directories?.map { it.absolutePath.replace(baseDirectory, "") }.orEmpty()
    }

    override fun listFilesByNameTemplate(relativePath: String, name: (String) -> Boolean): List<String> {
        val targetFile = relativePath.getRelativeFile()
        val directories = targetFile.listFiles { _, fileName -> name(fileName) }
        return directories?.map { it.absolutePath.replace(baseDirectory, "") }.orEmpty()
    }

    override fun lastModified(relativePath: String): Long {
        val targetFile = relativePath.getRelativeFile()
        return targetFile.lastModified()
    }
}