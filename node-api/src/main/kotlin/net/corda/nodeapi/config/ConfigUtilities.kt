package net.corda.nodeapi.config

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

private fun <T : Enum<T>> enumBridge(clazz: Class<T>, enumValueString: String): T {
    return java.lang.Enum.valueOf(clazz, enumValueString)
}
private class DummyEnum : Enum<DummyEnum>("", 0)

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
operator fun <T> Config.getValue(receiver: Any, metadata: KProperty<*>): T {
    if (metadata.returnType.isMarkedNullable && !hasPath(metadata.name)) {
        return null as T
    }
    val returnType = metadata.returnType.javaType
    return when (metadata.returnType.javaType) {
        String::class.java -> getString(metadata.name) as T
        Int::class.java -> getInt(metadata.name) as T
        Integer::class.java -> getInt(metadata.name) as T
        Long::class.java -> getLong(metadata.name) as T
        Double::class.java -> getDouble(metadata.name) as T
        Boolean::class.java -> getBoolean(metadata.name) as T
        LocalDate::class.java -> LocalDate.parse(getString(metadata.name)) as T
        Instant::class.java -> Instant.parse(getString(metadata.name)) as T
        HostAndPort::class.java -> HostAndPort.fromString(getString(metadata.name)) as T
        Path::class.java -> Paths.get(getString(metadata.name)) as T
        URL::class.java -> URL(getString(metadata.name)) as T
        Properties::class.java -> getProperties(metadata.name) as T
        else -> {
            if (returnType is Class<*> && Enum::class.java.isAssignableFrom(returnType)) {
                return enumBridge(returnType as Class<DummyEnum>, getString(metadata.name)) as T
            }
            throw IllegalArgumentException("Unsupported type ${metadata.returnType}")
        }
    }
}

/**
 * Helper class for optional configurations
 */
class OptionalConfig<out T>(val conf: Config, val lambda: () -> T) {
    operator fun getValue(receiver: Any, metadata: KProperty<*>): T {
        return if (conf.hasPath(metadata.name)) conf.getValue(receiver, metadata) else lambda()
    }
}

fun <T> Config.getOrElse(lambda: () -> T): OptionalConfig<T> = OptionalConfig(this, lambda)

fun Config.getProperties(path: String): Properties {
    val obj = this.getObject(path)
    val props = Properties()
    for ((property, objectValue) in obj.entries) {
        props.setProperty(property, objectValue.unwrapped().toString())
    }
    return props
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> Config.getListOrElse(path: String, default: Config.() -> List<T>): List<T> {
    return if (hasPath(path)) {
        (if (T::class == String::class) getStringList(path) else getConfigList(path)) as List<T>
    } else {
        this.default()
    }
}
