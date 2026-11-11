@file:OptIn(ExperimentalForeignApi::class)

package com.movexa.app.core.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.getBytes
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

actual fun appCachePath(name: String): String = NSTemporaryDirectory() + name

actual suspend fun writeBytesToFile(path: String, bytes: ByteArray): Boolean =
    withContext(Dispatchers.Main) {
        runCatching {
            val file = fopen(path, "wb") ?: return@runCatching false
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), file)
                }
            }
            fclose(file)
            true
        }.getOrDefault(false)
    }

actual suspend fun readBytesFromFile(path: String): ByteArray? = withContext(Dispatchers.Main) {
    runCatching {
        val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return@runCatching null
        val size = data.length.toInt()
        if (size <= 0) return@runCatching null
        val out = ByteArray(size)
        out.usePinned { pinned ->
            data.getBytes(pinned.addressOf(0), length = data.length)
        }
        out
    }.getOrNull()
}
