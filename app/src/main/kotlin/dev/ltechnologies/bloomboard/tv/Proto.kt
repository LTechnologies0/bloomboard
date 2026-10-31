package dev.ltechnologies.bloomboard.tv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

data class ProtoField(
    val number: Int,
    val wire: Int,
    val varint: Long = 0,
    val bytes: ByteArray = ByteArray(0),
)

class ProtoWriter {
    private val buffer = ByteArrayOutputStream()

    fun intField(field: Int, value: Int) {
        varintField(field, value.toLong() and 0xFFFFFFFFL)
    }

    fun varintField(field: Int, value: Long) {
        writeVarint(((field shl 3) or 0).toLong())
        writeVarint(value)
    }

    fun stringField(field: Int, value: String) {
        bytesField(field, value.toByteArray(Charsets.UTF_8))
    }

    fun bytesField(field: Int, value: ByteArray) {
        writeVarint(((field shl 3) or 2).toLong())
        writeVarint(value.size.toLong())
        buffer.write(value)
    }

    fun messageField(field: Int, block: ProtoWriter.() -> Unit) {
        bytesField(field, proto(block))
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()

    private fun writeVarint(value: Long) {
        var rest = value
        while (rest and 0x7FL.inv() != 0L) {
            buffer.write(((rest and 0x7F) or 0x80).toInt())
            rest = rest ushr 7
        }
        buffer.write((rest and 0x7F).toInt())
    }
}

fun proto(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(block).toByteArray()

fun encodeVarint(value: Long): ByteArray {
    val buffer = ByteArrayOutputStream()
    var rest = value
    while (rest and 0x7FL.inv() != 0L) {
        buffer.write(((rest and 0x7F) or 0x80).toInt())
        rest = rest ushr 7
    }
    buffer.write((rest and 0x7F).toInt())
    return buffer.toByteArray()
}

fun frame(message: ByteArray): ByteArray = encodeVarint(message.size.toLong()) + message

class ProtoReader(private val data: ByteArray) {
    private var index = 0

    fun fields(): List<ProtoField> {
        val out = ArrayList<ProtoField>()
        while (index < data.size) {
            val tag = readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when (wire) {
                0 -> out += ProtoField(field, wire, varint = readVarint())
                2 -> {
                    val length = readVarint().toInt()
                    if (length < 0 || index + length > data.size) {
                        throw IllegalArgumentException("truncated protobuf")
                    }
                    val bytes = data.copyOfRange(index, index + length)
                    index += length
                    out += ProtoField(field, wire, bytes = bytes)
                }
                1 -> index += 8
                5 -> index += 4
                else -> throw IllegalArgumentException("unsupported wire type")
            }
        }
        return out
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        repeat(10) {
            if (index >= data.size) throw IllegalArgumentException("truncated varint")
            val byte = data[index++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("varint too long")
    }
}

fun readPrefixed(input: InputStream, maxSize: Int = 262_144): ByteArray {
    val length = readStreamVarint(input)
    if (length < 0 || length > maxSize) throw IOException("rejected message")
    val out = ByteArray(length.toInt())
    var offset = 0
    while (offset < out.size) {
        val read = input.read(out, offset, out.size - offset)
        if (read < 0) throw EOFException()
        offset += read
    }
    return out
}

private fun readStreamVarint(input: InputStream): Long {
    var result = 0L
    var shift = 0
    repeat(5) {
        val byte = input.read()
        if (byte < 0) throw EOFException()
        result = result or ((byte and 0x7F).toLong() shl shift)
        if (byte and 0x80 == 0) return result
        shift += 7
    }
    throw IOException("rejected message")
}
