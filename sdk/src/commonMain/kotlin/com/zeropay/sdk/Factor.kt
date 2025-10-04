package com.zeropay.sdk

/**
 * Authentication Factor Types
 * 
 * PSD3 SCA Categories:
 * - Knowledge: Something you know (PIN, Password, Words)
 * - Possession: Something you have (Device, Token)
 * - Inherence: Something you are (Biometrics, Behavioral)
 */
enum class Factor(val category: Category) {
    // Knowledge factors
    PIN(Category.KNOWLEDGE),
    COLOUR(Category.KNOWLEDGE),
    EMOJI(Category.KNOWLEDGE),
    WORDS(Category.KNOWLEDGE),
    
    // Behavioral factors (inherence)
    PATTERN(Category.INHERENCE),
    MOUSE(Category.INHERENCE),
    STYLUS(Category.INHERENCE),
    VOICE(Category.INHERENCE),
    IMAGE_TAP(Category.INHERENCE),
    
    // Biometric factors (inherence) - Week 3+
    FINGERPRINT(Category.INHERENCE),
    FACE(Category.INHERENCE);
    
    enum class Category {
        KNOWLEDGE,   // Something you know
        POSSESSION,  // Something you have
        INHERENCE    // Something you are
    }
}
