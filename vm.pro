# proguard include file (http://proguard.sourceforge.net)

# we call the values method reflectively in Enum.valueOf():

-keep public class * extends java.lang.Enum {
   public static *** values();
 }

# the VM depends on the fixed layout of the following classes:

-keepclassmembers class java.lang.Class { !static <fields>; }
-keepclassmembers class java.lang.ClassLoader { !static <fields>; }
-keepclassmembers class java.lang.SystemClassLoader { !static <fields>; }
-keepclassmembers class java.lang.String { !static <fields>; }
-keepclassmembers class java.lang.Thread { !static <fields>; }
-keepclassmembers class java.lang.StackTraceElement { !static <fields>; }
-keepclassmembers class java.lang.Throwable { !static <fields>; }
-keepclassmembers class java.lang.Byte { !static <fields>; }
-keepclassmembers class java.lang.Boolean { !static <fields>; }
-keepclassmembers class java.lang.Short { !static <fields>; }
-keepclassmembers class java.lang.Character { !static <fields>; }
-keepclassmembers class java.lang.Integer { !static <fields>; }
-keepclassmembers class java.lang.Long { !static <fields>; }
-keepclassmembers class java.lang.Float { !static <fields>; }
-keepclassmembers class java.lang.Double { !static <fields>; }
-keepclassmembers class java.lang.ref.Reference { !static <fields>; }
-keepclassmembers class java.lang.ref.ReferenceQueue { !static <fields>; }
-keepclassmembers class java.lang.ref.WeakReference { !static <fields>; }
-keepclassmembers class java.lang.ref.PhantomReference { !static <fields>; }
-keepclassmembers class java.lang.reflect.Field { !static <fields>; }
-keepclassmembers class java.lang.reflect.Method { !static <fields>; }

# ClassLoader.getSystemClassloader() depends on the existence of this class:

-keep             class java.lang.SystemClassLoader

# the VM references these classes by name, so protect them from obfuscation:

-keepnames public class java.lang.**

# musn't obfuscate native method names:

-keepclasseswithmembernames class * {
   native <methods>;
 }
