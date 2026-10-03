package com.sridhar.harbor.remote

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sealing secrets from a phone to a TV over the plain LAN remote socket. The TV's public key travels only inside
 * its on-screen QR code (seen by the phone's camera, never sent over the network), so a Wi-Fi eavesdropper can
 * neither read the sealed message nor swap the key. ECDH P-256 → SHA-256 → AES-256-GCM.
 */
object SetupCrypto {
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()
    private const val INFO = "jellyverse-tv-setup-v1"

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    fun encodePublic(k: PublicKey): String = b64.encodeToString(k.encoded)
    fun decodePublic(s: String): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64.decode(s)))

    private fun aesKey(priv: PrivateKey, pub: PublicKey): SecretKeySpec {
        val shared = KeyAgreement.getInstance("ECDH").apply { init(priv); doPhase(pub, true) }.generateSecret()
        return SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(shared + INFO.toByteArray()), "AES")
    }

    /** Phone side: (ephemeral public key, iv, ciphertext), all base64url. */
    data class Sealed(val epk: String, val iv: String, val ct: String)

    fun seal(tvPublic: PublicKey, plain: String): Sealed {
        val eph = newKeyPair()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, aesKey(eph.private, tvPublic), GCMParameterSpec(128, iv)) }
        c.updateAAD(INFO.toByteArray())
        return Sealed(encodePublic(eph.public), b64.encodeToString(iv), b64.encodeToString(c.doFinal(plain.toByteArray())))
    }

    /** TV side: null if it wasn't sealed for [tvPrivate] (wrong key, tampered or garbage). */
    fun open(tvPrivate: PrivateKey, s: Sealed): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, aesKey(tvPrivate, decodePublic(s.epk)), GCMParameterSpec(128, unb64.decode(s.iv)))
        }
        c.updateAAD(INFO.toByteArray())
        String(c.doFinal(unb64.decode(s.ct)))
    }.getOrNull()
}
