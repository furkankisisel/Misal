package com.example.misal.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.PSource

data class EncryptedPayload(
    val encryptedText: String,
    val aesKeyForSender: String,
    val aesKeyForRecipient: String,
    val iv: String
)

data class EncryptedMediaPayload(
    val encryptedBytes: ByteArray,
    val aesKeyForSender: String,
    val aesKeyForRecipient: String,
    val iv: String
)

data class GroupEncryptedPayload(
    val encryptedText: String,
    val encryptedAesKeys: Map<String, String>,
    val iv: String
)

data class GroupEncryptedMediaPayload(
    val encryptedBytes: ByteArray,
    val encryptedAesKeys: Map<String, String>,
    val iv: String
)

class CryptoManager {

    companion object {
        private const val KEY_ALIAS = "misal_e2e_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    init {
        ensureKeyIsValid()
    }

    private fun ensureKeyIsValid() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
            return
        }

        // Katı kural: Eğer mevcut anahtar OAEP desteklemiyorsa, ACIMASIZCA SİL!
        try {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            if (privateKey != null) {
                val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
                val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
                val paddings = keyInfo.encryptionPaddings
                val digests = keyInfo.digests
                
                if (!paddings.contains(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP) || !digests.contains(KeyProperties.DIGEST_SHA1)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                    generateKeyPair()
                }
            } else {
                keyStore.deleteEntry(KEY_ALIAS)
                generateKeyPair()
            }
        } catch (e: Exception) {
            // Bir hata olursa güvenliği şansa bırakma, silip baştan üret.
            keyStore.deleteEntry(KEY_ALIAS)
            generateKeyPair()
        }
    }

    private fun generateKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512, KeyProperties.DIGEST_SHA1)
            .setKeySize(2048)
            .build()

        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()
    }
    
    fun generateKeyPairIfNeeded() {
        ensureKeyIsValid()
    }

    fun getPublicKeyBase64(): String? {
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey ?: return null
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun encryptMessage(message: String, recipientPublicKeyBase64: String): EncryptedPayload {
        // 1. Generate 256-bit AES Key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        // 2. Encrypt message with AES/GCM
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = aesCipher.iv // Rastgele 12-byte IV üretildi
        val encryptedMessageBytes = aesCipher.doFinal(message.toByteArray(Charsets.UTF_8))

        val encryptedTextBase64 = Base64.encodeToString(encryptedMessageBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        // 3. Encrypt AES Key with Recipient's RSA Public Key
        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        
        val rsaCipher1 = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val keyFactory = KeyFactory.getInstance("RSA")
        val recipientKeyBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
        val recipientPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientKeyBytes))
        
        rsaCipher1.init(Cipher.ENCRYPT_MODE, recipientPublicKey, oaepSpec)
        val aesKeyForRecipientBytes = rsaCipher1.doFinal(secretKey.encoded)
        val aesKeyForRecipientBase64 = Base64.encodeToString(aesKeyForRecipientBytes, Base64.NO_WRAP)

        // 4. Encrypt AES Key with Sender's RSA Public Key (To read own messages)
        val rsaCipher2 = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val senderPublicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("Sender public key not found")
        rsaCipher2.init(Cipher.ENCRYPT_MODE, senderPublicKey, oaepSpec)
        val aesKeyForSenderBytes = rsaCipher2.doFinal(secretKey.encoded)
        val aesKeyForSenderBase64 = Base64.encodeToString(aesKeyForSenderBytes, Base64.NO_WRAP)

        return EncryptedPayload(
            encryptedText = encryptedTextBase64,
            aesKeyForSender = aesKeyForSenderBase64,
            aesKeyForRecipient = aesKeyForRecipientBase64,
            iv = ivBase64
        )
    }

    fun encryptMessage(message: String, encryptedAesKeyBase64: String, ivBase64: String): String {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Private key not found")

        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
        
        val encryptedAesKeyBytes = Base64.decode(encryptedAesKeyBase64, Base64.NO_WRAP)
        val secretKeyBytes = rsaCipher.doFinal(encryptedAesKeyBytes)
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, ivBytes)
        
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encryptedMessageBytes = aesCipher.doFinal(message.toByteArray(Charsets.UTF_8))
        
        return Base64.encodeToString(encryptedMessageBytes, Base64.NO_WRAP)
    }

    fun encryptMessageForGroup(message: String, publicKeysMap: Map<String, String>): GroupEncryptedPayload {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = aesCipher.iv
        val encryptedMessageBytes = aesCipher.doFinal(message.toByteArray(Charsets.UTF_8))

        val encryptedTextBase64 = Base64.encodeToString(encryptedMessageBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        
        val encryptedAesKeys = mutableMapOf<String, String>()
        
        for ((userId, publicKeyBase64) in publicKeysMap) {
            try {
                val recipientKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
                val recipientPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientKeyBytes))
                val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
                rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey, oaepSpec)
                val encryptedAes = rsaCipher.doFinal(secretKey.encoded)
                encryptedAesKeys[userId] = Base64.encodeToString(encryptedAes, Base64.NO_WRAP)
            } catch (e: Exception) {
                e.printStackTrace()
                // Bu kullanıcı için şifreleme başarısız olsa bile diğerlerine devam edebiliriz
            }
        }

        return GroupEncryptedPayload(encryptedTextBase64, encryptedAesKeys, ivBase64)
    }

    fun decryptMessage(encryptedTextBase64: String, encryptedAesKeyBase64: String, ivBase64: String): String {
        // 1. Decrypt AES Key using Sender's Private RSA Key
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Private key not found")

        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
        
        val encryptedAesKeyBytes = Base64.decode(encryptedAesKeyBase64, Base64.NO_WRAP)
        val secretKeyBytes = rsaCipher.doFinal(encryptedAesKeyBytes)
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        // 2. Decrypt message using the decrypted AES Key
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, ivBytes) // 128-bit authentication tag length
        
        aesCipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val encryptedTextBytes = Base64.decode(encryptedTextBase64, Base64.NO_WRAP)
        val decryptedMessageBytes = aesCipher.doFinal(encryptedTextBytes)
        
        return String(decryptedMessageBytes, Charsets.UTF_8)
    }

    fun encryptMedia(bytes: ByteArray, recipientPublicKeyBase64: String): EncryptedMediaPayload {
        // 1. Generate 256-bit AES Key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        // 2. Encrypt bytes with AES/GCM
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = aesCipher.iv // Rastgele 12-byte IV üretildi
        val encryptedBytes = aesCipher.doFinal(bytes)

        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        // 3. Encrypt AES Key with Recipient's RSA Public Key
        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        
        val rsaCipher1 = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val keyFactory = KeyFactory.getInstance("RSA")
        val recipientKeyBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
        val recipientPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientKeyBytes))
        
        rsaCipher1.init(Cipher.ENCRYPT_MODE, recipientPublicKey, oaepSpec)
        val aesKeyForRecipientBytes = rsaCipher1.doFinal(secretKey.encoded)
        val aesKeyForRecipientBase64 = Base64.encodeToString(aesKeyForRecipientBytes, Base64.NO_WRAP)

        // 4. Encrypt AES Key with Sender's RSA Public Key
        val rsaCipher2 = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val senderPublicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("Sender public key not found")
        rsaCipher2.init(Cipher.ENCRYPT_MODE, senderPublicKey, oaepSpec)
        val aesKeyForSenderBytes = rsaCipher2.doFinal(secretKey.encoded)
        val aesKeyForSenderBase64 = Base64.encodeToString(aesKeyForSenderBytes, Base64.NO_WRAP)

        return EncryptedMediaPayload(
            encryptedBytes = encryptedBytes,
            aesKeyForSender = aesKeyForSenderBase64,
            aesKeyForRecipient = aesKeyForRecipientBase64,
            iv = ivBase64
        )
    }

    fun encryptMediaForGroup(mediaBytes: ByteArray, publicKeysMap: Map<String, String>): GroupEncryptedMediaPayload {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = aesCipher.iv
        val encryptedMessageBytes = aesCipher.doFinal(mediaBytes)

        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        
        val encryptedAesKeys = mutableMapOf<String, String>()
        
        for ((userId, publicKeyBase64) in publicKeysMap) {
            try {
                val recipientKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
                val recipientPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientKeyBytes))
                val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
                rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey, oaepSpec)
                val encryptedAes = rsaCipher.doFinal(secretKey.encoded)
                encryptedAesKeys[userId] = Base64.encodeToString(encryptedAes, Base64.NO_WRAP)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return GroupEncryptedMediaPayload(encryptedMessageBytes, encryptedAesKeys, ivBase64)
    }

    fun decryptMedia(encryptedBytes: ByteArray, encryptedAesKeyBase64: String, ivBase64: String): ByteArray {
        // 1. Decrypt AES Key using Sender's Private RSA Key
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Private key not found")

        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
        
        val encryptedAesKeyBytes = Base64.decode(encryptedAesKeyBase64, Base64.NO_WRAP)
        val secretKeyBytes = rsaCipher.doFinal(encryptedAesKeyBytes)
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        // 2. Decrypt message using the decrypted AES Key
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, ivBytes)
        
        aesCipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return aesCipher.doFinal(encryptedBytes)
    }
}
