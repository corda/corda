package com.r3.corda.networkmanage.common.persistence.entity

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.network.ParametersUpdate
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

        // This boolean flag is used when we want to explicitly point that it's time to switch parameters in network map.
        @Column(name = "flag_day", nullable = false)
        val flagDay: Boolean = false
) {
    fun toParametersUpdate(): ParametersUpdate {
        return ParametersUpdate(SecureHash.parse(networkParameters.hash), description, updateDeadline)
    }
}