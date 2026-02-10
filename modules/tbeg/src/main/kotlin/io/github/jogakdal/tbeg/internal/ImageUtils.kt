package io.github.jogakdal.tbeg.internal

internal fun ByteArray.detectImageType(): String = when {
    size < 4 -> "PNG"
    isPng() -> "PNG"
    isJpeg() -> "JPEG"
    isGif() -> "GIF"
    isBmp() -> "BMP"
    else -> "PNG"
}

private fun ByteArray.isPng(): Boolean =
    size >= 4 &&
    this[0] == 0x89.toByte() && this[1] == 0x50.toByte() &&
    this[2] == 0x4E.toByte() && this[3] == 0x47.toByte()

private fun ByteArray.isJpeg(): Boolean =
    size >= 3 &&
    this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private fun ByteArray.isGif(): Boolean =
    size >= 4 &&
    this[0] == 0x47.toByte() && this[1] == 0x49.toByte() &&
    this[2] == 0x46.toByte() && this[3] == 0x38.toByte()

private fun ByteArray.isBmp(): Boolean =
    size >= 2 &&
    this[0] == 0x42.toByte() && this[1] == 0x4D.toByte()
