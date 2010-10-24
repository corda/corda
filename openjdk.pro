# proguard include file (http://proguard.sourceforge.net)

# This file is for use in combination with vm.pro when ProGuarding
# OpenJDK-based builds

# the following methods and fields are refered to by name in the VM:

-keepclassmembers class java.lang.Thread {
   public void run();
 }

-keep class java.lang.System {
   private static void initializeSystemClass();
 }

-keep class java.lang.ClassLoader {
   private static java.lang.ClassLoader scl;
   private static boolean sclSet;

   protected ClassLoader(java.lang.ClassLoader);
 }

-keep class java.util.Properties {
   public java.lang.Object setProperty(java.lang.String, java.lang.String);
 }

-keep class avian.OpenJDK {
   public static java.security.ProtectionDomain getProtectionDomain();
 }

-keepclassmembers public class java.security.PrivilegedAction {
   public java.lang.Object run();
 }

-keepclassmembers public class * implements java.security.PrivilegedAction {
   public java.lang.Object run();
 }

-keepclassmembers public class java.security.PrivilegedExceptionAction {
   public java.lang.Object run();
 }

-keepclassmembers public class * implements java.security.PrivilegedExceptionAction {
   public java.lang.Object run();
 }

-keep public class java.security.PrivilegedActionException {
   public PrivilegedActionException(java.lang.Exception);
 }

# these class names are used to disambiguate JNI method lookups:

-keepnames public class java.security.PrivilegedAction
-keepnames public class java.security.PrivilegedExceptionAction
-keepnames public class java.security.AccessControlContext

# the following methods and fields are refered to by name in the OpenJDK
# native code:

-keep class java.util.Properties {
   public java.lang.Object put(java.lang.Object, java.lang.Object);
 }

-keepclassmembers class * {
   public boolean equals(java.lang.Object);
   public void wait();
   public void notify();
   public void notifyAll();
   public java.lang.String toString();
 }

-keepclassmembers class java.lang.String {
   public String(byte[]);
   public String(byte[], java.lang.String);
   public byte[] getBytes();
   public byte[] getBytes(java.lang.String);
 }

-keepclassmembers class java.io.FileDescriptor {
   private int fd;   
 }

-keepclassmembers class java.io.FileInputStream {
   private java.io.FileDescriptor fd;   
 }

-keepclassmembers class java.io.FileOutputStream {
   private java.io.FileDescriptor fd;   
 }

# changed in native code via sun.misc.Unsafe (todo: handle other
# Atomic* classes)
-keepclassmembers class java.util.concurrent.atomic.AtomicInteger {
   private int value;   
 }

# avoid inlining due to access check using a fixed offset into call stack:
-keep,allowshrinking,allowobfuscation class java.util.concurrent.atomic.AtomicReferenceFieldUpdater {
   *** newUpdater(...);
 }

# accessed reflectively via an AtomicReferenceFieldUpdater:
-keepclassmembers class java.io.BufferedInputStream {
   protected byte[] buf;
 }

-keep class java.lang.System {
   public static java.io.InputStream in;
   public static java.io.PrintStream out;
   public static java.io.PrintStream err;
   # avoid inlining due to access check using fixed offset into call stack:
   static java.lang.Class getCallerClass();
 }

# refered to by name from native code:
-keepnames public class java.io.InputStream
-keepnames public class java.io.PrintStream

# avoid inlining due to access check using fixed offset into call stack:
-keep,allowshrinking,allowobfuscation class java.lang.System {
   static java.lang.Class getCallerClass();
 }

-keep class java.io.UnixFileSystem {
   public UnixFileSystem();
 }

-keep class java.io.File {
   private java.lang.String path;
 }

-keepclassmembers class java.lang.ClassLoader$NativeLibrary {
   long handle;
   private int jniVersion;
 }
