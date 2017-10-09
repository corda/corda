package net.corda.signing.authentication

/*
 * Supported authentication modes
 */
enum class AuthMode {
    PASSWORD, CARD_READER, KEY_FILE
}