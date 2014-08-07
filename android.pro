# these are referenced in JniConstants.cpp:

-keep class java.text.Bidi$Run
-keep class java.math.BigDecimal
-keep class java.lang.Boolean
-keep class java.lang.Byte
-keep class java.nio.charset.CharsetICU {
   CharsetICU(java.lang.String, java.lang.String, java.lang.String[]);
 }
-keep class java.lang.reflect.Constructor
-keep class java.util.zip.Deflater
-keep class java.lang.Double
-keep class libcore.io.ErrnoException
-keep class java.lang.reflect.Field
-keep class libcore.icu.NativeDecimalFormat$FieldPositionIterator {
   void setData(int[]);
 }
-keep class java.io.FileDescriptor
-keep class libcore.io.GaiException
-keep class java.net.Inet6Address
-keep class java.net.InetAddress
-keep class java.net.InetSocketAddress
-keep class java.net.InetUnixAddress
-keep class java.util.zip.Inflater
-keep class java.lang.Integer
-keep class libcore.icu.LocaleData
-keep class java.lang.Long
-keep class java.lang.reflect.Method
-keep class libcore.util.MutableInt
-keep class libcore.util.MutableLong
-keep class java.text.ParsePosition
-keep class java.util.regex.PatternSyntaxException
-keep class java.lang.RealToString
-keep class java.net.Socket
-keep class java.net.SocketImpl
-keep class java.lang.String
-keep class libcore.io.StructAddrinfo {
   <fields>;
 }
-keep class libcore.io.StructFlock
-keep class libcore.io.StructGroupReq
-keep class libcore.io.StructLinger
-keep class libcore.io.StructPasswd {
   StructPasswd(java.lang.String, int, int, java.lang.String, java.lang.String);
 }
-keep class libcore.io.StructPollfd
-keep class libcore.io.StructStat {
   StructStat(long, long, int, long, int, int, long, long, long, long, long, long, long);
 }
-keep class libcore.io.StructStatFs
-keep class libcore.io.StructTimeval
-keep class libcore.io.StructUtsname {
   StructUtsname(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String);
 }
-keep class libcore.io.StructUcred

# referenced from libcore native code

-keep class libcore.icu.LocaleData {
   <fields>;
 }

-keep class org.conscrypt.OpenSSLBIOInputStream {
   <methods>;
 }

-keep class java.util.Calendar {
   void set(int, int, int, int, int, int);
 }

# called from the VM

-keep class java.lang.Thread {
   Thread(java.lang.ThreadGroup, java.lang.String, int, boolean);
 }

-keep class avian.Classes {
   java.lang.Class forName(java.lang.String, boolean, java.lang.ClassLoader);
   int findField(avian.VMClass, java.lang.String);
   int findMethod(avian.VMClass, java.lang.String, java.lang.Class[]);
   java.lang.annotation.Annotation getAnnotation(java.lang.ClassLoader, java.lang.Object[]);
 }

-keep class java.lang.VMThread {
   VMThread(java.lang.Thread);
 }

# loaded reflectively to handle embedded resources:
-keep class avian.avianvmresource.Handler
