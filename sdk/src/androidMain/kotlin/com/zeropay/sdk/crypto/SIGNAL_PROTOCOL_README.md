# Signal Protocol Implementation - DISABLED

## Status: Not Yet Integrated

The `SignalProtocol.kt` file has been disabled (renamed to `.kt.disabled`) because:

1. **Missing Dependency**: Requires `org.signal:libsignal-android` library
2. **Not Used**: No other part of the codebase references SignalProtocol
3. **Future Feature**: E2E encryption using Signal Protocol is planned but not yet integrated

## Purpose

This file implements the Signal Protocol (Double Ratchet Algorithm) for:
- Perfect forward secrecy
- Future secrecy (post-compromise security)
- Deniable authentication
- Asynchronous communication

## To Enable

1. Add dependency to `sdk/build.gradle.kts`:
   ```kotlin
   implementation("org.signal:libsignal-android:0.x.x")
   ```

2. Rename file back to `.kt`:
   ```bash
   mv SignalProtocol.kt.disabled SignalProtocol.kt
   ```

3. Integrate with enrollment/verification flows

4. Update API to support Signal Protocol message format

## Alternative

The current ZeroPay implementation already uses:
- Double encryption (PBKDF2 + KMS)
- Zero-knowledge proofs
- TLS 1.3 for transport security

Signal Protocol would add an additional layer of E2E encryption if needed in the future.
