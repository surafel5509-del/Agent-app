package com.nexa.ai.core.crypto

/** Output of [TokenCipher.encrypt]. The IV must be persisted next to the ciphertext. */
class EncryptedBlob(
    val ciphertext: ByteArray,
    val iv: ByteArray,
)

/** Symmetric encryption boundary so the store can be unit-tested without Android. */
interface TokenCipher {
    fun encrypt(plainText: String): EncryptedBlob
    fun decrypt(blob: EncryptedBlob): String
}

object CryptoFactory {
    fun base64Encode(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    fun base64Decode(text: String): ByteArray =
        java.util.Base64.getDecoder().decode(text)
}
