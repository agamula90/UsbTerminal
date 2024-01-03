package com.ismet.storage

interface Directory {
    fun exists(relativePath: String): Boolean
    fun create(relativePath: String): Boolean
    fun createRecursively(relativePath: String): Boolean
    fun listSubDirectoryPaths(relativePath: String): List<String>
    fun listFilesByNameTemplate(relativePath: String, name: (String) -> Boolean): List<String>

    fun lastModified(relativePath: String): Long
}