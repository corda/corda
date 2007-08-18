#ifndef MACHINE_H
#define MACHINE_H

#include "common.h"
#include "system.h"
#include "heap.h"
#include "finder.h"

#define JNICALL

#define PROTECT(thread, name)                                   \
  Thread::Protector MAKE_NAME(protector_) (thread, &name);

#define ACQUIRE(t, x) MonitorResource MAKE_NAME(monitorResource_) (t, x)

#define ACQUIRE_RAW(t, x) RawMonitorResource MAKE_NAME(monitorResource_) (t, x)

#define ENTER(t, state) StateResource MAKE_NAME(stateResource_) (t, state)

namespace vm {

const bool Verbose = false;
const bool DebugRun = false;
const bool DebugStack = false;
const bool DebugMonitors = false;
const bool DebugReferences = false;

const uintptr_t HashTakenMark = 1;
const uintptr_t ExtendedMark = 2;

const unsigned FrameBaseOffset = 0;
const unsigned FrameNextOffset = 1;
const unsigned FrameMethodOffset = 2;
const unsigned FrameIpOffset = 3;
const unsigned FrameFootprint = 4;

enum FieldCode {
  VoidField,
  ByteField,
  CharField,
  DoubleField,
  FloatField,
  IntField,
  LongField,
  ShortField,
  BooleanField,
  ObjectField
};

enum StackTag {
  IntTag, // must be zero
  ObjectTag
};

const int NativeLine = -1;
const int UnknownLine = -2;

// class flags:
const unsigned ReferenceFlag = 1 << 0;
const unsigned WeakReferenceFlag = 1 << 1;
const unsigned NeedInitFlag = 1 << 2;
const unsigned InitFlag = 1 << 3;
const unsigned PrimitiveFlag = 1 << 4;

// method flags:
const unsigned ClassInitFlag = 1 << 0;

class Thread;

typedef Thread JNIEnv;

typedef uint8_t jboolean;
typedef int8_t jbyte;
typedef uint16_t jchar;
typedef int16_t jshort;
typedef int32_t jint;
typedef int64_t jlong;
typedef float jfloat;
typedef double jdouble;

typedef jint jsize;

typedef void** jobject;

typedef jobject jclass;
typedef jobject jthrowable;
typedef jobject jstring;
typedef jobject jweak;

typedef jobject jarray;
typedef jarray jbooleanArray;
typedef jarray jbyteArray;
typedef jarray jcharArray;
typedef jarray jshortArray;
typedef jarray jintArray;
typedef jarray jlongArray;
typedef jarray jfloatArray;
typedef jarray jdoubleArray;
typedef jarray jobjectArray;

typedef void** jfieldID;
typedef void** jmethodID;

union jvalue {
  jboolean z;
  jbyte    b;
  jchar    c;
  jshort   s;
  jint     i;
  jlong    j;
  jfloat   f;
  jdouble  d;
  jobject  l;
};

struct JNINativeMethod {
  char* name;
  char* signature;
  void* function;
};

struct JavaVMVTable;

struct JavaVM {
  JavaVM(JavaVMVTable* vtable): vtable(vtable) { }

  JavaVMVTable* vtable;
};

struct JavaVMVTable {
  void* reserved0;
  void* reserved1;
  void* reserved2;

  jint
  (JNICALL *DestroyJavaVM)
  (JavaVM*);

  jint
  (JNICALL *AttachCurrentThread)
  (JavaVM*, void**, void*);

  jint
  (JNICALL *DetachCurrentThread)
  (JavaVM*);

  jint
  (JNICALL *GetEnv)
  (JavaVM*, void**, jint);

  jint
  (JNICALL *AttachCurrentThreadAsDaemon)
  (JavaVM*, void**, void*);
};

struct JNIEnvVTable {
  void* reserved0;
  void* reserved1;
  void* reserved2;
  void* reserved3;

  jint
  (JNICALL *GetVersion)
    (JNIEnv*);

  jclass
  (JNICALL *DefineClass)
    (JNIEnv*, const char*, jobject, const jbyte*, jsize);

  jclass
  (JNICALL *FindClass)
    (JNIEnv*, const char*);

  jmethodID
  (JNICALL *FromReflectedMethod)
    (JNIEnv*, jobject);

  jfieldID
  (JNICALL *FromReflectedField)
    (JNIEnv*, jobject);

  jobject
  (JNICALL *ToReflectedMethod)
    (JNIEnv*, jclass, jmethodID, jboolean);

  jclass
  (JNICALL *GetSuperclass)
    (JNIEnv*, jclass);

  jboolean
  (JNICALL *IsAssignableFrom)
    (JNIEnv*, jclass, jclass);

  jobject
  (JNICALL *ToReflectedField)
    (JNIEnv*, jclass, jfieldID, jboolean);

  jint
  (JNICALL *Throw)
    (JNIEnv*, jthrowable);

  jint
  (JNICALL *ThrowNew)
    (JNIEnv*, jclass, const char*);

  jthrowable
  (JNICALL *ExceptionOccurred)
    (JNIEnv*);

  void
  (JNICALL *ExceptionDescribe)
  (JNIEnv*);

  void
  (JNICALL *ExceptionClear)
  (JNIEnv*);

  void
  (JNICALL *FatalError)
  (JNIEnv*, const char*);

  jint
  (JNICALL *PushLocalFrame)
    (JNIEnv*, jint);

  jobject
  (JNICALL *PopLocalFrame)
    (JNIEnv*, jobject);

  jobject
  (JNICALL *NewGlobalRef)
    (JNIEnv*, jobject);

  void
  (JNICALL *DeleteGlobalRef)
  (JNIEnv*, jobject);

  void
  (JNICALL *DeleteLocalRef)
  (JNIEnv*, jobject);

  jboolean
  (JNICALL *IsSameObject)
    (JNIEnv*, jobject, jobject);

  jobject
  (JNICALL *NewLocalRef)
    (JNIEnv*, jobject);

  jint
  (JNICALL *EnsureLocalCapacity)
    (JNIEnv*, jint);

  jobject
  (JNICALL *AllocObject)
    (JNIEnv*, jclass);

  jobject
  (JNICALL *NewObject)
    (JNIEnv*, jclass, jmethodID, ...);

  jobject
  (JNICALL *NewObjectV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jobject
  (JNICALL *NewObjectA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jclass
  (JNICALL *GetObjectClass)
    (JNIEnv*, jobject);

  jboolean
  (JNICALL *IsInstanceOf)
    (JNIEnv*, jobject, jclass);

  jmethodID
  (JNICALL *GetMethodID)
    (JNIEnv*, jclass, const char*, const char*);

  jobject
  (JNICALL *CallObjectMethod)
    (JNIEnv*, jobject, jmethodID, ...);

  jobject
  (JNICALL *CallObjectMethodV)
    (JNIEnv*, jobject, jmethodID, va_list);

  jobject
  (JNICALL *CallObjectMethodA)
    (JNIEnv*, jobject, jmethodID, const jvalue*);

  jboolean
  (JNICALL *CallBooleanMethod)
    (JNIEnv*, jobject, jmethodID, ...);

  jboolean
  (JNICALL *CallBooleanMethodV)
    (JNIEnv*, jobject, jmethodID, va_list);

  jboolean
  (JNICALL *CallBooleanMethodA)
    (JNIEnv*, jobject, jmethodID, const jvalue*);

  jbyte
  (JNICALL *CallByteMethod)
    (JNIEnv*, jobject, jmethodID, ...);

  jbyte
  (JNICALL *CallByteMethodV)
    (JNIEnv*, jobject, jmethodID, va_list);

  jbyte
  (JNICALL *CallByteMethodA)
    (JNIEnv*, jobject, jmethodID, const jvalue*);

  jchar
  (JNICALL *CallCharMethod)
    (JNIEnv*, jobject, jmethodID, ...);

  jchar
  (JNICALL *CallCharMethodV)
    (JNIEnv*, jobject, jmethodID, va_list);

  jchar
  (JNICALL *CallCharMethodA)
    (JNIEnv*, jobject, jmethodID, const jvalue*);

  jshort
  (JNICALL *CallShortMethod)
    (JNIEnv*, jobject, jmethodID, ...);

  jshort
  (JNICALL *CallShortMethodV)
    (JNIEnv*, jobject, jmethodID, va_list);

  jshort
  (JNICALL *CallShortMethodA)
    (JNIEnv*, jobject, jmethodID, const jvalue*);

  jint
  (JNICALL *CallIntMethod)
    (JNIEnv*, jobject, jmethodID, ...);

  jint
  (JNICALL *CallIntMethodV)
    (JNIEnv*, jobject, jmethodID, va_list);

  jint
  (JNICALL *CallIntMethodA)
    (JNIEnv*, jobject, jmethodID, const jvalue*);

  jlong
  (JNICALL *CallLongMethod)
    (JNIEnv*, jobject, jmethodID, ...);

  jlong
  (JNICALL *CallLongMethodV)
    (JNIEnv*, jobject, jmethodID, va_list);

  jlong
  (JNICALL *CallLongMethodA)
    (JNIEnv*, jobject, jmethodID, const jvalue*);

  jfloat
  (JNICALL *CallFloatMethod)
  (JNIEnv*, jobject, jmethodID, ...);

  jfloat
  (JNICALL *CallFloatMethodV)
  (JNIEnv*, jobject, jmethodID, va_list);

  jfloat
  (JNICALL *CallFloatMethodA)
  (JNIEnv*, jobject, jmethodID, const jvalue*);

  jdouble
  (JNICALL *CallDoubleMethod)
  (JNIEnv*, jobject, jmethodID, ...);

  jdouble
  (JNICALL *CallDoubleMethodV)
  (JNIEnv*, jobject, jmethodID, va_list);

  jdouble
  (JNICALL *CallDoubleMethodA)
  (JNIEnv*, jobject, jmethodID, const jvalue*);

  void
  (JNICALL *CallVoidMethod)
  (JNIEnv*, jobject, jmethodID, ...);

  void
  (JNICALL *CallVoidMethodV)
  (JNIEnv*, jobject, jmethodID, va_list);

  void
  (JNICALL *CallVoidMethodA)
  (JNIEnv*, jobject, jmethodID, const jvalue*);

  jobject
  (JNICALL *CallNonvirtualObjectMethod)
    (JNIEnv*, jobject, jclass, jmethodID, ...);

  jobject
  (JNICALL *CallNonvirtualObjectMethodV)
    (JNIEnv*, jobject, jclass, jmethodID, va_list);

  jobject
  (JNICALL *CallNonvirtualObjectMethodA)
    (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  jboolean
  (JNICALL *CallNonvirtualBooleanMethod)
    (JNIEnv*, jobject, jclass, jmethodID, ...);

  jboolean
  (JNICALL *CallNonvirtualBooleanMethodV)
    (JNIEnv*, jobject, jclass, jmethodID, va_list);

  jboolean
  (JNICALL *CallNonvirtualBooleanMethodA)
    (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  jbyte
  (JNICALL *CallNonvirtualByteMethod)
    (JNIEnv*, jobject, jclass, jmethodID, ...);

  jbyte
  (JNICALL *CallNonvirtualByteMethodV)
    (JNIEnv*, jobject, jclass, jmethodID, va_list);

  jbyte
  (JNICALL *CallNonvirtualByteMethodA)
    (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  jchar
  (JNICALL *CallNonvirtualCharMethod)
    (JNIEnv*, jobject, jclass, jmethodID, ...);

  jchar
  (JNICALL *CallNonvirtualCharMethodV)
    (JNIEnv*, jobject, jclass, jmethodID, va_list);

  jchar
  (JNICALL *CallNonvirtualCharMethodA)
    (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  jshort
  (JNICALL *CallNonvirtualShortMethod)
    (JNIEnv*, jobject, jclass, jmethodID, ...);

  jshort
  (JNICALL *CallNonvirtualShortMethodV)
    (JNIEnv*, jobject, jclass, jmethodID,
     va_list);

  jshort
  (JNICALL *CallNonvirtualShortMethodA)
    (JNIEnv*, jobject, jclass, jmethodID,
     const jvalue*);

  jint
  (JNICALL *CallNonvirtualIntMethod)
    (JNIEnv*, jobject, jclass, jmethodID, ...);

  jint
  (JNICALL *CallNonvirtualIntMethodV)
    (JNIEnv*, jobject, jclass, jmethodID,
     va_list);

  jint
  (JNICALL *CallNonvirtualIntMethodA)
    (JNIEnv*, jobject, jclass, jmethodID,
     const jvalue*);

  jlong
  (JNICALL *CallNonvirtualLongMethod)
    (JNIEnv*, jobject, jclass, jmethodID, ...);

  jlong
  (JNICALL *CallNonvirtualLongMethodV)
    (JNIEnv*, jobject, jclass, jmethodID,
     va_list);
  jlong
  (JNICALL *CallNonvirtualLongMethodA)
    (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  jfloat
  (JNICALL *CallNonvirtualFloatMethod)
  (JNIEnv*, jobject, jclass, jmethodID, ...);

  jfloat
  (JNICALL *CallNonvirtualFloatMethodV)
  (JNIEnv*, jobject, jclass, jmethodID, va_list);

  jfloat
  (JNICALL *CallNonvirtualFloatMethodA)
  (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  jdouble
  (JNICALL *CallNonvirtualDoubleMethod)
  (JNIEnv*, jobject, jclass, jmethodID, ...);

  jdouble
  (JNICALL *CallNonvirtualDoubleMethodV)
  (JNIEnv*, jobject, jclass, jmethodID, va_list);

  jdouble
  (JNICALL *CallNonvirtualDoubleMethodA)
  (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  void
  (JNICALL *CallNonvirtualVoidMethod)
  (JNIEnv*, jobject, jclass, jmethodID, ...);

  void
  (JNICALL *CallNonvirtualVoidMethodV)
  (JNIEnv*, jobject, jclass, jmethodID, va_list);

  void
  (JNICALL *CallNonvirtualVoidMethodA)
  (JNIEnv*, jobject, jclass, jmethodID, const jvalue*);

  jfieldID
  (JNICALL *GetFieldID)
    (JNIEnv*, jclass, const char*, const char*);

  jobject
  (JNICALL *GetObjectField)
    (JNIEnv*, jobject, jfieldID);

  jboolean
  (JNICALL *GetBooleanField)
    (JNIEnv*, jobject, jfieldID);

  jbyte
  (JNICALL *GetByteField)
    (JNIEnv*, jobject, jfieldID);

  jchar
  (JNICALL *GetCharField)
    (JNIEnv*, jobject, jfieldID);

  jshort
  (JNICALL *GetShortField)
    (JNIEnv*, jobject, jfieldID);

  jint
  (JNICALL *GetIntField)
    (JNIEnv*, jobject, jfieldID);

  jlong
  (JNICALL *GetLongField)
    (JNIEnv*, jobject, jfieldID);

  jfloat
  (JNICALL *GetFloatField)
  (JNIEnv*, jobject, jfieldID);

  jdouble
  (JNICALL *GetDoubleField)
  (JNIEnv*, jobject, jfieldID);

  void
  (JNICALL *SetObjectField)
  (JNIEnv*, jobject, jfieldID, jobject);

  void
  (JNICALL *SetBooleanField)
  (JNIEnv*, jobject, jfieldID, jboolean);

  void
  (JNICALL *SetByteField)
  (JNIEnv*, jobject, jfieldID, jbyte);

  void
  (JNICALL *SetCharField)
  (JNIEnv*, jobject, jfieldID, jchar);

  void
  (JNICALL *SetShortField)
  (JNIEnv*, jobject, jfieldID, jshort);

  void
  (JNICALL *SetIntField)
  (JNIEnv*, jobject, jfieldID, jint);

  void
  (JNICALL *SetLongField)
  (JNIEnv*, jobject, jfieldID, jlong);

  void
  (JNICALL *SetFloatField)
  (JNIEnv*, jobject, jfieldID, jfloat);

  void
  (JNICALL *SetDoubleField)
  (JNIEnv*, jobject, jfieldID, jdouble);

  jmethodID
  (JNICALL *GetStaticMethodID)
    (JNIEnv*, jclass, const char*, const char*);

  jobject
  (JNICALL *CallStaticObjectMethod)
    (JNIEnv*, jclass, jmethodID, ...);

  jobject
  (JNICALL *CallStaticObjectMethodV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jobject
  (JNICALL *CallStaticObjectMethodA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jboolean
  (JNICALL *CallStaticBooleanMethod)
    (JNIEnv*, jclass, jmethodID, ...);

  jboolean
  (JNICALL *CallStaticBooleanMethodV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jboolean
  (JNICALL *CallStaticBooleanMethodA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jbyte
  (JNICALL *CallStaticByteMethod)
    (JNIEnv*, jclass, jmethodID, ...);

  jbyte
  (JNICALL *CallStaticByteMethodV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jbyte
  (JNICALL *CallStaticByteMethodA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jchar
  (JNICALL *CallStaticCharMethod)
    (JNIEnv*, jclass, jmethodID, ...);

  jchar
  (JNICALL *CallStaticCharMethodV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jchar
  (JNICALL *CallStaticCharMethodA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jshort
  (JNICALL *CallStaticShortMethod)
    (JNIEnv*, jclass, jmethodID, ...);

  jshort
  (JNICALL *CallStaticShortMethodV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jshort
  (JNICALL *CallStaticShortMethodA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jint
  (JNICALL *CallStaticIntMethod)
    (JNIEnv*, jclass, jmethodID, ...);

  jint
  (JNICALL *CallStaticIntMethodV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jint
  (JNICALL *CallStaticIntMethodA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jlong
  (JNICALL *CallStaticLongMethod)
    (JNIEnv*, jclass, jmethodID, ...);

  jlong
  (JNICALL *CallStaticLongMethodV)
    (JNIEnv*, jclass, jmethodID, va_list);

  jlong
  (JNICALL *CallStaticLongMethodA)
    (JNIEnv*, jclass, jmethodID, const jvalue*);

  jfloat
  (JNICALL *CallStaticFloatMethod)
  (JNIEnv*, jclass, jmethodID, ...);

  jfloat
  (JNICALL *CallStaticFloatMethodV)
  (JNIEnv*, jclass, jmethodID, va_list);

  jfloat
  (JNICALL *CallStaticFloatMethodA)
  (JNIEnv*, jclass, jmethodID, const jvalue*);

  jdouble
  (JNICALL *CallStaticDoubleMethod)
  (JNIEnv*, jclass, jmethodID, ...);

  jdouble
  (JNICALL *CallStaticDoubleMethodV)
  (JNIEnv*, jclass, jmethodID, va_list);

  jdouble
  (JNICALL *CallStaticDoubleMethodA)
  (JNIEnv*, jclass, jmethodID, const jvalue*);

  void
  (JNICALL *CallStaticVoidMethod)
  (JNIEnv*, jclass, jmethodID, ...);

  void
  (JNICALL *CallStaticVoidMethodV)
  (JNIEnv*, jclass, jmethodID, va_list);

  void
  (JNICALL *CallStaticVoidMethodA)
  (JNIEnv*, jclass, jmethodID, const jvalue*);

  jfieldID
  (JNICALL *GetStaticFieldID)
    (JNIEnv*, jclass, const char*, const char*);

  jobject
  (JNICALL *GetStaticObjectField)
    (JNIEnv*, jclass, jfieldID);

  jboolean
  (JNICALL *GetStaticBooleanField)
    (JNIEnv*, jclass, jfieldID);

  jbyte
  (JNICALL *GetStaticByteField)
    (JNIEnv*, jclass, jfieldID);

  jchar
  (JNICALL *GetStaticCharField)
    (JNIEnv*, jclass, jfieldID);

  jshort
  (JNICALL *GetStaticShortField)
    (JNIEnv*, jclass, jfieldID);

  jint
  (JNICALL *GetStaticIntField)
    (JNIEnv*, jclass, jfieldID);

  jlong
  (JNICALL *GetStaticLongField)
    (JNIEnv*, jclass, jfieldID);

  jfloat
  (JNICALL *GetStaticFloatField)
  (JNIEnv*, jclass, jfieldID);

  jdouble
  (JNICALL *GetStaticDoubleField)
  (JNIEnv*, jclass, jfieldID);

  void
  (JNICALL *SetStaticObjectField)
  (JNIEnv*, jclass, jfieldID, jobject);

  void
  (JNICALL *SetStaticBooleanField)
  (JNIEnv*, jclass, jfieldID, jboolean);

  void
  (JNICALL *SetStaticByteField)
  (JNIEnv*, jclass, jfieldID, jbyte);

  void
  (JNICALL *SetStaticCharField)
  (JNIEnv*, jclass, jfieldID, jchar);

  void
  (JNICALL *SetStaticShortField)
  (JNIEnv*, jclass, jfieldID, jshort);

  void
  (JNICALL *SetStaticIntField)
  (JNIEnv*, jclass, jfieldID, jint);

  void
  (JNICALL *SetStaticLongField)
  (JNIEnv*, jclass, jfieldID, jlong);

  void
  (JNICALL *SetStaticFloatField)
  (JNIEnv*, jclass, jfieldID, jfloat);

  void
  (JNICALL *SetStaticDoubleField)
  (JNIEnv*, jclass, jfieldID, jdouble);

  jstring
  (JNICALL *NewString)
    (JNIEnv*, const jchar*, jsize);

  jsize
  (JNICALL *GetStringLength)
    (JNIEnv*, jstring);

  const jchar*
  (JNICALL *GetStringChars)
  (JNIEnv*, jstring, jboolean*);

  void
  (JNICALL *ReleaseStringChars)
  (JNIEnv*, jstring, const jchar*);

  jstring
  (JNICALL *NewStringUTF)
    (JNIEnv*, const char*);

  jsize
  (JNICALL *GetStringUTFLength)
    (JNIEnv*, jstring);

  const char*
  (JNICALL *GetStringUTFChars)
  (JNIEnv*, jstring, jboolean*);

  void
  (JNICALL *ReleaseStringUTFChars)
  (JNIEnv*, jstring, const char*);

  jsize
  (JNICALL *GetArrayLength)
    (JNIEnv*, jarray);

  jobjectArray
  (JNICALL *NewObjectArray)
    (JNIEnv*, jsize, jclass, jobject);

  jobject
  (JNICALL *GetObjectArrayElement)
    (JNIEnv*, jobjectArray, jsize);

  void
  (JNICALL *SetObjectArrayElement)
  (JNIEnv*, jobjectArray, jsize, jobject);

  jbooleanArray
  (JNICALL *NewBooleanArray)
    (JNIEnv*, jsize);

  jbyteArray
  (JNICALL *NewByteArray)
    (JNIEnv*, jsize);

  jcharArray
  (JNICALL *NewCharArray)
    (JNIEnv*, jsize);

  jshortArray
  (JNICALL *NewShortArray)
    (JNIEnv*, jsize);

  jintArray
  (JNICALL *NewIntArray)
    (JNIEnv*, jsize);

  jlongArray
  (JNICALL *NewLongArray)
    (JNIEnv*, jsize);

  jfloatArray
  (JNICALL *NewFloatArray)
    (JNIEnv*, jsize);

  jdoubleArray
  (JNICALL *NewDoubleArray)
    (JNIEnv*, jsize);

  jboolean*
  (JNICALL *GetBooleanArrayElements)
  (JNIEnv*, jbooleanArray, jboolean*);

  jbyte*
  (JNICALL *GetByteArrayElements)
  (JNIEnv*, jbyteArray, jboolean*);

  jchar*
  (JNICALL *GetCharArrayElements)
  (JNIEnv*, jcharArray, jboolean*);

  jshort*
  (JNICALL *GetShortArrayElements)
  (JNIEnv*, jshortArray, jboolean*);

  jint*
  (JNICALL *GetIntArrayElements)
  (JNIEnv*, jintArray, jboolean*);

  jlong*
  (JNICALL *GetLongArrayElements)
  (JNIEnv*, jlongArray, jboolean*);

  jfloat*
  (JNICALL *GetFloatArrayElements)
  (JNIEnv*, jfloatArray, jboolean*);

  jdouble*
  (JNICALL *GetDoubleArrayElements)
  (JNIEnv*, jdoubleArray, jboolean*);

  void
  (JNICALL *ReleaseBooleanArrayElements)
  (JNIEnv*, jbooleanArray, jboolean*, jint);

  void
  (JNICALL *ReleaseByteArrayElements)
  (JNIEnv*, jbyteArray, jbyte*, jint);

  void
  (JNICALL *ReleaseCharArrayElements)
  (JNIEnv*, jcharArray, jchar*, jint);

  void
  (JNICALL *ReleaseShortArrayElements)
  (JNIEnv*, jshortArray, jshort*, jint);

  void
  (JNICALL *ReleaseIntArrayElements)
  (JNIEnv*, jintArray, jint*, jint);

  void
  (JNICALL *ReleaseLongArrayElements)
  (JNIEnv*, jlongArray, jlong*, jint);

  void
  (JNICALL *ReleaseFloatArrayElements)
  (JNIEnv*, jfloatArray, jfloat*, jint);

  void
  (JNICALL *ReleaseDoubleArrayElements)
  (JNIEnv*, jdoubleArray, jdouble*, jint);

  void
  (JNICALL *GetBooleanArrayRegion)
  (JNIEnv*, jbooleanArray, jsize, jsize, jboolean*);

  void
  (JNICALL *GetByteArrayRegion)
  (JNIEnv*, jbyteArray, jsize, jsize, jbyte*);

  void
  (JNICALL *GetCharArrayRegion)
  (JNIEnv*, jcharArray, jsize, jsize, jchar*);

  void
  (JNICALL *GetShortArrayRegion)
  (JNIEnv*, jshortArray, jsize, jsize, jshort*);

  void
  (JNICALL *GetIntArrayRegion)
  (JNIEnv*, jintArray, jsize, jsize, jint*);

  void
  (JNICALL *GetLongArrayRegion)
  (JNIEnv*, jlongArray, jsize, jsize, jlong*);

  void
  (JNICALL *GetFloatArrayRegion)
  (JNIEnv*, jfloatArray, jsize, jsize, jfloat*);

  void
  (JNICALL *GetDoubleArrayRegion)
  (JNIEnv*, jdoubleArray, jsize, jsize, jdouble*);

  void
  (JNICALL *SetBooleanArrayRegion)
  (JNIEnv*, jbooleanArray, jsize, jsize, const jboolean*);

  void
  (JNICALL *SetByteArrayRegion)
  (JNIEnv*, jbyteArray, jsize, jsize, const jbyte*);

  void
  (JNICALL *SetCharArrayRegion)
  (JNIEnv*, jcharArray, jsize, jsize, const jchar*);

  void
  (JNICALL *SetShortArrayRegion)
  (JNIEnv*, jshortArray, jsize, jsize, const jshort*);

  void
  (JNICALL *SetIntArrayRegion)
  (JNIEnv*, jintArray, jsize, jsize, const jint*);

  void
  (JNICALL *SetLongArrayRegion)
  (JNIEnv*, jlongArray, jsize, jsize, const jlong*);

  void
  (JNICALL *SetFloatArrayRegion)
  (JNIEnv*, jfloatArray, jsize, jsize, const jfloat*);

  void
  (JNICALL *SetDoubleArrayRegion)
  (JNIEnv*, jdoubleArray, jsize, jsize, const jdouble*);

  jint
  (JNICALL *RegisterNatives)
    (JNIEnv*, jclass, const JNINativeMethod*, jint);

  jint
  (JNICALL *UnregisterNatives)
    (JNIEnv*, jclass);

  jint
  (JNICALL *MonitorEnter)
    (JNIEnv*, jobject);

  jint
  (JNICALL *MonitorExit)
    (JNIEnv*, jobject);

  jint
  (JNICALL *GetJavaVM)
    (JNIEnv*, JavaVM**);

  void
  (JNICALL *GetStringRegion)
  (JNIEnv*, jstring, jsize, jsize, jchar*);

  void
  (JNICALL *GetStringUTFRegion)
  (JNIEnv*, jstring, jsize, jsize, char*);

  void*
  (JNICALL *GetPrimitiveArrayCritical)
  (JNIEnv*, jarray, jboolean*);

  void
  (JNICALL *ReleasePrimitiveArrayCritical)
  (JNIEnv*, jarray, void*, jint);

  const jchar*
  (JNICALL *GetStringCritical)
  (JNIEnv*, jstring, jboolean*);

  void
  (JNICALL *ReleaseStringCritical)
  (JNIEnv*, jstring, const jchar*);

  jweak
  (JNICALL *NewWeakGlobalRef)
  (JNIEnv*, jobject);

  void
  (JNICALL *DeleteWeakGlobalRef)
  (JNIEnv*, jweak);

  jboolean
  (JNICALL *ExceptionCheck)
    (JNIEnv*);

  jobject
  (JNICALL *NewDirectByteBuffer)
    (JNIEnv*, void*, jlong);

  void*
  (JNICALL *GetDirectBufferAddress)
  (JNIEnv* env, jobject);

  jlong
  (JNICALL *GetDirectBufferCapacity)
    (JNIEnv*, jobject);
};

inline int
strcmp(const int8_t* a, const int8_t* b)
{
  return ::strcmp(reinterpret_cast<const char*>(a),
                  reinterpret_cast<const char*>(b));
}

void
noop();

class Machine {
 public:
  enum {
#include "type-enums.cpp"
  } Type;

  Machine(System* system, Heap* heap, Finder* finder);

  ~Machine() { 
    dispose();
  }

  void dispose();

  System* system;
  Heap* heap;
  Finder* finder;
  Thread* rootThread;
  Thread* exclusive;
  unsigned activeCount;
  unsigned liveCount;
  System::Monitor* stateLock;
  System::Monitor* heapLock;
  System::Monitor* classLock;
  System::Monitor* referenceLock;
  System::Library* libraries;
  object loader;
  object bootstrapClassMap;
  object builtinMap;
  object monitorMap;
  object stringMap;
  object types;
  object finalizers;
  object tenuredFinalizers;
  object finalizeQueue;
  object weakReferences;
  object tenuredWeakReferences;
  bool unsafe;
  JNIEnvVTable jniEnvVTable;
};

object
run(Thread* t, const char* className, const char* methodName,
    const char* methodSpec, object this_, ...);

void
printTrace(Thread* t, object exception);

uint8_t&
threadInterrupted(Thread* t, object thread);

class Thread {
 public:
  enum State {
    NoState,
    ActiveState,
    IdleState,
    ZombieState,
    JoinedState,
    ExclusiveState,
    ExitState
  };

  class Protector {
   public:
    Protector(Thread* t, object* p): t(t), p(p), next(t->protector) {
      t->protector = this;
    }

    ~Protector() {
      t->protector = next;
    }

    Thread* t;
    object* p;
    Protector* next;
  };

  class Runnable: public System::Runnable {
   public:
    Runnable(Thread* t): t(t) { }

    virtual void attach(System::Thread* st) {
      t->systemThread = st;
    }

    virtual void run() {
      vm::run(t, "java/lang/Thread", "run", "()V", t->javaThread);

      if (t->exception) {
        printTrace(t, t->exception);
      }

      t->exit();
    }

    virtual bool interrupted() {
      return threadInterrupted(t, t->javaThread);
    }

    virtual void setInterrupted(bool v) {
      threadInterrupted(t, t->javaThread) = v;
    }

    Thread* t;
  };

  static const unsigned HeapSizeInBytes = 64 * 1024;
  static const unsigned StackSizeInBytes = 64 * 1024;

  static const unsigned HeapSizeInWords = HeapSizeInBytes / BytesPerWord;
  static const unsigned StackSizeInWords = StackSizeInBytes / BytesPerWord;

  Thread(Machine* m, object javaThread, Thread* parent);

  void exit();
  void dispose();

  JNIEnvVTable* vtable;
  Machine* vm;
  Thread* parent;
  Thread* peer;
  Thread* child;
  State state;
  System::Thread* systemThread;
  object javaThread;
  object code;
  object exception;
  object large;
  unsigned ip;
  unsigned sp;
  int frame;
  unsigned heapIndex;
  Protector* protector;
  Runnable runnable;
#ifdef VM_STRESS
  bool stress;
  object* heap;
#else // not VM_STRESS
  object heap[HeapSizeInWords];
#endif // not VM_STRESS
  uintptr_t stack[StackSizeInWords];
};

inline object
objectClass(Thread*, object o)
{
  return mask(cast<object>(o, 0));
}

void
enter(Thread* t, Thread::State state);

class StateResource {
 public:
  StateResource(Thread* t, Thread::State state): t(t), oldState(t->state) {
    enter(t, state);
  }

  ~StateResource() { enter(t, oldState); }

 private:
  Thread* t;
  Thread::State oldState;
};

void
collect(Thread* t, Heap::CollectionType type);

#ifdef VM_STRESS

inline void
stress(Thread* t)
{
  if ((not t->stress)
      and t->state != Thread::NoState
      and t->state != Thread::IdleState)
  {
    t->stress = true;
    { ENTER(t, Thread::ExclusiveState);

#  ifdef VM_STRESS_MAJOR
      collect(t, Heap::MajorCollection);
#  else // not VM_STRESS_MAJOR
      collect(t, Heap::MinorCollection);
#  endif // not VM_STRESS_MAJOR
    }

    t->stress = false;
  }
}

#else // not VM_STRESS

inline void
stress(Thread*)
{ }

#endif // not VM_STRESS

inline void
acquire(Thread* t, System::Monitor* m)
{
  if (not m->tryAcquire(t->systemThread)) {
    ENTER(t, Thread::IdleState);
    m->acquire(t->systemThread);
  }

  stress(t);
}

inline void
release(Thread* t, System::Monitor* m)
{
  m->release(t->systemThread);
}

class MonitorResource {
 public:
  MonitorResource(Thread* t, System::Monitor* m): t(t), m(m) {
    acquire(t, m);
  }

  ~MonitorResource() {
    release(t, m);
  }

 private:
  Thread* t;
  System::Monitor* m;
};

class RawMonitorResource {
 public:
  RawMonitorResource(Thread* t, System::Monitor* m): t(t), m(m) {
    m->acquire(t->systemThread);
  }

  ~RawMonitorResource() {
    release(t, m);
  }

 private:
  Thread* t;
  System::Monitor* m;
};

inline void NO_RETURN
abort(Thread* t)
{
  abort(t->vm->system);
}

inline void
assert(Thread* t, bool v)
{
  assert(t->vm->system, v);
}

inline void
expect(Thread* t, bool v)
{
  expect(t->vm->system, v);
}

inline object
allocateLarge(Thread* t, unsigned sizeInBytes)
{
  return t->large = t->vm->system->allocate(sizeInBytes);
}

inline object
allocateSmall(Thread* t, unsigned sizeInBytes)
{
  object o = t->heap + t->heapIndex;
  t->heapIndex += ceiling(sizeInBytes, BytesPerWord);
  return o;
}

object
allocate2(Thread* t, unsigned sizeInBytes);

inline object
allocate(Thread* t, unsigned sizeInBytes)
{
  stress(t);

  if (UNLIKELY(t->heapIndex + ceiling(sizeInBytes, BytesPerWord)
               >= Thread::HeapSizeInWords
               or t->vm->exclusive))
  {
    return allocate2(t, sizeInBytes);
  } else {
    return allocateSmall(t, sizeInBytes);
  }
}

inline void
mark(Thread* t, object& target)
{
  if (t->vm->heap->needsMark(&target)) {
    ACQUIRE_RAW(t, t->vm->heapLock);
    t->vm->heap->mark(&target);
  }
}

inline void
set(Thread* t, object& target, object value)
{
  target = value;
  mark(t, target);
}

inline void
setObjectClass(Thread* t, object o, object value)
{
  set(t, cast<object>(o, 0),
      reinterpret_cast<object>
      (reinterpret_cast<uintptr_t>(value)
       | reinterpret_cast<uintptr_t>(cast<object>(o, 0)) & (~PointerMask)));
}

object&
arrayBodyUnsafe(Thread*, object, unsigned);

#include "type-declarations.cpp"

object
makeTrace(Thread* t, int frame);

object
makeTrace(Thread* t);

inline object
makeRuntimeException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeRuntimeException(t, message, trace, 0);
}

inline object
makeIllegalStateException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeIllegalStateException(t, message, trace, 0);
}

inline object
makeIllegalArgumentException(Thread* t)
{
  return makeIllegalArgumentException(t, 0, makeTrace(t), 0);
}

inline object
makeIllegalMonitorStateException(Thread* t)
{
  return makeIllegalMonitorStateException(t, 0, makeTrace(t), 0);
}

inline object
makeArrayIndexOutOfBoundsException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeArrayIndexOutOfBoundsException(t, message, trace, 0);
}

inline object
makeArrayStoreException(Thread* t)
{
  return makeArrayStoreException(t, 0, makeTrace(t), 0);
}

inline object
makeNegativeArraySizeException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeNegativeArraySizeException(t, message, trace, 0);
}

inline object
makeClassCastException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeClassCastException(t, message, trace, 0);
}

inline object
makeClassNotFoundException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeClassNotFoundException(t, message, trace, 0);
}

inline object
makeNullPointerException(Thread* t)
{
  return makeNullPointerException(t, 0, makeTrace(t), 0);
}

inline object
makeInvocationTargetException(Thread* t, object targetException)
{
  PROTECT(t, targetException);
  object trace = makeTrace(t);
  return makeRuntimeException(t, 0, trace, targetException);
}

inline object
makeInterruptedException(Thread* t)
{
  return makeInterruptedException(t, 0, makeTrace(t), 0);
}

inline object
makeStackOverflowError(Thread* t)
{
  return makeStackOverflowError(t, 0, makeTrace(t), 0);
}

inline object
makeNoSuchFieldError(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeNoSuchFieldError(t, message, trace, 0);
}

inline object
makeNoSuchMethodError(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeNoSuchMethodError(t, message, trace, 0);
}

inline object
makeUnsatisfiedLinkError(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeUnsatisfiedLinkError(t, message, trace, 0);
}

inline object
makeExceptionInInitializerError(Thread* t, object cause)
{
  PROTECT(t, cause);
  object trace = makeTrace(t);
  return makeExceptionInInitializerError(t, 0, trace, cause);
}

object
make(Thread* t, object class_);

object
makeByteArray(Thread* t, const char* format, ...);

object
makeString(Thread* t, const char* format, ...);

void
stringChars(Thread* t, object string, char* chars);

bool
isAssignableFrom(Thread* t, object a, object b);

bool
instanceOf(Thread* t, object class_, object o);

object
classInitializer(Thread* t, object class_);

inline void
pushObject(Thread* t, object o)
{
  if (DebugStack) {
    fprintf(stderr, "push object %p at %d\n", o, t->sp);
  }

  assert(t, t->sp + 1 < Thread::StackSizeInWords / 2);
  t->stack[(t->sp * 2)    ] = ObjectTag;
  t->stack[(t->sp * 2) + 1] = reinterpret_cast<uintptr_t>(o);
  ++ t->sp;
}

inline void
pushInt(Thread* t, uint32_t v)
{
  if (DebugStack) {
    fprintf(stderr, "push int %d at %d\n", v, t->sp);
  }

  assert(t, t->sp + 1 < Thread::StackSizeInWords / 2);
  t->stack[(t->sp * 2)    ] = IntTag;
  t->stack[(t->sp * 2) + 1] = v;
  ++ t->sp;
}

inline void
pushFloat(Thread* t, float v)
{
  uint32_t a; memcpy(&a, &v, sizeof(uint32_t));
  pushInt(t, a);
}

inline void
pushLong(Thread* t, uint64_t v)
{
  if (DebugStack) {
    fprintf(stderr, "push long " LLD " at %d\n", v, t->sp);
  }

  pushInt(t, v >> 32);
  pushInt(t, v & 0xFFFFFFFF);
}

inline void
pushDouble(Thread* t, double v)
{
  uint64_t a; memcpy(&a, &v, sizeof(uint64_t));
  pushLong(t, a);
}

inline object
popObject(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr, "pop object %p at %d\n",
            reinterpret_cast<object>(t->stack[((t->sp - 1) * 2) + 1]),
            t->sp - 1);
  }

  assert(t, t->stack[(t->sp - 1) * 2] == ObjectTag);
  return reinterpret_cast<object>(t->stack[((-- t->sp) * 2) + 1]);
}

inline uint32_t
popInt(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr, "pop int " LD " at %d\n",
            t->stack[((t->sp - 1) * 2) + 1],
            t->sp - 1);
  }

  assert(t, t->stack[(t->sp - 1) * 2] == IntTag);
  return t->stack[((-- t->sp) * 2) + 1];
}

inline float
popFloat(Thread* t)
{
  uint32_t a = popInt(t);
  float f; memcpy(&f, &a, sizeof(float));
  return f;
}

inline uint64_t
popLong(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr, "pop long " LLD " at %d\n",
            (static_cast<uint64_t>(t->stack[((t->sp - 2) * 2) + 1]) << 32)
            | static_cast<uint64_t>(t->stack[((t->sp - 1) * 2) + 1]),
            t->sp - 2);
  }

  uint64_t a = popInt(t);
  uint64_t b = popInt(t);
  return (b << 32) | a;
}

inline float
popDouble(Thread* t)
{
  uint64_t a = popLong(t);
  double d; memcpy(&d, &a, sizeof(double));
  return d;
}

inline object
peekObject(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr, "peek object %p at %d\n",
            reinterpret_cast<object>(t->stack[(index * 2) + 1]),
            index);
  }

  assert(t, index < Thread::StackSizeInWords / 2);
  assert(t, t->stack[index * 2] == ObjectTag);
  return *reinterpret_cast<object*>(t->stack + (index * 2) + 1);
}

inline uint32_t
peekInt(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr, "peek int " LD " at %d\n",
            t->stack[(index * 2) + 1],
            index);
  }

  assert(t, index < Thread::StackSizeInWords / 2);
  assert(t, t->stack[index * 2] == IntTag);
  return t->stack[(index * 2) + 1];
}

inline uint64_t
peekLong(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr, "peek long " LLD " at %d\n",
            (static_cast<uint64_t>(t->stack[(index * 2) + 1]) << 32)
            | static_cast<uint64_t>(t->stack[((index + 1) * 2) + 1]),
            index);
  }

  return (static_cast<uint64_t>(peekInt(t, index)) << 32)
    | static_cast<uint64_t>(peekInt(t, index + 1));
}

inline void
pokeObject(Thread* t, unsigned index, object value)
{
  if (DebugStack) {
    fprintf(stderr, "poke object %p at %d\n", value, index);
  }

  t->stack[index * 2] = ObjectTag;
  t->stack[(index * 2) + 1] = reinterpret_cast<uintptr_t>(value);
}

inline void
pokeInt(Thread* t, unsigned index, uint32_t value)
{
  if (DebugStack) {
    fprintf(stderr, "poke int %d at %d\n", value, index);
  }

  t->stack[index * 2] = IntTag;
  t->stack[(index * 2) + 1] = value;
}

inline void
pokeLong(Thread* t, unsigned index, uint64_t value)
{
  if (DebugStack) {
    fprintf(stderr, "poke long " LLD " at %d\n", value, index);
  }

  pokeInt(t, index, value >> 32);
  pokeInt(t, index + 1, value & 0xFFFFFFFF);
}

inline object*
pushReference(Thread* t, object o)
{
  if (o) {
    expect(t, t->sp + 1 < Thread::StackSizeInWords / 2);
    pushObject(t, o);
    return reinterpret_cast<object*>(t->stack + ((t->sp - 1) * 2) + 1);
  } else {
    return 0;
  }
}

inline int
frameNext(Thread* t, int frame)
{
  return peekInt(t, frame + FrameNextOffset);
}

inline object
frameMethod(Thread* t, int frame)
{
  return peekObject(t, frame + FrameMethodOffset);
}

inline unsigned
frameIp(Thread* t, int frame)
{
  return peekInt(t, frame + FrameIpOffset);
}

inline unsigned
frameBase(Thread* t, int frame)
{
  return peekInt(t, frame + FrameBaseOffset);
}

inline object
localObject(Thread* t, unsigned index)
{
  return peekObject(t, frameBase(t, t->frame) + index);
}

inline uint32_t
localInt(Thread* t, unsigned index)
{
  return peekInt(t, frameBase(t, t->frame) + index);
}

inline uint64_t
localLong(Thread* t, unsigned index)
{
  return peekLong(t, frameBase(t, t->frame) + index);
}

inline void
setLocalObject(Thread* t, unsigned index, object value)
{
  pokeObject(t, frameBase(t, t->frame) + index, value);
}

inline void
setLocalInt(Thread* t, unsigned index, uint32_t value)
{
  pokeInt(t, frameBase(t, t->frame) + index, value);
}

inline void
setLocalLong(Thread* t, unsigned index, uint64_t value)
{
  pokeLong(t, frameBase(t, t->frame) + index, value);
}

inline object
makeTrace(Thread* t)
{
  pokeInt(t, t->frame + FrameIpOffset, t->ip);
  return makeTrace(t, t->frame);
}

inline uint32_t
hash(const int8_t* s, unsigned length)
{
  uint32_t h = 0;
  for (unsigned i = 0; i < length; ++i) {
    h = (h * 31) + static_cast<unsigned>(s[i]);
  }
  return h;  
}

inline uint32_t
hash(const uint16_t* s, unsigned length)
{
  uint32_t h = 0;
  for (unsigned i = 0; i < length; ++i) {
    h = (h * 31) + s[i];
  }
  return h;  
}

inline unsigned
baseSize(Thread* t, object o, object class_)
{
  return ceiling(classFixedSize(t, class_), BytesPerWord)
    + ceiling(classArrayElementSize(t, class_)
              * cast<uintptr_t>(o, classFixedSize(t, class_) - BytesPerWord),
              BytesPerWord);
}

inline bool
objectExtended(Thread*, object o)
{
  return (cast<uintptr_t>(o, 0) & (~PointerMask)) == ExtendedMark;
}

inline uintptr_t&
extendedWord(Thread* t, object o, unsigned baseSize)
{
  assert(t, objectExtended(t, o));
  return cast<uintptr_t>(o, baseSize * BytesPerWord);
}

inline unsigned
extendedSize(Thread* t, object o, unsigned baseSize)
{
  return baseSize + objectExtended(t, o);
}

inline bool
hashTaken(Thread*, object o)
{
  return (cast<uintptr_t>(o, 0) & (~PointerMask)) == HashTakenMark;
}

inline void
markHashTaken(Thread* t, object o)
{
  assert(t, not objectExtended(t, o));
  cast<uintptr_t>(o, 0) |= HashTakenMark;

  ACQUIRE_RAW(t, t->vm->heapLock);
  t->vm->heap->pad(o, 1);
}

inline uint32_t
takeHash(Thread*, object o)
{
  return reinterpret_cast<uintptr_t>(o) / BytesPerWord;
}

inline uint32_t
objectHash(Thread* t, object o)
{
  if (objectExtended(t, o)) {
    return extendedWord(t, o, baseSize(t, o, objectClass(t, o)));
  } else {
    markHashTaken(t, o);
    return takeHash(t, o);
  }
}

inline bool
objectEqual(Thread*, object a, object b)
{
  return a == b;
}

inline uint32_t
byteArrayHash(Thread* t, object array)
{
  return hash(&byteArrayBody(t, array, 0), byteArrayLength(t, array));
}

inline uint32_t
charArrayHash(Thread* t, object array)
{
  return hash(&charArrayBody(t, array, 0), charArrayLength(t, array));
}

inline bool
byteArrayEqual(Thread* t, object a, object b)
{
  return a == b or
    ((byteArrayLength(t, a) == byteArrayLength(t, b)) and
     memcmp(&byteArrayBody(t, a, 0), &byteArrayBody(t, b, 0),
            byteArrayLength(t, a)) == 0);
}

inline uint32_t
stringHash(Thread* t, object s)
{
  if (stringHashCode(t, s) == 0) {
    object data = stringData(t, s);
    if (objectClass(t, data)
        == arrayBody(t, t->vm->types, Machine::ByteArrayType))
    {
      stringHashCode(t, s) = hash
        (&byteArrayBody(t, data, stringOffset(t, s)), stringLength(t, s));
    } else {
      stringHashCode(t, s) = hash
        (&charArrayBody(t, data, stringOffset(t, s)), stringLength(t, s));
    }
  }
  return stringHashCode(t, s);
}

inline uint16_t
stringCharAt(Thread* t, object s, int i)
{
  object data = stringData(t, s);
  if (objectClass(t, data)
      == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    return byteArrayBody(t, data, i);
  } else {
    return charArrayBody(t, data, i);
  }
}

inline bool
stringEqual(Thread* t, object a, object b)
{
  if (a == b) {
    return true;
  } else if (stringLength(t, a) == stringLength(t, b)) {
    for (int i = 0; i < stringLength(t, a); ++i) {
      if (stringCharAt(t, a, i) != stringCharAt(t, b, i)) {
        return false;
      }
    }
    return true;
  } else {
    return false;
  }
}

inline bool
intArrayEqual(Thread* t, object a, object b)
{
  return a == b or
    ((intArrayLength(t, a) == intArrayLength(t, b)) and
     memcmp(&intArrayBody(t, a, 0), &intArrayBody(t, b, 0),
            intArrayLength(t, a) * 4) == 0);
}

inline uint32_t
methodHash(Thread* t, object method)
{
  return byteArrayHash(t, methodName(t, method))
    ^ byteArrayHash(t, methodSpec(t, method));
}

inline bool
methodEqual(Thread* t, object a, object b)
{
  return a == b or
    (byteArrayEqual(t, methodName(t, a), methodName(t, b)) and
     byteArrayEqual(t, methodSpec(t, a), methodSpec(t, b)));
}

object
hashMapFindNode(Thread* t, object map, object key,
                uint32_t (*hash)(Thread*, object),
                bool (*equal)(Thread*, object, object));

inline object
hashMapFind(Thread* t, object map, object key,
            uint32_t (*hash)(Thread*, object),
            bool (*equal)(Thread*, object, object))
{
  object n = hashMapFindNode(t, map, key, hash, equal);
  return (n ? tripleSecond(t, n) : 0);
}

void
hashMapResize(Thread* t, object map, uint32_t (*hash)(Thread*, object),
              unsigned size);

void
hashMapInsert(Thread* t, object map, object key, object value,
              uint32_t (*hash)(Thread*, object));

inline bool
hashMapInsertOrReplace(Thread* t, object map, object key, object value,
                       uint32_t (*hash)(Thread*, object),
                       bool (*equal)(Thread*, object, object))
{
  object n = hashMapFindNode(t, map, key, hash, equal);
  if (n == 0) {
    hashMapInsert(t, map, key, value, hash);
    return true;
  } else {
    set(t, tripleSecond(t, n), value);
    return false;
  }
}

inline bool
hashMapInsertMaybe(Thread* t, object map, object key, object value,
                   uint32_t (*hash)(Thread*, object),
                   bool (*equal)(Thread*, object, object))
{
  object n = hashMapFindNode(t, map, key, hash, equal);
  if (n == 0) {
    hashMapInsert(t, map, key, value, hash);
    return true;
  } else {
    return false;
  }
}

object
hashMapRemove(Thread* t, object map, object key,
              uint32_t (*hash)(Thread*, object),
              bool (*equal)(Thread*, object, object));

object
hashMapIterator(Thread* t, object map);

object
hashMapIteratorNext(Thread* t, object it);

void
listAppend(Thread* t, object list, object value);

unsigned
fieldCode(Thread* t, unsigned javaCode);

unsigned
fieldType(Thread* t, unsigned code);

unsigned
primitiveSize(Thread* t, unsigned code);

inline unsigned
fieldSize(Thread* t, object field)
{
  unsigned code = fieldCode(t, field);
  if (code == ObjectField) {
    return BytesPerWord;
  } else {
    return primitiveSize(t, code);
  }
}

object
findLoadedClass(Thread* t, object spec);

object
parseClass(Thread* t, const uint8_t* data, unsigned length);

object
resolveClass(Thread* t, object spec);

object
resolveObjectArrayClass(Thread* t, object elementSpec);

object
makeObjectArray(Thread* t, object elementClass, unsigned count, bool clear);

inline unsigned
objectArrayLength(Thread* t, object array)
{
  assert(t, classFixedSize(t, objectClass(t, array)) == BytesPerWord * 2);
  assert(t, classArrayElementSize(t, objectClass(t, array)) == BytesPerWord);
  return cast<uintptr_t>(array, BytesPerWord);
}

inline object&
objectArrayBody(Thread* t, object array, unsigned index)
{
  assert(t, classFixedSize(t, objectClass(t, array)) == BytesPerWord * 2);
  assert(t, classArrayElementSize(t, objectClass(t, array)) == BytesPerWord);
  assert(t, classObjectMask(t, objectClass(t, array))
         == classObjectMask(t, arrayBody
                            (t, t->vm->types, Machine::ArrayType)));
  return cast<object>(array, (2 + index) * BytesPerWord);
}

unsigned
parameterFootprint(const char* s);

inline unsigned
parameterFootprint(Thread* t, object spec)
{
  return parameterFootprint
    (reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0)));
}

unsigned
parameterCount(const char* s);

inline unsigned
parameterCount(Thread* t, object spec)
{
  return parameterCount
    (reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0)));
}

int
lineNumber(Thread* t, object method, unsigned ip);

void
addFinalizer(Thread* t, object target, void (*finalize)(Thread*, object));

System::Monitor*
objectMonitor(Thread* t, object o);

inline void
acquire(Thread* t, object o)
{
  System::Monitor* m = objectMonitor(t, o);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p acquires %p for %x\n",
            t, m, objectHash(t, o));
  }

  acquire(t, m);
}

inline void
release(Thread* t, object o)
{
  System::Monitor* m = objectMonitor(t, o);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p releases %p for %x\n",
            t, m, objectHash(t, o));
  }

  release(t, m);
}

inline void
wait(Thread* t, object o, int64_t milliseconds)
{
  System::Monitor* m = objectMonitor(t, o);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p waits " LLD " millis on %p for %x\n",
            t, milliseconds, m, objectHash(t, o));
  }

  if (m->owner() == t->systemThread) {
    ENTER(t, Thread::IdleState);

    bool interrupted = m->wait(t->systemThread, milliseconds);
    if (interrupted) {
      t->exception = makeInterruptedException(t);
    }
  } else {
    t->exception = makeIllegalMonitorStateException(t);
  }

  if (DebugMonitors) {
    fprintf(stderr, "thread %p wakes up on %p for %x\n",
            t, m, objectHash(t, o));
  }

  stress(t);
}

inline void
notify(Thread* t, object o)
{
  System::Monitor* m = objectMonitor(t, o);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p notifies on %p for %x\n",
            t, m, objectHash(t, o));
  }

  if (m->owner() == t->systemThread) {
    m->notify(t->systemThread);
  } else {
    t->exception = makeIllegalMonitorStateException(t);
  }
}

inline void
notifyAll(Thread* t, object o)
{
  System::Monitor* m = objectMonitor(t, o);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p notifies all on %p for %x\n",
            t, m, objectHash(t, o));
  }

  if (m->owner() == t->systemThread) {
    m->notifyAll(t->systemThread);
  } else {
    t->exception = makeIllegalMonitorStateException(t);
  }
}

inline void
interrupt(Thread*, Thread* target)
{
  target->systemThread->interrupt();
}

object
intern(Thread* t, object s);

void
exit(Thread* t);

} // namespace vm

#endif//MACHINE_H
