# proguard include file (http://proguard.sourceforge.net)

# we call the values method reflectively in Enum.valueOf():

-keepclassmembers public class * extends java.lang.Enum {
   public static *** values();
 }

# the VM depends on the fixed layout of the following classes:

-keepclassmembers class java.lang.Class { !static <fields>; }
-keepclassmembers class java.lang.ClassLoader { !static <fields>; }
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

# the VM may throw instances of the following:

-keep public class avian.IncompatibleContinuationException
-keep public class java.lang.RuntimeException
-keep public class java.lang.IllegalStateException
-keep public class java.lang.IllegalArgumentException
-keep public class java.lang.IllegalMonitorStateException
-keep public class java.lang.ArrayIndexOutOfBoundsException
-keep public class java.lang.ArrayStoreException
-keep public class java.lang.NegativeArraySizeException
-keep public class java.lang.ClassCastException
-keep public class java.lang.ClassNotFoundException
-keep public class java.lang.NullPointerException
-keep public class java.lang.InterruptedException
-keep public class java.lang.StackOverflowError
-keep public class java.lang.NoSuchFieldError
-keep public class java.lang.NoSuchMethodError
-keep public class java.lang.UnsatisfiedLinkError
-keep public class java.lang.ExceptionInInitializerError
-keep public class java.lang.OutOfMemoryError
-keep public class java.lang.reflect.InvocationTargetException
-keep public class java.io.IOException

# ClassLoader.getSystemClassloader() depends on the existence of this class:

-keep             class avian.SystemClassLoader

# the VM references these classes by name, so protect them from obfuscation:

-keepnames public class java.lang.**

# Don't optimize calls to ResourceBundle
-keep,allowshrinking,allowobfuscation public class java.util.ResourceBundle {
  public static java.util.ResourceBundle getBundle(...);
}

# musn't obfuscate native method names:

-keepclasseswithmembernames class * {
   native <methods>;
 }

# Thread.run is called by name in the VM

-keepclassmembernames class java.lang.Thread { void run(); }

# when continuations are enabled, the VM may call these methods by name:

-keepclassmembers class avian.Continuations {
   *** wind(...);
   *** rewind(...);
 }

-keepclassmembernames class avian.CallbackReceiver {
   *** receive(...);
 }

# the above methods include these classes in their signatures:

-keepnames public class avian.Callback
-keepnames public class java.util.concurrent.Callable
