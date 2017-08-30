package net.corda.nodeapi.internal.serialization

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
        arrayOf(Array<Any>(0, {}).javaClass,
                Notification::class.java,
                Notification.Kind::class.java,
                ArrayList::class.java,
                listOf<Any>().javaClass, // EmptyList
                Pair::class.java,
                ByteArray::class.java,
                UUID::class.java,
                LinkedHashSet::class.java,
                setOf<Unit>().javaClass, // EmptySet
                Currency::class.java,
                listOf(Unit).javaClass, // SingletonList
                setOf(Unit).javaClass, // SingletonSet
                mapOf(Unit to Unit).javaClass, // SingletonSet
                NetworkHostAndPort::class.java,
                SimpleString::class.java,
                KryoException::class.java,
                StringBuffer::class.java,
                Unit::class.java,
                java.io.ByteArrayInputStream::class.java,
                java.lang.Class::class.java,
                java.math.BigDecimal::class.java,
                java.security.KeyPair::class.java,
                java.time.Duration::class.java, // Matches the list in TimeSerializers.addDefaultSerializers:
                java.time.Instant::class.java,
                java.time.LocalDate::class.java,
                java.time.LocalDateTime::class.java,
                java.time.ZoneOffset::class.java,
                java.time.ZoneId::class.java,
                java.time.OffsetTime::class.java,
                java.time.OffsetDateTime::class.java,
                java.time.ZonedDateTime::class.java,
                java.time.Year::class.java,
                java.time.YearMonth::class.java,
                java.time.MonthDay::class.java,
                java.time.Period::class.java,
                java.time.DayOfWeek::class.java, // No custom serialiser but it's an enum.
                java.util.Collections.singletonMap("A", "B").javaClass,
                java.util.LinkedHashMap::class.java,
                BigDecimal::class.java,
                LocalDate::class.java,
                Period::class.java,
                BitSet::class.java,
                OnErrorNotImplementedException::class.java
        ).map { custom.addToWhitelist(it) }
        return true
    }
}
