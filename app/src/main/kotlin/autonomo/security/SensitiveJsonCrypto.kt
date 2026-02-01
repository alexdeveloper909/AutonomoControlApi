package autonomo.security

import autonomo.config.AppConfig
import autonomo.config.KmsConfig
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DataKeySpec
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest

interface SensitiveJsonCrypto {
    fun isEncrypted(value: String): Boolean

    fun encrypt(
        context: String,
        pk: String,
        sk: String?,
        attributeName: String,
        plaintextJson: String
    ): String

    fun decrypt(
        context: String,
        pk: String,
        sk: String?,
        attributeName: String,
        storedValue: String
    ): String
}

object SensitiveJsonCryptoProvider {
    val instance: SensitiveJsonCrypto by lazy {
        if (!AppConfig.sensitiveJsonEncryptionEnabled) {
            NoopSensitiveJsonCrypto
        } else {
            val keyArn = AppConfig.envelopeKmsKeyArn
                ?: throw IllegalStateException(
                    "SENSITIVE_JSON_ENCRYPTION_ENABLED=true requires ENVELOPE_KMS_KEY_ARN (or ENVELOPE_KMS_KEY_ID)"
                )
            KmsEnvelopeSensitiveJsonCrypto(
                kms = KmsConfig.client(),
                kmsKeyId = keyArn
            )
        }
    }
}

private object NoopSensitiveJsonCrypto : SensitiveJsonCrypto {
    override fun isEncrypted(value: String): Boolean = value.startsWith(KmsEnvelopeSensitiveJsonCrypto.PREFIX)

    override fun encrypt(
        context: String,
        pk: String,
        sk: String?,
        attributeName: String,
        plaintextJson: String
    ): String = plaintextJson

    override fun decrypt(
        context: String,
        pk: String,
        sk: String?,
        attributeName: String,
        storedValue: String
    ): String {
        if (isEncrypted(storedValue)) {
            throw IllegalStateException(
                "Encountered encrypted $context.$attributeName but SENSITIVE_JSON_ENCRYPTION_ENABLED=false. " +
                    "Set ENVELOPE_KMS_KEY_ARN (and enable encryption) to read it."
            )
        }
        return storedValue
    }
}

private class KmsEnvelopeSensitiveJsonCrypto(
    private val kms: KmsClient,
    private val kmsKeyId: String
) : SensitiveJsonCrypto {
    override fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    override fun encrypt(
        context: String,
        pk: String,
        sk: String?,
        attributeName: String,
        plaintextJson: String
    ): String {
        if (plaintextJson.isEmpty()) return plaintextJson

        val aad = aad(context, pk, sk, attributeName)
        val dataKey = kms.generateDataKey(
            GenerateDataKeyRequest.builder()
                .keyId(kmsKeyId)
                .keySpec(DataKeySpec.AES_256)
                .build()
        )

        val dekPlain = dataKey.plaintext().asByteArray()
        val dekEncrypted = dataKey.ciphertextBlob().asByteArray()

        try {
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(dekPlain, "AES"),
                GCMParameterSpec(128, iv)
            )
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plaintextJson.toByteArray(StandardCharsets.UTF_8))

            return buildString(PREFIX.length + 16 + ciphertext.size + dekEncrypted.size) {
                append(PREFIX)
                append(b64Encoder.encodeToString(iv))
                append(DELIM)
                append(b64Encoder.encodeToString(ciphertext))
                append(DELIM)
                append(b64Encoder.encodeToString(dekEncrypted))
            }
        } finally {
            dekPlain.fill(0)
        }
    }

    override fun decrypt(
        context: String,
        pk: String,
        sk: String?,
        attributeName: String,
        storedValue: String
    ): String {
        if (!isEncrypted(storedValue)) return storedValue

        val parts = storedValue.substring(PREFIX.length).split(DELIM, limit = 3)
        require(parts.size == 3) { "invalid encrypted blob format (expected 3 parts)" }

        val iv = b64Decoder.decode(parts[0])
        val ciphertext = b64Decoder.decode(parts[1])
        val dekEncrypted = b64Decoder.decode(parts[2])

        val aad = aad(context, pk, sk, attributeName)
        val dekPlain = kms.decrypt(
            DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(dekEncrypted))
                .build()
        ).plaintext().asByteArray()

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(dekPlain, "AES"),
                GCMParameterSpec(128, iv)
            )
            cipher.updateAAD(aad)
            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, StandardCharsets.UTF_8)
        } finally {
            dekPlain.fill(0)
        }
    }

    private fun aad(context: String, pk: String, sk: String?, attributeName: String): ByteArray {
        val value = buildString {
            append("autonomo-control|")
            append(context)
            append('|')
            append(attributeName)
            append("|pk=")
            append(pk)
            append("|sk=")
            append(sk ?: "")
        }
        return value.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        const val PREFIX = "ENC1|"
        private const val DELIM = "|"
        private val b64Encoder = Base64.getEncoder()
        private val b64Decoder = Base64.getDecoder()
        private val secureRandom = SecureRandom()
    }
}
