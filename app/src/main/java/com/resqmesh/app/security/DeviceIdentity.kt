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
    const val DISCRIMINATOR = "RESQSecureMesh"
    const val VERSION = 1
    const val MAX_PAYLOAD_BYTES = 2_048
    const val MAX_ENVELOPE_OVERHEAD_BYTES = 512
    const val MAX_BYTES = 4_096
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun maxEncodedSizeForPayload(payloadSize: Int): Int {
        require(payloadSize >= 0) { "Payload size cannot be negative" }
        val base64Bytes = ((payloadSize + 2) / 3) * 4
        return base64Bytes + MAX_ENVELOPE_OVERHEAD_BYTES
    }

    fun encode(payload: ByteArray, identity: DeviceIdentity, nowMillis: Long, ttlMillis: Long): ByteArray {
        require(payload.isNotEmpty()) { "Cannot secure an empty payload" }
        require(payload.size <= MAX_PAYLOAD_BYTES) {
            "Payload exceeds maximum secure envelope payload size ($MAX_PAYLOAD_BYTES bytes)"
        }
        require(ttlMillis > 0L) { "Secure envelope TTL must be positive" }
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
        trustStore: PeerTrustStore,
        legacyPayload: ByteArray? = null
    ): VerifiedMeshPayload? = decode(
        encoded = encoded,
        verifier = verifier,
        nowMillis = nowMillis,
        legacyPayload = legacyPayload,
        isKnownFingerprint = trustStore::isKnown
    )

    fun decode(
        encoded: ByteArray,
        verifier: DeviceIdentity,
        nowMillis: Long,
        legacyPayload: ByteArray? = null,
        isKnownFingerprint: (String) -> Boolean = { false }
    ): VerifiedMeshPayload? {
        if (encoded.isEmpty() || encoded.size > MAX_BYTES) return null
        val fields = runCatching { String(encoded, Charsets.UTF_8).split('|') }.getOrNull()
            ?: return null
        if (fields.firstOrNull() != DISCRIMINATOR) {
            return legacyPayload?.takeIf { it.isNotEmpty() && it.size <= MAX_PAYLOAD_BYTES }?.let {
                VerifiedMeshPayload(it, null, MeshTrustState.LEGACY_UNSIGNED)
            }
        }
        if (fields.size != 8 || fields[1].toIntOrNull() != VERSION) return null
        val fingerprint = fields[2].trim().lowercase()
        if (!PeerTrustStore.isValidFingerprint(fingerprint)) return null
        val publicKey = runCatching { decoder.decode(fields[3]) }.getOrNull() ?: return null
        val issuedAt = fields[4].toLongOrNull() ?: return null
        val expiresAt = fields[5].toLongOrNull() ?: return null
        val payload = runCatching { decoder.decode(fields[6]) }.getOrNull() ?: return null
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD_BYTES) return null
        val signature = runCatching { decoder.decode(fields[7]) }.getOrNull() ?: return null
        if (expiresAt <= nowMillis || issuedAt > nowMillis + 5 * 60_000L || expiresAt <= issuedAt) return null
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

class PeerTrustStore private constructor(
    private val readRaw: () -> String,
    private val writeRaw: (String) -> Unit
) {
    constructor(context: Context) : this(
        readRaw = {
            context.applicationContext
                .getSharedPreferences("resqmesh_peer_trust", Context.MODE_PRIVATE)
                .getString("fingerprints", "[]") ?: "[]"
        },
        writeRaw = { value ->
            context.applicationContext
                .getSharedPreferences("resqmesh_peer_trust", Context.MODE_PRIVATE)
                .edit()
                .putString("fingerprints", value)
                .commit()
        }
    )

    private val lock = Any()

    fun isKnown(fingerprint: String): Boolean = synchronized(lock) {
        val normalized = normalizeFingerprint(fingerprint) ?: return@synchronized false
        trustedFingerprintsLocked().contains(normalized)
    }

    fun trustedFingerprints(): List<String> = synchronized(lock) {
        trustedFingerprintsLocked()
    }

    fun trust(fingerprint: String) = synchronized(lock) {
        val normalized = normalizeFingerprint(fingerprint) ?: return@synchronized
        val current = trustedFingerprintsLocked().toMutableList()
        if (normalized in current) return@synchronized
        current += normalized
        while (current.size > MAX_TRUSTED_PEERS) current.removeAt(0)
        writeRaw(JSONArray(current).toString())
    }

    fun forget(fingerprint: String) = synchronized(lock) {
        val normalized = normalizeFingerprint(fingerprint) ?: return@synchronized
        val kept = trustedFingerprintsLocked().filterNot { it == normalized }
        writeRaw(JSONArray(kept).toString())
    }

    private fun trustedFingerprintsLocked(): List<String> {
        val peers = runCatching { JSONArray(readRaw()) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until peers.length()) {
                normalizeFingerprint(peers.optString(i))?.let { fp ->
                    if (fp !in this) add(fp)
                }
            }
        }
    }

    companion object {
        private const val MAX_TRUSTED_PEERS = 100
        private const val SHA256_HEX_LENGTH = 64

        fun inMemory(): PeerTrustStore {
            var raw = "[]"
            return PeerTrustStore(
                readRaw = { raw },
                writeRaw = { raw = it }
            )
        }

        fun isValidFingerprint(fingerprint: String): Boolean {
            val trimmed = fingerprint.trim().lowercase()
            return trimmed.length == SHA256_HEX_LENGTH &&
                trimmed.all { it in '0'..'9' || it in 'a'..'f' }
        }

        fun shortFingerprint(fingerprint: String?): String {
            val normalized = fingerprint?.let(::normalizeFingerprint)
                ?: return fingerprint?.trim()?.take(12).orEmpty()
            return normalized.take(16).chunked(4).joinToString(":")
        }

        private fun normalizeFingerprint(fingerprint: String): String? =
            fingerprint.trim().lowercase().takeIf(::isValidFingerprint)
    }
}
