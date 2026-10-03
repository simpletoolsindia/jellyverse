package com.sridhar.harbor.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SetupCryptoTest {
    @Test fun roundTripsOnlyForTheRightTv() {
        val tv = SetupCrypto.newKeyPair()
        val pub = SetupCrypto.decodePublic(SetupCrypto.encodePublic(tv.public))
        val sealed = SetupCrypto.seal(pub, """{"user":"demo"}""")
        assertThat(SetupCrypto.open(tv.private, sealed)).isEqualTo("""{"user":"demo"}""")
        assertThat(SetupCrypto.open(SetupCrypto.newKeyPair().private, sealed)).isNull()
        val tampered = sealed.copy(ct = sealed.ct.dropLast(2) + if (sealed.ct.endsWith("AA")) "BB" else "AA")
        assertThat(SetupCrypto.open(tv.private, tampered)).isNull()
    }
}
