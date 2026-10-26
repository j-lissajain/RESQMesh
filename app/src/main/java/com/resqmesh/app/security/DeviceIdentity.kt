package com.resqmesh.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.LinkedHashSet
import org.json.JSONArray

data class DeviceIdentitySnapshot(
    val fingerprint: String,
    val publicKey: ByteArray
)

interface DeviceIdentity {
    val snapshot: DeviceIdentitySnapshot
    fun sign(payload: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, payload: ByteArray, signature: ByteArray): Boolean
}

private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
private const val KEY_ALGORITHM = "EC"

class AndroidKeystoreDeviceIdentity(
    private val context: Context,
    private val alias: String = "resqmesh-device-signing"
) : DeviceIdentity {
    private val keyPair: KeyPair
        get() = loadOrCreateKeyPair()

    override val snapshot: DeviceIdentitySnapshot
        get() {
            val publicKey = keyPair.public.encoded
            return DeviceIdentitySnapshot(fingerprint(publicKey), publicKey)
        }

    override fun sign(payload: ByteArray): ByteArray {
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(keyPair.private)
        signer.update(payload)
        return signer.sign()
    }

    override fun verify(publicKey: ByteArray, payload: ByteArray, signature: ByteArray): Boolean = runCatching {
        val key = KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(publicKey))
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(key)
        verifier.update(payload)
        verifier.verify(signature)
    }.getOrDefault(false)

    private fun loadOrCreateKeyPair(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = (store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.let {
            KeyPair(it.certificate.publicKey, it.privateKey)
        }
        if (existing != null) return existing

        return try {
            KeyPairGenerator.getInstance(KEY_ALGORITHM, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
            }.generateKeyPair()
        } catch (firstFailure: Exception) {
            runCatching { store.deleteEntry(alias) }
            KeyPairGenerator.getInstance(KEY_ALGORITHM, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
            }.generateKeyPair()
        }
    }

    companion object {
        fun fingerprint(publicKey: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(publicKey)
                .joinToString("") { "%02x".format(it) }
    }
}

/** A JVM-testable identity implementation. Production code uses Android Keystore. */
class SoftwareDeviceIdentity(private val pair: KeyPair) : DeviceIdentity {
    override val snapshot: DeviceIdentitySnapshot = DeviceIdentitySnapshot(
        AndroidKeystoreDeviceIdentity.fingerprint(pair.public.encoded),
        pair.public.encoded
    )

    override fun sign(payload: ByteArray): ByteArray = Signature.getInstance(SIGNATURE_ALGORITHM).run {
        initSign(pair.private)
        update(payload)
        sign()
    }

    override fun verify(publicKey: ByteArray, payload: ByteArray, signature: ByteArray): Boolean = runCatching {
        val key = KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(publicKey))
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(key)
            update(payload)
            verify(signature)
        }
    }.getOrDefault(false)

    companion object {
        fun generate(): SoftwareDeviceIdentity = SoftwareDeviceIdentity(
            KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
        )
    }
}

enum class MeshTrustState {
    LOCAL,
    AUTHENTICATED_KNOWN,
    AUTHENTICATED_UNKNOWN,
    LEGACY_UNSIGNED,
    INVALID
}

data class VerifiedMeshPayload(
    val payload: ByteArray,
    val fingerprint: String?,
    val trustState: MeshTrustState,
    val issuedAtMillis: Long? = null,
    val expiresAtMillis: Long? = null
)

/** Bounded process-lifetime replay guard for authenticated envelopes. */
class MeshReplayGuard(private val maxEntries: Int = 512) {
    private val seen = LinkedHashSet<String>()

    @Synchronized
    fun accept(payload: VerifiedMeshPayload): Boolean {
        val issuedAt = payload.issuedAtMillis ?: return true
        val fingerprint = payload.fingerprint ?: return true
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.payload)
            .joinToString("") { "%02x".format(it) }
        val key = "$fingerprint:$issuedAt:$digest"
        if (!seen.add(key)) return false
        while (seen.size > maxEntries) seen.remove(seen.first())
        return true
    }
}

object SecureMeshEnvelopeCodec {
    private const val DISCRIMINATOR = "RESQSecureMesh"
    private const val VERSION = 1
    private const val MAX_BYTES = 1_024
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(payload: ByteArray, identity: DeviceIdentity, nowMillis: Long, ttlMillis: Long): ByteArray {
        require(payload.isNotEmpty()) { "Cannot secure an empty payload" }
        val publicKey = encoder.encodeToString(identity.snapshot.publicKey)
        val fingerprint = identity.snapshot.fingerprint
        val payloadText = encoder.encodeToString(payload)
        val expiresAt = nowMillis + ttlMillis
        val canonical = canonical(fingerprint, publicKey, nowMillis, expiresAt, payloadText)
        val signature = encoder.encodeToString(identity.sign(canonical))
        return listOf(
            DISCRIMINATOR,
            VERSION.toString(),
            fingerprint,
            publicKey,
            nowMillis.toString(),
            expiresAt.toString(),
            payloadText,
            signature
        ).joinToString("|").toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "Secure mesh envelope is too large" }
        }
    }

    fun decode(
        encoded: ByteArray,
        verifier: DeviceIdentity,
        nowMillis: Long,
        legacyPayload: ByteArray? = null,
        isKnownFingerprint: (String) -> Boolean = { false }
    ): VerifiedMeshPayload? {
        if (encoded.size > MAX_BYTES) return null
        val fields = runCatching { String(encoded, Charsets.UTF_8).split('|') }.getOrNull()
            ?: return null
        if (fields.firstOrNull() != DISCRIMINATOR) {
            return legacyPayload?.let {
                VerifiedMeshPayload(it, null, MeshTrustState.LEGACY_UNSIGNED)
            }
        }
        if (fields.size != 8 || fields[1].toIntOrNull() != VERSION) return null
        val fingerprint = fields[2]
        val publicKey = runCatching { decoder.decode(fields[3]) }.getOrNull() ?: return null
        val issuedAt = fields[4].toLongOrNull() ?: return null
        val expiresAt = fields[5].toLongOrNull() ?: return null
        val payload = runCatching { decoder.decode(fields[6]) }.getOrNull() ?: return null
        val signature = runCatching { decoder.decode(fields[7]) }.getOrNull() ?: return null
        if (expiresAt <= nowMillis || issuedAt > nowMillis + 5 * 60_000L) return null
        if (AndroidKeystoreDeviceIdentity.fingerprint(publicKey) != fingerprint) return null
        val canonical = canonical(fingerprint, fields[3], issuedAt, expiresAt, fields[6])
        if (!verifier.verify(publicKey, canonical, signature)) return null
        return VerifiedMeshPayload(
            payload,
            fingerprint,
            if (isKnownFingerprint(fingerprint)) MeshTrustState.AUTHENTICATED_KNOWN else MeshTrustState.AUTHENTICATED_UNKNOWN,
            issuedAt,
            expiresAt
        )
    }

    private fun canonical(
        fingerprint: String,
        publicKey: String,
        issuedAt: Long,
        expiresAt: Long,
        payload: String
    ): ByteArray = listOf(DISCRIMINATOR, VERSION, fingerprint, publicKey, issuedAt, expiresAt, payload)
        .joinToString("|")
        .toByteArray(Charsets.UTF_8)
}

class PeerTrustStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("resqmesh_peer_trust", Context.MODE_PRIVATE)
    private val lock = Any()

    fun isKnown(fingerprint: String): Boolean = synchronized(lock) {
        val peers = runCatching { JSONArray(preferences.getString("fingerprints", "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        (0 until peers.length()).any { peers.optString(it) == fingerprint }
    }

    fun trust(fingerprint: String) = synchronized(lock) {
        if (fingerprint.isBlank() || isKnown(fingerprint)) return@synchronized
        val peers = runCatching { JSONArray(preferences.getString("fingerprints", "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        peers.put(fingerprint)
        while (peers.length() > 100) peers.remove(0)
        preferences.edit().putString("fingerprints", peers.toString()).commit()
    }

    fun forget(fingerprint: String) = synchronized(lock) {
        val peers = runCatching { JSONArray(preferences.getString("fingerprints", "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        val kept = JSONArray()
        for (i in 0 until peers.length()) if (peers.optString(i) != fingerprint) kept.put(peers.optString(i))
        preferences.edit().putString("fingerprints", kept.toString()).commit()
    }
}
