# proguard include file (http://proguard.sourceforge.net)

# we call the values method reflectively in Enum.valueOf():

-keep public class * extends java.lang.Enum {
   public static *** values();
 }

# the VM depends on a fixed layout for the following classes:

-keepclassmembers class java.lang.Class { <fields>; }
-keepclassmembers class java.lang.ClassLoader { <fields>; }
-keepclassmembers class java.lang.SystemClassLoader { <fields>; }
-keepclassmembers class java.lang.String { <fields>; }
-keepclassmembers class java.lang.Thread { <fields>; }
-keepclassmembers class java.lang.StackTraceElement { <fields>; }
-keepclassmembers class java.lang.Throwable { <fields>; }
-keepclassmembers class java.lang.Byte { <fields>; }
-keepclassmembers class java.lang.Boolean { <fields>; }
-keepclassmembers class java.lang.Short { <fields>; }
-keepclassmembers class java.lang.Character { <fields>; }
-keepclassmembers class java.lang.Integer { <fields>; }
-keepclassmembers class java.lang.Long { <fields>; }
-keepclassmembers class java.lang.Float { <fields>; }
-keepclassmembers class java.lang.Double { <fields>; }
-keepclassmembers class java.lang.ref.Reference { <fields>; }
-keepclassmembers class java.lang.ref.ReferenceQueue { <fields>; }
-keepclassmembers class java.lang.ref.WeakReference { <fields>; }
-keepclassmembers class java.lang.ref.PhantomReference { <fields>; }
-keepclassmembers class java.lang.reflect.Field { <fields>; }
-keepclassmembers class java.lang.reflect.Method { <fields>; }

-keepnames public class java.lang.*

-keepclasseswithmembernames class * {
   native <methods>;
 }
