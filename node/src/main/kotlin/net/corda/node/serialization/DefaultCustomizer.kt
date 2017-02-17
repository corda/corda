package net.corda.node.serialization

import com.esotericsoftware.kryo.KryoException
import com.google.common.net.HostAndPort
import net.corda.contracts.asset.Cash
import net.corda.core.ErrorOr
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.IllegalFlowLogicException
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.StateMachineInfo
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.node.NodeInfo
import net.corda.core.node.PhysicalLocation
import net.corda.core.node.ServiceEntry
import net.corda.core.node.WorldCoordinate
import net.corda.core.node.services.*
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.serialization.SerializationCustomization
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.PermissionException
import net.corda.node.services.messaging.RPCException
import net.corda.node.services.statemachine.FlowSessionException
import org.apache.activemq.artemis.api.core.SimpleString
import rx.Notification
import java.util.*

object DefaultCustomizer {
    fun customize(custom: SerializationCustomization) {
        custom.apply {

            addToWhitelist(Party::class.java)
            addToWhitelist(Array<Any>(0, {}).javaClass)

            addToWhitelist(ErrorOr::class.java)
            addToWhitelist(Notification::class.java)
            addToWhitelist(Notification.Kind::class.java)

            addToWhitelist(ArrayList::class.java)
            addToWhitelist(listOf<Any>().javaClass) // EmptyList
            addToWhitelist(IllegalStateException::class.java)
            addToWhitelist(Pair::class.java)
            addToWhitelist(StateMachineUpdate.Added::class.java)
            addToWhitelist(StateMachineUpdate.Removed::class.java)
            addToWhitelist(StateMachineInfo::class.java)
            addToWhitelist(DigitalSignature.WithKey::class.java)
            addToWhitelist(DigitalSignature.LegallyIdentifiable::class.java)
            addToWhitelist(ByteArray::class.java)
            addToWhitelist(CompositeKey.Leaf::class.java)
            addToWhitelist(CompositeKey.Node::class.java)
            addToWhitelist(Vault::class.java)
            addToWhitelist(Vault.Update::class.java)
            addToWhitelist(StateMachineRunId::class.java)
            addToWhitelist(StateMachineTransactionMapping::class.java)
            addToWhitelist(UUID::class.java)
            addToWhitelist(UniqueIdentifier::class.java)
            addToWhitelist(LinkedHashSet::class.java)
            addToWhitelist(StateAndRef::class.java)
            addToWhitelist(setOf<Unit>().javaClass) // EmptySet
            addToWhitelist(StateRef::class.java)
            addToWhitelist(SecureHash.SHA256::class.java)
            addToWhitelist(TransactionState::class.java)
            addToWhitelist(Cash.State::class.java)
            addToWhitelist(Amount::class.java)
            addToWhitelist(Issued::class.java)
            addToWhitelist(PartyAndReference::class.java)
            addToWhitelist(OpaqueBytes::class.java)
            addToWhitelist(Currency::class.java)
            addToWhitelist(Cash::class.java)
            addToWhitelist(Cash.Clauses.ConserveAmount::class.java)
            addToWhitelist(listOf(Unit).javaClass) // SingletonList
            addToWhitelist(setOf(Unit).javaClass) // SingletonSet
            addToWhitelist(ServiceEntry::class.java)
            addToWhitelist(NodeInfo::class.java)
            addToWhitelist(PhysicalLocation::class.java)
            addToWhitelist(NetworkMapCache.MapChange.Added::class.java)
            addToWhitelist(NetworkMapCache.MapChange.Removed::class.java)
            addToWhitelist(NetworkMapCache.MapChange.Modified::class.java)
            addToWhitelist(ArtemisMessagingComponent.NodeAddress::class.java)
            addToWhitelist(ArtemisMessagingComponent.NetworkMapAddress::class.java)
            addToWhitelist(ServiceInfo::class.java)
            addToWhitelist(ServiceType.getServiceType("ab", "ab").javaClass)
            addToWhitelist(ServiceType.parse("ab").javaClass)
            addToWhitelist(WorldCoordinate::class.java)
            addToWhitelist(HostAndPort::class.java)
            addToWhitelist(SimpleString::class.java)
            addToWhitelist(ServiceEntry::class.java)
            addToWhitelist(FlowException::class.java)
            addToWhitelist(FlowSessionException::class.java)
            addToWhitelist(IllegalFlowLogicException::class.java)
            addToWhitelist(RuntimeException::class.java)
            addToWhitelist(IllegalArgumentException::class.java)
            addToWhitelist(ArrayIndexOutOfBoundsException::class.java)
            addToWhitelist(IndexOutOfBoundsException::class.java)
            addToWhitelist(NoSuchElementException::class.java)
            addToWhitelist(RPCException::class.java)
            addToWhitelist(PermissionException::class.java)
            addToWhitelist(Throwable::class.java)
            addToWhitelist(FlowHandle::class.java)
            addToWhitelist(KryoException::class.java)
            addToWhitelist(StringBuffer::class.java)
            addToWhitelist(Unit::class.java)
        }
    }
}