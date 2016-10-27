package com.r3corda.explorer.model

import com.r3corda.core.crypto.Party
import javafx.beans.property.SimpleObjectProperty

class IdentityModel {
    val myIdentity = SimpleObjectProperty<Party?>()
    val notary = SimpleObjectProperty<Party?>()
}