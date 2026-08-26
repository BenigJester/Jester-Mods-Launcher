package com.moodtools.hub.soulpatch

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/** Minimal Android binary-XML string-pool editor used for the Soul Knight factory bridge. */
internal object BinaryXmlStringPool {
    private const val XML_TYPE = 0x0003
    private const val STRING_POOL_TYPE = 0x0001
    private const val UTF8_FLAG = 0x00000100
    private const val SORTED_FLAG = 0x00000001

    fun replaceExact(document: ByteArray, expected: String, replacement: String): ByteArray {
        require(document.size >= 8 && u16(document, 0) == XML_TYPE) {
            "AndroidManifest.xml is not binary XML"
        }
        val xmlHeaderSize = u16(document, 2)
        val declaredSize = i32(document, 4)
        require(xmlHeaderSize in 8..document.size && declaredSize == document.size) {
            "AndroidManifest.xml has an invalid chunk header"
        }

        var cursor = xmlHeaderSize
        while (cursor + 8 <= document.size) {
            val type = u16(document, cursor)
            val headerSize = u16(document, cursor + 2)
            val chunkSize = i32(document, cursor + 4)
            require(headerSize >= 8 && chunkSize >= headerSize && cursor + chunkSize <= document.size) {
                "AndroidManifest.xml contains an invalid chunk"
            }
            if (type == STRING_POOL_TYPE) {
                val rebuilt = rebuildStringPool(
                    document.copyOfRange(cursor, cursor + chunkSize),
                    expected,
                    replacement
                )
                val output = ByteArray(document.size - chunkSize + rebuilt.size)
                document.copyInto(output, 0, 0, cursor)
                rebuilt.copyInto(output, cursor)
                document.copyInto(output, cursor + rebuilt.size, cursor + chunkSize, document.size)
                putI32(output, 4, output.size)
                return output
            }
            cursor += chunkSize
        }
        error("AndroidManifest.xml has no string pool")
    }

    private fun rebuildStringPool(chunk: ByteArray, expected: String, replacement: String): ByteArray {
        require(chunk.size >= 28 && u16(chunk, 0) == STRING_POOL_TYPE) {
            "AndroidManifest.xml string pool is invalid"
        }
        val headerSize = u16(chunk, 2)
        val stringCount = i32(chunk, 8)
        val styleCount = i32(chunk, 12)
        val originalFlags = i32(chunk, 16)
        val stringsStart = i32(chunk, 20)
        val stylesStart = i32(chunk, 24)
        require(headerSize >= 28 && stringCount >= 0 && styleCount >= 0) {
            "AndroidManifest.xml string-pool counts are invalid"
        }
        val offsetsEnd = headerSize + (stringCount + styleCount) * 4
        require(offsetsEnd <= stringsStart && stringsStart <= chunk.size) {
            "AndroidManifest.xml string-pool offsets are invalid"
        }
        val stringDataEnd = if (stylesStart == 0) chunk.size else stylesStart
        require(stringDataEnd in stringsStart..chunk.size) {
            "AndroidManifest.xml string-pool data is invalid"
        }

        val utf8 = originalFlags and UTF8_FLAG != 0
        val strings = ArrayList<String>(stringCount)
        var replacements = 0
        repeat(stringCount) { index ->
            val relativeOffset = i32(chunk, headerSize + index * 4)
            require(relativeOffset >= 0 && stringsStart + relativeOffset < stringDataEnd) {
                "AndroidManifest.xml contains an invalid string offset"
            }
            val value = if (utf8) {
                decodeUtf8(chunk, stringsStart + relativeOffset, stringDataEnd)
            } else {
                decodeUtf16(chunk, stringsStart + relativeOffset, stringDataEnd)
            }
            if (value == expected) {
                strings += replacement
                replacements++
            } else {
                strings += value
            }
        }
        require(replacements == 1) {
            "Expected one $expected declaration in AndroidManifest.xml; found $replacements"
        }

        val encodedStrings = ByteArrayOutputStream()
        val newOffsets = IntArray(stringCount)
        strings.forEachIndexed { index, value ->
            newOffsets[index] = encodedStrings.size()
            encodedStrings.write(if (utf8) encodeUtf8(value) else encodeUtf16(value))
        }
        while (encodedStrings.size() % 4 != 0) encodedStrings.write(0)
        val stringBytes = encodedStrings.toByteArray()
        val styleBytes = if (stylesStart == 0) ByteArray(0) else chunk.copyOfRange(stylesStart, chunk.size)
        val newStringsStart = offsetsEnd
        val newStylesStart = if (styleCount == 0) 0 else newStringsStart + stringBytes.size
        val newSize = newStringsStart + stringBytes.size + styleBytes.size
        val output = ByteArray(newSize)

        chunk.copyInto(output, 0, 0, headerSize)
        putI32(output, 4, newSize)
        putI32(output, 16, originalFlags and SORTED_FLAG.inv())
        putI32(output, 20, newStringsStart)
        putI32(output, 24, newStylesStart)
        newOffsets.forEachIndexed { index, offset -> putI32(output, headerSize + index * 4, offset) }
        repeat(styleCount) { index ->
            putI32(
                output,
                headerSize + stringCount * 4 + index * 4,
                i32(chunk, headerSize + stringCount * 4 + index * 4)
            )
        }
        stringBytes.copyInto(output, newStringsStart)
        styleBytes.copyInto(output, newStringsStart + stringBytes.size)
        return output
    }

    private fun decodeUtf8(bytes: ByteArray, offset: Int, limit: Int): String {
        var cursor = offset
        val utf16Length = readUtf8Length(bytes, cursor, limit)
        cursor += utf16Length.second
        val byteLength = readUtf8Length(bytes, cursor, limit)
        cursor += byteLength.second
        require(cursor + byteLength.first < limit && bytes[cursor + byteLength.first].toInt() == 0) {
            "AndroidManifest.xml contains a truncated UTF-8 string"
        }
        return String(bytes, cursor, byteLength.first, Charsets.UTF_8)
    }

    private fun decodeUtf16(bytes: ByteArray, offset: Int, limit: Int): String {
        val length = readUtf16Length(bytes, offset, limit)
        val cursor = offset + length.second
        val byteLength = length.first * 2
        require(cursor + byteLength + 1 < limit && u16(bytes, cursor + byteLength) == 0) {
            "AndroidManifest.xml contains a truncated UTF-16 string"
        }
        return String(bytes, cursor, byteLength, Charset.forName("UTF-16LE"))
    }

    private fun encodeUtf8(value: String): ByteArray {
        val encoded = value.toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().apply {
            writeUtf8Length(this, value.length)
            writeUtf8Length(this, encoded.size)
            write(encoded)
            write(0)
        }.toByteArray()
    }

    private fun encodeUtf16(value: String): ByteArray {
        val encoded = value.toByteArray(Charset.forName("UTF-16LE"))
        return ByteArrayOutputStream().apply {
            writeUtf16Length(this, value.length)
            write(encoded)
            write(0)
            write(0)
        }.toByteArray()
    }

    private fun readUtf8Length(bytes: ByteArray, offset: Int, limit: Int): Pair<Int, Int> {
        require(offset < limit)
        val first = bytes[offset].toInt() and 0xff
        return if (first and 0x80 == 0) {
            first to 1
        } else {
            require(offset + 1 < limit)
            (((first and 0x7f) shl 8) or (bytes[offset + 1].toInt() and 0xff)) to 2
        }
    }

    private fun readUtf16Length(bytes: ByteArray, offset: Int, limit: Int): Pair<Int, Int> {
        require(offset + 1 < limit)
        val first = u16(bytes, offset)
        return if (first and 0x8000 == 0) {
            first to 2
        } else {
            require(offset + 3 < limit)
            (((first and 0x7fff) shl 16) or u16(bytes, offset + 2)) to 4
        }
    }

    private fun writeUtf8Length(output: ByteArrayOutputStream, length: Int) {
        require(length <= 0x7fff)
        if (length <= 0x7f) {
            output.write(length)
        } else {
            output.write((length shr 8) or 0x80)
            output.write(length and 0xff)
        }
    }

    private fun writeUtf16Length(output: ByteArrayOutputStream, length: Int) {
        if (length <= 0x7fff) {
            output.write(length and 0xff)
            output.write(length shr 8)
        } else {
            val high = (length shr 16) or 0x8000
            output.write(high and 0xff)
            output.write(high shr 8)
            output.write(length and 0xff)
            output.write((length shr 8) and 0xff)
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

    private fun i32(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun putI32(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }
}
