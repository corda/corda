package com.r3.corda.networkmanage.common.persistence.entity

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.network.ParametersUpdate
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "parameters_update")
data class ParametersUpdateEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        // TODO use @MapsId to get rid of additional sequence
        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "network_parameters", foreignKey = ForeignKey(name = "FK__param_up__net_param"))
        val networkParameters: NetworkParametersEntity,

        @Column(name = "description", nullable = false)
        val description: String,

        @Column(name = "update_deadline", nullable = false)
        val updateDeadline: Instant,

        @Column(name = "status", length = 16, nullable = false, columnDefinition = "NVARCHAR(16)")
        @Enumerated(EnumType.STRING)
        val status: UpdateStatus = UpdateStatus.NEW
) : Serializable {
    fun toParametersUpdate(): ParametersUpdate {
        return ParametersUpdate(SecureHash.parse(networkParameters.hash), description, updateDeadline)
    }
}

enum class UpdateStatus {
    /** A newly created update. */
    NEW,
    /**
     * An update that has passed its deadline and flagged to be made active on the next signing event. At most only one
     * update with status either NEW or FLAG_DAY can exist.
     */
    FLAG_DAY,
    /** Any previously flagged update that has been activated into the network map. */
    APPLIED,
    /** A new or flag day update that has been cancelled. */
    CANCELLED
}

