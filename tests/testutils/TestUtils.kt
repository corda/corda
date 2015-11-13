package testutils

import java.security.KeyPairGenerator

object TestUtils {
    val keypair = KeyPairGenerator.getInstance("EC").genKeyPair()
    val keypair2 = KeyPairGenerator.getInstance("EC").genKeyPair()
}