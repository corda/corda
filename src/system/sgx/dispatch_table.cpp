// Inside the enclave we don't have the rtld to do symbol lookups for us. So, we must manually compute
// the name->address mappings at compile time and then let the SGX ELF loader apply the relocs itself.
//
// Because there are quite a few symbols we might want to look up, the final file is generated using a script.
// This is just a template.
//

#ifndef AVIAN_SGX_DISPATCH_TABLE_H
#define AVIAN_SGX_DISPATCH_TABLE_H

#include <string>
#include <map>
#include <avian/common.h>

using namespace std;

extern "C" {
void Avian_avian_Classes_acquireClassLock();
void Avian_avian_Classes_defineVMClass();
void Avian_avian_Classes_initialize();
void Avian_avian_Classes_makeString();
void Avian_avian_Classes_primitiveClass();
void Avian_avian_Classes_releaseClassLock();
void Avian_avian_Classes_resolveVMClass();
void Avian_avian_Classes_toVMClass();
void Avian_avian_Classes_toVMMethod();
void Avian_avian_Continuations_00024Continuation_handleException();
void Avian_avian_Continuations_00024Continuation_handleResult();
void Avian_avian_Continuations_callWithCurrentContinuation();
void Avian_avian_Continuations_dynamicWind2();
void Avian_avian_Machine_dumpHeap();
void Avian_avian_Machine_tryNative();
void Avian_avian_Singleton_getInt();
void Avian_avian_Singleton_getLong();
void Avian_avian_Singleton_getObject();
void Avian_avian_SystemClassLoader_00024ResourceEnumeration_nextResourceURLPrefix();
void Avian_avian_SystemClassLoader_appLoader();
void Avian_avian_SystemClassLoader_findLoadedVMClass();
void Avian_avian_SystemClassLoader_findVMClass();
void Avian_avian_SystemClassLoader_getClass();
void Avian_avian_SystemClassLoader_getPackageSource();
void Avian_avian_SystemClassLoader_resourceURLPrefix();
void Avian_avian_SystemClassLoader_vmClass();
void Avian_avian_avianvmresource_Handler_00024ResourceInputStream_available();
void Avian_avian_avianvmresource_Handler_00024ResourceInputStream_close();
void Avian_avian_avianvmresource_Handler_00024ResourceInputStream_getContentLength();
void Avian_avian_avianvmresource_Handler_00024ResourceInputStream_open();
void Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI();
void Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI_3BII();
void Avian_java_lang_Class_getEnclosingClass();
void Avian_java_lang_Class_getEnclosingConstructor();
void Avian_java_lang_Class_getEnclosingMethod();
void Avian_java_lang_Class_getSuperclass();
void Avian_java_lang_Object_clone();
void Avian_java_lang_Object_getVMClass();
void Avian_java_lang_Object_hashCode();
void Avian_java_lang_Object_notify();
void Avian_java_lang_Object_notifyAll();
void Avian_java_lang_Object_toString();
void Avian_java_lang_Object_wait();
void Avian_java_lang_Runtime_exit();
void Avian_java_lang_Runtime_freeMemory();
void Avian_java_lang_Runtime_totalMemory();
void Avian_java_nio_FixedArrayByteBuffer_allocateFixed();
void Avian_sun_misc_Perf_createLong();
void Avian_sun_misc_Perf_registerNatives();
void Avian_sun_misc_Unsafe_addressSize();
void Avian_sun_misc_Unsafe_allocateInstance();
void Avian_sun_misc_Unsafe_allocateMemory();
void Avian_sun_misc_Unsafe_arrayBaseOffset();
void Avian_sun_misc_Unsafe_arrayIndexScale();
void Avian_sun_misc_Unsafe_compareAndSwapInt();
void Avian_sun_misc_Unsafe_compareAndSwapLong();
void Avian_sun_misc_Unsafe_compareAndSwapObject();
void Avian_sun_misc_Unsafe_copyMemory();
void Avian_sun_misc_Unsafe_defineClass__Ljava_lang_String_2_3BIILjava_lang_ClassLoader_2Ljava_security_ProtectionDomain_2();
void Avian_sun_misc_Unsafe_ensureClassInitialized();
void Avian_sun_misc_Unsafe_freeMemory();
void Avian_sun_misc_Unsafe_getAddress__J();
void Avian_sun_misc_Unsafe_getBooleanVolatile();
void Avian_sun_misc_Unsafe_getBoolean__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_getByteVolatile();
void Avian_sun_misc_Unsafe_getByte__J();
void Avian_sun_misc_Unsafe_getByte__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_getCharVolatile();
void Avian_sun_misc_Unsafe_getChar__J();
void Avian_sun_misc_Unsafe_getChar__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_getDoubleVolatile();
void Avian_sun_misc_Unsafe_getDouble__J();
void Avian_sun_misc_Unsafe_getDouble__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_getFloatVolatile();
void Avian_sun_misc_Unsafe_getFloat__J();
void Avian_sun_misc_Unsafe_getFloat__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_getIntVolatile();
void Avian_sun_misc_Unsafe_getInt__J();
void Avian_sun_misc_Unsafe_getInt__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_getLongVolatile();
void Avian_sun_misc_Unsafe_getLong__J();
void Avian_sun_misc_Unsafe_getLong__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_getObject();
void Avian_sun_misc_Unsafe_getObjectVolatile();
void Avian_sun_misc_Unsafe_getShortVolatile();
void Avian_sun_misc_Unsafe_getShort__J();
void Avian_sun_misc_Unsafe_getShort__Ljava_lang_Object_2J();
void Avian_sun_misc_Unsafe_monitorEnter();
void Avian_sun_misc_Unsafe_monitorExit();
void Avian_sun_misc_Unsafe_objectFieldOffset();
void Avian_sun_misc_Unsafe_pageSize();
void Avian_sun_misc_Unsafe_park();
void Avian_sun_misc_Unsafe_putAddress__JJ();
void Avian_sun_misc_Unsafe_putBooleanVolatile();
void Avian_sun_misc_Unsafe_putBoolean__Ljava_lang_Object_2JZ();
void Avian_sun_misc_Unsafe_putByteVolatile();
void Avian_sun_misc_Unsafe_putByte__JB();
void Avian_sun_misc_Unsafe_putByte__Ljava_lang_Object_2JB();
void Avian_sun_misc_Unsafe_putCharVolatile();
void Avian_sun_misc_Unsafe_putChar__JC();
void Avian_sun_misc_Unsafe_putChar__Ljava_lang_Object_2JC();
void Avian_sun_misc_Unsafe_putDoubleVolatile();
void Avian_sun_misc_Unsafe_putDouble__JD();
void Avian_sun_misc_Unsafe_putDouble__Ljava_lang_Object_2JD();
void Avian_sun_misc_Unsafe_putFloatVolatile();
void Avian_sun_misc_Unsafe_putFloat__JF();
void Avian_sun_misc_Unsafe_putFloat__Ljava_lang_Object_2JF();
void Avian_sun_misc_Unsafe_putIntVolatile();
void Avian_sun_misc_Unsafe_putInt__JI();
void Avian_sun_misc_Unsafe_putInt__Ljava_lang_Object_2JI();
void Avian_sun_misc_Unsafe_putLongVolatile();
void Avian_sun_misc_Unsafe_putLong__JJ();
void Avian_sun_misc_Unsafe_putLong__Ljava_lang_Object_2JJ();
void Avian_sun_misc_Unsafe_putObject();
void Avian_sun_misc_Unsafe_putObjectVolatile();
void Avian_sun_misc_Unsafe_putOrderedInt();
void Avian_sun_misc_Unsafe_putOrderedLong();
void Avian_sun_misc_Unsafe_putOrderedObject();
void Avian_sun_misc_Unsafe_putShortVolatile();
void Avian_sun_misc_Unsafe_putShort__JS();
void Avian_sun_misc_Unsafe_putShort__Ljava_lang_Object_2JS();
void Avian_sun_misc_Unsafe_registerNatives();
void Avian_sun_misc_Unsafe_setMemory();
void Avian_sun_misc_Unsafe_staticFieldBase();
void Avian_sun_misc_Unsafe_staticFieldOffset();
void Avian_sun_misc_Unsafe_throwException();
void Avian_sun_misc_Unsafe_unpark();
void JVM_Accept();
void JVM_ActiveProcessorCount();
void JVM_AllocateNewArray();
void JVM_AllocateNewObject();
void JVM_ArrayCopy();
void JVM_AssertionStatusDirectives();
void JVM_Available();
void JVM_Bind();
void JVM_CX8Field();
void JVM_ClassDepth();
void JVM_ClassLoaderDepth();
void JVM_Clone();
void JVM_Close();
void JVM_CompileClass();
void JVM_CompileClasses();
void JVM_CompilerCommand();
void JVM_Connect();
void JVM_ConstantPoolGetClassAt();
void JVM_ConstantPoolGetClassAtIfLoaded();
void JVM_ConstantPoolGetDoubleAt();
void JVM_ConstantPoolGetFieldAt();
void JVM_ConstantPoolGetFieldAtIfLoaded();
void JVM_ConstantPoolGetFloatAt();
void JVM_ConstantPoolGetIntAt();
void JVM_ConstantPoolGetLongAt();
void JVM_ConstantPoolGetMemberRefInfoAt();
void JVM_ConstantPoolGetMethodAt();
void JVM_ConstantPoolGetMethodAtIfLoaded();
void JVM_ConstantPoolGetSize();
void JVM_ConstantPoolGetStringAt();
void JVM_ConstantPoolGetUTF8At();
void JVM_CountStackFrames();
void JVM_CurrentClassLoader();
void JVM_CurrentLoadedClass();
void JVM_CurrentThread();
void JVM_CurrentTimeMillis();
void JVM_DefineClass();
void JVM_DefineClassWithSource();
void JVM_DefineClassWithSourceCond();
void JVM_DesiredAssertionStatus();
void JVM_DisableCompiler();
void JVM_DoPrivileged();
void JVM_DumpAllStacks();
void JVM_DumpThreads();
void JVM_EnableCompiler();
void JVM_Exit();
void JVM_FillInStackTrace();
void JVM_FindClassFromBootLoader();
void JVM_FindClassFromCaller();
void JVM_FindClassFromClass();
void JVM_FindClassFromClassLoader();
void JVM_FindLibraryEntry();
void JVM_FindLoadedClass();
void JVM_FindPrimitiveClass();
void JVM_FindSignal();
void JVM_FreeMemory();
void JVM_GC();
void JVM_GetAllThreads();
void JVM_GetArrayElement();
void JVM_GetArrayLength();
void JVM_GetCPClassNameUTF();
void JVM_GetCPFieldClassNameUTF();
void JVM_GetCPFieldModifiers();
void JVM_GetCPFieldNameUTF();
void JVM_GetCPFieldSignatureUTF();
void JVM_GetCPMethodClassNameUTF();
void JVM_GetCPMethodModifiers();
void JVM_GetCPMethodNameUTF();
void JVM_GetCPMethodSignatureUTF();
void JVM_GetCallerClass();
void JVM_GetClassAccessFlags();
void JVM_GetClassAnnotations();
void JVM_GetClassCPEntriesCount();
void JVM_GetClassCPTypes();
void JVM_GetClassConstantPool();
void JVM_GetClassContext();
void JVM_GetClassDeclaredConstructors();
void JVM_GetClassDeclaredFields();
void JVM_GetClassDeclaredMethods();
void JVM_GetClassFieldsCount();
void JVM_GetClassInterfaces();
void JVM_GetClassLoader();
void JVM_GetClassMethodsCount();
void JVM_GetClassModifiers();
void JVM_GetClassName();
void JVM_GetClassNameUTF();
void JVM_GetClassSignature();
void JVM_GetClassSigners();
void JVM_GetClassTypeAnnotations();
void JVM_GetComponentType();
void JVM_GetDeclaredClasses();
void JVM_GetDeclaringClass();
void JVM_GetEnclosingMethodInfo();
void JVM_GetFieldIxModifiers();
void JVM_GetFieldTypeAnnotations();
void JVM_GetHostByAddr();
void JVM_GetHostByName();
void JVM_GetHostName();
void JVM_GetInheritedAccessControlContext();
void JVM_GetInterfaceVersion();
void JVM_GetLastErrorString();
void JVM_GetManagement();
void JVM_GetMethodIxArgsSize();
void JVM_GetMethodIxByteCode();
void JVM_GetMethodIxByteCodeLength();
void JVM_GetMethodIxExceptionIndexes();
void JVM_GetMethodIxExceptionTableEntry();
void JVM_GetMethodIxExceptionTableLength();
void JVM_GetMethodIxExceptionsCount();
void JVM_GetMethodIxLocalsCount();
void JVM_GetMethodIxMaxStack();
void JVM_GetMethodIxModifiers();
void JVM_GetMethodIxNameUTF();
void JVM_GetMethodIxSignatureUTF();
void JVM_GetMethodParameters();
void JVM_GetMethodTypeAnnotations();
void JVM_GetPrimitiveArrayElement();
void JVM_GetProtectionDomain();
void JVM_GetProtoByName();
void JVM_GetResourceLookupCache();
void JVM_GetResourceLookupCacheURLs();
void JVM_GetSockName();
void JVM_GetSockOpt();
void JVM_GetStackAccessControlContext();
void JVM_GetStackTraceDepth();
void JVM_GetStackTraceElement();
void JVM_GetSystemPackage();
void JVM_GetSystemPackages();
void JVM_GetTemporaryDirectory();
void JVM_GetThreadStateNames();
void JVM_GetThreadStateValues();
void JVM_GetVersionInfo();
void JVM_Halt();
void JVM_HoldsLock();
void JVM_IHashCode();
void JVM_InitAgentProperties();
void JVM_InitProperties();
void JVM_InitializeCompiler();
void JVM_InitializeSocketLibrary();
void JVM_InternString();
void JVM_Interrupt();
void JVM_InvokeMethod();
void JVM_IsArrayClass();
void JVM_IsConstructorIx();
void JVM_IsInterface();
void JVM_IsInterrupted();
void JVM_IsNaN();
void JVM_IsPrimitiveClass();
void JVM_IsSameClassPackage();
void JVM_IsSilentCompiler();
void JVM_IsSupportedJNIVersion();
void JVM_IsThreadAlive();
void JVM_IsVMGeneratedMethodIx();
void JVM_KnownToNotExist();
void JVM_LatestUserDefinedLoader();
void JVM_Listen();
void JVM_LoadClass0();
void JVM_LoadLibrary();
void JVM_Lseek();
void JVM_MaxMemory();
void JVM_MaxObjectInspectionAge();
void JVM_MonitorNotify();
void JVM_MonitorNotifyAll();
void JVM_MonitorWait();
void JVM_NanoTime();
void JVM_NativePath();
void JVM_NewArray();
void JVM_NewInstanceFromConstructor();
void JVM_NewMultiArray();
void JVM_OnExit();
void JVM_Open();
void JVM_PrintStackTrace();
void JVM_RaiseSignal();
void JVM_RawMonitorCreate();
void JVM_RawMonitorDestroy();
void JVM_RawMonitorEnter();
void JVM_RawMonitorExit();
void JVM_Read();
void JVM_Recv();
void JVM_RecvFrom();
void JVM_RegisterSignal();
void JVM_ReleaseUTF();
void JVM_ResolveClass();
void JVM_ResumeThread();
void JVM_Send();
void JVM_SendTo();
void JVM_SetArrayElement();
void JVM_SetClassSigners();
void JVM_SetLength();
void JVM_SetNativeThreadName();
void JVM_SetPrimitiveArrayElement();
void JVM_SetProtectionDomain();
void JVM_SetSockOpt();
void JVM_SetThreadPriority();
void JVM_Sleep();
void JVM_Socket();
void JVM_SocketAvailable();
void JVM_SocketClose();
void JVM_SocketShutdown();
void JVM_StartThread();
void JVM_StopThread();
void JVM_SupportsCX8();
void JVM_SuspendThread();
void JVM_Sync();
void JVM_Timeout();
void JVM_TotalMemory();
void JVM_TraceInstructions();
void JVM_TraceMethodCalls();
void JVM_UnloadLibrary();
void JVM_Write();
void JVM_Yield();
void Java_java_io_Console_echo();
void Java_java_io_Console_encoding();
void Java_java_io_Console_istty();
void Java_java_io_FileDescriptor_initIDs();
void Java_java_io_FileDescriptor_sync();
void Java_java_io_FileInputStream_available();
void Java_java_io_FileInputStream_close0();
void Java_java_io_FileInputStream_initIDs();
void Java_java_io_FileInputStream_open0();
void Java_java_io_FileInputStream_read0();
void Java_java_io_FileInputStream_readBytes();
void Java_java_io_FileInputStream_skip();
void Java_java_io_FileOutputStream_close0();
void Java_java_io_FileOutputStream_initIDs();
void Java_java_io_FileOutputStream_open0();
void Java_java_io_FileOutputStream_write();
void Java_java_io_FileOutputStream_writeBytes();
void Java_java_io_ObjectInputStream_bytesToDoubles();
void Java_java_io_ObjectInputStream_bytesToFloats();
void Java_java_io_ObjectOutputStream_doublesToBytes();
void Java_java_io_ObjectOutputStream_floatsToBytes();
void Java_java_io_ObjectStreamClass_hasStaticInitializer();
void Java_java_io_ObjectStreamClass_initNative();
void Java_java_io_RandomAccessFile_close0();
void Java_java_io_RandomAccessFile_getFilePointer();
void Java_java_io_RandomAccessFile_initIDs();
void Java_java_io_RandomAccessFile_length();
void Java_java_io_RandomAccessFile_open0();
void Java_java_io_RandomAccessFile_read0();
void Java_java_io_RandomAccessFile_readBytes();
void Java_java_io_RandomAccessFile_seek0();
void Java_java_io_RandomAccessFile_setLength();
void Java_java_io_RandomAccessFile_write0();
void Java_java_io_RandomAccessFile_writeBytes();
void Java_java_io_UnixFileSystem_canonicalize0();
void Java_java_io_UnixFileSystem_checkAccess();
void Java_java_io_UnixFileSystem_createDirectory();
void Java_java_io_UnixFileSystem_createFileExclusively();
void Java_java_io_UnixFileSystem_delete0();
void Java_java_io_UnixFileSystem_getBooleanAttributes0();
void Java_java_io_UnixFileSystem_getLastModifiedTime();
void Java_java_io_UnixFileSystem_getLength();
void Java_java_io_UnixFileSystem_getSpace();
void Java_java_io_UnixFileSystem_initIDs();
void Java_java_io_UnixFileSystem_list();
void Java_java_io_UnixFileSystem_rename0();
void Java_java_io_UnixFileSystem_setLastModifiedTime();
void Java_java_io_UnixFileSystem_setPermission();
void Java_java_io_UnixFileSystem_setReadOnly();
void Java_java_lang_ClassLoader_00024NativeLibrary_find();
void Java_java_lang_ClassLoader_00024NativeLibrary_load();
void Java_java_lang_ClassLoader_00024NativeLibrary_unload();
void Java_java_lang_ClassLoader_defineClass0();
void Java_java_lang_ClassLoader_defineClass1();
void Java_java_lang_ClassLoader_defineClass2();
void Java_java_lang_ClassLoader_findBootstrapClass();
void Java_java_lang_ClassLoader_findBuiltinLib();
void Java_java_lang_ClassLoader_findLoadedClass0();
void Java_java_lang_ClassLoader_registerNatives();
void Java_java_lang_ClassLoader_resolveClass0();
void Java_java_lang_Class_forName0();
void Java_java_lang_Class_getPrimitiveClass();
void Java_java_lang_Class_isAssignableFrom();
void Java_java_lang_Class_isInstance();
void Java_java_lang_Class_registerNatives();
void Java_java_lang_Compiler_registerNatives();
void Java_java_lang_Double_doubleToRawLongBits();
void Java_java_lang_Double_longBitsToDouble();
void Java_java_lang_Float_floatToRawIntBits();
void Java_java_lang_Float_intBitsToFloat();
void Java_java_lang_Object_getClass();
void Java_java_lang_Object_registerNatives();
void Java_java_lang_Package_getSystemPackage0();
void Java_java_lang_Package_getSystemPackages0();
void Java_java_lang_ProcessEnvironment_environ();
void Java_java_lang_Runtime_availableProcessors();
void Java_java_lang_Runtime_freeMemory();
void Java_java_lang_Runtime_gc();
void Java_java_lang_Runtime_maxMemory();
void Java_java_lang_Runtime_runFinalization0();
void Java_java_lang_Runtime_totalMemory();
void Java_java_lang_Runtime_traceInstructions();
void Java_java_lang_Runtime_traceMethodCalls();
void Java_java_lang_SecurityManager_classDepth();
void Java_java_lang_SecurityManager_classLoaderDepth0();
void Java_java_lang_SecurityManager_currentClassLoader0();
void Java_java_lang_SecurityManager_currentLoadedClass0();
void Java_java_lang_SecurityManager_getClassContext();
void Java_java_lang_Shutdown_halt0();
void Java_java_lang_Shutdown_runAllFinalizers();
void Java_java_lang_StrictMath_IEEEremainder();
void Java_java_lang_StrictMath_acos();
void Java_java_lang_StrictMath_asin();
void Java_java_lang_StrictMath_atan();
void Java_java_lang_StrictMath_atan2();
void Java_java_lang_StrictMath_cbrt();
void Java_java_lang_StrictMath_cos();
void Java_java_lang_StrictMath_cosh();
void Java_java_lang_StrictMath_exp();
void Java_java_lang_StrictMath_expm1();
void Java_java_lang_StrictMath_hypot();
void Java_java_lang_StrictMath_log();
void Java_java_lang_StrictMath_log10();
void Java_java_lang_StrictMath_log1p();
void Java_java_lang_StrictMath_pow();
void Java_java_lang_StrictMath_sin();
void Java_java_lang_StrictMath_sinh();
void Java_java_lang_StrictMath_sqrt();
void Java_java_lang_StrictMath_tan();
void Java_java_lang_StrictMath_tanh();
void Java_java_lang_String_intern();
void Java_java_lang_System_identityHashCode();
void Java_java_lang_System_initProperties();
void Java_java_lang_System_mapLibraryName();
void Java_java_lang_System_registerNatives();
void Java_java_lang_System_setErr0();
void Java_java_lang_System_setIn0();
void Java_java_lang_System_setOut0();
void Java_java_lang_Thread_registerNatives();
void Java_java_lang_Throwable_fillInStackTrace();
void Java_java_lang_Throwable_getStackTraceDepth();
void Java_java_lang_Throwable_getStackTraceElement();
void Java_java_lang_UNIXProcess_destroyProcess();
void Java_java_lang_UNIXProcess_forkAndExec();
void Java_java_lang_UNIXProcess_init();
void Java_java_lang_UNIXProcess_waitForProcessExit();
void Java_java_lang_reflect_Array_get();
void Java_java_lang_reflect_Array_getBoolean();
void Java_java_lang_reflect_Array_getByte();
void Java_java_lang_reflect_Array_getChar();
void Java_java_lang_reflect_Array_getDouble();
void Java_java_lang_reflect_Array_getFloat();
void Java_java_lang_reflect_Array_getInt();
void Java_java_lang_reflect_Array_getLength();
void Java_java_lang_reflect_Array_getLong();
void Java_java_lang_reflect_Array_getShort();
void Java_java_lang_reflect_Array_multiNewArray();
void Java_java_lang_reflect_Array_newArray();
void Java_java_lang_reflect_Array_set();
void Java_java_lang_reflect_Array_setBoolean();
void Java_java_lang_reflect_Array_setByte();
void Java_java_lang_reflect_Array_setChar();
void Java_java_lang_reflect_Array_setDouble();
void Java_java_lang_reflect_Array_setFloat();
void Java_java_lang_reflect_Array_setInt();
void Java_java_lang_reflect_Array_setLong();
void Java_java_lang_reflect_Array_setShort();
void Java_java_lang_reflect_Proxy_defineClass0();
void Java_java_net_DatagramPacket_init();
void Java_java_net_Inet4AddressImpl_getHostByAddr();
void Java_java_net_Inet4AddressImpl_getLocalHostName();
void Java_java_net_Inet4AddressImpl_isReachable0();
void Java_java_net_Inet4AddressImpl_lookupAllHostAddr();
void Java_java_net_Inet4Address_init();
void Java_java_net_Inet6AddressImpl_getHostByAddr();
void Java_java_net_Inet6AddressImpl_getLocalHostName();
void Java_java_net_Inet6AddressImpl_isReachable0();
void Java_java_net_Inet6AddressImpl_lookupAllHostAddr();
void Java_java_net_Inet6Address_init();
void Java_java_net_InetAddressImplFactory_isIPv6Supported();
void Java_java_net_InetAddress_init();
void Java_java_net_NetworkInterface_getAll();
void Java_java_net_NetworkInterface_getByIndex0();
void Java_java_net_NetworkInterface_getByInetAddress0();
void Java_java_net_NetworkInterface_getByName0();
void Java_java_net_NetworkInterface_getMTU0();
void Java_java_net_NetworkInterface_getMacAddr0();
void Java_java_net_NetworkInterface_init();
void Java_java_net_NetworkInterface_isLoopback0();
void Java_java_net_NetworkInterface_isP2P0();
void Java_java_net_NetworkInterface_isUp0();
void Java_java_net_NetworkInterface_supportsMulticast0();
void Java_java_net_PlainDatagramSocketImpl_bind0();
void Java_java_net_PlainDatagramSocketImpl_connect0();
void Java_java_net_PlainDatagramSocketImpl_dataAvailable();
void Java_java_net_PlainDatagramSocketImpl_datagramSocketClose();
void Java_java_net_PlainDatagramSocketImpl_datagramSocketCreate();
void Java_java_net_PlainDatagramSocketImpl_disconnect0();
void Java_java_net_PlainDatagramSocketImpl_getTTL();
void Java_java_net_PlainDatagramSocketImpl_getTimeToLive();
void Java_java_net_PlainDatagramSocketImpl_init();
void Java_java_net_PlainDatagramSocketImpl_join();
void Java_java_net_PlainDatagramSocketImpl_leave();
void Java_java_net_PlainDatagramSocketImpl_peek();
void Java_java_net_PlainDatagramSocketImpl_peekData();
void Java_java_net_PlainDatagramSocketImpl_receive0();
void Java_java_net_PlainDatagramSocketImpl_send();
void Java_java_net_PlainDatagramSocketImpl_setTTL();
void Java_java_net_PlainDatagramSocketImpl_setTimeToLive();
void Java_java_net_PlainDatagramSocketImpl_socketGetOption();
void Java_java_net_PlainDatagramSocketImpl_socketSetOption0();
void Java_java_net_PlainSocketImpl_initProto();
void Java_java_net_PlainSocketImpl_socketAccept();
void Java_java_net_PlainSocketImpl_socketAvailable();
void Java_java_net_PlainSocketImpl_socketBind();
void Java_java_net_PlainSocketImpl_socketClose0();
void Java_java_net_PlainSocketImpl_socketConnect();
void Java_java_net_PlainSocketImpl_socketCreate();
void Java_java_net_PlainSocketImpl_socketGetOption();
void Java_java_net_PlainSocketImpl_socketListen();
void Java_java_net_PlainSocketImpl_socketSendUrgentData();
void Java_java_net_PlainSocketImpl_socketSetOption0();
void Java_java_net_PlainSocketImpl_socketShutdown();
void Java_java_net_SocketInputStream_init();
void Java_java_net_SocketInputStream_socketRead0();
void Java_java_net_SocketOutputStream_init();
void Java_java_net_SocketOutputStream_socketWrite0();
void Java_java_nio_Bits_copyFromIntArray();
void Java_java_nio_Bits_copyFromLongArray();
void Java_java_nio_Bits_copyFromShortArray();
void Java_java_nio_Bits_copyToIntArray();
void Java_java_nio_Bits_copyToLongArray();
void Java_java_nio_Bits_copyToShortArray();
void Java_java_nio_MappedByteBuffer_force0();
void Java_java_nio_MappedByteBuffer_isLoaded0();
void Java_java_nio_MappedByteBuffer_load0();
void Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2();
void Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2Ljava_security_AccessControlContext_2();
void Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2();
void Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2Ljava_security_AccessControlContext_2();
void Java_java_security_AccessController_getInheritedAccessControlContext();
void Java_java_security_AccessController_getStackAccessControlContext();
void Java_java_util_TimeZone_getSystemGMTOffsetID();
void Java_java_util_TimeZone_getSystemTimeZoneID();
void Java_java_util_concurrent_atomic_AtomicLong_VMSupportsCS8();
void Java_java_util_jar_JarFile_getMetaInfEntryNames();
void Java_java_util_logging_FileHandler_isSetUID();
void Java_java_util_prefs_FileSystemPreferences_chmod();
void Java_java_util_prefs_FileSystemPreferences_lockFile0();
void Java_java_util_prefs_FileSystemPreferences_unlockFile0();
void Java_java_util_zip_Adler32_update();
void Java_java_util_zip_Adler32_updateByteBuffer();
void Java_java_util_zip_Adler32_updateBytes();
void Java_java_util_zip_CRC32_update();
void Java_java_util_zip_CRC32_updateByteBuffer();
void Java_java_util_zip_CRC32_updateBytes();
void Java_java_util_zip_Deflater_deflateBytes();
void Java_java_util_zip_Deflater_end();
void Java_java_util_zip_Deflater_getAdler();
void Java_java_util_zip_Deflater_init();
void Java_java_util_zip_Deflater_initIDs();
void Java_java_util_zip_Deflater_reset();
void Java_java_util_zip_Deflater_setDictionary();
void Java_java_util_zip_Inflater_end();
void Java_java_util_zip_Inflater_getAdler();
void Java_java_util_zip_Inflater_inflateBytes();
void Java_java_util_zip_Inflater_init();
void Java_java_util_zip_Inflater_initIDs();
void Java_java_util_zip_Inflater_reset();
void Java_java_util_zip_Inflater_setDictionary();
void Java_java_util_zip_ZipFile_close();
void Java_java_util_zip_ZipFile_freeEntry();
void Java_java_util_zip_ZipFile_getCommentBytes();
void Java_java_util_zip_ZipFile_getEntry();
void Java_java_util_zip_ZipFile_getEntryBytes();
void Java_java_util_zip_ZipFile_getEntryCSize();
void Java_java_util_zip_ZipFile_getEntryCrc();
void Java_java_util_zip_ZipFile_getEntryFlag();
void Java_java_util_zip_ZipFile_getEntryMethod();
void Java_java_util_zip_ZipFile_getEntrySize();
void Java_java_util_zip_ZipFile_getEntryTime();
void Java_java_util_zip_ZipFile_getNextEntry();
void Java_java_util_zip_ZipFile_getTotal();
void Java_java_util_zip_ZipFile_getZipMessage();
void Java_java_util_zip_ZipFile_initIDs();
void Java_java_util_zip_ZipFile_open();
void Java_java_util_zip_ZipFile_read();
void Java_java_util_zip_ZipFile_startsWithLOC();
void Java_sun_management_VMManagementImpl_getAvailableProcessors();
void Java_sun_management_VMManagementImpl_getClassInitializationTime();
void Java_sun_management_VMManagementImpl_getClassLoadingTime();
void Java_sun_management_VMManagementImpl_getClassVerificationTime();
void Java_sun_management_VMManagementImpl_getDaemonThreadCount();
void Java_sun_management_VMManagementImpl_getInitializedClassCount();
void Java_sun_management_VMManagementImpl_getLiveThreadCount();
void Java_sun_management_VMManagementImpl_getLoadedClassSize();
void Java_sun_management_VMManagementImpl_getMethodDataSize();
void Java_sun_management_VMManagementImpl_getPeakThreadCount();
void Java_sun_management_VMManagementImpl_getProcessId();
void Java_sun_management_VMManagementImpl_getSafepointCount();
void Java_sun_management_VMManagementImpl_getSafepointSyncTime();
void Java_sun_management_VMManagementImpl_getStartupTime();
void Java_sun_management_VMManagementImpl_getTotalApplicationNonStoppedTime();
void Java_sun_management_VMManagementImpl_getTotalClassCount();
void Java_sun_management_VMManagementImpl_getTotalCompileTime();
void Java_sun_management_VMManagementImpl_getTotalSafepointTime();
void Java_sun_management_VMManagementImpl_getTotalThreadCount();
void Java_sun_management_VMManagementImpl_getUnloadedClassCount();
void Java_sun_management_VMManagementImpl_getUnloadedClassSize();
void Java_sun_management_VMManagementImpl_getUptime0();
void Java_sun_management_VMManagementImpl_getVerboseClass();
void Java_sun_management_VMManagementImpl_getVerboseGC();
void Java_sun_management_VMManagementImpl_getVersion0();
void Java_sun_management_VMManagementImpl_getVmArguments0();
void Java_sun_management_VMManagementImpl_initOptionalSupportFields();
void Java_sun_management_VMManagementImpl_isThreadAllocatedMemoryEnabled();
void Java_sun_management_VMManagementImpl_isThreadContentionMonitoringEnabled();
void Java_sun_management_VMManagementImpl_isThreadCpuTimeEnabled();
void Java_sun_misc_GC_maxObjectInspectionAge();
void Java_sun_misc_MessageUtils_toStderr();
void Java_sun_misc_MessageUtils_toStdout();
void Java_sun_misc_NativeSignalHandler_handle0();
void Java_sun_misc_Signal_findSignal();
void Java_sun_misc_Signal_handle0();
void Java_sun_misc_Signal_raise0();
void Java_sun_misc_URLClassPath_getLookupCacheForClassLoader();
void Java_sun_misc_URLClassPath_getLookupCacheURLs();
void Java_sun_misc_URLClassPath_knownToNotExist0();
void Java_sun_misc_VMSupport_getVMTemporaryDirectory();
void Java_sun_misc_VMSupport_initAgentProperties();
void Java_sun_misc_VM_getThreadStateValues();
void Java_sun_misc_VM_initialize();
void Java_sun_misc_VM_latestUserDefinedLoader();
void Java_sun_misc_Version_getJdkSpecialVersion();
void Java_sun_misc_Version_getJdkVersionInfo();
void Java_sun_misc_Version_getJvmSpecialVersion();
void Java_sun_misc_Version_getJvmVersionInfo();
void Java_sun_net_ExtendedOptionsImpl_flowSupported();
void Java_sun_net_ExtendedOptionsImpl_getFlowOption();
void Java_sun_net_ExtendedOptionsImpl_init();
void Java_sun_net_ExtendedOptionsImpl_setFlowOption();
void Java_sun_net_dns_ResolverConfigurationImpl_fallbackDomain0();
void Java_sun_net_dns_ResolverConfigurationImpl_localDomain0();
void Java_sun_net_spi_DefaultProxySelector_getSystemProxy();
void Java_sun_net_spi_DefaultProxySelector_init();
void Java_sun_nio_ch_DatagramChannelImpl_disconnect0();
void Java_sun_nio_ch_DatagramChannelImpl_initIDs();
void Java_sun_nio_ch_DatagramChannelImpl_receive0();
void Java_sun_nio_ch_DatagramChannelImpl_send0();
void Java_sun_nio_ch_DatagramDispatcher_read0();
void Java_sun_nio_ch_DatagramDispatcher_readv0();
void Java_sun_nio_ch_DatagramDispatcher_write0();
void Java_sun_nio_ch_DatagramDispatcher_writev0();
void Java_sun_nio_ch_EPollArrayWrapper_epollCreate();
void Java_sun_nio_ch_EPollArrayWrapper_epollCtl();
void Java_sun_nio_ch_EPollArrayWrapper_epollWait();
void Java_sun_nio_ch_EPollArrayWrapper_init();
void Java_sun_nio_ch_EPollArrayWrapper_interrupt();
void Java_sun_nio_ch_EPollArrayWrapper_offsetofData();
void Java_sun_nio_ch_EPollArrayWrapper_sizeofEPollEvent();
void Java_sun_nio_ch_FileChannelImpl_close0();
void Java_sun_nio_ch_FileChannelImpl_initIDs();
void Java_sun_nio_ch_FileChannelImpl_map0();
void Java_sun_nio_ch_FileChannelImpl_position0();
void Java_sun_nio_ch_FileChannelImpl_transferTo0();
void Java_sun_nio_ch_FileChannelImpl_unmap0();
void Java_sun_nio_ch_FileDispatcherImpl_close0();
void Java_sun_nio_ch_FileDispatcherImpl_closeIntFD();
void Java_sun_nio_ch_FileDispatcherImpl_force0();
void Java_sun_nio_ch_FileDispatcherImpl_init();
void Java_sun_nio_ch_FileDispatcherImpl_lock0();
void Java_sun_nio_ch_FileDispatcherImpl_preClose0();
void Java_sun_nio_ch_FileDispatcherImpl_pread0();
void Java_sun_nio_ch_FileDispatcherImpl_pwrite0();
void Java_sun_nio_ch_FileDispatcherImpl_read0();
void Java_sun_nio_ch_FileDispatcherImpl_readv0();
void Java_sun_nio_ch_FileDispatcherImpl_release0();
void Java_sun_nio_ch_FileDispatcherImpl_size0();
void Java_sun_nio_ch_FileDispatcherImpl_truncate0();
void Java_sun_nio_ch_FileDispatcherImpl_write0();
void Java_sun_nio_ch_FileDispatcherImpl_writev0();
void Java_sun_nio_ch_FileKey_init();
void Java_sun_nio_ch_FileKey_initIDs();
void Java_sun_nio_ch_IOUtil_configureBlocking();
void Java_sun_nio_ch_IOUtil_drain();
void Java_sun_nio_ch_IOUtil_fdLimit();
void Java_sun_nio_ch_IOUtil_fdVal();
void Java_sun_nio_ch_IOUtil_initIDs();
void Java_sun_nio_ch_IOUtil_iovMax();
void Java_sun_nio_ch_IOUtil_makePipe();
void Java_sun_nio_ch_IOUtil_randomBytes();
void Java_sun_nio_ch_IOUtil_setfdVal();
void Java_sun_nio_ch_InheritedChannel_close0();
void Java_sun_nio_ch_InheritedChannel_dup();
void Java_sun_nio_ch_InheritedChannel_dup2();
void Java_sun_nio_ch_InheritedChannel_open0();
void Java_sun_nio_ch_InheritedChannel_peerAddress0();
void Java_sun_nio_ch_InheritedChannel_peerPort0();
void Java_sun_nio_ch_InheritedChannel_soType0();
void Java_sun_nio_ch_NativeThread_current();
void Java_sun_nio_ch_NativeThread_init();
void Java_sun_nio_ch_NativeThread_signal();
void Java_sun_nio_ch_Net_bind0();
void Java_sun_nio_ch_Net_blockOrUnblock4();
void Java_sun_nio_ch_Net_blockOrUnblock6();
void Java_sun_nio_ch_Net_canIPv6SocketJoinIPv4Group0();
void Java_sun_nio_ch_Net_canJoin6WithIPv4Group0();
void Java_sun_nio_ch_Net_connect0();
void Java_sun_nio_ch_Net_getIntOption0();
void Java_sun_nio_ch_Net_getInterface4();
void Java_sun_nio_ch_Net_getInterface6();
void Java_sun_nio_ch_Net_initIDs();
void Java_sun_nio_ch_Net_isExclusiveBindAvailable();
void Java_sun_nio_ch_Net_isIPv6Available0();
void Java_sun_nio_ch_Net_joinOrDrop4();
void Java_sun_nio_ch_Net_joinOrDrop6();
void Java_sun_nio_ch_Net_listen();
void Java_sun_nio_ch_Net_localInetAddress();
void Java_sun_nio_ch_Net_localPort();
void Java_sun_nio_ch_Net_poll();
void Java_sun_nio_ch_Net_pollconnValue();
void Java_sun_nio_ch_Net_pollerrValue();
void Java_sun_nio_ch_Net_pollhupValue();
void Java_sun_nio_ch_Net_pollinValue();
void Java_sun_nio_ch_Net_pollnvalValue();
void Java_sun_nio_ch_Net_polloutValue();
void Java_sun_nio_ch_Net_setIntOption0();
void Java_sun_nio_ch_Net_setInterface4();
void Java_sun_nio_ch_Net_setInterface6();
void Java_sun_nio_ch_Net_shutdown();
void Java_sun_nio_ch_Net_socket0();
void Java_sun_nio_ch_PollArrayWrapper_interrupt();
void Java_sun_nio_ch_PollArrayWrapper_poll0();
void Java_sun_nio_ch_ServerSocketChannelImpl_accept0();
void Java_sun_nio_ch_ServerSocketChannelImpl_initIDs();
void Java_sun_nio_ch_SocketChannelImpl_checkConnect();
void Java_sun_nio_ch_SocketChannelImpl_sendOutOfBandData();
void Java_sun_nio_fs_UnixNativeDispatcher_access0();
void Java_sun_nio_fs_UnixNativeDispatcher_chmod0();
void Java_sun_nio_fs_UnixNativeDispatcher_chown0();
void Java_sun_nio_fs_UnixNativeDispatcher_close();
void Java_sun_nio_fs_UnixNativeDispatcher_closedir();
void Java_sun_nio_fs_UnixNativeDispatcher_dup();
void Java_sun_nio_fs_UnixNativeDispatcher_fchmod();
void Java_sun_nio_fs_UnixNativeDispatcher_fchown();
void Java_sun_nio_fs_UnixNativeDispatcher_fclose();
void Java_sun_nio_fs_UnixNativeDispatcher_fdopendir();
void Java_sun_nio_fs_UnixNativeDispatcher_fopen0();
void Java_sun_nio_fs_UnixNativeDispatcher_fpathconf();
void Java_sun_nio_fs_UnixNativeDispatcher_fstat();
void Java_sun_nio_fs_UnixNativeDispatcher_fstatat0();
void Java_sun_nio_fs_UnixNativeDispatcher_futimes();
void Java_sun_nio_fs_UnixNativeDispatcher_getcwd();
void Java_sun_nio_fs_UnixNativeDispatcher_getgrgid();
void Java_sun_nio_fs_UnixNativeDispatcher_getgrnam0();
void Java_sun_nio_fs_UnixNativeDispatcher_getpwnam0();
void Java_sun_nio_fs_UnixNativeDispatcher_getpwuid();
void Java_sun_nio_fs_UnixNativeDispatcher_init();
void Java_sun_nio_fs_UnixNativeDispatcher_lchown0();
void Java_sun_nio_fs_UnixNativeDispatcher_link0();
void Java_sun_nio_fs_UnixNativeDispatcher_lstat0();
void Java_sun_nio_fs_UnixNativeDispatcher_mkdir0();
void Java_sun_nio_fs_UnixNativeDispatcher_mknod0();
void Java_sun_nio_fs_UnixNativeDispatcher_open0();
void Java_sun_nio_fs_UnixNativeDispatcher_openat0();
void Java_sun_nio_fs_UnixNativeDispatcher_opendir0();
void Java_sun_nio_fs_UnixNativeDispatcher_pathconf0();
void Java_sun_nio_fs_UnixNativeDispatcher_read();
void Java_sun_nio_fs_UnixNativeDispatcher_readdir();
void Java_sun_nio_fs_UnixNativeDispatcher_readlink0();
void Java_sun_nio_fs_UnixNativeDispatcher_realpath0();
void Java_sun_nio_fs_UnixNativeDispatcher_rename0();
void Java_sun_nio_fs_UnixNativeDispatcher_renameat0();
void Java_sun_nio_fs_UnixNativeDispatcher_rmdir0();
void Java_sun_nio_fs_UnixNativeDispatcher_stat0();
void Java_sun_nio_fs_UnixNativeDispatcher_statvfs0();
void Java_sun_nio_fs_UnixNativeDispatcher_strerror();
void Java_sun_nio_fs_UnixNativeDispatcher_symlink0();
void Java_sun_nio_fs_UnixNativeDispatcher_unlink0();
void Java_sun_nio_fs_UnixNativeDispatcher_unlinkat0();
void Java_sun_nio_fs_UnixNativeDispatcher_utimes0();
void Java_sun_nio_fs_UnixNativeDispatcher_write();
void Java_sun_reflect_ConstantPool_getClassAt0();
void Java_sun_reflect_ConstantPool_getClassAtIfLoaded0();
void Java_sun_reflect_ConstantPool_getDoubleAt0();
void Java_sun_reflect_ConstantPool_getFieldAt0();
void Java_sun_reflect_ConstantPool_getFieldAtIfLoaded0();
void Java_sun_reflect_ConstantPool_getFloatAt0();
void Java_sun_reflect_ConstantPool_getIntAt0();
void Java_sun_reflect_ConstantPool_getLongAt0();
void Java_sun_reflect_ConstantPool_getMemberRefInfoAt0();
void Java_sun_reflect_ConstantPool_getMethodAt0();
void Java_sun_reflect_ConstantPool_getMethodAtIfLoaded0();
void Java_sun_reflect_ConstantPool_getSize0();
void Java_sun_reflect_ConstantPool_getStringAt0();
void Java_sun_reflect_ConstantPool_getUTF8At0();
void Java_sun_reflect_NativeConstructorAccessorImpl_newInstance0();
void Java_sun_reflect_NativeMethodAccessorImpl_invoke0();
void Java_sun_reflect_Reflection_getCallerClass__();
void Java_sun_reflect_Reflection_getCallerClass__I();
void Java_sun_reflect_Reflection_getClassAccessFlags();
}

namespace {
    struct entry {
        const char *name;
        const void *addr;
    } entries[] = {
{ "Avian_avian_Classes_acquireClassLock", vm::voidPointer(Avian_avian_Classes_acquireClassLock) },
{ "Avian_avian_Classes_defineVMClass", vm::voidPointer(Avian_avian_Classes_defineVMClass) },
{ "Avian_avian_Classes_initialize", vm::voidPointer(Avian_avian_Classes_initialize) },
{ "Avian_avian_Classes_makeString", vm::voidPointer(Avian_avian_Classes_makeString) },
{ "Avian_avian_Classes_primitiveClass", vm::voidPointer(Avian_avian_Classes_primitiveClass) },
{ "Avian_avian_Classes_releaseClassLock", vm::voidPointer(Avian_avian_Classes_releaseClassLock) },
{ "Avian_avian_Classes_resolveVMClass", vm::voidPointer(Avian_avian_Classes_resolveVMClass) },
{ "Avian_avian_Classes_toVMClass", vm::voidPointer(Avian_avian_Classes_toVMClass) },
{ "Avian_avian_Classes_toVMMethod", vm::voidPointer(Avian_avian_Classes_toVMMethod) },
{ "Avian_avian_Continuations_00024Continuation_handleException", vm::voidPointer(Avian_avian_Continuations_00024Continuation_handleException) },
{ "Avian_avian_Continuations_00024Continuation_handleResult", vm::voidPointer(Avian_avian_Continuations_00024Continuation_handleResult) },
{ "Avian_avian_Continuations_callWithCurrentContinuation", vm::voidPointer(Avian_avian_Continuations_callWithCurrentContinuation) },
{ "Avian_avian_Continuations_dynamicWind2", vm::voidPointer(Avian_avian_Continuations_dynamicWind2) },
{ "Avian_avian_Machine_dumpHeap", vm::voidPointer(Avian_avian_Machine_dumpHeap) },
{ "Avian_avian_Machine_tryNative", vm::voidPointer(Avian_avian_Machine_tryNative) },
{ "Avian_avian_Singleton_getInt", vm::voidPointer(Avian_avian_Singleton_getInt) },
{ "Avian_avian_Singleton_getLong", vm::voidPointer(Avian_avian_Singleton_getLong) },
{ "Avian_avian_Singleton_getObject", vm::voidPointer(Avian_avian_Singleton_getObject) },
{ "Avian_avian_SystemClassLoader_00024ResourceEnumeration_nextResourceURLPrefix", vm::voidPointer(Avian_avian_SystemClassLoader_00024ResourceEnumeration_nextResourceURLPrefix) },
{ "Avian_avian_SystemClassLoader_appLoader", vm::voidPointer(Avian_avian_SystemClassLoader_appLoader) },
{ "Avian_avian_SystemClassLoader_findLoadedVMClass", vm::voidPointer(Avian_avian_SystemClassLoader_findLoadedVMClass) },
{ "Avian_avian_SystemClassLoader_findVMClass", vm::voidPointer(Avian_avian_SystemClassLoader_findVMClass) },
{ "Avian_avian_SystemClassLoader_getClass", vm::voidPointer(Avian_avian_SystemClassLoader_getClass) },
{ "Avian_avian_SystemClassLoader_getPackageSource", vm::voidPointer(Avian_avian_SystemClassLoader_getPackageSource) },
{ "Avian_avian_SystemClassLoader_resourceURLPrefix", vm::voidPointer(Avian_avian_SystemClassLoader_resourceURLPrefix) },
{ "Avian_avian_SystemClassLoader_vmClass", vm::voidPointer(Avian_avian_SystemClassLoader_vmClass) },
{ "Avian_avian_avianvmresource_Handler_00024ResourceInputStream_available", vm::voidPointer(Avian_avian_avianvmresource_Handler_00024ResourceInputStream_available) },
{ "Avian_avian_avianvmresource_Handler_00024ResourceInputStream_close", vm::voidPointer(Avian_avian_avianvmresource_Handler_00024ResourceInputStream_close) },
{ "Avian_avian_avianvmresource_Handler_00024ResourceInputStream_getContentLength", vm::voidPointer(Avian_avian_avianvmresource_Handler_00024ResourceInputStream_getContentLength) },
{ "Avian_avian_avianvmresource_Handler_00024ResourceInputStream_open", vm::voidPointer(Avian_avian_avianvmresource_Handler_00024ResourceInputStream_open) },
{ "Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI", vm::voidPointer(Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI) },
{ "Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI_3BII", vm::voidPointer(Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI_3BII) },
{ "Avian_java_lang_Class_getEnclosingClass", vm::voidPointer(Avian_java_lang_Class_getEnclosingClass) },
{ "Avian_java_lang_Class_getEnclosingConstructor", vm::voidPointer(Avian_java_lang_Class_getEnclosingConstructor) },
{ "Avian_java_lang_Class_getEnclosingMethod", vm::voidPointer(Avian_java_lang_Class_getEnclosingMethod) },
{ "Avian_java_lang_Class_getSuperclass", vm::voidPointer(Avian_java_lang_Class_getSuperclass) },
{ "Avian_java_lang_Object_clone", vm::voidPointer(Avian_java_lang_Object_clone) },
{ "Avian_java_lang_Object_getVMClass", vm::voidPointer(Avian_java_lang_Object_getVMClass) },
{ "Avian_java_lang_Object_hashCode", vm::voidPointer(Avian_java_lang_Object_hashCode) },
{ "Avian_java_lang_Object_notify", vm::voidPointer(Avian_java_lang_Object_notify) },
{ "Avian_java_lang_Object_notifyAll", vm::voidPointer(Avian_java_lang_Object_notifyAll) },
{ "Avian_java_lang_Object_toString", vm::voidPointer(Avian_java_lang_Object_toString) },
{ "Avian_java_lang_Object_wait", vm::voidPointer(Avian_java_lang_Object_wait) },
{ "Avian_java_lang_Runtime_exit", vm::voidPointer(Avian_java_lang_Runtime_exit) },
{ "Avian_java_lang_Runtime_freeMemory", vm::voidPointer(Avian_java_lang_Runtime_freeMemory) },
{ "Avian_java_lang_Runtime_totalMemory", vm::voidPointer(Avian_java_lang_Runtime_totalMemory) },
{ "Avian_java_nio_FixedArrayByteBuffer_allocateFixed", vm::voidPointer(Avian_java_nio_FixedArrayByteBuffer_allocateFixed) },
{ "Avian_sun_misc_Perf_createLong", vm::voidPointer(Avian_sun_misc_Perf_createLong) },
{ "Avian_sun_misc_Perf_registerNatives", vm::voidPointer(Avian_sun_misc_Perf_registerNatives) },
{ "Avian_sun_misc_Unsafe_addressSize", vm::voidPointer(Avian_sun_misc_Unsafe_addressSize) },
{ "Avian_sun_misc_Unsafe_allocateInstance", vm::voidPointer(Avian_sun_misc_Unsafe_allocateInstance) },
{ "Avian_sun_misc_Unsafe_allocateMemory", vm::voidPointer(Avian_sun_misc_Unsafe_allocateMemory) },
{ "Avian_sun_misc_Unsafe_arrayBaseOffset", vm::voidPointer(Avian_sun_misc_Unsafe_arrayBaseOffset) },
{ "Avian_sun_misc_Unsafe_arrayIndexScale", vm::voidPointer(Avian_sun_misc_Unsafe_arrayIndexScale) },
{ "Avian_sun_misc_Unsafe_compareAndSwapInt", vm::voidPointer(Avian_sun_misc_Unsafe_compareAndSwapInt) },
{ "Avian_sun_misc_Unsafe_compareAndSwapLong", vm::voidPointer(Avian_sun_misc_Unsafe_compareAndSwapLong) },
{ "Avian_sun_misc_Unsafe_compareAndSwapObject", vm::voidPointer(Avian_sun_misc_Unsafe_compareAndSwapObject) },
{ "Avian_sun_misc_Unsafe_copyMemory", vm::voidPointer(Avian_sun_misc_Unsafe_copyMemory) },
{ "Avian_sun_misc_Unsafe_defineClass__Ljava_lang_String_2_3BIILjava_lang_ClassLoader_2Ljava_security_ProtectionDomain_2", vm::voidPointer(Avian_sun_misc_Unsafe_defineClass__Ljava_lang_String_2_3BIILjava_lang_ClassLoader_2Ljava_security_ProtectionDomain_2) },
{ "Avian_sun_misc_Unsafe_ensureClassInitialized", vm::voidPointer(Avian_sun_misc_Unsafe_ensureClassInitialized) },
{ "Avian_sun_misc_Unsafe_freeMemory", vm::voidPointer(Avian_sun_misc_Unsafe_freeMemory) },
{ "Avian_sun_misc_Unsafe_getAddress__J", vm::voidPointer(Avian_sun_misc_Unsafe_getAddress__J) },
{ "Avian_sun_misc_Unsafe_getBooleanVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getBooleanVolatile) },
{ "Avian_sun_misc_Unsafe_getBoolean__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getBoolean__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_getByteVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getByteVolatile) },
{ "Avian_sun_misc_Unsafe_getByte__J", vm::voidPointer(Avian_sun_misc_Unsafe_getByte__J) },
{ "Avian_sun_misc_Unsafe_getByte__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getByte__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_getCharVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getCharVolatile) },
{ "Avian_sun_misc_Unsafe_getChar__J", vm::voidPointer(Avian_sun_misc_Unsafe_getChar__J) },
{ "Avian_sun_misc_Unsafe_getChar__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getChar__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_getDoubleVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getDoubleVolatile) },
{ "Avian_sun_misc_Unsafe_getDouble__J", vm::voidPointer(Avian_sun_misc_Unsafe_getDouble__J) },
{ "Avian_sun_misc_Unsafe_getDouble__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getDouble__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_getFloatVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getFloatVolatile) },
{ "Avian_sun_misc_Unsafe_getFloat__J", vm::voidPointer(Avian_sun_misc_Unsafe_getFloat__J) },
{ "Avian_sun_misc_Unsafe_getFloat__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getFloat__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_getIntVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getIntVolatile) },
{ "Avian_sun_misc_Unsafe_getInt__J", vm::voidPointer(Avian_sun_misc_Unsafe_getInt__J) },
{ "Avian_sun_misc_Unsafe_getInt__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getInt__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_getLongVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getLongVolatile) },
{ "Avian_sun_misc_Unsafe_getLong__J", vm::voidPointer(Avian_sun_misc_Unsafe_getLong__J) },
{ "Avian_sun_misc_Unsafe_getLong__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getLong__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_getObject", vm::voidPointer(Avian_sun_misc_Unsafe_getObject) },
{ "Avian_sun_misc_Unsafe_getObjectVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getObjectVolatile) },
{ "Avian_sun_misc_Unsafe_getShortVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_getShortVolatile) },
{ "Avian_sun_misc_Unsafe_getShort__J", vm::voidPointer(Avian_sun_misc_Unsafe_getShort__J) },
{ "Avian_sun_misc_Unsafe_getShort__Ljava_lang_Object_2J", vm::voidPointer(Avian_sun_misc_Unsafe_getShort__Ljava_lang_Object_2J) },
{ "Avian_sun_misc_Unsafe_monitorEnter", vm::voidPointer(Avian_sun_misc_Unsafe_monitorEnter) },
{ "Avian_sun_misc_Unsafe_monitorExit", vm::voidPointer(Avian_sun_misc_Unsafe_monitorExit) },
{ "Avian_sun_misc_Unsafe_objectFieldOffset", vm::voidPointer(Avian_sun_misc_Unsafe_objectFieldOffset) },
{ "Avian_sun_misc_Unsafe_pageSize", vm::voidPointer(Avian_sun_misc_Unsafe_pageSize) },
{ "Avian_sun_misc_Unsafe_park", vm::voidPointer(Avian_sun_misc_Unsafe_park) },
{ "Avian_sun_misc_Unsafe_putAddress__JJ", vm::voidPointer(Avian_sun_misc_Unsafe_putAddress__JJ) },
{ "Avian_sun_misc_Unsafe_putBooleanVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putBooleanVolatile) },
{ "Avian_sun_misc_Unsafe_putBoolean__Ljava_lang_Object_2JZ", vm::voidPointer(Avian_sun_misc_Unsafe_putBoolean__Ljava_lang_Object_2JZ) },
{ "Avian_sun_misc_Unsafe_putByteVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putByteVolatile) },
{ "Avian_sun_misc_Unsafe_putByte__JB", vm::voidPointer(Avian_sun_misc_Unsafe_putByte__JB) },
{ "Avian_sun_misc_Unsafe_putByte__Ljava_lang_Object_2JB", vm::voidPointer(Avian_sun_misc_Unsafe_putByte__Ljava_lang_Object_2JB) },
{ "Avian_sun_misc_Unsafe_putCharVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putCharVolatile) },
{ "Avian_sun_misc_Unsafe_putChar__JC", vm::voidPointer(Avian_sun_misc_Unsafe_putChar__JC) },
{ "Avian_sun_misc_Unsafe_putChar__Ljava_lang_Object_2JC", vm::voidPointer(Avian_sun_misc_Unsafe_putChar__Ljava_lang_Object_2JC) },
{ "Avian_sun_misc_Unsafe_putDoubleVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putDoubleVolatile) },
{ "Avian_sun_misc_Unsafe_putDouble__JD", vm::voidPointer(Avian_sun_misc_Unsafe_putDouble__JD) },
{ "Avian_sun_misc_Unsafe_putDouble__Ljava_lang_Object_2JD", vm::voidPointer(Avian_sun_misc_Unsafe_putDouble__Ljava_lang_Object_2JD) },
{ "Avian_sun_misc_Unsafe_putFloatVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putFloatVolatile) },
{ "Avian_sun_misc_Unsafe_putFloat__JF", vm::voidPointer(Avian_sun_misc_Unsafe_putFloat__JF) },
{ "Avian_sun_misc_Unsafe_putFloat__Ljava_lang_Object_2JF", vm::voidPointer(Avian_sun_misc_Unsafe_putFloat__Ljava_lang_Object_2JF) },
{ "Avian_sun_misc_Unsafe_putIntVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putIntVolatile) },
{ "Avian_sun_misc_Unsafe_putInt__JI", vm::voidPointer(Avian_sun_misc_Unsafe_putInt__JI) },
{ "Avian_sun_misc_Unsafe_putInt__Ljava_lang_Object_2JI", vm::voidPointer(Avian_sun_misc_Unsafe_putInt__Ljava_lang_Object_2JI) },
{ "Avian_sun_misc_Unsafe_putLongVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putLongVolatile) },
{ "Avian_sun_misc_Unsafe_putLong__JJ", vm::voidPointer(Avian_sun_misc_Unsafe_putLong__JJ) },
{ "Avian_sun_misc_Unsafe_putLong__Ljava_lang_Object_2JJ", vm::voidPointer(Avian_sun_misc_Unsafe_putLong__Ljava_lang_Object_2JJ) },
{ "Avian_sun_misc_Unsafe_putObject", vm::voidPointer(Avian_sun_misc_Unsafe_putObject) },
{ "Avian_sun_misc_Unsafe_putObjectVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putObjectVolatile) },
{ "Avian_sun_misc_Unsafe_putOrderedInt", vm::voidPointer(Avian_sun_misc_Unsafe_putOrderedInt) },
{ "Avian_sun_misc_Unsafe_putOrderedLong", vm::voidPointer(Avian_sun_misc_Unsafe_putOrderedLong) },
{ "Avian_sun_misc_Unsafe_putOrderedObject", vm::voidPointer(Avian_sun_misc_Unsafe_putOrderedObject) },
{ "Avian_sun_misc_Unsafe_putShortVolatile", vm::voidPointer(Avian_sun_misc_Unsafe_putShortVolatile) },
{ "Avian_sun_misc_Unsafe_putShort__JS", vm::voidPointer(Avian_sun_misc_Unsafe_putShort__JS) },
{ "Avian_sun_misc_Unsafe_putShort__Ljava_lang_Object_2JS", vm::voidPointer(Avian_sun_misc_Unsafe_putShort__Ljava_lang_Object_2JS) },
{ "Avian_sun_misc_Unsafe_registerNatives", vm::voidPointer(Avian_sun_misc_Unsafe_registerNatives) },
{ "Avian_sun_misc_Unsafe_setMemory", vm::voidPointer(Avian_sun_misc_Unsafe_setMemory) },
{ "Avian_sun_misc_Unsafe_staticFieldBase", vm::voidPointer(Avian_sun_misc_Unsafe_staticFieldBase) },
{ "Avian_sun_misc_Unsafe_staticFieldOffset", vm::voidPointer(Avian_sun_misc_Unsafe_staticFieldOffset) },
{ "Avian_sun_misc_Unsafe_throwException", vm::voidPointer(Avian_sun_misc_Unsafe_throwException) },
{ "Avian_sun_misc_Unsafe_unpark", vm::voidPointer(Avian_sun_misc_Unsafe_unpark) },
{ "JVM_Accept", vm::voidPointer(JVM_Accept) },
{ "JVM_ActiveProcessorCount", vm::voidPointer(JVM_ActiveProcessorCount) },
{ "JVM_AllocateNewArray", vm::voidPointer(JVM_AllocateNewArray) },
{ "JVM_AllocateNewObject", vm::voidPointer(JVM_AllocateNewObject) },
{ "JVM_ArrayCopy", vm::voidPointer(JVM_ArrayCopy) },
{ "JVM_AssertionStatusDirectives", vm::voidPointer(JVM_AssertionStatusDirectives) },
{ "JVM_Available", vm::voidPointer(JVM_Available) },
{ "JVM_Bind", vm::voidPointer(JVM_Bind) },
{ "JVM_CX8Field", vm::voidPointer(JVM_CX8Field) },
{ "JVM_ClassDepth", vm::voidPointer(JVM_ClassDepth) },
{ "JVM_ClassLoaderDepth", vm::voidPointer(JVM_ClassLoaderDepth) },
{ "JVM_Clone", vm::voidPointer(JVM_Clone) },
{ "JVM_Close", vm::voidPointer(JVM_Close) },
{ "JVM_CompileClass", vm::voidPointer(JVM_CompileClass) },
{ "JVM_CompileClasses", vm::voidPointer(JVM_CompileClasses) },
{ "JVM_CompilerCommand", vm::voidPointer(JVM_CompilerCommand) },
{ "JVM_Connect", vm::voidPointer(JVM_Connect) },
{ "JVM_ConstantPoolGetClassAt", vm::voidPointer(JVM_ConstantPoolGetClassAt) },
{ "JVM_ConstantPoolGetClassAtIfLoaded", vm::voidPointer(JVM_ConstantPoolGetClassAtIfLoaded) },
{ "JVM_ConstantPoolGetDoubleAt", vm::voidPointer(JVM_ConstantPoolGetDoubleAt) },
{ "JVM_ConstantPoolGetFieldAt", vm::voidPointer(JVM_ConstantPoolGetFieldAt) },
{ "JVM_ConstantPoolGetFieldAtIfLoaded", vm::voidPointer(JVM_ConstantPoolGetFieldAtIfLoaded) },
{ "JVM_ConstantPoolGetFloatAt", vm::voidPointer(JVM_ConstantPoolGetFloatAt) },
{ "JVM_ConstantPoolGetIntAt", vm::voidPointer(JVM_ConstantPoolGetIntAt) },
{ "JVM_ConstantPoolGetLongAt", vm::voidPointer(JVM_ConstantPoolGetLongAt) },
{ "JVM_ConstantPoolGetMemberRefInfoAt", vm::voidPointer(JVM_ConstantPoolGetMemberRefInfoAt) },
{ "JVM_ConstantPoolGetMethodAt", vm::voidPointer(JVM_ConstantPoolGetMethodAt) },
{ "JVM_ConstantPoolGetMethodAtIfLoaded", vm::voidPointer(JVM_ConstantPoolGetMethodAtIfLoaded) },
{ "JVM_ConstantPoolGetSize", vm::voidPointer(JVM_ConstantPoolGetSize) },
{ "JVM_ConstantPoolGetStringAt", vm::voidPointer(JVM_ConstantPoolGetStringAt) },
{ "JVM_ConstantPoolGetUTF8At", vm::voidPointer(JVM_ConstantPoolGetUTF8At) },
{ "JVM_CountStackFrames", vm::voidPointer(JVM_CountStackFrames) },
{ "JVM_CurrentClassLoader", vm::voidPointer(JVM_CurrentClassLoader) },
{ "JVM_CurrentLoadedClass", vm::voidPointer(JVM_CurrentLoadedClass) },
{ "JVM_CurrentThread", vm::voidPointer(JVM_CurrentThread) },
{ "JVM_CurrentTimeMillis", vm::voidPointer(JVM_CurrentTimeMillis) },
{ "JVM_DefineClass", vm::voidPointer(JVM_DefineClass) },
{ "JVM_DefineClassWithSource", vm::voidPointer(JVM_DefineClassWithSource) },
{ "JVM_DefineClassWithSourceCond", vm::voidPointer(JVM_DefineClassWithSourceCond) },
{ "JVM_DesiredAssertionStatus", vm::voidPointer(JVM_DesiredAssertionStatus) },
{ "JVM_DisableCompiler", vm::voidPointer(JVM_DisableCompiler) },
{ "JVM_DoPrivileged", vm::voidPointer(JVM_DoPrivileged) },
{ "JVM_DumpAllStacks", vm::voidPointer(JVM_DumpAllStacks) },
{ "JVM_DumpThreads", vm::voidPointer(JVM_DumpThreads) },
{ "JVM_EnableCompiler", vm::voidPointer(JVM_EnableCompiler) },
{ "JVM_Exit", vm::voidPointer(JVM_Exit) },
{ "JVM_FillInStackTrace", vm::voidPointer(JVM_FillInStackTrace) },
{ "JVM_FindClassFromBootLoader", vm::voidPointer(JVM_FindClassFromBootLoader) },
{ "JVM_FindClassFromCaller", vm::voidPointer(JVM_FindClassFromCaller) },
{ "JVM_FindClassFromClass", vm::voidPointer(JVM_FindClassFromClass) },
{ "JVM_FindClassFromClassLoader", vm::voidPointer(JVM_FindClassFromClassLoader) },
{ "JVM_FindLibraryEntry", vm::voidPointer(JVM_FindLibraryEntry) },
{ "JVM_FindLoadedClass", vm::voidPointer(JVM_FindLoadedClass) },
{ "JVM_FindPrimitiveClass", vm::voidPointer(JVM_FindPrimitiveClass) },
{ "JVM_FindSignal", vm::voidPointer(JVM_FindSignal) },
{ "JVM_FreeMemory", vm::voidPointer(JVM_FreeMemory) },
{ "JVM_GC", vm::voidPointer(JVM_GC) },
{ "JVM_GetAllThreads", vm::voidPointer(JVM_GetAllThreads) },
{ "JVM_GetArrayElement", vm::voidPointer(JVM_GetArrayElement) },
{ "JVM_GetArrayLength", vm::voidPointer(JVM_GetArrayLength) },
{ "JVM_GetCPClassNameUTF", vm::voidPointer(JVM_GetCPClassNameUTF) },
{ "JVM_GetCPFieldClassNameUTF", vm::voidPointer(JVM_GetCPFieldClassNameUTF) },
{ "JVM_GetCPFieldModifiers", vm::voidPointer(JVM_GetCPFieldModifiers) },
{ "JVM_GetCPFieldNameUTF", vm::voidPointer(JVM_GetCPFieldNameUTF) },
{ "JVM_GetCPFieldSignatureUTF", vm::voidPointer(JVM_GetCPFieldSignatureUTF) },
{ "JVM_GetCPMethodClassNameUTF", vm::voidPointer(JVM_GetCPMethodClassNameUTF) },
{ "JVM_GetCPMethodModifiers", vm::voidPointer(JVM_GetCPMethodModifiers) },
{ "JVM_GetCPMethodNameUTF", vm::voidPointer(JVM_GetCPMethodNameUTF) },
{ "JVM_GetCPMethodSignatureUTF", vm::voidPointer(JVM_GetCPMethodSignatureUTF) },
{ "JVM_GetCallerClass", vm::voidPointer(JVM_GetCallerClass) },
{ "JVM_GetClassAccessFlags", vm::voidPointer(JVM_GetClassAccessFlags) },
{ "JVM_GetClassAnnotations", vm::voidPointer(JVM_GetClassAnnotations) },
{ "JVM_GetClassCPEntriesCount", vm::voidPointer(JVM_GetClassCPEntriesCount) },
{ "JVM_GetClassCPTypes", vm::voidPointer(JVM_GetClassCPTypes) },
{ "JVM_GetClassConstantPool", vm::voidPointer(JVM_GetClassConstantPool) },
{ "JVM_GetClassContext", vm::voidPointer(JVM_GetClassContext) },
{ "JVM_GetClassDeclaredConstructors", vm::voidPointer(JVM_GetClassDeclaredConstructors) },
{ "JVM_GetClassDeclaredFields", vm::voidPointer(JVM_GetClassDeclaredFields) },
{ "JVM_GetClassDeclaredMethods", vm::voidPointer(JVM_GetClassDeclaredMethods) },
{ "JVM_GetClassFieldsCount", vm::voidPointer(JVM_GetClassFieldsCount) },
{ "JVM_GetClassInterfaces", vm::voidPointer(JVM_GetClassInterfaces) },
{ "JVM_GetClassLoader", vm::voidPointer(JVM_GetClassLoader) },
{ "JVM_GetClassMethodsCount", vm::voidPointer(JVM_GetClassMethodsCount) },
{ "JVM_GetClassModifiers", vm::voidPointer(JVM_GetClassModifiers) },
{ "JVM_GetClassName", vm::voidPointer(JVM_GetClassName) },
{ "JVM_GetClassNameUTF", vm::voidPointer(JVM_GetClassNameUTF) },
{ "JVM_GetClassSignature", vm::voidPointer(JVM_GetClassSignature) },
{ "JVM_GetClassSigners", vm::voidPointer(JVM_GetClassSigners) },
{ "JVM_GetClassTypeAnnotations", vm::voidPointer(JVM_GetClassTypeAnnotations) },
{ "JVM_GetComponentType", vm::voidPointer(JVM_GetComponentType) },
{ "JVM_GetDeclaredClasses", vm::voidPointer(JVM_GetDeclaredClasses) },
{ "JVM_GetDeclaringClass", vm::voidPointer(JVM_GetDeclaringClass) },
{ "JVM_GetEnclosingMethodInfo", vm::voidPointer(JVM_GetEnclosingMethodInfo) },
{ "JVM_GetFieldIxModifiers", vm::voidPointer(JVM_GetFieldIxModifiers) },
{ "JVM_GetFieldTypeAnnotations", vm::voidPointer(JVM_GetFieldTypeAnnotations) },
{ "JVM_GetHostByAddr", vm::voidPointer(JVM_GetHostByAddr) },
{ "JVM_GetHostByName", vm::voidPointer(JVM_GetHostByName) },
{ "JVM_GetHostName", vm::voidPointer(JVM_GetHostName) },
{ "JVM_GetInheritedAccessControlContext", vm::voidPointer(JVM_GetInheritedAccessControlContext) },
{ "JVM_GetInterfaceVersion", vm::voidPointer(JVM_GetInterfaceVersion) },
{ "JVM_GetLastErrorString", vm::voidPointer(JVM_GetLastErrorString) },
{ "JVM_GetManagement", vm::voidPointer(JVM_GetManagement) },
{ "JVM_GetMethodIxArgsSize", vm::voidPointer(JVM_GetMethodIxArgsSize) },
{ "JVM_GetMethodIxByteCode", vm::voidPointer(JVM_GetMethodIxByteCode) },
{ "JVM_GetMethodIxByteCodeLength", vm::voidPointer(JVM_GetMethodIxByteCodeLength) },
{ "JVM_GetMethodIxExceptionIndexes", vm::voidPointer(JVM_GetMethodIxExceptionIndexes) },
{ "JVM_GetMethodIxExceptionTableEntry", vm::voidPointer(JVM_GetMethodIxExceptionTableEntry) },
{ "JVM_GetMethodIxExceptionTableLength", vm::voidPointer(JVM_GetMethodIxExceptionTableLength) },
{ "JVM_GetMethodIxExceptionsCount", vm::voidPointer(JVM_GetMethodIxExceptionsCount) },
{ "JVM_GetMethodIxLocalsCount", vm::voidPointer(JVM_GetMethodIxLocalsCount) },
{ "JVM_GetMethodIxMaxStack", vm::voidPointer(JVM_GetMethodIxMaxStack) },
{ "JVM_GetMethodIxModifiers", vm::voidPointer(JVM_GetMethodIxModifiers) },
{ "JVM_GetMethodIxNameUTF", vm::voidPointer(JVM_GetMethodIxNameUTF) },
{ "JVM_GetMethodIxSignatureUTF", vm::voidPointer(JVM_GetMethodIxSignatureUTF) },
{ "JVM_GetMethodParameters", vm::voidPointer(JVM_GetMethodParameters) },
{ "JVM_GetMethodTypeAnnotations", vm::voidPointer(JVM_GetMethodTypeAnnotations) },
{ "JVM_GetPrimitiveArrayElement", vm::voidPointer(JVM_GetPrimitiveArrayElement) },
{ "JVM_GetProtectionDomain", vm::voidPointer(JVM_GetProtectionDomain) },
{ "JVM_GetProtoByName", vm::voidPointer(JVM_GetProtoByName) },
{ "JVM_GetResourceLookupCache", vm::voidPointer(JVM_GetResourceLookupCache) },
{ "JVM_GetResourceLookupCacheURLs", vm::voidPointer(JVM_GetResourceLookupCacheURLs) },
{ "JVM_GetSockName", vm::voidPointer(JVM_GetSockName) },
{ "JVM_GetSockOpt", vm::voidPointer(JVM_GetSockOpt) },
{ "JVM_GetStackAccessControlContext", vm::voidPointer(JVM_GetStackAccessControlContext) },
{ "JVM_GetStackTraceDepth", vm::voidPointer(JVM_GetStackTraceDepth) },
{ "JVM_GetStackTraceElement", vm::voidPointer(JVM_GetStackTraceElement) },
{ "JVM_GetSystemPackage", vm::voidPointer(JVM_GetSystemPackage) },
{ "JVM_GetSystemPackages", vm::voidPointer(JVM_GetSystemPackages) },
{ "JVM_GetTemporaryDirectory", vm::voidPointer(JVM_GetTemporaryDirectory) },
{ "JVM_GetThreadStateNames", vm::voidPointer(JVM_GetThreadStateNames) },
{ "JVM_GetThreadStateValues", vm::voidPointer(JVM_GetThreadStateValues) },
{ "JVM_GetVersionInfo", vm::voidPointer(JVM_GetVersionInfo) },
{ "JVM_Halt", vm::voidPointer(JVM_Halt) },
{ "JVM_HoldsLock", vm::voidPointer(JVM_HoldsLock) },
{ "JVM_IHashCode", vm::voidPointer(JVM_IHashCode) },
{ "JVM_InitAgentProperties", vm::voidPointer(JVM_InitAgentProperties) },
{ "JVM_InitProperties", vm::voidPointer(JVM_InitProperties) },
{ "JVM_InitializeCompiler", vm::voidPointer(JVM_InitializeCompiler) },
{ "JVM_InitializeSocketLibrary", vm::voidPointer(JVM_InitializeSocketLibrary) },
{ "JVM_InternString", vm::voidPointer(JVM_InternString) },
{ "JVM_Interrupt", vm::voidPointer(JVM_Interrupt) },
{ "JVM_InvokeMethod", vm::voidPointer(JVM_InvokeMethod) },
{ "JVM_IsArrayClass", vm::voidPointer(JVM_IsArrayClass) },
{ "JVM_IsConstructorIx", vm::voidPointer(JVM_IsConstructorIx) },
{ "JVM_IsInterface", vm::voidPointer(JVM_IsInterface) },
{ "JVM_IsInterrupted", vm::voidPointer(JVM_IsInterrupted) },
{ "JVM_IsNaN", vm::voidPointer(JVM_IsNaN) },
{ "JVM_IsPrimitiveClass", vm::voidPointer(JVM_IsPrimitiveClass) },
{ "JVM_IsSameClassPackage", vm::voidPointer(JVM_IsSameClassPackage) },
{ "JVM_IsSilentCompiler", vm::voidPointer(JVM_IsSilentCompiler) },
{ "JVM_IsSupportedJNIVersion", vm::voidPointer(JVM_IsSupportedJNIVersion) },
{ "JVM_IsThreadAlive", vm::voidPointer(JVM_IsThreadAlive) },
{ "JVM_IsVMGeneratedMethodIx", vm::voidPointer(JVM_IsVMGeneratedMethodIx) },
{ "JVM_KnownToNotExist", vm::voidPointer(JVM_KnownToNotExist) },
{ "JVM_LatestUserDefinedLoader", vm::voidPointer(JVM_LatestUserDefinedLoader) },
{ "JVM_Listen", vm::voidPointer(JVM_Listen) },
{ "JVM_LoadClass0", vm::voidPointer(JVM_LoadClass0) },
{ "JVM_LoadLibrary", vm::voidPointer(JVM_LoadLibrary) },
{ "JVM_Lseek", vm::voidPointer(JVM_Lseek) },
{ "JVM_MaxMemory", vm::voidPointer(JVM_MaxMemory) },
{ "JVM_MaxObjectInspectionAge", vm::voidPointer(JVM_MaxObjectInspectionAge) },
{ "JVM_MonitorNotify", vm::voidPointer(JVM_MonitorNotify) },
{ "JVM_MonitorNotifyAll", vm::voidPointer(JVM_MonitorNotifyAll) },
{ "JVM_MonitorWait", vm::voidPointer(JVM_MonitorWait) },
{ "JVM_NanoTime", vm::voidPointer(JVM_NanoTime) },
{ "JVM_NativePath", vm::voidPointer(JVM_NativePath) },
{ "JVM_NewArray", vm::voidPointer(JVM_NewArray) },
{ "JVM_NewInstanceFromConstructor", vm::voidPointer(JVM_NewInstanceFromConstructor) },
{ "JVM_NewMultiArray", vm::voidPointer(JVM_NewMultiArray) },
{ "JVM_OnExit", vm::voidPointer(JVM_OnExit) },
{ "JVM_Open", vm::voidPointer(JVM_Open) },
{ "JVM_PrintStackTrace", vm::voidPointer(JVM_PrintStackTrace) },
{ "JVM_RaiseSignal", vm::voidPointer(JVM_RaiseSignal) },
{ "JVM_RawMonitorCreate", vm::voidPointer(JVM_RawMonitorCreate) },
{ "JVM_RawMonitorDestroy", vm::voidPointer(JVM_RawMonitorDestroy) },
{ "JVM_RawMonitorEnter", vm::voidPointer(JVM_RawMonitorEnter) },
{ "JVM_RawMonitorExit", vm::voidPointer(JVM_RawMonitorExit) },
{ "JVM_Read", vm::voidPointer(JVM_Read) },
{ "JVM_Recv", vm::voidPointer(JVM_Recv) },
{ "JVM_RecvFrom", vm::voidPointer(JVM_RecvFrom) },
{ "JVM_RegisterSignal", vm::voidPointer(JVM_RegisterSignal) },
{ "JVM_ReleaseUTF", vm::voidPointer(JVM_ReleaseUTF) },
{ "JVM_ResolveClass", vm::voidPointer(JVM_ResolveClass) },
{ "JVM_ResumeThread", vm::voidPointer(JVM_ResumeThread) },
{ "JVM_Send", vm::voidPointer(JVM_Send) },
{ "JVM_SendTo", vm::voidPointer(JVM_SendTo) },
{ "JVM_SetArrayElement", vm::voidPointer(JVM_SetArrayElement) },
{ "JVM_SetClassSigners", vm::voidPointer(JVM_SetClassSigners) },
{ "JVM_SetLength", vm::voidPointer(JVM_SetLength) },
{ "JVM_SetNativeThreadName", vm::voidPointer(JVM_SetNativeThreadName) },
{ "JVM_SetPrimitiveArrayElement", vm::voidPointer(JVM_SetPrimitiveArrayElement) },
{ "JVM_SetProtectionDomain", vm::voidPointer(JVM_SetProtectionDomain) },
{ "JVM_SetSockOpt", vm::voidPointer(JVM_SetSockOpt) },
{ "JVM_SetThreadPriority", vm::voidPointer(JVM_SetThreadPriority) },
{ "JVM_Sleep", vm::voidPointer(JVM_Sleep) },
{ "JVM_Socket", vm::voidPointer(JVM_Socket) },
{ "JVM_SocketAvailable", vm::voidPointer(JVM_SocketAvailable) },
{ "JVM_SocketClose", vm::voidPointer(JVM_SocketClose) },
{ "JVM_SocketShutdown", vm::voidPointer(JVM_SocketShutdown) },
{ "JVM_StartThread", vm::voidPointer(JVM_StartThread) },
{ "JVM_StopThread", vm::voidPointer(JVM_StopThread) },
{ "JVM_SupportsCX8", vm::voidPointer(JVM_SupportsCX8) },
{ "JVM_SuspendThread", vm::voidPointer(JVM_SuspendThread) },
{ "JVM_Sync", vm::voidPointer(JVM_Sync) },
{ "JVM_Timeout", vm::voidPointer(JVM_Timeout) },
{ "JVM_TotalMemory", vm::voidPointer(JVM_TotalMemory) },
{ "JVM_TraceInstructions", vm::voidPointer(JVM_TraceInstructions) },
{ "JVM_TraceMethodCalls", vm::voidPointer(JVM_TraceMethodCalls) },
{ "JVM_UnloadLibrary", vm::voidPointer(JVM_UnloadLibrary) },
{ "JVM_Write", vm::voidPointer(JVM_Write) },
{ "JVM_Yield", vm::voidPointer(JVM_Yield) },
{ "Java_java_io_Console_echo", vm::voidPointer(Java_java_io_Console_echo) },
{ "Java_java_io_Console_encoding", vm::voidPointer(Java_java_io_Console_encoding) },
{ "Java_java_io_Console_istty", vm::voidPointer(Java_java_io_Console_istty) },
{ "Java_java_io_FileDescriptor_initIDs", vm::voidPointer(Java_java_io_FileDescriptor_initIDs) },
{ "Java_java_io_FileDescriptor_sync", vm::voidPointer(Java_java_io_FileDescriptor_sync) },
{ "Java_java_io_FileInputStream_available", vm::voidPointer(Java_java_io_FileInputStream_available) },
{ "Java_java_io_FileInputStream_close0", vm::voidPointer(Java_java_io_FileInputStream_close0) },
{ "Java_java_io_FileInputStream_initIDs", vm::voidPointer(Java_java_io_FileInputStream_initIDs) },
{ "Java_java_io_FileInputStream_open0", vm::voidPointer(Java_java_io_FileInputStream_open0) },
{ "Java_java_io_FileInputStream_read0", vm::voidPointer(Java_java_io_FileInputStream_read0) },
{ "Java_java_io_FileInputStream_readBytes", vm::voidPointer(Java_java_io_FileInputStream_readBytes) },
{ "Java_java_io_FileInputStream_skip", vm::voidPointer(Java_java_io_FileInputStream_skip) },
{ "Java_java_io_FileOutputStream_close0", vm::voidPointer(Java_java_io_FileOutputStream_close0) },
{ "Java_java_io_FileOutputStream_initIDs", vm::voidPointer(Java_java_io_FileOutputStream_initIDs) },
{ "Java_java_io_FileOutputStream_open0", vm::voidPointer(Java_java_io_FileOutputStream_open0) },
{ "Java_java_io_FileOutputStream_write", vm::voidPointer(Java_java_io_FileOutputStream_write) },
{ "Java_java_io_FileOutputStream_writeBytes", vm::voidPointer(Java_java_io_FileOutputStream_writeBytes) },
{ "Java_java_io_ObjectInputStream_bytesToDoubles", vm::voidPointer(Java_java_io_ObjectInputStream_bytesToDoubles) },
{ "Java_java_io_ObjectInputStream_bytesToFloats", vm::voidPointer(Java_java_io_ObjectInputStream_bytesToFloats) },
{ "Java_java_io_ObjectOutputStream_doublesToBytes", vm::voidPointer(Java_java_io_ObjectOutputStream_doublesToBytes) },
{ "Java_java_io_ObjectOutputStream_floatsToBytes", vm::voidPointer(Java_java_io_ObjectOutputStream_floatsToBytes) },
{ "Java_java_io_ObjectStreamClass_hasStaticInitializer", vm::voidPointer(Java_java_io_ObjectStreamClass_hasStaticInitializer) },
{ "Java_java_io_ObjectStreamClass_initNative", vm::voidPointer(Java_java_io_ObjectStreamClass_initNative) },
{ "Java_java_io_RandomAccessFile_close0", vm::voidPointer(Java_java_io_RandomAccessFile_close0) },
{ "Java_java_io_RandomAccessFile_getFilePointer", vm::voidPointer(Java_java_io_RandomAccessFile_getFilePointer) },
{ "Java_java_io_RandomAccessFile_initIDs", vm::voidPointer(Java_java_io_RandomAccessFile_initIDs) },
{ "Java_java_io_RandomAccessFile_length", vm::voidPointer(Java_java_io_RandomAccessFile_length) },
{ "Java_java_io_RandomAccessFile_open0", vm::voidPointer(Java_java_io_RandomAccessFile_open0) },
{ "Java_java_io_RandomAccessFile_read0", vm::voidPointer(Java_java_io_RandomAccessFile_read0) },
{ "Java_java_io_RandomAccessFile_readBytes", vm::voidPointer(Java_java_io_RandomAccessFile_readBytes) },
{ "Java_java_io_RandomAccessFile_seek0", vm::voidPointer(Java_java_io_RandomAccessFile_seek0) },
{ "Java_java_io_RandomAccessFile_setLength", vm::voidPointer(Java_java_io_RandomAccessFile_setLength) },
{ "Java_java_io_RandomAccessFile_write0", vm::voidPointer(Java_java_io_RandomAccessFile_write0) },
{ "Java_java_io_RandomAccessFile_writeBytes", vm::voidPointer(Java_java_io_RandomAccessFile_writeBytes) },
{ "Java_java_io_UnixFileSystem_canonicalize0", vm::voidPointer(Java_java_io_UnixFileSystem_canonicalize0) },
{ "Java_java_io_UnixFileSystem_checkAccess", vm::voidPointer(Java_java_io_UnixFileSystem_checkAccess) },
{ "Java_java_io_UnixFileSystem_createDirectory", vm::voidPointer(Java_java_io_UnixFileSystem_createDirectory) },
{ "Java_java_io_UnixFileSystem_createFileExclusively", vm::voidPointer(Java_java_io_UnixFileSystem_createFileExclusively) },
{ "Java_java_io_UnixFileSystem_delete0", vm::voidPointer(Java_java_io_UnixFileSystem_delete0) },
{ "Java_java_io_UnixFileSystem_getBooleanAttributes0", vm::voidPointer(Java_java_io_UnixFileSystem_getBooleanAttributes0) },
{ "Java_java_io_UnixFileSystem_getLastModifiedTime", vm::voidPointer(Java_java_io_UnixFileSystem_getLastModifiedTime) },
{ "Java_java_io_UnixFileSystem_getLength", vm::voidPointer(Java_java_io_UnixFileSystem_getLength) },
{ "Java_java_io_UnixFileSystem_getSpace", vm::voidPointer(Java_java_io_UnixFileSystem_getSpace) },
{ "Java_java_io_UnixFileSystem_initIDs", vm::voidPointer(Java_java_io_UnixFileSystem_initIDs) },
{ "Java_java_io_UnixFileSystem_list", vm::voidPointer(Java_java_io_UnixFileSystem_list) },
{ "Java_java_io_UnixFileSystem_rename0", vm::voidPointer(Java_java_io_UnixFileSystem_rename0) },
{ "Java_java_io_UnixFileSystem_setLastModifiedTime", vm::voidPointer(Java_java_io_UnixFileSystem_setLastModifiedTime) },
{ "Java_java_io_UnixFileSystem_setPermission", vm::voidPointer(Java_java_io_UnixFileSystem_setPermission) },
{ "Java_java_io_UnixFileSystem_setReadOnly", vm::voidPointer(Java_java_io_UnixFileSystem_setReadOnly) },
{ "Java_java_lang_ClassLoader_00024NativeLibrary_find", vm::voidPointer(Java_java_lang_ClassLoader_00024NativeLibrary_find) },
{ "Java_java_lang_ClassLoader_00024NativeLibrary_load", vm::voidPointer(Java_java_lang_ClassLoader_00024NativeLibrary_load) },
{ "Java_java_lang_ClassLoader_00024NativeLibrary_unload", vm::voidPointer(Java_java_lang_ClassLoader_00024NativeLibrary_unload) },
{ "Java_java_lang_ClassLoader_defineClass0", vm::voidPointer(Java_java_lang_ClassLoader_defineClass0) },
{ "Java_java_lang_ClassLoader_defineClass1", vm::voidPointer(Java_java_lang_ClassLoader_defineClass1) },
{ "Java_java_lang_ClassLoader_defineClass2", vm::voidPointer(Java_java_lang_ClassLoader_defineClass2) },
{ "Java_java_lang_ClassLoader_findBootstrapClass", vm::voidPointer(Java_java_lang_ClassLoader_findBootstrapClass) },
{ "Java_java_lang_ClassLoader_findBuiltinLib", vm::voidPointer(Java_java_lang_ClassLoader_findBuiltinLib) },
{ "Java_java_lang_ClassLoader_findLoadedClass0", vm::voidPointer(Java_java_lang_ClassLoader_findLoadedClass0) },
{ "Java_java_lang_ClassLoader_registerNatives", vm::voidPointer(Java_java_lang_ClassLoader_registerNatives) },
{ "Java_java_lang_ClassLoader_resolveClass0", vm::voidPointer(Java_java_lang_ClassLoader_resolveClass0) },
{ "Java_java_lang_Class_forName0", vm::voidPointer(Java_java_lang_Class_forName0) },
{ "Java_java_lang_Class_getPrimitiveClass", vm::voidPointer(Java_java_lang_Class_getPrimitiveClass) },
{ "Java_java_lang_Class_isAssignableFrom", vm::voidPointer(Java_java_lang_Class_isAssignableFrom) },
{ "Java_java_lang_Class_isInstance", vm::voidPointer(Java_java_lang_Class_isInstance) },
{ "Java_java_lang_Class_registerNatives", vm::voidPointer(Java_java_lang_Class_registerNatives) },
{ "Java_java_lang_Compiler_registerNatives", vm::voidPointer(Java_java_lang_Compiler_registerNatives) },
{ "Java_java_lang_Double_doubleToRawLongBits", vm::voidPointer(Java_java_lang_Double_doubleToRawLongBits) },
{ "Java_java_lang_Double_longBitsToDouble", vm::voidPointer(Java_java_lang_Double_longBitsToDouble) },
{ "Java_java_lang_Float_floatToRawIntBits", vm::voidPointer(Java_java_lang_Float_floatToRawIntBits) },
{ "Java_java_lang_Float_intBitsToFloat", vm::voidPointer(Java_java_lang_Float_intBitsToFloat) },
{ "Java_java_lang_Object_getClass", vm::voidPointer(Java_java_lang_Object_getClass) },
{ "Java_java_lang_Object_registerNatives", vm::voidPointer(Java_java_lang_Object_registerNatives) },
{ "Java_java_lang_Package_getSystemPackage0", vm::voidPointer(Java_java_lang_Package_getSystemPackage0) },
{ "Java_java_lang_Package_getSystemPackages0", vm::voidPointer(Java_java_lang_Package_getSystemPackages0) },
{ "Java_java_lang_ProcessEnvironment_environ", vm::voidPointer(Java_java_lang_ProcessEnvironment_environ) },
{ "Java_java_lang_Runtime_availableProcessors", vm::voidPointer(Java_java_lang_Runtime_availableProcessors) },
{ "Java_java_lang_Runtime_freeMemory", vm::voidPointer(Java_java_lang_Runtime_freeMemory) },
{ "Java_java_lang_Runtime_gc", vm::voidPointer(Java_java_lang_Runtime_gc) },
{ "Java_java_lang_Runtime_maxMemory", vm::voidPointer(Java_java_lang_Runtime_maxMemory) },
{ "Java_java_lang_Runtime_runFinalization0", vm::voidPointer(Java_java_lang_Runtime_runFinalization0) },
{ "Java_java_lang_Runtime_totalMemory", vm::voidPointer(Java_java_lang_Runtime_totalMemory) },
{ "Java_java_lang_Runtime_traceInstructions", vm::voidPointer(Java_java_lang_Runtime_traceInstructions) },
{ "Java_java_lang_Runtime_traceMethodCalls", vm::voidPointer(Java_java_lang_Runtime_traceMethodCalls) },
{ "Java_java_lang_SecurityManager_classDepth", vm::voidPointer(Java_java_lang_SecurityManager_classDepth) },
{ "Java_java_lang_SecurityManager_classLoaderDepth0", vm::voidPointer(Java_java_lang_SecurityManager_classLoaderDepth0) },
{ "Java_java_lang_SecurityManager_currentClassLoader0", vm::voidPointer(Java_java_lang_SecurityManager_currentClassLoader0) },
{ "Java_java_lang_SecurityManager_currentLoadedClass0", vm::voidPointer(Java_java_lang_SecurityManager_currentLoadedClass0) },
{ "Java_java_lang_SecurityManager_getClassContext", vm::voidPointer(Java_java_lang_SecurityManager_getClassContext) },
{ "Java_java_lang_Shutdown_halt0", vm::voidPointer(Java_java_lang_Shutdown_halt0) },
{ "Java_java_lang_Shutdown_runAllFinalizers", vm::voidPointer(Java_java_lang_Shutdown_runAllFinalizers) },
{ "Java_java_lang_StrictMath_IEEEremainder", vm::voidPointer(Java_java_lang_StrictMath_IEEEremainder) },
{ "Java_java_lang_StrictMath_acos", vm::voidPointer(Java_java_lang_StrictMath_acos) },
{ "Java_java_lang_StrictMath_asin", vm::voidPointer(Java_java_lang_StrictMath_asin) },
{ "Java_java_lang_StrictMath_atan", vm::voidPointer(Java_java_lang_StrictMath_atan) },
{ "Java_java_lang_StrictMath_atan2", vm::voidPointer(Java_java_lang_StrictMath_atan2) },
{ "Java_java_lang_StrictMath_cbrt", vm::voidPointer(Java_java_lang_StrictMath_cbrt) },
{ "Java_java_lang_StrictMath_cos", vm::voidPointer(Java_java_lang_StrictMath_cos) },
{ "Java_java_lang_StrictMath_cosh", vm::voidPointer(Java_java_lang_StrictMath_cosh) },
{ "Java_java_lang_StrictMath_exp", vm::voidPointer(Java_java_lang_StrictMath_exp) },
{ "Java_java_lang_StrictMath_expm1", vm::voidPointer(Java_java_lang_StrictMath_expm1) },
{ "Java_java_lang_StrictMath_hypot", vm::voidPointer(Java_java_lang_StrictMath_hypot) },
{ "Java_java_lang_StrictMath_log", vm::voidPointer(Java_java_lang_StrictMath_log) },
{ "Java_java_lang_StrictMath_log10", vm::voidPointer(Java_java_lang_StrictMath_log10) },
{ "Java_java_lang_StrictMath_log1p", vm::voidPointer(Java_java_lang_StrictMath_log1p) },
{ "Java_java_lang_StrictMath_pow", vm::voidPointer(Java_java_lang_StrictMath_pow) },
{ "Java_java_lang_StrictMath_sin", vm::voidPointer(Java_java_lang_StrictMath_sin) },
{ "Java_java_lang_StrictMath_sinh", vm::voidPointer(Java_java_lang_StrictMath_sinh) },
{ "Java_java_lang_StrictMath_sqrt", vm::voidPointer(Java_java_lang_StrictMath_sqrt) },
{ "Java_java_lang_StrictMath_tan", vm::voidPointer(Java_java_lang_StrictMath_tan) },
{ "Java_java_lang_StrictMath_tanh", vm::voidPointer(Java_java_lang_StrictMath_tanh) },
{ "Java_java_lang_String_intern", vm::voidPointer(Java_java_lang_String_intern) },
{ "Java_java_lang_System_identityHashCode", vm::voidPointer(Java_java_lang_System_identityHashCode) },
{ "Java_java_lang_System_initProperties", vm::voidPointer(Java_java_lang_System_initProperties) },
{ "Java_java_lang_System_mapLibraryName", vm::voidPointer(Java_java_lang_System_mapLibraryName) },
{ "Java_java_lang_System_registerNatives", vm::voidPointer(Java_java_lang_System_registerNatives) },
{ "Java_java_lang_System_setErr0", vm::voidPointer(Java_java_lang_System_setErr0) },
{ "Java_java_lang_System_setIn0", vm::voidPointer(Java_java_lang_System_setIn0) },
{ "Java_java_lang_System_setOut0", vm::voidPointer(Java_java_lang_System_setOut0) },
{ "Java_java_lang_Thread_registerNatives", vm::voidPointer(Java_java_lang_Thread_registerNatives) },
{ "Java_java_lang_Throwable_fillInStackTrace", vm::voidPointer(Java_java_lang_Throwable_fillInStackTrace) },
{ "Java_java_lang_Throwable_getStackTraceDepth", vm::voidPointer(Java_java_lang_Throwable_getStackTraceDepth) },
{ "Java_java_lang_Throwable_getStackTraceElement", vm::voidPointer(Java_java_lang_Throwable_getStackTraceElement) },
{ "Java_java_lang_UNIXProcess_destroyProcess", vm::voidPointer(Java_java_lang_UNIXProcess_destroyProcess) },
{ "Java_java_lang_UNIXProcess_forkAndExec", vm::voidPointer(Java_java_lang_UNIXProcess_forkAndExec) },
{ "Java_java_lang_UNIXProcess_init", vm::voidPointer(Java_java_lang_UNIXProcess_init) },
{ "Java_java_lang_UNIXProcess_waitForProcessExit", vm::voidPointer(Java_java_lang_UNIXProcess_waitForProcessExit) },
{ "Java_java_lang_reflect_Array_get", vm::voidPointer(Java_java_lang_reflect_Array_get) },
{ "Java_java_lang_reflect_Array_getBoolean", vm::voidPointer(Java_java_lang_reflect_Array_getBoolean) },
{ "Java_java_lang_reflect_Array_getByte", vm::voidPointer(Java_java_lang_reflect_Array_getByte) },
{ "Java_java_lang_reflect_Array_getChar", vm::voidPointer(Java_java_lang_reflect_Array_getChar) },
{ "Java_java_lang_reflect_Array_getDouble", vm::voidPointer(Java_java_lang_reflect_Array_getDouble) },
{ "Java_java_lang_reflect_Array_getFloat", vm::voidPointer(Java_java_lang_reflect_Array_getFloat) },
{ "Java_java_lang_reflect_Array_getInt", vm::voidPointer(Java_java_lang_reflect_Array_getInt) },
{ "Java_java_lang_reflect_Array_getLength", vm::voidPointer(Java_java_lang_reflect_Array_getLength) },
{ "Java_java_lang_reflect_Array_getLong", vm::voidPointer(Java_java_lang_reflect_Array_getLong) },
{ "Java_java_lang_reflect_Array_getShort", vm::voidPointer(Java_java_lang_reflect_Array_getShort) },
{ "Java_java_lang_reflect_Array_multiNewArray", vm::voidPointer(Java_java_lang_reflect_Array_multiNewArray) },
{ "Java_java_lang_reflect_Array_newArray", vm::voidPointer(Java_java_lang_reflect_Array_newArray) },
{ "Java_java_lang_reflect_Array_set", vm::voidPointer(Java_java_lang_reflect_Array_set) },
{ "Java_java_lang_reflect_Array_setBoolean", vm::voidPointer(Java_java_lang_reflect_Array_setBoolean) },
{ "Java_java_lang_reflect_Array_setByte", vm::voidPointer(Java_java_lang_reflect_Array_setByte) },
{ "Java_java_lang_reflect_Array_setChar", vm::voidPointer(Java_java_lang_reflect_Array_setChar) },
{ "Java_java_lang_reflect_Array_setDouble", vm::voidPointer(Java_java_lang_reflect_Array_setDouble) },
{ "Java_java_lang_reflect_Array_setFloat", vm::voidPointer(Java_java_lang_reflect_Array_setFloat) },
{ "Java_java_lang_reflect_Array_setInt", vm::voidPointer(Java_java_lang_reflect_Array_setInt) },
{ "Java_java_lang_reflect_Array_setLong", vm::voidPointer(Java_java_lang_reflect_Array_setLong) },
{ "Java_java_lang_reflect_Array_setShort", vm::voidPointer(Java_java_lang_reflect_Array_setShort) },
{ "Java_java_lang_reflect_Proxy_defineClass0", vm::voidPointer(Java_java_lang_reflect_Proxy_defineClass0) },
{ "Java_java_net_DatagramPacket_init", vm::voidPointer(Java_java_net_DatagramPacket_init) },
{ "Java_java_net_Inet4AddressImpl_getHostByAddr", vm::voidPointer(Java_java_net_Inet4AddressImpl_getHostByAddr) },
{ "Java_java_net_Inet4AddressImpl_getLocalHostName", vm::voidPointer(Java_java_net_Inet4AddressImpl_getLocalHostName) },
{ "Java_java_net_Inet4AddressImpl_isReachable0", vm::voidPointer(Java_java_net_Inet4AddressImpl_isReachable0) },
{ "Java_java_net_Inet4AddressImpl_lookupAllHostAddr", vm::voidPointer(Java_java_net_Inet4AddressImpl_lookupAllHostAddr) },
{ "Java_java_net_Inet4Address_init", vm::voidPointer(Java_java_net_Inet4Address_init) },
{ "Java_java_net_Inet6AddressImpl_getHostByAddr", vm::voidPointer(Java_java_net_Inet6AddressImpl_getHostByAddr) },
{ "Java_java_net_Inet6AddressImpl_getLocalHostName", vm::voidPointer(Java_java_net_Inet6AddressImpl_getLocalHostName) },
{ "Java_java_net_Inet6AddressImpl_isReachable0", vm::voidPointer(Java_java_net_Inet6AddressImpl_isReachable0) },
{ "Java_java_net_Inet6AddressImpl_lookupAllHostAddr", vm::voidPointer(Java_java_net_Inet6AddressImpl_lookupAllHostAddr) },
{ "Java_java_net_Inet6Address_init", vm::voidPointer(Java_java_net_Inet6Address_init) },
{ "Java_java_net_InetAddressImplFactory_isIPv6Supported", vm::voidPointer(Java_java_net_InetAddressImplFactory_isIPv6Supported) },
{ "Java_java_net_InetAddress_init", vm::voidPointer(Java_java_net_InetAddress_init) },
{ "Java_java_net_NetworkInterface_getAll", vm::voidPointer(Java_java_net_NetworkInterface_getAll) },
{ "Java_java_net_NetworkInterface_getByIndex0", vm::voidPointer(Java_java_net_NetworkInterface_getByIndex0) },
{ "Java_java_net_NetworkInterface_getByInetAddress0", vm::voidPointer(Java_java_net_NetworkInterface_getByInetAddress0) },
{ "Java_java_net_NetworkInterface_getByName0", vm::voidPointer(Java_java_net_NetworkInterface_getByName0) },
{ "Java_java_net_NetworkInterface_getMTU0", vm::voidPointer(Java_java_net_NetworkInterface_getMTU0) },
{ "Java_java_net_NetworkInterface_getMacAddr0", vm::voidPointer(Java_java_net_NetworkInterface_getMacAddr0) },
{ "Java_java_net_NetworkInterface_init", vm::voidPointer(Java_java_net_NetworkInterface_init) },
{ "Java_java_net_NetworkInterface_isLoopback0", vm::voidPointer(Java_java_net_NetworkInterface_isLoopback0) },
{ "Java_java_net_NetworkInterface_isP2P0", vm::voidPointer(Java_java_net_NetworkInterface_isP2P0) },
{ "Java_java_net_NetworkInterface_isUp0", vm::voidPointer(Java_java_net_NetworkInterface_isUp0) },
{ "Java_java_net_NetworkInterface_supportsMulticast0", vm::voidPointer(Java_java_net_NetworkInterface_supportsMulticast0) },
{ "Java_java_net_PlainDatagramSocketImpl_bind0", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_bind0) },
{ "Java_java_net_PlainDatagramSocketImpl_connect0", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_connect0) },
{ "Java_java_net_PlainDatagramSocketImpl_dataAvailable", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_dataAvailable) },
{ "Java_java_net_PlainDatagramSocketImpl_datagramSocketClose", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_datagramSocketClose) },
{ "Java_java_net_PlainDatagramSocketImpl_datagramSocketCreate", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_datagramSocketCreate) },
{ "Java_java_net_PlainDatagramSocketImpl_disconnect0", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_disconnect0) },
{ "Java_java_net_PlainDatagramSocketImpl_getTTL", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_getTTL) },
{ "Java_java_net_PlainDatagramSocketImpl_getTimeToLive", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_getTimeToLive) },
{ "Java_java_net_PlainDatagramSocketImpl_init", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_init) },
{ "Java_java_net_PlainDatagramSocketImpl_join", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_join) },
{ "Java_java_net_PlainDatagramSocketImpl_leave", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_leave) },
{ "Java_java_net_PlainDatagramSocketImpl_peek", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_peek) },
{ "Java_java_net_PlainDatagramSocketImpl_peekData", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_peekData) },
{ "Java_java_net_PlainDatagramSocketImpl_receive0", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_receive0) },
{ "Java_java_net_PlainDatagramSocketImpl_send", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_send) },
{ "Java_java_net_PlainDatagramSocketImpl_setTTL", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_setTTL) },
{ "Java_java_net_PlainDatagramSocketImpl_setTimeToLive", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_setTimeToLive) },
{ "Java_java_net_PlainDatagramSocketImpl_socketGetOption", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_socketGetOption) },
{ "Java_java_net_PlainDatagramSocketImpl_socketSetOption0", vm::voidPointer(Java_java_net_PlainDatagramSocketImpl_socketSetOption0) },
{ "Java_java_net_PlainSocketImpl_initProto", vm::voidPointer(Java_java_net_PlainSocketImpl_initProto) },
{ "Java_java_net_PlainSocketImpl_socketAccept", vm::voidPointer(Java_java_net_PlainSocketImpl_socketAccept) },
{ "Java_java_net_PlainSocketImpl_socketAvailable", vm::voidPointer(Java_java_net_PlainSocketImpl_socketAvailable) },
{ "Java_java_net_PlainSocketImpl_socketBind", vm::voidPointer(Java_java_net_PlainSocketImpl_socketBind) },
{ "Java_java_net_PlainSocketImpl_socketClose0", vm::voidPointer(Java_java_net_PlainSocketImpl_socketClose0) },
{ "Java_java_net_PlainSocketImpl_socketConnect", vm::voidPointer(Java_java_net_PlainSocketImpl_socketConnect) },
{ "Java_java_net_PlainSocketImpl_socketCreate", vm::voidPointer(Java_java_net_PlainSocketImpl_socketCreate) },
{ "Java_java_net_PlainSocketImpl_socketGetOption", vm::voidPointer(Java_java_net_PlainSocketImpl_socketGetOption) },
{ "Java_java_net_PlainSocketImpl_socketListen", vm::voidPointer(Java_java_net_PlainSocketImpl_socketListen) },
{ "Java_java_net_PlainSocketImpl_socketSendUrgentData", vm::voidPointer(Java_java_net_PlainSocketImpl_socketSendUrgentData) },
{ "Java_java_net_PlainSocketImpl_socketSetOption0", vm::voidPointer(Java_java_net_PlainSocketImpl_socketSetOption0) },
{ "Java_java_net_PlainSocketImpl_socketShutdown", vm::voidPointer(Java_java_net_PlainSocketImpl_socketShutdown) },
{ "Java_java_net_SocketInputStream_init", vm::voidPointer(Java_java_net_SocketInputStream_init) },
{ "Java_java_net_SocketInputStream_socketRead0", vm::voidPointer(Java_java_net_SocketInputStream_socketRead0) },
{ "Java_java_net_SocketOutputStream_init", vm::voidPointer(Java_java_net_SocketOutputStream_init) },
{ "Java_java_net_SocketOutputStream_socketWrite0", vm::voidPointer(Java_java_net_SocketOutputStream_socketWrite0) },
{ "Java_java_nio_Bits_copyFromIntArray", vm::voidPointer(Java_java_nio_Bits_copyFromIntArray) },
{ "Java_java_nio_Bits_copyFromLongArray", vm::voidPointer(Java_java_nio_Bits_copyFromLongArray) },
{ "Java_java_nio_Bits_copyFromShortArray", vm::voidPointer(Java_java_nio_Bits_copyFromShortArray) },
{ "Java_java_nio_Bits_copyToIntArray", vm::voidPointer(Java_java_nio_Bits_copyToIntArray) },
{ "Java_java_nio_Bits_copyToLongArray", vm::voidPointer(Java_java_nio_Bits_copyToLongArray) },
{ "Java_java_nio_Bits_copyToShortArray", vm::voidPointer(Java_java_nio_Bits_copyToShortArray) },
{ "Java_java_nio_MappedByteBuffer_force0", vm::voidPointer(Java_java_nio_MappedByteBuffer_force0) },
{ "Java_java_nio_MappedByteBuffer_isLoaded0", vm::voidPointer(Java_java_nio_MappedByteBuffer_isLoaded0) },
{ "Java_java_nio_MappedByteBuffer_load0", vm::voidPointer(Java_java_nio_MappedByteBuffer_load0) },
{ "Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2", vm::voidPointer(Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2) },
{ "Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2Ljava_security_AccessControlContext_2", vm::voidPointer(Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedAction_2Ljava_security_AccessControlContext_2) },
{ "Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2", vm::voidPointer(Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2) },
{ "Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2Ljava_security_AccessControlContext_2", vm::voidPointer(Java_java_security_AccessController_doPrivileged__Ljava_security_PrivilegedExceptionAction_2Ljava_security_AccessControlContext_2) },
{ "Java_java_security_AccessController_getInheritedAccessControlContext", vm::voidPointer(Java_java_security_AccessController_getInheritedAccessControlContext) },
{ "Java_java_security_AccessController_getStackAccessControlContext", vm::voidPointer(Java_java_security_AccessController_getStackAccessControlContext) },
{ "Java_java_util_TimeZone_getSystemGMTOffsetID", vm::voidPointer(Java_java_util_TimeZone_getSystemGMTOffsetID) },
{ "Java_java_util_TimeZone_getSystemTimeZoneID", vm::voidPointer(Java_java_util_TimeZone_getSystemTimeZoneID) },
{ "Java_java_util_concurrent_atomic_AtomicLong_VMSupportsCS8", vm::voidPointer(Java_java_util_concurrent_atomic_AtomicLong_VMSupportsCS8) },
{ "Java_java_util_jar_JarFile_getMetaInfEntryNames", vm::voidPointer(Java_java_util_jar_JarFile_getMetaInfEntryNames) },
{ "Java_java_util_logging_FileHandler_isSetUID", vm::voidPointer(Java_java_util_logging_FileHandler_isSetUID) },
{ "Java_java_util_prefs_FileSystemPreferences_chmod", vm::voidPointer(Java_java_util_prefs_FileSystemPreferences_chmod) },
{ "Java_java_util_prefs_FileSystemPreferences_lockFile0", vm::voidPointer(Java_java_util_prefs_FileSystemPreferences_lockFile0) },
{ "Java_java_util_prefs_FileSystemPreferences_unlockFile0", vm::voidPointer(Java_java_util_prefs_FileSystemPreferences_unlockFile0) },
{ "Java_java_util_zip_Adler32_update", vm::voidPointer(Java_java_util_zip_Adler32_update) },
{ "Java_java_util_zip_Adler32_updateByteBuffer", vm::voidPointer(Java_java_util_zip_Adler32_updateByteBuffer) },
{ "Java_java_util_zip_Adler32_updateBytes", vm::voidPointer(Java_java_util_zip_Adler32_updateBytes) },
{ "Java_java_util_zip_CRC32_update", vm::voidPointer(Java_java_util_zip_CRC32_update) },
{ "Java_java_util_zip_CRC32_updateByteBuffer", vm::voidPointer(Java_java_util_zip_CRC32_updateByteBuffer) },
{ "Java_java_util_zip_CRC32_updateBytes", vm::voidPointer(Java_java_util_zip_CRC32_updateBytes) },
{ "Java_java_util_zip_Deflater_deflateBytes", vm::voidPointer(Java_java_util_zip_Deflater_deflateBytes) },
{ "Java_java_util_zip_Deflater_end", vm::voidPointer(Java_java_util_zip_Deflater_end) },
{ "Java_java_util_zip_Deflater_getAdler", vm::voidPointer(Java_java_util_zip_Deflater_getAdler) },
{ "Java_java_util_zip_Deflater_init", vm::voidPointer(Java_java_util_zip_Deflater_init) },
{ "Java_java_util_zip_Deflater_initIDs", vm::voidPointer(Java_java_util_zip_Deflater_initIDs) },
{ "Java_java_util_zip_Deflater_reset", vm::voidPointer(Java_java_util_zip_Deflater_reset) },
{ "Java_java_util_zip_Deflater_setDictionary", vm::voidPointer(Java_java_util_zip_Deflater_setDictionary) },
{ "Java_java_util_zip_Inflater_end", vm::voidPointer(Java_java_util_zip_Inflater_end) },
{ "Java_java_util_zip_Inflater_getAdler", vm::voidPointer(Java_java_util_zip_Inflater_getAdler) },
{ "Java_java_util_zip_Inflater_inflateBytes", vm::voidPointer(Java_java_util_zip_Inflater_inflateBytes) },
{ "Java_java_util_zip_Inflater_init", vm::voidPointer(Java_java_util_zip_Inflater_init) },
{ "Java_java_util_zip_Inflater_initIDs", vm::voidPointer(Java_java_util_zip_Inflater_initIDs) },
{ "Java_java_util_zip_Inflater_reset", vm::voidPointer(Java_java_util_zip_Inflater_reset) },
{ "Java_java_util_zip_Inflater_setDictionary", vm::voidPointer(Java_java_util_zip_Inflater_setDictionary) },
{ "Java_java_util_zip_ZipFile_close", vm::voidPointer(Java_java_util_zip_ZipFile_close) },
{ "Java_java_util_zip_ZipFile_freeEntry", vm::voidPointer(Java_java_util_zip_ZipFile_freeEntry) },
{ "Java_java_util_zip_ZipFile_getCommentBytes", vm::voidPointer(Java_java_util_zip_ZipFile_getCommentBytes) },
{ "Java_java_util_zip_ZipFile_getEntry", vm::voidPointer(Java_java_util_zip_ZipFile_getEntry) },
{ "Java_java_util_zip_ZipFile_getEntryBytes", vm::voidPointer(Java_java_util_zip_ZipFile_getEntryBytes) },
{ "Java_java_util_zip_ZipFile_getEntryCSize", vm::voidPointer(Java_java_util_zip_ZipFile_getEntryCSize) },
{ "Java_java_util_zip_ZipFile_getEntryCrc", vm::voidPointer(Java_java_util_zip_ZipFile_getEntryCrc) },
{ "Java_java_util_zip_ZipFile_getEntryFlag", vm::voidPointer(Java_java_util_zip_ZipFile_getEntryFlag) },
{ "Java_java_util_zip_ZipFile_getEntryMethod", vm::voidPointer(Java_java_util_zip_ZipFile_getEntryMethod) },
{ "Java_java_util_zip_ZipFile_getEntrySize", vm::voidPointer(Java_java_util_zip_ZipFile_getEntrySize) },
{ "Java_java_util_zip_ZipFile_getEntryTime", vm::voidPointer(Java_java_util_zip_ZipFile_getEntryTime) },
{ "Java_java_util_zip_ZipFile_getNextEntry", vm::voidPointer(Java_java_util_zip_ZipFile_getNextEntry) },
{ "Java_java_util_zip_ZipFile_getTotal", vm::voidPointer(Java_java_util_zip_ZipFile_getTotal) },
{ "Java_java_util_zip_ZipFile_getZipMessage", vm::voidPointer(Java_java_util_zip_ZipFile_getZipMessage) },
{ "Java_java_util_zip_ZipFile_initIDs", vm::voidPointer(Java_java_util_zip_ZipFile_initIDs) },
{ "Java_java_util_zip_ZipFile_open", vm::voidPointer(Java_java_util_zip_ZipFile_open) },
{ "Java_java_util_zip_ZipFile_read", vm::voidPointer(Java_java_util_zip_ZipFile_read) },
{ "Java_java_util_zip_ZipFile_startsWithLOC", vm::voidPointer(Java_java_util_zip_ZipFile_startsWithLOC) },
{ "Java_sun_management_VMManagementImpl_getAvailableProcessors", vm::voidPointer(Java_sun_management_VMManagementImpl_getAvailableProcessors) },
{ "Java_sun_management_VMManagementImpl_getClassInitializationTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getClassInitializationTime) },
{ "Java_sun_management_VMManagementImpl_getClassLoadingTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getClassLoadingTime) },
{ "Java_sun_management_VMManagementImpl_getClassVerificationTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getClassVerificationTime) },
{ "Java_sun_management_VMManagementImpl_getDaemonThreadCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getDaemonThreadCount) },
{ "Java_sun_management_VMManagementImpl_getInitializedClassCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getInitializedClassCount) },
{ "Java_sun_management_VMManagementImpl_getLiveThreadCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getLiveThreadCount) },
{ "Java_sun_management_VMManagementImpl_getLoadedClassSize", vm::voidPointer(Java_sun_management_VMManagementImpl_getLoadedClassSize) },
{ "Java_sun_management_VMManagementImpl_getMethodDataSize", vm::voidPointer(Java_sun_management_VMManagementImpl_getMethodDataSize) },
{ "Java_sun_management_VMManagementImpl_getPeakThreadCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getPeakThreadCount) },
{ "Java_sun_management_VMManagementImpl_getProcessId", vm::voidPointer(Java_sun_management_VMManagementImpl_getProcessId) },
{ "Java_sun_management_VMManagementImpl_getSafepointCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getSafepointCount) },
{ "Java_sun_management_VMManagementImpl_getSafepointSyncTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getSafepointSyncTime) },
{ "Java_sun_management_VMManagementImpl_getStartupTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getStartupTime) },
{ "Java_sun_management_VMManagementImpl_getTotalApplicationNonStoppedTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getTotalApplicationNonStoppedTime) },
{ "Java_sun_management_VMManagementImpl_getTotalClassCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getTotalClassCount) },
{ "Java_sun_management_VMManagementImpl_getTotalCompileTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getTotalCompileTime) },
{ "Java_sun_management_VMManagementImpl_getTotalSafepointTime", vm::voidPointer(Java_sun_management_VMManagementImpl_getTotalSafepointTime) },
{ "Java_sun_management_VMManagementImpl_getTotalThreadCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getTotalThreadCount) },
{ "Java_sun_management_VMManagementImpl_getUnloadedClassCount", vm::voidPointer(Java_sun_management_VMManagementImpl_getUnloadedClassCount) },
{ "Java_sun_management_VMManagementImpl_getUnloadedClassSize", vm::voidPointer(Java_sun_management_VMManagementImpl_getUnloadedClassSize) },
{ "Java_sun_management_VMManagementImpl_getUptime0", vm::voidPointer(Java_sun_management_VMManagementImpl_getUptime0) },
{ "Java_sun_management_VMManagementImpl_getVerboseClass", vm::voidPointer(Java_sun_management_VMManagementImpl_getVerboseClass) },
{ "Java_sun_management_VMManagementImpl_getVerboseGC", vm::voidPointer(Java_sun_management_VMManagementImpl_getVerboseGC) },
{ "Java_sun_management_VMManagementImpl_getVersion0", vm::voidPointer(Java_sun_management_VMManagementImpl_getVersion0) },
{ "Java_sun_management_VMManagementImpl_getVmArguments0", vm::voidPointer(Java_sun_management_VMManagementImpl_getVmArguments0) },
{ "Java_sun_management_VMManagementImpl_initOptionalSupportFields", vm::voidPointer(Java_sun_management_VMManagementImpl_initOptionalSupportFields) },
{ "Java_sun_management_VMManagementImpl_isThreadAllocatedMemoryEnabled", vm::voidPointer(Java_sun_management_VMManagementImpl_isThreadAllocatedMemoryEnabled) },
{ "Java_sun_management_VMManagementImpl_isThreadContentionMonitoringEnabled", vm::voidPointer(Java_sun_management_VMManagementImpl_isThreadContentionMonitoringEnabled) },
{ "Java_sun_management_VMManagementImpl_isThreadCpuTimeEnabled", vm::voidPointer(Java_sun_management_VMManagementImpl_isThreadCpuTimeEnabled) },
{ "Java_sun_misc_GC_maxObjectInspectionAge", vm::voidPointer(Java_sun_misc_GC_maxObjectInspectionAge) },
{ "Java_sun_misc_MessageUtils_toStderr", vm::voidPointer(Java_sun_misc_MessageUtils_toStderr) },
{ "Java_sun_misc_MessageUtils_toStdout", vm::voidPointer(Java_sun_misc_MessageUtils_toStdout) },
{ "Java_sun_misc_NativeSignalHandler_handle0", vm::voidPointer(Java_sun_misc_NativeSignalHandler_handle0) },
{ "Java_sun_misc_Signal_findSignal", vm::voidPointer(Java_sun_misc_Signal_findSignal) },
{ "Java_sun_misc_Signal_handle0", vm::voidPointer(Java_sun_misc_Signal_handle0) },
{ "Java_sun_misc_Signal_raise0", vm::voidPointer(Java_sun_misc_Signal_raise0) },
{ "Java_sun_misc_URLClassPath_getLookupCacheForClassLoader", vm::voidPointer(Java_sun_misc_URLClassPath_getLookupCacheForClassLoader) },
{ "Java_sun_misc_URLClassPath_getLookupCacheURLs", vm::voidPointer(Java_sun_misc_URLClassPath_getLookupCacheURLs) },
{ "Java_sun_misc_URLClassPath_knownToNotExist0", vm::voidPointer(Java_sun_misc_URLClassPath_knownToNotExist0) },
{ "Java_sun_misc_VMSupport_getVMTemporaryDirectory", vm::voidPointer(Java_sun_misc_VMSupport_getVMTemporaryDirectory) },
{ "Java_sun_misc_VMSupport_initAgentProperties", vm::voidPointer(Java_sun_misc_VMSupport_initAgentProperties) },
{ "Java_sun_misc_VM_getThreadStateValues", vm::voidPointer(Java_sun_misc_VM_getThreadStateValues) },
{ "Java_sun_misc_VM_initialize", vm::voidPointer(Java_sun_misc_VM_initialize) },
{ "Java_sun_misc_VM_latestUserDefinedLoader", vm::voidPointer(Java_sun_misc_VM_latestUserDefinedLoader) },
{ "Java_sun_misc_Version_getJdkSpecialVersion", vm::voidPointer(Java_sun_misc_Version_getJdkSpecialVersion) },
{ "Java_sun_misc_Version_getJdkVersionInfo", vm::voidPointer(Java_sun_misc_Version_getJdkVersionInfo) },
{ "Java_sun_misc_Version_getJvmSpecialVersion", vm::voidPointer(Java_sun_misc_Version_getJvmSpecialVersion) },
{ "Java_sun_misc_Version_getJvmVersionInfo", vm::voidPointer(Java_sun_misc_Version_getJvmVersionInfo) },
{ "Java_sun_net_ExtendedOptionsImpl_flowSupported", vm::voidPointer(Java_sun_net_ExtendedOptionsImpl_flowSupported) },
{ "Java_sun_net_ExtendedOptionsImpl_getFlowOption", vm::voidPointer(Java_sun_net_ExtendedOptionsImpl_getFlowOption) },
{ "Java_sun_net_ExtendedOptionsImpl_init", vm::voidPointer(Java_sun_net_ExtendedOptionsImpl_init) },
{ "Java_sun_net_ExtendedOptionsImpl_setFlowOption", vm::voidPointer(Java_sun_net_ExtendedOptionsImpl_setFlowOption) },
{ "Java_sun_net_dns_ResolverConfigurationImpl_fallbackDomain0", vm::voidPointer(Java_sun_net_dns_ResolverConfigurationImpl_fallbackDomain0) },
{ "Java_sun_net_dns_ResolverConfigurationImpl_localDomain0", vm::voidPointer(Java_sun_net_dns_ResolverConfigurationImpl_localDomain0) },
{ "Java_sun_net_spi_DefaultProxySelector_getSystemProxy", vm::voidPointer(Java_sun_net_spi_DefaultProxySelector_getSystemProxy) },
{ "Java_sun_net_spi_DefaultProxySelector_init", vm::voidPointer(Java_sun_net_spi_DefaultProxySelector_init) },
{ "Java_sun_nio_ch_DatagramChannelImpl_disconnect0", vm::voidPointer(Java_sun_nio_ch_DatagramChannelImpl_disconnect0) },
{ "Java_sun_nio_ch_DatagramChannelImpl_initIDs", vm::voidPointer(Java_sun_nio_ch_DatagramChannelImpl_initIDs) },
{ "Java_sun_nio_ch_DatagramChannelImpl_receive0", vm::voidPointer(Java_sun_nio_ch_DatagramChannelImpl_receive0) },
{ "Java_sun_nio_ch_DatagramChannelImpl_send0", vm::voidPointer(Java_sun_nio_ch_DatagramChannelImpl_send0) },
{ "Java_sun_nio_ch_DatagramDispatcher_read0", vm::voidPointer(Java_sun_nio_ch_DatagramDispatcher_read0) },
{ "Java_sun_nio_ch_DatagramDispatcher_readv0", vm::voidPointer(Java_sun_nio_ch_DatagramDispatcher_readv0) },
{ "Java_sun_nio_ch_DatagramDispatcher_write0", vm::voidPointer(Java_sun_nio_ch_DatagramDispatcher_write0) },
{ "Java_sun_nio_ch_DatagramDispatcher_writev0", vm::voidPointer(Java_sun_nio_ch_DatagramDispatcher_writev0) },
{ "Java_sun_nio_ch_EPollArrayWrapper_epollCreate", vm::voidPointer(Java_sun_nio_ch_EPollArrayWrapper_epollCreate) },
{ "Java_sun_nio_ch_EPollArrayWrapper_epollCtl", vm::voidPointer(Java_sun_nio_ch_EPollArrayWrapper_epollCtl) },
{ "Java_sun_nio_ch_EPollArrayWrapper_epollWait", vm::voidPointer(Java_sun_nio_ch_EPollArrayWrapper_epollWait) },
{ "Java_sun_nio_ch_EPollArrayWrapper_init", vm::voidPointer(Java_sun_nio_ch_EPollArrayWrapper_init) },
{ "Java_sun_nio_ch_EPollArrayWrapper_interrupt", vm::voidPointer(Java_sun_nio_ch_EPollArrayWrapper_interrupt) },
{ "Java_sun_nio_ch_EPollArrayWrapper_offsetofData", vm::voidPointer(Java_sun_nio_ch_EPollArrayWrapper_offsetofData) },
{ "Java_sun_nio_ch_EPollArrayWrapper_sizeofEPollEvent", vm::voidPointer(Java_sun_nio_ch_EPollArrayWrapper_sizeofEPollEvent) },
{ "Java_sun_nio_ch_FileChannelImpl_close0", vm::voidPointer(Java_sun_nio_ch_FileChannelImpl_close0) },
{ "Java_sun_nio_ch_FileChannelImpl_initIDs", vm::voidPointer(Java_sun_nio_ch_FileChannelImpl_initIDs) },
{ "Java_sun_nio_ch_FileChannelImpl_map0", vm::voidPointer(Java_sun_nio_ch_FileChannelImpl_map0) },
{ "Java_sun_nio_ch_FileChannelImpl_position0", vm::voidPointer(Java_sun_nio_ch_FileChannelImpl_position0) },
{ "Java_sun_nio_ch_FileChannelImpl_transferTo0", vm::voidPointer(Java_sun_nio_ch_FileChannelImpl_transferTo0) },
{ "Java_sun_nio_ch_FileChannelImpl_unmap0", vm::voidPointer(Java_sun_nio_ch_FileChannelImpl_unmap0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_close0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_close0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_closeIntFD", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_closeIntFD) },
{ "Java_sun_nio_ch_FileDispatcherImpl_force0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_force0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_init", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_init) },
{ "Java_sun_nio_ch_FileDispatcherImpl_lock0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_lock0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_preClose0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_preClose0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_pread0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_pread0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_pwrite0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_pwrite0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_read0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_read0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_readv0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_readv0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_release0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_release0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_size0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_size0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_truncate0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_truncate0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_write0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_write0) },
{ "Java_sun_nio_ch_FileDispatcherImpl_writev0", vm::voidPointer(Java_sun_nio_ch_FileDispatcherImpl_writev0) },
{ "Java_sun_nio_ch_FileKey_init", vm::voidPointer(Java_sun_nio_ch_FileKey_init) },
{ "Java_sun_nio_ch_FileKey_initIDs", vm::voidPointer(Java_sun_nio_ch_FileKey_initIDs) },
{ "Java_sun_nio_ch_IOUtil_configureBlocking", vm::voidPointer(Java_sun_nio_ch_IOUtil_configureBlocking) },
{ "Java_sun_nio_ch_IOUtil_drain", vm::voidPointer(Java_sun_nio_ch_IOUtil_drain) },
{ "Java_sun_nio_ch_IOUtil_fdLimit", vm::voidPointer(Java_sun_nio_ch_IOUtil_fdLimit) },
{ "Java_sun_nio_ch_IOUtil_fdVal", vm::voidPointer(Java_sun_nio_ch_IOUtil_fdVal) },
{ "Java_sun_nio_ch_IOUtil_initIDs", vm::voidPointer(Java_sun_nio_ch_IOUtil_initIDs) },
{ "Java_sun_nio_ch_IOUtil_iovMax", vm::voidPointer(Java_sun_nio_ch_IOUtil_iovMax) },
{ "Java_sun_nio_ch_IOUtil_makePipe", vm::voidPointer(Java_sun_nio_ch_IOUtil_makePipe) },
{ "Java_sun_nio_ch_IOUtil_randomBytes", vm::voidPointer(Java_sun_nio_ch_IOUtil_randomBytes) },
{ "Java_sun_nio_ch_IOUtil_setfdVal", vm::voidPointer(Java_sun_nio_ch_IOUtil_setfdVal) },
{ "Java_sun_nio_ch_InheritedChannel_close0", vm::voidPointer(Java_sun_nio_ch_InheritedChannel_close0) },
{ "Java_sun_nio_ch_InheritedChannel_dup", vm::voidPointer(Java_sun_nio_ch_InheritedChannel_dup) },
{ "Java_sun_nio_ch_InheritedChannel_dup2", vm::voidPointer(Java_sun_nio_ch_InheritedChannel_dup2) },
{ "Java_sun_nio_ch_InheritedChannel_open0", vm::voidPointer(Java_sun_nio_ch_InheritedChannel_open0) },
{ "Java_sun_nio_ch_InheritedChannel_peerAddress0", vm::voidPointer(Java_sun_nio_ch_InheritedChannel_peerAddress0) },
{ "Java_sun_nio_ch_InheritedChannel_peerPort0", vm::voidPointer(Java_sun_nio_ch_InheritedChannel_peerPort0) },
{ "Java_sun_nio_ch_InheritedChannel_soType0", vm::voidPointer(Java_sun_nio_ch_InheritedChannel_soType0) },
{ "Java_sun_nio_ch_NativeThread_current", vm::voidPointer(Java_sun_nio_ch_NativeThread_current) },
{ "Java_sun_nio_ch_NativeThread_init", vm::voidPointer(Java_sun_nio_ch_NativeThread_init) },
{ "Java_sun_nio_ch_NativeThread_signal", vm::voidPointer(Java_sun_nio_ch_NativeThread_signal) },
{ "Java_sun_nio_ch_Net_bind0", vm::voidPointer(Java_sun_nio_ch_Net_bind0) },
{ "Java_sun_nio_ch_Net_blockOrUnblock4", vm::voidPointer(Java_sun_nio_ch_Net_blockOrUnblock4) },
{ "Java_sun_nio_ch_Net_blockOrUnblock6", vm::voidPointer(Java_sun_nio_ch_Net_blockOrUnblock6) },
{ "Java_sun_nio_ch_Net_canIPv6SocketJoinIPv4Group0", vm::voidPointer(Java_sun_nio_ch_Net_canIPv6SocketJoinIPv4Group0) },
{ "Java_sun_nio_ch_Net_canJoin6WithIPv4Group0", vm::voidPointer(Java_sun_nio_ch_Net_canJoin6WithIPv4Group0) },
{ "Java_sun_nio_ch_Net_connect0", vm::voidPointer(Java_sun_nio_ch_Net_connect0) },
{ "Java_sun_nio_ch_Net_getIntOption0", vm::voidPointer(Java_sun_nio_ch_Net_getIntOption0) },
{ "Java_sun_nio_ch_Net_getInterface4", vm::voidPointer(Java_sun_nio_ch_Net_getInterface4) },
{ "Java_sun_nio_ch_Net_getInterface6", vm::voidPointer(Java_sun_nio_ch_Net_getInterface6) },
{ "Java_sun_nio_ch_Net_initIDs", vm::voidPointer(Java_sun_nio_ch_Net_initIDs) },
{ "Java_sun_nio_ch_Net_isExclusiveBindAvailable", vm::voidPointer(Java_sun_nio_ch_Net_isExclusiveBindAvailable) },
{ "Java_sun_nio_ch_Net_isIPv6Available0", vm::voidPointer(Java_sun_nio_ch_Net_isIPv6Available0) },
{ "Java_sun_nio_ch_Net_joinOrDrop4", vm::voidPointer(Java_sun_nio_ch_Net_joinOrDrop4) },
{ "Java_sun_nio_ch_Net_joinOrDrop6", vm::voidPointer(Java_sun_nio_ch_Net_joinOrDrop6) },
{ "Java_sun_nio_ch_Net_listen", vm::voidPointer(Java_sun_nio_ch_Net_listen) },
{ "Java_sun_nio_ch_Net_localInetAddress", vm::voidPointer(Java_sun_nio_ch_Net_localInetAddress) },
{ "Java_sun_nio_ch_Net_localPort", vm::voidPointer(Java_sun_nio_ch_Net_localPort) },
{ "Java_sun_nio_ch_Net_poll", vm::voidPointer(Java_sun_nio_ch_Net_poll) },
{ "Java_sun_nio_ch_Net_pollconnValue", vm::voidPointer(Java_sun_nio_ch_Net_pollconnValue) },
{ "Java_sun_nio_ch_Net_pollerrValue", vm::voidPointer(Java_sun_nio_ch_Net_pollerrValue) },
{ "Java_sun_nio_ch_Net_pollhupValue", vm::voidPointer(Java_sun_nio_ch_Net_pollhupValue) },
{ "Java_sun_nio_ch_Net_pollinValue", vm::voidPointer(Java_sun_nio_ch_Net_pollinValue) },
{ "Java_sun_nio_ch_Net_pollnvalValue", vm::voidPointer(Java_sun_nio_ch_Net_pollnvalValue) },
{ "Java_sun_nio_ch_Net_polloutValue", vm::voidPointer(Java_sun_nio_ch_Net_polloutValue) },
{ "Java_sun_nio_ch_Net_setIntOption0", vm::voidPointer(Java_sun_nio_ch_Net_setIntOption0) },
{ "Java_sun_nio_ch_Net_setInterface4", vm::voidPointer(Java_sun_nio_ch_Net_setInterface4) },
{ "Java_sun_nio_ch_Net_setInterface6", vm::voidPointer(Java_sun_nio_ch_Net_setInterface6) },
{ "Java_sun_nio_ch_Net_shutdown", vm::voidPointer(Java_sun_nio_ch_Net_shutdown) },
{ "Java_sun_nio_ch_Net_socket0", vm::voidPointer(Java_sun_nio_ch_Net_socket0) },
{ "Java_sun_nio_ch_PollArrayWrapper_interrupt", vm::voidPointer(Java_sun_nio_ch_PollArrayWrapper_interrupt) },
{ "Java_sun_nio_ch_PollArrayWrapper_poll0", vm::voidPointer(Java_sun_nio_ch_PollArrayWrapper_poll0) },
{ "Java_sun_nio_ch_ServerSocketChannelImpl_accept0", vm::voidPointer(Java_sun_nio_ch_ServerSocketChannelImpl_accept0) },
{ "Java_sun_nio_ch_ServerSocketChannelImpl_initIDs", vm::voidPointer(Java_sun_nio_ch_ServerSocketChannelImpl_initIDs) },
{ "Java_sun_nio_ch_SocketChannelImpl_checkConnect", vm::voidPointer(Java_sun_nio_ch_SocketChannelImpl_checkConnect) },
{ "Java_sun_nio_ch_SocketChannelImpl_sendOutOfBandData", vm::voidPointer(Java_sun_nio_ch_SocketChannelImpl_sendOutOfBandData) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_access0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_access0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_chmod0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_chmod0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_chown0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_chown0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_close", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_close) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_closedir", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_closedir) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_dup", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_dup) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fchmod", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fchmod) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fchown", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fchown) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fclose", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fclose) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fdopendir", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fdopendir) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fopen0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fopen0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fpathconf", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fpathconf) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fstat", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fstat) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_fstatat0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_fstatat0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_futimes", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_futimes) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_getcwd", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_getcwd) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_getgrgid", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_getgrgid) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_getgrnam0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_getgrnam0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_getpwnam0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_getpwnam0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_getpwuid", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_getpwuid) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_init", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_init) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_lchown0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_lchown0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_link0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_link0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_lstat0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_lstat0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_mkdir0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_mkdir0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_mknod0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_mknod0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_open0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_open0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_openat0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_openat0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_opendir0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_opendir0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_pathconf0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_pathconf0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_read", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_read) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_readdir", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_readdir) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_readlink0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_readlink0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_realpath0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_realpath0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_rename0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_rename0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_renameat0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_renameat0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_rmdir0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_rmdir0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_stat0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_stat0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_statvfs0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_statvfs0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_strerror", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_strerror) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_symlink0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_symlink0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_unlink0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_unlink0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_unlinkat0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_unlinkat0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_utimes0", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_utimes0) },
{ "Java_sun_nio_fs_UnixNativeDispatcher_write", vm::voidPointer(Java_sun_nio_fs_UnixNativeDispatcher_write) },
{ "Java_sun_reflect_ConstantPool_getClassAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getClassAt0) },
{ "Java_sun_reflect_ConstantPool_getClassAtIfLoaded0", vm::voidPointer(Java_sun_reflect_ConstantPool_getClassAtIfLoaded0) },
{ "Java_sun_reflect_ConstantPool_getDoubleAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getDoubleAt0) },
{ "Java_sun_reflect_ConstantPool_getFieldAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getFieldAt0) },
{ "Java_sun_reflect_ConstantPool_getFieldAtIfLoaded0", vm::voidPointer(Java_sun_reflect_ConstantPool_getFieldAtIfLoaded0) },
{ "Java_sun_reflect_ConstantPool_getFloatAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getFloatAt0) },
{ "Java_sun_reflect_ConstantPool_getIntAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getIntAt0) },
{ "Java_sun_reflect_ConstantPool_getLongAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getLongAt0) },
{ "Java_sun_reflect_ConstantPool_getMemberRefInfoAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getMemberRefInfoAt0) },
{ "Java_sun_reflect_ConstantPool_getMethodAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getMethodAt0) },
{ "Java_sun_reflect_ConstantPool_getMethodAtIfLoaded0", vm::voidPointer(Java_sun_reflect_ConstantPool_getMethodAtIfLoaded0) },
{ "Java_sun_reflect_ConstantPool_getSize0", vm::voidPointer(Java_sun_reflect_ConstantPool_getSize0) },
{ "Java_sun_reflect_ConstantPool_getStringAt0", vm::voidPointer(Java_sun_reflect_ConstantPool_getStringAt0) },
{ "Java_sun_reflect_ConstantPool_getUTF8At0", vm::voidPointer(Java_sun_reflect_ConstantPool_getUTF8At0) },
{ "Java_sun_reflect_NativeConstructorAccessorImpl_newInstance0", vm::voidPointer(Java_sun_reflect_NativeConstructorAccessorImpl_newInstance0) },
{ "Java_sun_reflect_NativeMethodAccessorImpl_invoke0", vm::voidPointer(Java_sun_reflect_NativeMethodAccessorImpl_invoke0) },
{ "Java_sun_reflect_Reflection_getCallerClass__", vm::voidPointer(Java_sun_reflect_Reflection_getCallerClass__) },
{ "Java_sun_reflect_Reflection_getCallerClass__I", vm::voidPointer(Java_sun_reflect_Reflection_getCallerClass__I) },
{ "Java_sun_reflect_Reflection_getClassAccessFlags", vm::voidPointer(Java_sun_reflect_Reflection_getClassAccessFlags) },
            { NULL, NULL }
    };

    static int symbol_table_size = -1;

    static int comparator(const void *e1, const void *e2) {
        const struct entry *entry1 = static_cast<const struct entry*>(e1);
        const struct entry *entry2 = static_cast<const struct entry*>(e2);
        return strcmp(entry1->name, entry2->name);
    }
}

extern "C" const void *find_in_dispatch_table(const char *name) {
    if (symbol_table_size == -1) {
        symbol_table_size = 0;
        while (entries[symbol_table_size].name) symbol_table_size++;
    }
    struct entry key = {name, NULL};
    struct entry *result = (struct entry *) bsearch(&key, entries, symbol_table_size, sizeof(struct entry), comparator);
    if (result) {
        return result->addr;
    } else {
        return NULL;
    }
}

#endif //AVIAN_SGX_DISPATCH_TABLE_H
