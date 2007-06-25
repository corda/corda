#ifndef JNI_H
#define JNI_H

#include "stdint.h"
#include "stdarg.h"

#define JNIEXPORT 
#define JNIIMPORT
#define JNICALL

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

struct JavaVM {
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

struct JNIEnv {
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

#endif//JNI_H
