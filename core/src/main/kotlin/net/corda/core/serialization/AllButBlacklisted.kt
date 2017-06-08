package net.corda.core.serialization

import sun.misc.Unsafe
import sun.security.util.Password
import java.io.*
import java.lang.invoke.*
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.lang.reflect.ReflectPermission
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import java.security.*
import java.sql.Connection
import java.util.*
import java.util.logging.Handler
import java.util.zip.ZipFile
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

/**
 * This is a [ClassWhitelist] implementation where everything is whitelisted except for blacklisted classes and interfaces.
 * In practice, as flows are arbitrary code in which it is convenient to do many things,
 * we can often end up pulling in a lot of objects that do not make sense to put in a checkpoint.
 * Thus, by blacklisting classes/interfaces we don't expect to be serialised, we can better handle/monitor the aforementioned behaviour.
 * Inheritance works for blacklisted items, but one can specifically exclude classes from blacklisting as well.
 */
object AllButBlacklisted : ClassWhitelist {

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
            Random::class.java.name,

            // known blacklisted interfaces.
            Connection::class.java.name,
            AutoCloseable::class.java.name,

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
            // TODO: add more from java.net.

            // java.io classes.
            Console::class.java.name,
            File::class.java.name,
            FileDescriptor::class.java.name,
            FilePermission::class.java.name,
            RandomAccessFile::class.java.name,
            Reader::class.java.name,
            Writer::class.java.name,
            // TODO: add more from java.io.

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

    // specifically exclude classes from the blacklist,
    // even if any of their superclasses and/or implemented interfaces are blacklisted.
    private val excludedClasses = hashSetOf<String>(
            LinkedHashSet::class.java.name,
            LinkedHashMap::class.java.name,
            InputStream::class.java.name,
            BufferedInputStream::class.java.name,
            Class.forName("sun.net.www.protocol.jar.JarURLConnection\$JarURLInputStream").name
    )

    /**
     * This implementation supports inheritance; thus, if a superclass or superinterface is blacklisted, so is the input class.
     */
    override fun hasListed(type: Class<*>): Boolean {
        // check if excluded.
        if (type.name in excludedClasses)
            return true
        // check if listed.
        if (type.name in blacklistedClasses)
            return false
        // inheritance check.
        else {
            val aMatch = blacklistedClasses.firstOrNull { Class.forName(it).isAssignableFrom(type) }
            if (aMatch != null) {
                // TODO: blacklistedClasses += type.name // add it, so checking is faster next time we encounter this class.
                return false
            }
        }
        return true
    }
}
