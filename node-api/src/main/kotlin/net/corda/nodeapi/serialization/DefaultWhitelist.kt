package net.corda.nodeapi.serialization

import com.esotericsoftware.kryo.KryoException
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import net.corda.core.utilities.NetworkHostAndPort
import org.apache.activemq.artemis.api.core.SimpleString
import rx.Notification
import rx.exceptions.OnErrorNotImplementedException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.util.*

/**
 * NOTE: We do not whitelist [HashMap] or [HashSet] since they are unstable under serialization.
 */
class DefaultWhitelist : CordaPluginRegistry() {
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.apply {
            // TODO: Turn this into an array and use map {}
            addToWhitelist(Array<Any>(0, {}).javaClass)
            addToWhitelist(Notification::class.java)
            addToWhitelist(Notification.Kind::class.java)
            addToWhitelist(ArrayList::class.java)
            addToWhitelist(listOf<Any>().javaClass) // EmptyList
            addToWhitelist(Pair::class.java)
            addToWhitelist(ByteArray::class.java)
            addToWhitelist(UUID::class.java)
            addToWhitelist(LinkedHashSet::class.java)
            addToWhitelist(setOf<Unit>().javaClass) // EmptySet
            addToWhitelist(Currency::class.java)
            addToWhitelist(listOf(Unit).javaClass) // SingletonList
            addToWhitelist(setOf(Unit).javaClass) // SingletonSet
            addToWhitelist(mapOf(Unit to Unit).javaClass) // SingletonSet
            addToWhitelist(NetworkHostAndPort::class.java)
            addToWhitelist(SimpleString::class.java)
            addToWhitelist(KryoException::class.java)
            addToWhitelist(StringBuffer::class.java)
            addToWhitelist(Unit::class.java)
            addToWhitelist(java.io.ByteArrayInputStream::class.java)
            addToWhitelist(java.lang.Class::class.java)
            addToWhitelist(java.math.BigDecimal::class.java)
            addToWhitelist(java.security.KeyPair::class.java)

            // Matches the list in TimeSerializers.addDefaultSerializers:
            addToWhitelist(java.time.Duration::class.java)
            addToWhitelist(java.time.Instant::class.java)
            addToWhitelist(java.time.LocalDate::class.java)
            addToWhitelist(java.time.LocalDateTime::class.java)
            addToWhitelist(java.time.ZoneOffset::class.java)
            addToWhitelist(java.time.ZoneId::class.java)
            addToWhitelist(java.time.OffsetTime::class.java)
            addToWhitelist(java.time.OffsetDateTime::class.java)
            addToWhitelist(java.time.ZonedDateTime::class.java)
            addToWhitelist(java.time.Year::class.java)
            addToWhitelist(java.time.YearMonth::class.java)
            addToWhitelist(java.time.MonthDay::class.java)
            addToWhitelist(java.time.Period::class.java)
            addToWhitelist(java.time.DayOfWeek::class.java)   // No custom serialiser but it's an enum.

            addToWhitelist(java.util.Collections.singletonMap("A", "B").javaClass)
            addToWhitelist(java.util.LinkedHashMap::class.java)
            addToWhitelist(BigDecimal::class.java)
            addToWhitelist(LocalDate::class.java)
            addToWhitelist(Period::class.java)
            addToWhitelist(BitSet::class.java)
            addToWhitelist(OnErrorNotImplementedException::class.java)
        }
        return true
    }
}
