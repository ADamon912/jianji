package com.example.jianji.data

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

interface LedgerDocuments {
    fun read(uri: Uri): ByteArray
    fun write(uri: Uri, bytes: ByteArray)
}

class AndroidLedgerDocuments(private val resolver: ContentResolver) : LedgerDocuments {
    override fun read(uri: Uri): ByteArray =
        (resolver.openInputStream(uri) ?: throw IOException("无法打开文件")).use(::readLimited)

    override fun write(uri: Uri, bytes: ByteArray) {
        (resolver.openOutputStream(uri, "wt") ?: throw IOException("无法创建文件")).use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }
}

internal fun readLimited(stream: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        require(output.size() + count <= LedgerTransfer.MAX_BYTES) { "文件不能超过 10 MiB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
