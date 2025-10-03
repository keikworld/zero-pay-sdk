package com.zeropay.sdk

/**
 * Enumeration of all available authentication factors.
 * 
 * Updated to include:
 * - WORDS: 4-word selection authentication
 * - IMAGE_TAP: Tap 2 locations on an image (GDPR-compliant)
 */
enum class Factor {
    /** Pattern authentication with micro-timing analysis */
    PATTERN_MICRO,
    
    /** Pattern authentication with normalized timing */
    PATTERN_NORMAL,
    
    /** Mouse drawing biometrics */
    MOUSE_DRAW,
    
    /** Stylus drawing biometrics with pressure */
    STYLUS_DRAW,
    
    /** Colour sequence selection */
    COLOUR,
    
    /** Emoji sequence selection */
    EMOJI,
    
    /** 4-word selection authentication (new) */
    WORDS,
    
    /** Image tap authentication - tap 2 locations (new, GDPR-compliant) */
    IMAGE_TAP,
    
    /** PIN code authentication */
    PIN,
    
    /** NFC tag authentication */
    NFC,
    
    /** Balance/accelerometer authentication */
    BALANCE,
    
    /** Face recognition authentication */
    FACE,
    
    /** Voice authentication */
    VOICE
}
