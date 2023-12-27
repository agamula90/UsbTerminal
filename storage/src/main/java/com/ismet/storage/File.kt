package com.ismet.storage

import java.io.PrintStream

interface File {
    fun exists(relativePath: String): Boolean
    fun create(relativePath: String): Boolean
    fun read(relativePath: String): String
    fun write(relativePath: String, content: String)
    fun append(relativePath: String, content: String)
    fun delete(relativePath: String): Boolean
    fun print(relativePath: String): PrintStream?
}