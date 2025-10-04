package com.zeropay.sdk.crypto

import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.state.*
import org.signal.libsignal.protocol.util.KeyHelper

/**
 * Signal Protocol Implementation for E2E Encryption
 * 
 * Provides:
 * - Perfect forward secrecy (compromised keys don't affect past messages)
 * - Future secrecy (post-compromise security)
 * - Deniable authentication
 * - Asynchronous communication
 * 
 * Based on Double Ratchet Algorithm (Signal Protocol)
 */
class SignalProtocolManager(
    private val localAddress: SignalProtocolAddress,
    private val sessionStore: SessionStore,
    private val preKeyStore: PreKeyStore,
    private val signedPreKeyStore: SignedPreKeyStore,
    private val identityKeyStore: IdentityKeyStore
) {
    
    /**
     * Initialize Signal Protocol session with remote party
     */
    fun initializeSession(
        remoteAddress: SignalProtocolAddress,
        preKeyBundle: PreKeyBundle
    ) {
        val sessionBuilder = SessionBuilder(
            sessionStore,
            preKeyStore,
            signedPreKeyStore,
            identityKeyStore,
            remoteAddress
        )
        
        sessionBuilder.process(preKeyBundle)
    }
    
    /**
     * Encrypt message using Signal Protocol
     * 
     * @param recipientAddress Recipient's Signal address
     * @param message Plaintext message
     * @return Encrypted ciphertext
     */
    fun encrypt(recipientAddress: SignalProtocolAddress, message: ByteArray): CiphertextMessage {
        val sessionCipher = SessionCipher(
            sessionStore,
            preKeyStore,
            signedPreKeyStore,
            identityKeyStore,
            recipientAddress
        )
        
        return sessionCipher.encrypt(message)
    }
    
    /**
     * Decrypt message using Signal Protocol
     * 
     * @param senderAddress Sender's Signal address
     * @param ciphertext Encrypted message
     * @return Decrypted plaintext
     */
    fun decrypt(senderAddress: SignalProtocolAddress, ciphertext: CiphertextMessage): ByteArray {
        val sessionCipher = SessionCipher(
            sessionStore,
            preKeyStore,
            signedPreKeyStore,
            identityKeyStore,
            senderAddress
        )
        
        return when (ciphertext.type) {
            CiphertextMessage.PREKEY_TYPE -> {
                val preKeyMessage = PreKeySignalMessage(ciphertext.serialize())
                sessionCipher.decrypt(preKeyMessage)
            }
            CiphertextMessage.WHISPER_TYPE -> {
                val signalMessage = SignalMessage(ciphertext.serialize())
                sessionCipher.decrypt(signalMessage)
            }
            else -> throw IllegalArgumentException("Unknown message type: ${ciphertext.type}")
        }
    }
    
    /**
     * Check if session exists with remote party
     */
    fun hasSession(remoteAddress: SignalProtocolAddress): Boolean {
        return sessionStore.containsSession(remoteAddress)
    }
    
    /**
     * Delete session with remote party
     */
    fun deleteSession(remoteAddress: SignalProtocolAddress) {
        sessionStore.deleteSession(remoteAddress)
    }
    
    /**
     * Generate pre-keys for registration
     * 
     * @param start Starting key ID
     * @param count Number of keys to generate
     * @return List of pre-key records
     */
    fun generatePreKeys(start: Int, count: Int): List<PreKeyRecord> {
        return KeyHelper.generatePreKeys(start, count)
    }
    
    /**
     * Generate signed pre-key
     * 
     * @param identityKeyPair User's identity key pair
     * @param signedPreKeyId Signed pre-key ID
     * @return Signed pre-key record
     */
    fun generateSignedPreKey(
        identityKeyPair: IdentityKeyPair,
        signedPreKeyId: Int
    ): SignedPreKeyRecord {
        return KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId)
    }
    
    /**
     * Store pre-keys in key store
     */
    fun storePreKeys(preKeys: List<PreKeyRecord>) {
        preKeys.forEach { preKey ->
            preKeyStore.storePreKey(preKey.id, preKey)
        }
    }
    
    /**
     * Store signed pre-key in key store
     */
    fun storeSignedPreKey(signedPreKey: SignedPreKeyRecord) {
        signedPreKeyStore.storeSignedPreKey(signedPreKey.id, signedPreKey)
    }
    
    companion object {
        /**
         * Generate identity key pair for new user
         */
        fun generateIdentityKeyPair(): IdentityKeyPair {
            return KeyHelper.generateIdentityKeyPair()
        }
        
        /**
         * Generate registration ID
         */
        fun generateRegistrationId(): Int {
            return KeyHelper.generateRegistrationId(false)
        }
        
        /**
         * Create PreKeyBundle for initial session establishment
         */
        fun createPreKeyBundle(
            registrationId: Int,
            deviceId: Int,
            preKeyId: Int,
            preKeyPublic: ECPublicKey,
            signedPreKeyId: Int,
            signedPreKeyPublic: ECPublicKey,
            signedPreKeySignature: ByteArray,
            identityKey: IdentityKey
        ): PreKeyBundle {
            return PreKeyBundle(
                registrationId,
                deviceId,
                preKeyId,
                preKeyPublic,
                signedPreKeyId,
                signedPreKeyPublic,
                signedPreKeySignature,
                identityKey
            )
        }
    }
}

/**
 * Simple in-memory stores for development
 * In production, use persistent storage (SQLite, etc.)
 */
class InMemorySessionStore : SessionStore {
    private val sessions = mutableMapOf<SignalProtocolAddress, ByteArray>()
    
    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        return sessions[address]?.let { SessionRecord(it) } ?: SessionRecord()
    }
    
    override fun getSubDeviceSessions(name: String): List<Int> {
        return sessions.keys
            .filter { it.name == name }
            .map { it.deviceId }
    }
    
    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessions[address] = record.serialize()
    }
    
    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return sessions.containsKey(address)
    }
    
    override fun deleteSession(address: SignalProtocolAddress) {
        sessions.remove(address)
    }
    
    override fun deleteAllSessions(name: String) {
        sessions.keys.removeAll { it.name == name }
    }
}

class InMemoryPreKeyStore : PreKeyStore {
    private val preKeys = mutableMapOf<Int, ByteArray>()
    
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return preKeys[preKeyId]?.let { PreKeyRecord(it) }
            ?: throw InvalidKeyIdException("No such prekey: $preKeyId")
    }
    
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys[preKeyId] = record.serialize()
    }
    
    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeys.containsKey(preKeyId)
    }
    
    override fun removePreKey(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }
}

class InMemorySignedPreKeyStore : SignedPreKeyStore {
    private val signedPreKeys = mutableMapOf<Int, ByteArray>()
    
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        return signedPreKeys[signedPreKeyId]?.let { SignedPreKeyRecord(it) }
            ?: throw InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
    }
    
    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return signedPreKeys.values.map { SignedPreKeyRecord(it) }
    }
    
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[signedPreKeyId] = record.serialize()
    }
    
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeys.containsKey(signedPreKeyId)
    }
    
    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
    }
}

class InMemoryIdentityKeyStore(
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int
) : IdentityKeyStore {
    private val trustedKeys = mutableMapOf<SignalProtocolAddress, IdentityKey>()
    
    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair
    }
    
    override fun getLocalRegistrationId(): Int {
        return registrationId
    }
    
    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existing = trustedKeys[address]
        trustedKeys[address] = identityKey
        return existing == null || existing != identityKey
    }
    
    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val trusted = trustedKeys[address]
        return trusted == null || trusted == identityKey
    }
    
    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return trustedKeys[address]
    }
}
