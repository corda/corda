# ProGuard config for how to strip the enclave JAR down to a size that actually fits.

-verbose

# Renaming of class members would make it harder to dynamically load code.
-dontoptimize

# Bytecode optimizations can break things, disable for now.
-dontobfuscate

# OpenJDK generates so many warnings we have to disable them.
-ignorewarnings
-dontwarn
-dontnote

# Root the Corda code so the enclavelet and everything statically reachable from it is kept.
-keep public class com.r3.enclaves.*.Enclavelet {
    public static *;
}

# Kryo accesses its own serialisers via reflection.
-keep class com.esotericsoftware.kryo.serializers.**
-keepclassmembers class com.esotericsoftware.kryo.serializers.** {
    <methods>;
    <fields>;
}

# Ditto, this serializer is constructed using reflection, so we must not optimize out the constructor.
-keepclassmembers class node.corda.core.serialization.ImmutableClassSerializer {
	<methods>;
}

# Quick hack to stop things we need to deserialize being stripped out.
-keep class com.r3.contracts.**
-keepclassmembers class node.corda.contracts.** {
	<methods>;
	<fields>;
}

-keep class com.sun.crypto.provider.** { *; }
-keep class sun.security.internal.** { *; }
-keep class sun.security.provider.** { *; }
-keep class javax.crypto.** { *; }
-keep class sun.nio.cs.** { *  ; }
-keep class java.util.** { *; }