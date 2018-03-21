package com.r3.corda.networkmanage.common.persistence.entity

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.network.ParametersUpdate
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "parameters_update")
class ParametersUpdateEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        // TODO use @MapsId to get rid of additional sequence
        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "network_parameters", foreignKey = ForeignKey(name = "FK__param_up__net_param"))
        val networkParameters: NetworkParametersEntity,

        @Column(name = "description")
        val description: String,

        @Column(name = "update_deadline")
        val updateDeadline: Instant,

        // This boolean flag is used when we want to explicitly point that it's time to switch parameters in network map.
        @Column(name = "flag_day")
        val flagDay: Boolean = false
) {
    fun toParametersUpdate(): ParametersUpdate = ParametersUpdate(SecureHash.parse(networkParameters.parametersHash), description, updateDeadline)

    fun toNetMapUpdate(): ParametersUpdate? = if (!flagDay) ParametersUpdate(SecureHash.parse(networkParameters.parametersHash), description, updateDeadline) else null

    fun copy(id: Long? = this.id,
             networkParameters: NetworkParametersEntity = this.networkParameters,
             description: String = this.description,
             updateDeadline: Instant = this.updateDeadline,
             flagDay: Boolean = this.flagDay): ParametersUpdateEntity {
        return ParametersUpdateEntity(
                id = id,
                networkParameters = networkParameters,
                description = description,
                updateDeadline = updateDeadline,
                flagDay = flagDay
        )
    }
}