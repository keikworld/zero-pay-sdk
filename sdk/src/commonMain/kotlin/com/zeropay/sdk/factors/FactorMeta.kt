package com.zeropay.sdk.factors

/**
 * Metadata about factor modes.
 * MICRO mode includes precise timing data.
 * NORMAL mode uses normalized timing data.
 */
enum class FactorMode {
    /** Micro-timing mode with precise temporal data */
    MICRO,
    
    /** Normal mode with normalized temporal data */
    NORMAL
}
