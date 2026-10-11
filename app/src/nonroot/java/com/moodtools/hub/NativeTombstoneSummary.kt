package com.moodtools.hub

/** Selected fields from AOSP debuggerd/proto/tombstone.proto. No memory dumps, logs or abort text.
 * Unknown fields are skipped; malformed/oversized messages fail closed. No protobuf dependency.
 */
internal object NativeTombstoneSummary {
    private data class Field(val number: Int, val value: Long = 0, val bytes: ByteArray? = null)

    private fun fields(bytes: ByteArray): List<Field> {
        require(bytes.size <= 2 * 1024 * 1024)
        var position = 0
        fun varint(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                require(position < bytes.size)
                val next = bytes[position++].toInt() and 255
                if (shift == 63) require(next <= 1)
                value = value or ((next and 127).toLong() shl shift)
                if (next and 128 == 0) return value
            }
            error("Invalid varint")
        }
        val result = mutableListOf<Field>()
        while (position < bytes.size) {
            require(result.size < 100_000)
            val tag = varint()
            val number = (tag ushr 3).toInt()
            require(number > 0)
            when ((tag and 7).toInt()) {
                0 -> result += Field(number, varint())
                1, 5 -> {
                    val length = if (tag and 7 == 1L) 8 else 4
                    require(position + length <= bytes.size)
                    position += length
                }
                2 -> {
                    val length = varint()
                    require(length in 0..(bytes.size - position).toLong())
                    val end = position + length.toInt()
                    result += Field(number, bytes = bytes.copyOfRange(position, end))
                    position = end
                }
                else -> error("Unsupported protobuf wire type")
            }
        }
        return result
    }

    private fun List<Field>.number(id: Int) = firstOrNull { it.number == id }?.value ?: 0L
    private fun List<Field>.text(id: Int): String = firstOrNull { it.number == id }?.bytes
        ?.toString(Charsets.UTF_8)?.take(240)?.replace(Regex("[^A-Za-z0-9_.$<>:+/() -]"), "_").orEmpty()
    private fun List<Field>.message(id: Int) = firstOrNull { it.number == id }?.bytes?.let(::fields).orEmpty()

    fun decode(bytes: ByteArray): String {
        val tombstone = fields(bytes)
        require(tombstone.any { it.number == 10 && it.bytes != null })
        val signal = tombstone.message(10)
        val tid = tombstone.number(6)
        return buildString {
            appendLine("Native signal: ${signal.text(2)} (${signal.number(1)}), code ${signal.text(4)} (${signal.number(3).toInt()})")
            if (signal.number(8) != 0L) appendLine("Fault address: 0x${signal.number(9).toULong().toString(16)}")
            appendLine("Crashing TID: $tid")
            val thread = tombstone.filter { it.number == 16 && it.bytes != null }
                .map { fields(requireNotNull(it.bytes)) }.firstOrNull { it.number(1) == tid }?.message(2)
            if (thread == null) {
                appendLine("Crashing thread unavailable in system trace")
            } else {
                thread.filter { it.number == 3 && it.bytes != null }.take(40).forEach {
                    val register = fields(requireNotNull(it.bytes))
                    appendLine("Register ${register.text(1)} = 0x${register.number(2).toULong().toString(16)}")
                }
                thread.filter { it.number == 4 && it.bytes != null }.take(64).forEachIndexed { index, field ->
                    val frame = fields(requireNotNull(field.bytes))
                    val library = frame.text(6).substringAfterLast('/')
                    appendLine("#$index $library +0x${frame.number(1).toULong().toString(16)} " +
                        "${frame.text(4)}+${frame.number(5)} buildId=${frame.text(8)}")
                }
            }
            appendLine("Addresses indicate where the crash occurred, not proof of a faulty hook.")
        }
    }
}
