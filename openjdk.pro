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

# ProGuard will not realize AtomicInteger.value is changed from native
# code unless we tell it (todo: handle other Atomic* classes)
-keepclassmembers class java.util.concurrent.atomic.AtomicInteger {
   private int value;   
 }

# this field as accessed via an AtomicReferenceFieldUpdater:
-keepclassmembers class java.io.BufferedInputStream {
   protected byte[] buf;
 }



# (to be continued...)
