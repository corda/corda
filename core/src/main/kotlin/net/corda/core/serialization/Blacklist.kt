package net.corda.core.serialization

import sun.misc.Unsafe
import sun.security.util.Password
import java.io.*
import java.lang.invoke.*
import java.lang.reflect.*
import java.net.*
import java.security.*
import java.sql.Connection
import java.util.HashMap
import java.util.logging.Handler
import java.util.zip.ZipFile
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

/**
 * This object keeps a set of blacklisted classes and interfaces that should not be used
 * for serialisation when a flow checkpoints.
 * In practice, as flows are arbitrary code in which it is convenient to do many things,
 * we can often end up pulling in a lot of objects that do not make sense to put in a checkpoint.
 * Please note that black-listing check precedes white-listing,
 * thus even if a class/interface is whitelisted or annotated as [CordaSerializable] it won't be serialised if
 * this or any of its superclasses and/or implemented interfaces is in the blacklist.
 */
object Blacklist : ClassWhitelist {

    private val blacklistedClasses = hashSetOf<String>(

            // known blacklisted classes.
            Thread::class.java.name,
            HashSet::class.java.name,
            HashMap::class.java.name,
            ClassLoader::class.java.name,
            Handler::class.java.name, // MemoryHandler, StreamHandler
            Runtime::class.java.name,
            Unsafe::class.java.name,
            ZipFile::class.java.name,
            Provider::class.java.name,
            SecurityManager::class.java.name,

            // known blacklisted interfaces.
            Connection::class.java.name,
            // TODO: add AutoCloseable, but exclude (hardcode) those we may need, such as InputStream.

            // java.security.
            PrivateKey::class.java.name,
            KeyPair::class.java.name,
            KeyStore::class.java.name,
            Password::class.java.name,
            AccessController::class.java.name,
            Permission::class.java.name,

            // java.net classes.
            DatagramSocket::class.java.name,
            ServerSocket::class.java.name,
            Socket::class.java.name,
            URLConnection::class.java.name,
            // TODO: java.net more classes, interfaces and exceptions.

            // java.io classes.
            Console::class.java.name,
            File::class.java.name,
            FileDescriptor::class.java.name,
            FilePermission::class.java.name,
            RandomAccessFile::class.java.name,
            Reader::class.java.name,
            Writer::class.java.name,
            // TODO: java.io more classes, interfaces and exceptions.

            // java.lang.invoke classes.
            CallSite::class.java.name, // for all CallSites eg MutableCallSite, VolatileCallSite etc.
            LambdaMetafactory::class.java.name,
            MethodHandle::class.java.name,
            MethodHandleProxies::class.java.name,
            MethodHandles::class.java.name,
            MethodHandles.Lookup::class.java.name,
            MethodType::class.java.name,
            SerializedLambda::class.java.name,
            SwitchPoint::class.java.name,

            // java.lang.invoke package interfaces.
            MethodHandleInfo::class.java.name,

            // java.lang.invoke package exceptions.
            LambdaConversionException::class.java.name,
            WrongMethodTypeException::class.java.name,

            // java.lang.reflect
            AccessibleObject::class.java.name, // for Executable, Field, Method, Constructor
            Modifier::class.java.name,
            Parameter::class.java.name,
            ReflectPermission::class.java.name
            // TODO: add more from java.lang.reflect.
    )

    override fun hasListed(type: Class<*>): Boolean {
        // specifically exclude classes from the blacklist,
        // even if any of their superclasses and/or implemented interfaces are blacklisted.
        when (type) {
            LinkedHashSet::class.java -> return false
        }
        // check if listed.
        if (type.name in blacklistedClasses)
            return true
        // inheritance check.
        else {
            val aMatch = blacklistedClasses.firstOrNull { Class.forName(it).isAssignableFrom(type) }
            if (aMatch != null) {
                // TODO: blacklistedClasses += type.name // add it, so checking is faster next time we encounter this class.
                return true
            }
        }
        return false
    }
}