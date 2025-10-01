package com.zeropay.sdk

/**
 * Enumeration of all available authentication factors.
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
