package com.snapzy.app.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual fun appCachePath(name: String): String =
    File(AndroidAppContext.context.cacheDir, name).absolutePath

actual suspend fun writeBytesToFile(path: String, bytes: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

actual suspend fun readBytesFromFile(path: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching { File(path).readBytes() }.getOrNull()
}
