package net.corda.tools.shell

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.google.common.io.Closeables
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.internal.copyTo
import net.corda.core.internal.inputStream
import org.crsh.command.InvocationContext
import rx.Observable
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.util.*

//region Extra serializers
//
// These serializers are used to enable the user to specify objects that aren't natural data containers in the shell,
// and for the shell to print things out that otherwise wouldn't be usefully printable.

object ObservableSerializer : JsonSerializer<Observable<*>>() {
    override fun serialize(value: Observable<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString("(observable)")
    }
}

/**
 * String value deserialized to [UniqueIdentifier].
 * Any string value used as [UniqueIdentifier.externalId].
 * If string contains underscore(i.e. externalId_uuid) then split with it.
 *      Index 0 as [UniqueIdentifier.externalId]
 *      Index 1 as [UniqueIdentifier.id]
 * */
object UniqueIdentifierDeserializer : JsonDeserializer<UniqueIdentifier>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UniqueIdentifier {
        //Check if externalId and UUID may be separated by underscore.
        if (p.text.contains("_")) {
            val ids = p.text.split("_")
            //Create UUID object from string.
            val uuid: UUID = UUID.fromString(ids[1])
            //Create UniqueIdentifier object using externalId and UUID.
            return UniqueIdentifier(ids[0], uuid)
        }
        //Any other string used as externalId.
        return UniqueIdentifier.fromString(p.text)
    }
}

/**
 * String value deserialized to [UUID].
 * */
object UUIDDeserializer : JsonDeserializer<UUID>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UUID {
        //Create UUID object from string.
        return UUID.fromString(p.text)
    }
}

// An InputStream found in a response triggers a request to the user to provide somewhere to save it.
object InputStreamSerializer : JsonSerializer<InputStream>() {
    var invokeContext: InvocationContext<*>? = null

    override fun serialize(value: InputStream, gen: JsonGenerator, serializers: SerializerProvider) {

        value.use {
            val toPath = invokeContext!!.readLine("Path to save stream to (enter to ignore): ", true)
            if (toPath == null || toPath.isBlank()) {
                gen.writeString("<not saved>")
            } else {
                val path = Paths.get(toPath)
                it.copyTo(path)
                gen.writeString("<saved to: ${path.toAbsolutePath()}>")
            }
        }
    }
}

// A file name is deserialized to an InputStream if found.
object InputStreamDeserializer : JsonDeserializer<InputStream>() {
    // Keep track of them so we can close them later.
    private val streams = Collections.synchronizedSet(HashSet<InputStream>())

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): InputStream {
        val stream = object : BufferedInputStream(Paths.get(p.text).inputStream()) {
            override fun close() {
                super.close()
                streams.remove(this)
            }
        }
        streams += stream
        return stream
    }

    fun closeAll() {
        // Clone the set with toList() here so each closed stream can be removed from the set inside close().
        streams.toList().forEach { Closeables.closeQuietly(it) }
    }
}
//endregion