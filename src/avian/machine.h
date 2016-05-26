/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef MACHINE_H
#define MACHINE_H

#include "avian/common.h"
#include "java-common.h"
#include <avian/system/system.h>
#include <avian/system/signal.h>
#include <avian/heap/heap.h>
#include <avian/util/hash.h>
#include "avian/finder.h"
#include "avian/processor.h"
#include "avian/constants.h"
#include "avian/arch.h"

using namespace avian::util;

#ifdef PLATFORM_WINDOWS
#define JNICALL __stdcall
#else
#define JNICALL
#endif

#define PROTECT(thread, name) \
  Thread::SingleProtector MAKE_NAME(protector_)(thread, &name);

#define ACQUIRE(t, x) MonitorResource MAKE_NAME(monitorResource_)(t, x)

#define ACQUIRE_OBJECT(t, x) \
  ObjectMonitorResource MAKE_NAME(monitorResource_)(t, x)

#define ACQUIRE_FIELD_FOR_READ(t, field) \
  FieldReadResource MAKE_NAME(monitorResource_)(t, field)

#define ACQUIRE_FIELD_FOR_WRITE(t, field) \
  FieldWriteResource MAKE_NAME(monitorResource_)(t, field)

#define ACQUIRE_RAW(t, x) RawMonitorResource MAKE_NAME(monitorResource_)(t, x)

#define ENTER(t, state) StateResource MAKE_NAME(stateResource_)(t, state)

#define THREAD_RESOURCE0(t, releaseBody)                     \
  class MAKE_NAME(Resource_) : public Thread::AutoResource { \
   public:                                                   \
    MAKE_NAME(Resource_)(Thread * t) : AutoResource(t)       \
    {                                                        \
    }                                                        \
    ~MAKE_NAME(Resource_)()                                  \
    {                                                        \
      releaseBody;                                           \
    }                                                        \
    virtual void release()                                   \
    {                                                        \
      this->MAKE_NAME(Resource_)::~MAKE_NAME(Resource_)();   \
    }                                                        \
  } MAKE_NAME(resource_)(t);

#define OBJECT_RESOURCE(t, name, releaseBody)                      \
  class MAKE_NAME(Resource_) : public Thread::AutoResource {       \
   public:                                                         \
    MAKE_NAME(Resource_)(Thread * t, object name)                  \
        : AutoResource(t), name(name), protector(t, &(this->name)) \
    {                                                              \
    }                                                              \
    ~MAKE_NAME(Resource_)()                                        \
    {                                                              \
      releaseBody;                                                 \
    }                                                              \
    virtual void release()                                         \
    {                                                              \
      this->MAKE_NAME(Resource_)::~MAKE_NAME(Resource_)();         \
    }                                                              \
                                                                   \
   private:                                                        \
    object name;                                                   \
    Thread::SingleProtector protector;                             \
  } MAKE_NAME(resource_)(t, name);

#define THREAD_RESOURCE(t, type, name, releaseBody)                           \
  class MAKE_NAME(Resource_) : public Thread::AutoResource {                  \
   public:                                                                    \
    MAKE_NAME(Resource_)(Thread * t, type name) : AutoResource(t), name(name) \
    {                                                                         \
    }                                                                         \
    ~MAKE_NAME(Resource_)()                                                   \
    {                                                                         \
      releaseBody;                                                            \
    }                                                                         \
    virtual void release()                                                    \
    {                                                                         \
      this->MAKE_NAME(Resource_)::~MAKE_NAME(Resource_)();                    \
    }                                                                         \
                                                                              \
   private:                                                                   \
    type name;                                                                \
  } MAKE_NAME(resource_)(t, name);

#define THREAD_RESOURCE2(t, type1, name1, type2, name2, releaseBody) \
  class MAKE_NAME(Resource_) : public Thread::AutoResource {         \
   public:                                                           \
    MAKE_NAME(Resource_)(Thread * t, type1 name1, type2 name2)       \
        : AutoResource(t), name1(name1), name2(name2)                \
    {                                                                \
    }                                                                \
    ~MAKE_NAME(Resource_)()                                          \
    {                                                                \
      releaseBody;                                                   \
    }                                                                \
    virtual void release()                                           \
    {                                                                \
      this->MAKE_NAME(Resource_)::~MAKE_NAME(Resource_)();           \
    }                                                                \
                                                                     \
   private:                                                          \
    type1 name1;                                                     \
    type2 name2;                                                     \
  } MAKE_NAME(resource_)(t, name1, name2);

namespace vm {

const bool Verbose = false;
const bool DebugRun = false;
const bool DebugStack = false;
const bool DebugMonitors = false;
const bool DebugReferences = false;

const bool AbortOnOutOfMemoryError = false;

const uintptr_t HashTakenMark = 1;
const uintptr_t ExtendedMark = 2;
const uintptr_t FixedMark = 3;

const unsigned ThreadHeapSizeInBytes = 64 * 1024;
const unsigned ThreadHeapSizeInWords = ThreadHeapSizeInBytes / BytesPerWord;

const unsigned ThreadBackupHeapSizeInBytes = 2 * 1024;
const unsigned ThreadBackupHeapSizeInWords = ThreadBackupHeapSizeInBytes
                                             / BytesPerWord;

const unsigned ThreadHeapPoolSize = 64;

const unsigned FixedFootprintThresholdInBytes = ThreadHeapPoolSize
                                                * ThreadHeapSizeInBytes;

// number of zombie threads which may accumulate before we force a GC
// to clean them up:
const unsigned ZombieCollectionThreshold = 16;

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
  IntTag,  // must be zero
  ObjectTag
};

const int NativeLine = -2;
const int UnknownLine = -1;

// class vmFlags:
const unsigned ReferenceFlag = 1 << 0;
const unsigned WeakReferenceFlag = 1 << 1;
const unsigned NeedInitFlag = 1 << 2;
const unsigned InitFlag = 1 << 3;
const unsigned InitErrorFlag = 1 << 4;
const unsigned PrimitiveFlag = 1 << 5;
const unsigned BootstrapFlag = 1 << 6;
const unsigned HasFinalizerFlag = 1 << 7;
const unsigned LinkFlag = 1 << 8;
const unsigned HasFinalMemberFlag = 1 << 9;
const unsigned SingletonFlag = 1 << 10;
const unsigned ContinuationFlag = 1 << 11;

// method vmFlags:
const unsigned ClassInitFlag = 1 << 0;
const unsigned ConstructorFlag = 1 << 1;

#ifndef JNI_VERSION_1_6
#define JNI_VERSION_1_6 0x00010006
#endif

#ifndef JNI_TRUE
#define JNI_TRUE 1
#endif

#ifndef JNI_OK
#define JNI_OK 0
#endif

typedef Machine JavaVM;
typedef Thread JNIEnv;

struct JNINativeMethod {
  char* name;
  char* signature;
  void* function;
};

struct JavaVMOption {
  char* optionString;
  void* extraInfo;
};

struct JavaVMInitArgs {
  jint version;

  jint nOptions;
  JavaVMOption* options;
  jboolean ignoreUnrecognized;
};

struct JavaVMVTable {
  void* reserved0;
  void* reserved1;
  void* reserved2;

  jint(JNICALL* DestroyJavaVM)(JavaVM*);

  jint(JNICALL* AttachCurrentThread)(JavaVM*, JNIEnv**, void*);

  jint(JNICALL* DetachCurrentThread)(JavaVM*);

  jint(JNICALL* GetEnv)(JavaVM*, JNIEnv**, jint);

  jint(JNICALL* AttachCurrentThreadAsDaemon)(JavaVM*, JNIEnv**, void*);
};

struct JNIEnvVTable {
  void* reserved0;
  void* reserved1;
  void* reserved2;
  void* reserved3;

  jint(JNICALL* GetVersion)(JNIEnv*);

  jclass(
      JNICALL* DefineClass)(JNIEnv*, const char*, jobject, const jbyte*, jsize);

  jclass(JNICALL* FindClass)(JNIEnv*, const char*);

  jmethodID(JNICALL* FromReflectedMethod)(JNIEnv*, jobject);

  jfieldID(JNICALL* FromReflectedField)(JNIEnv*, jobject);

  jobject(JNICALL* ToReflectedMethod)(JNIEnv*, jclass, jmethodID, jboolean);

  jclass(JNICALL* GetSuperclass)(JNIEnv*, jclass);

  jboolean(JNICALL* IsAssignableFrom)(JNIEnv*, jclass, jclass);

  jobject(JNICALL* ToReflectedField)(JNIEnv*, jclass, jfieldID, jboolean);

  jint(JNICALL* Throw)(JNIEnv*, jthrowable);

  jint(JNICALL* ThrowNew)(JNIEnv*, jclass, const char*);

  jthrowable(JNICALL* ExceptionOccurred)(JNIEnv*);

  void(JNICALL* ExceptionDescribe)(JNIEnv*);

  void(JNICALL* ExceptionClear)(JNIEnv*);

  void(JNICALL* FatalError)(JNIEnv*, const char*);

  jint(JNICALL* PushLocalFrame)(JNIEnv*, jint);

  jobject(JNICALL* PopLocalFrame)(JNIEnv*, jobject);

  jobject(JNICALL* NewGlobalRef)(JNIEnv*, jobject);

  void(JNICALL* DeleteGlobalRef)(JNIEnv*, jobject);

  void(JNICALL* DeleteLocalRef)(JNIEnv*, jobject);

  jboolean(JNICALL* IsSameObject)(JNIEnv*, jobject, jobject);

  jobject(JNICALL* NewLocalRef)(JNIEnv*, jobject);

  jint(JNICALL* EnsureLocalCapacity)(JNIEnv*, jint);

  jobject(JNICALL* AllocObject)(JNIEnv*, jclass);

  jobject(JNICALL* NewObject)(JNIEnv*, jclass, jmethodID, ...);

  jobject(JNICALL* NewObjectV)(JNIEnv*, jclass, jmethodID, va_list);

  jobject(JNICALL* NewObjectA)(JNIEnv*, jclass, jmethodID, const jvalue*);

  jclass(JNICALL* GetObjectClass)(JNIEnv*, jobject);

  jboolean(JNICALL* IsInstanceOf)(JNIEnv*, jobject, jclass);

  jmethodID(JNICALL* GetMethodID)(JNIEnv*, jclass, const char*, const char*);

  jobject(JNICALL* CallObjectMethod)(JNIEnv*, jobject, jmethodID, ...);

  jobject(JNICALL* CallObjectMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jobject(JNICALL* CallObjectMethodA)(JNIEnv*,
                                      jobject,
                                      jmethodID,
                                      const jvalue*);

  jboolean(JNICALL* CallBooleanMethod)(JNIEnv*, jobject, jmethodID, ...);

  jboolean(JNICALL* CallBooleanMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jboolean(JNICALL* CallBooleanMethodA)(JNIEnv*,
                                        jobject,
                                        jmethodID,
                                        const jvalue*);

  jbyte(JNICALL* CallByteMethod)(JNIEnv*, jobject, jmethodID, ...);

  jbyte(JNICALL* CallByteMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jbyte(JNICALL* CallByteMethodA)(JNIEnv*, jobject, jmethodID, const jvalue*);

  jchar(JNICALL* CallCharMethod)(JNIEnv*, jobject, jmethodID, ...);

  jchar(JNICALL* CallCharMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jchar(JNICALL* CallCharMethodA)(JNIEnv*, jobject, jmethodID, const jvalue*);

  jshort(JNICALL* CallShortMethod)(JNIEnv*, jobject, jmethodID, ...);

  jshort(JNICALL* CallShortMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jshort(JNICALL* CallShortMethodA)(JNIEnv*, jobject, jmethodID, const jvalue*);

  jint(JNICALL* CallIntMethod)(JNIEnv*, jobject, jmethodID, ...);

  jint(JNICALL* CallIntMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jint(JNICALL* CallIntMethodA)(JNIEnv*, jobject, jmethodID, const jvalue*);

  jlong(JNICALL* CallLongMethod)(JNIEnv*, jobject, jmethodID, ...);

  jlong(JNICALL* CallLongMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jlong(JNICALL* CallLongMethodA)(JNIEnv*, jobject, jmethodID, const jvalue*);

  jfloat(JNICALL* CallFloatMethod)(JNIEnv*, jobject, jmethodID, ...);

  jfloat(JNICALL* CallFloatMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jfloat(JNICALL* CallFloatMethodA)(JNIEnv*, jobject, jmethodID, const jvalue*);

  jdouble(JNICALL* CallDoubleMethod)(JNIEnv*, jobject, jmethodID, ...);

  jdouble(JNICALL* CallDoubleMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  jdouble(JNICALL* CallDoubleMethodA)(JNIEnv*,
                                      jobject,
                                      jmethodID,
                                      const jvalue*);

  void(JNICALL* CallVoidMethod)(JNIEnv*, jobject, jmethodID, ...);

  void(JNICALL* CallVoidMethodV)(JNIEnv*, jobject, jmethodID, va_list);

  void(JNICALL* CallVoidMethodA)(JNIEnv*, jobject, jmethodID, const jvalue*);

  jobject(JNICALL* CallNonvirtualObjectMethod)(JNIEnv*,
                                               jobject,
                                               jclass,
                                               jmethodID,
                                               ...);

  jobject(JNICALL* CallNonvirtualObjectMethodV)(JNIEnv*,
                                                jobject,
                                                jclass,
                                                jmethodID,
                                                va_list);

  jobject(JNICALL* CallNonvirtualObjectMethodA)(JNIEnv*,
                                                jobject,
                                                jclass,
                                                jmethodID,
                                                const jvalue*);

  jboolean(JNICALL* CallNonvirtualBooleanMethod)(JNIEnv*,
                                                 jobject,
                                                 jclass,
                                                 jmethodID,
                                                 ...);

  jboolean(JNICALL* CallNonvirtualBooleanMethodV)(JNIEnv*,
                                                  jobject,
                                                  jclass,
                                                  jmethodID,
                                                  va_list);

  jboolean(JNICALL* CallNonvirtualBooleanMethodA)(JNIEnv*,
                                                  jobject,
                                                  jclass,
                                                  jmethodID,
                                                  const jvalue*);

  jbyte(JNICALL* CallNonvirtualByteMethod)(JNIEnv*,
                                           jobject,
                                           jclass,
                                           jmethodID,
                                           ...);

  jbyte(JNICALL* CallNonvirtualByteMethodV)(JNIEnv*,
                                            jobject,
                                            jclass,
                                            jmethodID,
                                            va_list);

  jbyte(JNICALL* CallNonvirtualByteMethodA)(JNIEnv*,
                                            jobject,
                                            jclass,
                                            jmethodID,
                                            const jvalue*);

  jchar(JNICALL* CallNonvirtualCharMethod)(JNIEnv*,
                                           jobject,
                                           jclass,
                                           jmethodID,
                                           ...);

  jchar(JNICALL* CallNonvirtualCharMethodV)(JNIEnv*,
                                            jobject,
                                            jclass,
                                            jmethodID,
                                            va_list);

  jchar(JNICALL* CallNonvirtualCharMethodA)(JNIEnv*,
                                            jobject,
                                            jclass,
                                            jmethodID,
                                            const jvalue*);

  jshort(JNICALL* CallNonvirtualShortMethod)(JNIEnv*,
                                             jobject,
                                             jclass,
                                             jmethodID,
                                             ...);

  jshort(JNICALL* CallNonvirtualShortMethodV)(JNIEnv*,
                                              jobject,
                                              jclass,
                                              jmethodID,
                                              va_list);

  jshort(JNICALL* CallNonvirtualShortMethodA)(JNIEnv*,
                                              jobject,
                                              jclass,
                                              jmethodID,
                                              const jvalue*);

  jint(JNICALL* CallNonvirtualIntMethod)(JNIEnv*,
                                         jobject,
                                         jclass,
                                         jmethodID,
                                         ...);

  jint(JNICALL* CallNonvirtualIntMethodV)(JNIEnv*,
                                          jobject,
                                          jclass,
                                          jmethodID,
                                          va_list);

  jint(JNICALL* CallNonvirtualIntMethodA)(JNIEnv*,
                                          jobject,
                                          jclass,
                                          jmethodID,
                                          const jvalue*);

  jlong(JNICALL* CallNonvirtualLongMethod)(JNIEnv*,
                                           jobject,
                                           jclass,
                                           jmethodID,
                                           ...);

  jlong(JNICALL* CallNonvirtualLongMethodV)(JNIEnv*,
                                            jobject,
                                            jclass,
                                            jmethodID,
                                            va_list);
  jlong(JNICALL* CallNonvirtualLongMethodA)(JNIEnv*,
                                            jobject,
                                            jclass,
                                            jmethodID,
                                            const jvalue*);

  jfloat(JNICALL* CallNonvirtualFloatMethod)(JNIEnv*,
                                             jobject,
                                             jclass,
                                             jmethodID,
                                             ...);

  jfloat(JNICALL* CallNonvirtualFloatMethodV)(JNIEnv*,
                                              jobject,
                                              jclass,
                                              jmethodID,
                                              va_list);

  jfloat(JNICALL* CallNonvirtualFloatMethodA)(JNIEnv*,
                                              jobject,
                                              jclass,
                                              jmethodID,
                                              const jvalue*);

  jdouble(JNICALL* CallNonvirtualDoubleMethod)(JNIEnv*,
                                               jobject,
                                               jclass,
                                               jmethodID,
                                               ...);

  jdouble(JNICALL* CallNonvirtualDoubleMethodV)(JNIEnv*,
                                                jobject,
                                                jclass,
                                                jmethodID,
                                                va_list);

  jdouble(JNICALL* CallNonvirtualDoubleMethodA)(JNIEnv*,
                                                jobject,
                                                jclass,
                                                jmethodID,
                                                const jvalue*);

  void(JNICALL* CallNonvirtualVoidMethod)(JNIEnv*,
                                          jobject,
                                          jclass,
                                          jmethodID,
                                          ...);

  void(JNICALL* CallNonvirtualVoidMethodV)(JNIEnv*,
                                           jobject,
                                           jclass,
                                           jmethodID,
                                           va_list);

  void(JNICALL* CallNonvirtualVoidMethodA)(JNIEnv*,
                                           jobject,
                                           jclass,
                                           jmethodID,
                                           const jvalue*);

  jfieldID(JNICALL* GetFieldID)(JNIEnv*, jclass, const char*, const char*);

  jobject(JNICALL* GetObjectField)(JNIEnv*, jobject, jfieldID);

  jboolean(JNICALL* GetBooleanField)(JNIEnv*, jobject, jfieldID);

  jbyte(JNICALL* GetByteField)(JNIEnv*, jobject, jfieldID);

  jchar(JNICALL* GetCharField)(JNIEnv*, jobject, jfieldID);

  jshort(JNICALL* GetShortField)(JNIEnv*, jobject, jfieldID);

  jint(JNICALL* GetIntField)(JNIEnv*, jobject, jfieldID);

  jlong(JNICALL* GetLongField)(JNIEnv*, jobject, jfieldID);

  jfloat(JNICALL* GetFloatField)(JNIEnv*, jobject, jfieldID);

  jdouble(JNICALL* GetDoubleField)(JNIEnv*, jobject, jfieldID);

  void(JNICALL* SetObjectField)(JNIEnv*, jobject, jfieldID, jobject);

  void(JNICALL* SetBooleanField)(JNIEnv*, jobject, jfieldID, jboolean);

  void(JNICALL* SetByteField)(JNIEnv*, jobject, jfieldID, jbyte);

  void(JNICALL* SetCharField)(JNIEnv*, jobject, jfieldID, jchar);

  void(JNICALL* SetShortField)(JNIEnv*, jobject, jfieldID, jshort);

  void(JNICALL* SetIntField)(JNIEnv*, jobject, jfieldID, jint);

  void(JNICALL* SetLongField)(JNIEnv*, jobject, jfieldID, jlong);

  void(JNICALL* SetFloatField)(JNIEnv*, jobject, jfieldID, jfloat);

  void(JNICALL* SetDoubleField)(JNIEnv*, jobject, jfieldID, jdouble);

  jmethodID(JNICALL* GetStaticMethodID)(JNIEnv*,
                                        jclass,
                                        const char*,
                                        const char*);

  jobject(JNICALL* CallStaticObjectMethod)(JNIEnv*, jclass, jmethodID, ...);

  jobject(JNICALL* CallStaticObjectMethodV)(JNIEnv*,
                                            jclass,
                                            jmethodID,
                                            va_list);

  jobject(JNICALL* CallStaticObjectMethodA)(JNIEnv*,
                                            jclass,
                                            jmethodID,
                                            const jvalue*);

  jboolean(JNICALL* CallStaticBooleanMethod)(JNIEnv*, jclass, jmethodID, ...);

  jboolean(JNICALL* CallStaticBooleanMethodV)(JNIEnv*,
                                              jclass,
                                              jmethodID,
                                              va_list);

  jboolean(JNICALL* CallStaticBooleanMethodA)(JNIEnv*,
                                              jclass,
                                              jmethodID,
                                              const jvalue*);

  jbyte(JNICALL* CallStaticByteMethod)(JNIEnv*, jclass, jmethodID, ...);

  jbyte(JNICALL* CallStaticByteMethodV)(JNIEnv*, jclass, jmethodID, va_list);

  jbyte(JNICALL* CallStaticByteMethodA)(JNIEnv*,
                                        jclass,
                                        jmethodID,
                                        const jvalue*);

  jchar(JNICALL* CallStaticCharMethod)(JNIEnv*, jclass, jmethodID, ...);

  jchar(JNICALL* CallStaticCharMethodV)(JNIEnv*, jclass, jmethodID, va_list);

  jchar(JNICALL* CallStaticCharMethodA)(JNIEnv*,
                                        jclass,
                                        jmethodID,
                                        const jvalue*);

  jshort(JNICALL* CallStaticShortMethod)(JNIEnv*, jclass, jmethodID, ...);

  jshort(JNICALL* CallStaticShortMethodV)(JNIEnv*, jclass, jmethodID, va_list);

  jshort(JNICALL* CallStaticShortMethodA)(JNIEnv*,
                                          jclass,
                                          jmethodID,
                                          const jvalue*);

  jint(JNICALL* CallStaticIntMethod)(JNIEnv*, jclass, jmethodID, ...);

  jint(JNICALL* CallStaticIntMethodV)(JNIEnv*, jclass, jmethodID, va_list);

  jint(JNICALL* CallStaticIntMethodA)(JNIEnv*,
                                      jclass,
                                      jmethodID,
                                      const jvalue*);

  jlong(JNICALL* CallStaticLongMethod)(JNIEnv*, jclass, jmethodID, ...);

  jlong(JNICALL* CallStaticLongMethodV)(JNIEnv*, jclass, jmethodID, va_list);

  jlong(JNICALL* CallStaticLongMethodA)(JNIEnv*,
                                        jclass,
                                        jmethodID,
                                        const jvalue*);

  jfloat(JNICALL* CallStaticFloatMethod)(JNIEnv*, jclass, jmethodID, ...);

  jfloat(JNICALL* CallStaticFloatMethodV)(JNIEnv*, jclass, jmethodID, va_list);

  jfloat(JNICALL* CallStaticFloatMethodA)(JNIEnv*,
                                          jclass,
                                          jmethodID,
                                          const jvalue*);

  jdouble(JNICALL* CallStaticDoubleMethod)(JNIEnv*, jclass, jmethodID, ...);

  jdouble(JNICALL* CallStaticDoubleMethodV)(JNIEnv*,
                                            jclass,
                                            jmethodID,
                                            va_list);

  jdouble(JNICALL* CallStaticDoubleMethodA)(JNIEnv*,
                                            jclass,
                                            jmethodID,
                                            const jvalue*);

  void(JNICALL* CallStaticVoidMethod)(JNIEnv*, jclass, jmethodID, ...);

  void(JNICALL* CallStaticVoidMethodV)(JNIEnv*, jclass, jmethodID, va_list);

  void(JNICALL* CallStaticVoidMethodA)(JNIEnv*,
                                       jclass,
                                       jmethodID,
                                       const jvalue*);

  jfieldID(JNICALL* GetStaticFieldID)(JNIEnv*,
                                      jclass,
                                      const char*,
                                      const char*);

  jobject(JNICALL* GetStaticObjectField)(JNIEnv*, jclass, jfieldID);

  jboolean(JNICALL* GetStaticBooleanField)(JNIEnv*, jclass, jfieldID);

  jbyte(JNICALL* GetStaticByteField)(JNIEnv*, jclass, jfieldID);

  jchar(JNICALL* GetStaticCharField)(JNIEnv*, jclass, jfieldID);

  jshort(JNICALL* GetStaticShortField)(JNIEnv*, jclass, jfieldID);

  jint(JNICALL* GetStaticIntField)(JNIEnv*, jclass, jfieldID);

  jlong(JNICALL* GetStaticLongField)(JNIEnv*, jclass, jfieldID);

  jfloat(JNICALL* GetStaticFloatField)(JNIEnv*, jclass, jfieldID);

  jdouble(JNICALL* GetStaticDoubleField)(JNIEnv*, jclass, jfieldID);

  void(JNICALL* SetStaticObjectField)(JNIEnv*, jclass, jfieldID, jobject);

  void(JNICALL* SetStaticBooleanField)(JNIEnv*, jclass, jfieldID, jboolean);

  void(JNICALL* SetStaticByteField)(JNIEnv*, jclass, jfieldID, jbyte);

  void(JNICALL* SetStaticCharField)(JNIEnv*, jclass, jfieldID, jchar);

  void(JNICALL* SetStaticShortField)(JNIEnv*, jclass, jfieldID, jshort);

  void(JNICALL* SetStaticIntField)(JNIEnv*, jclass, jfieldID, jint);

  void(JNICALL* SetStaticLongField)(JNIEnv*, jclass, jfieldID, jlong);

  void(JNICALL* SetStaticFloatField)(JNIEnv*, jclass, jfieldID, jfloat);

  void(JNICALL* SetStaticDoubleField)(JNIEnv*, jclass, jfieldID, jdouble);

  jstring(JNICALL* NewString)(JNIEnv*, const jchar*, jsize);

  jsize(JNICALL* GetStringLength)(JNIEnv*, jstring);

  const jchar*(JNICALL* GetStringChars)(JNIEnv*, jstring, jboolean*);

  void(JNICALL* ReleaseStringChars)(JNIEnv*, jstring, const jchar*);

  jstring(JNICALL* NewStringUTF)(JNIEnv*, const char*);

  jsize(JNICALL* GetStringUTFLength)(JNIEnv*, jstring);

  const char*(JNICALL* GetStringUTFChars)(JNIEnv*, jstring, jboolean*);

  void(JNICALL* ReleaseStringUTFChars)(JNIEnv*, jstring, const char*);

  jsize(JNICALL* GetArrayLength)(JNIEnv*, jarray);

  jobjectArray(JNICALL* NewObjectArray)(JNIEnv*, jsize, jclass, jobject);

  jobject(JNICALL* GetObjectArrayElement)(JNIEnv*, jobjectArray, jsize);

  void(JNICALL* SetObjectArrayElement)(JNIEnv*, jobjectArray, jsize, jobject);

  jbooleanArray(JNICALL* NewBooleanArray)(JNIEnv*, jsize);

  jbyteArray(JNICALL* NewByteArray)(JNIEnv*, jsize);

  jcharArray(JNICALL* NewCharArray)(JNIEnv*, jsize);

  jshortArray(JNICALL* NewShortArray)(JNIEnv*, jsize);

  jintArray(JNICALL* NewIntArray)(JNIEnv*, jsize);

  jlongArray(JNICALL* NewLongArray)(JNIEnv*, jsize);

  jfloatArray(JNICALL* NewFloatArray)(JNIEnv*, jsize);

  jdoubleArray(JNICALL* NewDoubleArray)(JNIEnv*, jsize);

  jboolean*(JNICALL* GetBooleanArrayElements)(JNIEnv*,
                                              jbooleanArray,
                                              jboolean*);

  jbyte*(JNICALL* GetByteArrayElements)(JNIEnv*, jbyteArray, jboolean*);

  jchar*(JNICALL* GetCharArrayElements)(JNIEnv*, jcharArray, jboolean*);

  jshort*(JNICALL* GetShortArrayElements)(JNIEnv*, jshortArray, jboolean*);

  jint*(JNICALL* GetIntArrayElements)(JNIEnv*, jintArray, jboolean*);

  jlong*(JNICALL* GetLongArrayElements)(JNIEnv*, jlongArray, jboolean*);

  jfloat*(JNICALL* GetFloatArrayElements)(JNIEnv*, jfloatArray, jboolean*);

  jdouble*(JNICALL* GetDoubleArrayElements)(JNIEnv*, jdoubleArray, jboolean*);

  void(JNICALL* ReleaseBooleanArrayElements)(JNIEnv*,
                                             jbooleanArray,
                                             jboolean*,
                                             jint);

  void(JNICALL* ReleaseByteArrayElements)(JNIEnv*, jbyteArray, jbyte*, jint);

  void(JNICALL* ReleaseCharArrayElements)(JNIEnv*, jcharArray, jchar*, jint);

  void(JNICALL* ReleaseShortArrayElements)(JNIEnv*, jshortArray, jshort*, jint);

  void(JNICALL* ReleaseIntArrayElements)(JNIEnv*, jintArray, jint*, jint);

  void(JNICALL* ReleaseLongArrayElements)(JNIEnv*, jlongArray, jlong*, jint);

  void(JNICALL* ReleaseFloatArrayElements)(JNIEnv*, jfloatArray, jfloat*, jint);

  void(JNICALL* ReleaseDoubleArrayElements)(JNIEnv*,
                                            jdoubleArray,
                                            jdouble*,
                                            jint);

  void(JNICALL* GetBooleanArrayRegion)(JNIEnv*,
                                       jbooleanArray,
                                       jsize,
                                       jsize,
                                       jboolean*);

  void(JNICALL* GetByteArrayRegion)(JNIEnv*, jbyteArray, jsize, jsize, jbyte*);

  void(JNICALL* GetCharArrayRegion)(JNIEnv*, jcharArray, jsize, jsize, jchar*);

  void(JNICALL* GetShortArrayRegion)(JNIEnv*,
                                     jshortArray,
                                     jsize,
                                     jsize,
                                     jshort*);

  void(JNICALL* GetIntArrayRegion)(JNIEnv*, jintArray, jsize, jsize, jint*);

  void(JNICALL* GetLongArrayRegion)(JNIEnv*, jlongArray, jsize, jsize, jlong*);

  void(JNICALL* GetFloatArrayRegion)(JNIEnv*,
                                     jfloatArray,
                                     jsize,
                                     jsize,
                                     jfloat*);

  void(JNICALL* GetDoubleArrayRegion)(JNIEnv*,
                                      jdoubleArray,
                                      jsize,
                                      jsize,
                                      jdouble*);

  void(JNICALL* SetBooleanArrayRegion)(JNIEnv*,
                                       jbooleanArray,
                                       jsize,
                                       jsize,
                                       const jboolean*);

  void(JNICALL* SetByteArrayRegion)(JNIEnv*,
                                    jbyteArray,
                                    jsize,
                                    jsize,
                                    const jbyte*);

  void(JNICALL* SetCharArrayRegion)(JNIEnv*,
                                    jcharArray,
                                    jsize,
                                    jsize,
                                    const jchar*);

  void(JNICALL* SetShortArrayRegion)(JNIEnv*,
                                     jshortArray,
                                     jsize,
                                     jsize,
                                     const jshort*);

  void(JNICALL* SetIntArrayRegion)(JNIEnv*,
                                   jintArray,
                                   jsize,
                                   jsize,
                                   const jint*);

  void(JNICALL* SetLongArrayRegion)(JNIEnv*,
                                    jlongArray,
                                    jsize,
                                    jsize,
                                    const jlong*);

  void(JNICALL* SetFloatArrayRegion)(JNIEnv*,
                                     jfloatArray,
                                     jsize,
                                     jsize,
                                     const jfloat*);

  void(JNICALL* SetDoubleArrayRegion)(JNIEnv*,
                                      jdoubleArray,
                                      jsize,
                                      jsize,
                                      const jdouble*);

  jint(JNICALL* RegisterNatives)(JNIEnv*, jclass, const JNINativeMethod*, jint);

  jint(JNICALL* UnregisterNatives)(JNIEnv*, jclass);

  jint(JNICALL* MonitorEnter)(JNIEnv*, jobject);

  jint(JNICALL* MonitorExit)(JNIEnv*, jobject);

  jint(JNICALL* GetJavaVM)(JNIEnv*, JavaVM**);

  void(JNICALL* GetStringRegion)(JNIEnv*, jstring, jsize, jsize, jchar*);

  void(JNICALL* GetStringUTFRegion)(JNIEnv*, jstring, jsize, jsize, char*);

  void*(JNICALL* GetPrimitiveArrayCritical)(JNIEnv*, jarray, jboolean*);

  void(JNICALL* ReleasePrimitiveArrayCritical)(JNIEnv*, jarray, void*, jint);

  const jchar*(JNICALL* GetStringCritical)(JNIEnv*, jstring, jboolean*);

  void(JNICALL* ReleaseStringCritical)(JNIEnv*, jstring, const jchar*);

  jweak(JNICALL* NewWeakGlobalRef)(JNIEnv*, jobject);

  void(JNICALL* DeleteWeakGlobalRef)(JNIEnv*, jweak);

  jboolean(JNICALL* ExceptionCheck)(JNIEnv*);

  jobject(JNICALL* NewDirectByteBuffer)(JNIEnv*, void*, jlong);

  void*(JNICALL* GetDirectBufferAddress)(JNIEnv* env, jobject);

  jlong(JNICALL* GetDirectBufferCapacity)(JNIEnv*, jobject);
};

inline void atomicOr(uint32_t* p, int v)
{
  for (uint32_t old = *p; not atomicCompareAndSwap32(p, old, old | v);
       old = *p) {
  }
}

inline void atomicAnd(uint32_t* p, int v)
{
  for (uint32_t old = *p; not atomicCompareAndSwap32(p, old, old & v);
       old = *p) {
  }
}

inline int strcmp(const int8_t* a, const int8_t* b)
{
  return ::strcmp(reinterpret_cast<const char*>(a),
                  reinterpret_cast<const char*>(b));
}

void noop();

class Reference {
 public:
  Reference(object target, Reference** handle, bool weak)
      : target(target), next(*handle), handle(handle), count(0), weak(weak)
  {
    if (next) {
      next->handle = &next;
    }
    *handle = this;
  }

  object target;
  Reference* next;
  Reference** handle;
  unsigned count;
  bool weak;
};

class Classpath;

class Gc {
 public:
  enum Type {
#include "type-enums.cpp"
  };
};

class GcObject {
 public:
  template <class T>
  T* as(Thread* t);

  template <class T>
  bool isa(Thread* t);

 protected:
  template <class T>
  T& field_at(size_t offset)
  {
    return *reinterpret_cast<T*>(reinterpret_cast<uint8_t*>(this) + offset);
  }
};

class GcFinalizer;
class GcClassLoader;
class GcJreference;
class GcArray;
class GcThrowable;
class GcRoots;

class Machine {
 public:
  enum AllocationType {
    MovableAllocation,
    FixedAllocation,
    ImmortalAllocation
  };

  Machine(System* system,
          Heap* heap,
          Finder* bootFinder,
          Finder* appFinder,
          Processor* processor,
          Classpath* classpath,
          const char** properties,
          unsigned propertyCount,
          const char** arguments,
          unsigned argumentCount,
          unsigned stackSizeInBytes);

  ~Machine()
  {
    dispose();
  }

  void dispose();

  JavaVMVTable* vtable;
  System* system;
  Heap::Client* heapClient;
  Heap* heap;
  Finder* bootFinder;
  Finder* appFinder;
  Processor* processor;
  Classpath* classpath;
  Thread* rootThread;
  Thread* exclusive;
  Thread* finalizeThread;
  Reference* jniReferences;
  char** properties;
  unsigned propertyCount;
  const char** arguments;
  unsigned argumentCount;
  unsigned threadCount;
  unsigned activeCount;
  unsigned liveCount;
  unsigned daemonCount;
  unsigned fixedFootprint;
  unsigned stackSizeInBytes;
  System::Local* localThread;
  System::Monitor* stateLock;
  System::Monitor* heapLock;
  System::Monitor* classLock;
  System::Monitor* referenceLock;
  System::Monitor* shutdownLock;
  System::Library* libraries;
  FILE* errorLog;
  BootImage* bootimage;
  GcArray* types;
  GcRoots* roots;
  GcFinalizer* finalizers;
  GcFinalizer* tenuredFinalizers;
  GcFinalizer* finalizeQueue;
  GcJreference* weakReferences;
  GcJreference* tenuredWeakReferences;
  bool unsafe;
  bool collecting;
  bool triedBuiltinOnLoad;
  bool dumpedHeapOnOOM;
  bool alive;
  JavaVMVTable javaVMVTable;
  JNIEnvVTable jniEnvVTable;
  uintptr_t* heapPool[ThreadHeapPoolSize];
  unsigned heapPoolIndex;
  size_t bootimageSize;
};

void printTrace(Thread* t, GcThrowable* exception);

void enterActiveState(Thread* t);

#ifdef VM_STRESS

inline void stress(Thread* t);

#else  // not VM_STRESS

#define stress(t)

#endif  // not VM_STRESS

uint64_t runThread(Thread*, uintptr_t*);

uint64_t run(Thread* t,
             uint64_t (*function)(Thread*, uintptr_t*),
             uintptr_t* arguments);

void checkDaemon(Thread* t);

GcRoots* roots(Thread* t);

extern "C" uint64_t vmRun(uint64_t (*function)(Thread*, uintptr_t*),
                          uintptr_t* arguments,
                          void* checkpoint);

extern "C" void vmRun_returnAddress();

class GcThread;
class GcThrowable;
class GcString;

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

  enum Flag {
    UseBackupHeapFlag = 1 << 0,
    WaitingFlag = 1 << 1,
    TracingFlag = 1 << 2,
    DaemonFlag = 1 << 3,
    StressFlag = 1 << 4,
    ActiveFlag = 1 << 5,
    SystemFlag = 1 << 6,
    JoinFlag = 1 << 7,
    TryNativeFlag = 1 << 8
  };

  class Protector {
   public:
    Protector(Thread* t) : t(t), next(t->protector)
    {
      t->protector = this;
    }

    ~Protector()
    {
      t->protector = next;
    }

    virtual void visit(Heap::Visitor* v) = 0;

    Thread* t;
    Protector* next;
  };

  class SingleProtector : public Protector {
   public:
    SingleProtector(Thread* t, void* p) : Protector(t), p(p)
    {
    }

    virtual void visit(Heap::Visitor* v)
    {
      v->visit(p);
    }

    void* p;
  };

  class Resource {
   public:
    Resource(Thread* t, Resource* next) : t(t), next(next)
    {
      t->resource = this;
    }

    virtual void release() = 0;

    Thread* t;
    Resource* next;
  };

  class AutoResource : public Resource {
   public:
    AutoResource(Thread* t) : Resource(t, t->resource)
    {
    }

    ~AutoResource()
    {
      t->resource = next;
    }

    virtual void release() = 0;
  };

  class ClassInitStack : public AutoResource {
   public:
    ClassInitStack(Thread* t, GcClass* class_)
        : AutoResource(t),
          next(t->classInitStack),
          class_(class_),
          protector(t, &(this->class_))
    {
      t->classInitStack = this;
    }

    ~ClassInitStack()
    {
      t->classInitStack = next;
    }

    virtual void release()
    {
      this->ClassInitStack::~ClassInitStack();
    }

    ClassInitStack* next;
    GcClass* class_;
    SingleProtector protector;
  };

  class LibraryLoadStack : public AutoResource {
   public:
    LibraryLoadStack(Thread* t, GcClassLoader* classLoader)
        : AutoResource(t),
          next(t->libraryLoadStack),
          classLoader(classLoader),
          protector(t, &(this->classLoader))
    {
      t->libraryLoadStack = this;
    }

    ~LibraryLoadStack()
    {
      t->libraryLoadStack = next;
    }

    virtual void release()
    {
      this->LibraryLoadStack::~LibraryLoadStack();
    }

    LibraryLoadStack* next;
    GcClassLoader* classLoader;
    SingleProtector protector;
  };

  class Checkpoint {
   public:
    Checkpoint(Thread* t)
        : t(t),
          next(t->checkpoint),
          resource(t->resource),
          protector(t->protector),
          noThrow(false)
    {
      t->checkpoint = this;
    }

    ~Checkpoint()
    {
      t->checkpoint = next;
    }

    virtual void NO_RETURN unwind() = 0;

    Thread* t;
    Checkpoint* next;
    Resource* resource;
    Protector* protector;
    bool noThrow;
  };

  class RunCheckpoint : public Checkpoint {
   public:
    RunCheckpoint(Thread* t) : Checkpoint(t), stack(0)
    {
    }

    virtual void unwind()
    {
      void* stack = this->stack;
      this->stack = 0;
      expect(t->m->system, stack);
      vmJump(voidPointer(vmRun_returnAddress), 0, stack, t, 0, 0);
    }

    void* stack;
  };

  class Runnable : public System::Runnable {
   public:
    Runnable(Thread* t) : t(t)
    {
    }

    virtual void attach(System::Thread* st)
    {
      t->systemThread = st;
    }

    virtual void run();

    virtual bool interrupted();

    virtual void setInterrupted(bool v);

    Thread* t;
  };

  Thread(Machine* m, GcThread* javaThread, Thread* parent);

  void init();
  void exit();
  void dispose();

  void setFlag(Flag flag) {
    atomicOr(&flags, flag);
  }

  void clearFlag(Flag flag) {
    atomicAnd(&flags, ~flag);
  }

  unsigned getFlags() {
    return flags;
  }

  JNIEnvVTable* vtable;
  Machine* m;
  Thread* parent;
  Thread* peer;
  Thread* child;
  Thread* waitNext;
  State state;
  unsigned criticalLevel;
  System::Thread* systemThread;
  System::Monitor* lock;
  GcThread* javaThread;
  GcThrowable* exception;
  unsigned heapIndex;
  unsigned heapOffset;
  Protector* protector;
  ClassInitStack* classInitStack;
  LibraryLoadStack* libraryLoadStack;
  Resource* resource;
  Checkpoint* checkpoint;
  Runnable runnable;
  uintptr_t* defaultHeap;
  uintptr_t* heap;
  uintptr_t backupHeap[ThreadBackupHeapSizeInWords];
  unsigned backupHeapIndex;

 private:
  unsigned flags;
};

class GcJfield;

class Classpath {
 public:
  virtual GcJclass* makeJclass(Thread* t, GcClass* class_) = 0;

  virtual GcString* makeString(Thread* t,
                               object array,
                               int32_t offset,
                               int32_t length) = 0;

  virtual GcThread* makeThread(Thread* t, Thread* parent) = 0;

  virtual object makeJMethod(Thread* t, GcMethod* vmMethod) = 0;

  virtual GcMethod* getVMMethod(Thread* t, object jmethod) = 0;

  virtual object makeJField(Thread* t, GcField* vmField) = 0;

  virtual GcField* getVMField(Thread* t, GcJfield* jfield) = 0;

  virtual void clearInterrupted(Thread* t) = 0;

  virtual void runThread(Thread* t) = 0;

  virtual void resolveNative(Thread* t, GcMethod* method) = 0;

  virtual void interceptMethods(Thread* t) = 0;

  virtual void preBoot(Thread* t) = 0;

  virtual bool mayInitClasses() = 0;

  virtual void boot(Thread* t) = 0;

  virtual const char* bootClasspath() = 0;

  virtual object makeDirectByteBuffer(Thread* t, void* p, jlong capacity) = 0;

  virtual void* getDirectBufferAddress(Thread* t, object buffer) = 0;

  virtual int64_t getDirectBufferCapacity(Thread* t, object buffer) = 0;

  virtual bool canTailCall(Thread* t,
                           GcMethod* caller,
                           GcByteArray* calleeClassName,
                           GcByteArray* calleeMethodName,
                           GcByteArray* calleeMethodSpec) = 0;

  virtual GcClassLoader* libraryClassLoader(Thread* t, GcMethod* caller) = 0;

  virtual void shutDown(Thread* t) = 0;

  virtual void dispose() = 0;
};

#ifdef _MSC_VER

template <class T>
class ThreadRuntimeArray : public Thread::AutoResource {
 public:
  ThreadRuntimeArray(Thread* t, unsigned size)
      : AutoResource(t),
        body(static_cast<T*>(t->m->heap->allocate(size * sizeof(T)))),
        size(size)
  {
  }

  ~ThreadRuntimeArray()
  {
    t->m->heap->free(body, size * sizeof(T));
  }

  virtual void release()
  {
    ThreadRuntimeArray::~ThreadRuntimeArray();
  }

  T* body;
  unsigned size;
};

#define THREAD_RUNTIME_ARRAY(thread, type, name, size) \
  ThreadRuntimeArray<type> name(thread, size);

#else  // not _MSC_VER

#define THREAD_RUNTIME_ARRAY(thread, type, name, size) type name##_body[size];

#endif  // not _MSC_VER

Classpath* makeClasspath(System* system,
                         Allocator* allocator,
                         const char* javaHome,
                         const char* embedPrefix);

typedef uint64_t(JNICALL* FastNativeFunction)(Thread*, GcMethod*, uintptr_t*);
typedef void(JNICALL* FastVoidNativeFunction)(Thread*, GcMethod*, uintptr_t*);

inline GcClass* objectClass(Thread*, object o)
{
  return reinterpret_cast<GcClass*>(
      maskAlignedPointer(fieldAtOffset<object>(o, 0)));
}

inline unsigned stackSizeInWords(Thread* t)
{
  return t->m->stackSizeInBytes / BytesPerWord;
}

void enter(Thread* t, Thread::State state);

inline void enterActiveState(Thread* t)
{
  enter(t, Thread::ActiveState);
}

class StateResource : public Thread::AutoResource {
 public:
  StateResource(Thread* t, Thread::State state)
      : AutoResource(t), oldState(t->state)
  {
    enter(t, state);
  }

  ~StateResource()
  {
    enter(t, oldState);
  }

  virtual void release()
  {
    this->StateResource::~StateResource();
  }

 private:
  Thread::State oldState;
};

inline void dispose(Thread* t, Reference* r)
{
  *(r->handle) = r->next;
  if (r->next) {
    r->next->handle = r->handle;
  }
  t->m->heap->free(r, sizeof(*r));
}

inline void acquire(Thread*, Reference* r)
{
  ++r->count;
}

inline void release(Thread* t, Reference* r)
{
  if ((--r->count) == 0) {
    dispose(t, r);
  }
}

void collect(Thread* t, Heap::CollectionType type, int pendingAllocation = 0);

void shutDown(Thread* t);

#ifdef VM_STRESS

inline void stress(Thread* t)
{
  if ((not t->m->unsafe)
      and (t->getFlags() & (Thread::StressFlag | Thread::TracingFlag)) == 0
      and t->state != Thread::NoState and t->state != Thread::IdleState) {
    t->setFlag(Thread::StressFlag);

#ifdef VM_STRESS_MAJOR
    collect(t, Heap::MajorCollection);
#else   // not VM_STRESS_MAJOR
    collect(t, Heap::MinorCollection);
#endif  // not VM_STRESS_MAJOR

    t->clearFlag(Thread::StressFlag);
  }
}

#endif  // not VM_STRESS

inline void acquire(Thread* t, System::Monitor* m)
{
  if (not m->tryAcquire(t->systemThread)) {
    ENTER(t, Thread::IdleState);
    m->acquire(t->systemThread);
  }

  stress(t);
}

inline void release(Thread* t, System::Monitor* m)
{
  m->release(t->systemThread);
}

class MonitorResource : public Thread::AutoResource {
 public:
  MonitorResource(Thread* t, System::Monitor* m) : AutoResource(t), m(m)
  {
    acquire(t, m);
  }

  ~MonitorResource()
  {
    vm::release(t, m);
  }

  virtual void release()
  {
    this->MonitorResource::~MonitorResource();
  }

 private:
  System::Monitor* m;
};

class RawMonitorResource : public Thread::Resource {
 public:
  RawMonitorResource(Thread* t, System::Monitor* m)
      : Resource(t, t->resource), m(m)
  {
    m->acquire(t->systemThread);
  }

  ~RawMonitorResource()
  {
    t->resource = next;
    vm::release(t, m);
  }

  virtual void release()
  {
    this->RawMonitorResource::~RawMonitorResource();
  }

 private:
  System::Monitor* m;
};

inline Aborter* getAborter(Thread* t)
{
  return t->m->system;
}

inline bool ensure(Thread* t, unsigned sizeInBytes)
{
  if (t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
      > ThreadHeapSizeInWords) {
    if (sizeInBytes <= ThreadBackupHeapSizeInBytes) {
      expect(t, (t->getFlags() & Thread::UseBackupHeapFlag) == 0);

      t->setFlag(Thread::UseBackupHeapFlag);

      return true;
    } else {
      return false;
    }
  } else {
    return true;
  }
}

object allocate2(Thread* t, unsigned sizeInBytes, bool objectMask);

object allocate3(Thread* t,
                 Alloc* allocator,
                 Machine::AllocationType type,
                 unsigned sizeInBytes,
                 bool objectMask);

inline object allocateSmall(Thread* t, unsigned sizeInBytes)
{
  assertT(t,
          t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
          <= ThreadHeapSizeInWords);

  object o = reinterpret_cast<object>(t->heap + t->heapIndex);
  t->heapIndex += ceilingDivide(sizeInBytes, BytesPerWord);
  return o;
}

inline object allocate(Thread* t, unsigned sizeInBytes, bool objectMask)
{
  stress(t);

  if (UNLIKELY(t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
               > ThreadHeapSizeInWords or t->m->exclusive)) {
    return allocate2(t, sizeInBytes, objectMask);
  } else {
    assertT(t, t->criticalLevel == 0);
    return allocateSmall(t, sizeInBytes);
  }
}

inline void mark(Thread* t, object o, unsigned offset, unsigned count)
{
  t->m->heap->mark(o, offset / BytesPerWord, count);
}

inline void mark(Thread* t, object o, unsigned offset)
{
  t->m->heap->mark(o, offset / BytesPerWord, 1);
}

inline void setField(Thread* t, object target, unsigned offset, object value)
{
  fieldAtOffset<object>(target, offset) = value;
  mark(t, target, offset);
}

inline void setObject(Thread* t,
                      GcObject* target,
                      unsigned offset,
                      GcObject* value)
{
  setField(t, target, offset, value);
}

inline void setObjectClass(Thread*, object o, GcClass* c)
{
  fieldAtOffset<object>(o, 0) = reinterpret_cast<object>(
      reinterpret_cast<intptr_alias_t>(c)
      | (reinterpret_cast<intptr_alias_t>(fieldAtOffset<object>(o, 0))
         & (~PointerMask)));
}

inline const char* findProperty(Machine* m, const char* name)
{
  for (unsigned i = 0; i < m->propertyCount; ++i) {
    const char* p = m->properties[i];
    const char* n = name;
    while (*p and *p != '=' and *n and *p == *n) {
      ++p;
      ++n;
    }
    if (*p == '=' and *n == 0) {
      return p + 1;
    }
  }
  return 0;
}

inline const char* findProperty(Thread* t, const char* name)
{
  return findProperty(t->m, name);
}

object arrayBodyUnsafe(Thread*, GcArray*, unsigned);

bool instanceOf(Thread* t, GcClass* class_, object o);

template <class T>
T* GcObject::as(Thread* t UNUSED)
{
  assertT(t,
          t->m->unsafe || instanceOf(t,
                                     reinterpret_cast<GcClass*>(arrayBodyUnsafe(
                                         t, t->m->types, T::Type)),
                                     this));
  return static_cast<T*>(this);
}

template <class T>
bool GcObject::isa(Thread* t)
{
  return instanceOf(
      t,
      reinterpret_cast<GcClass*>(arrayBodyUnsafe(t, t->m->types, T::Type)),
      this);
}

template <class T>
T* cast(Thread* t UNUSED, object o)
{
  if (o == 0) {
    return 0;
  }
  assertT(t,
          t->m->unsafe || instanceOf(t,
                                     reinterpret_cast<GcClass*>(arrayBodyUnsafe(
                                         t, t->m->types, T::Type)),
                                     o));
  return reinterpret_cast<T*>(o);
}

#include "type-declarations.cpp"

inline object arrayBodyUnsafe(Thread*, GcArray* a, unsigned index)
{
  return a->body()[index];
}

inline void Thread::Runnable::run()
{
  enterActiveState(t);

  vm::run(t, runThread, 0);

  if (t->exception and t->exception != roots(t)->shutdownInProgress()) {
    printTrace(t, t->exception);
  }

  t->exit();
}

inline bool Thread::Runnable::interrupted()
{
  return t->javaThread and t->javaThread->interrupted();
}

inline void Thread::Runnable::setInterrupted(bool v)
{
  t->javaThread->interrupted() = v;
}

inline uint64_t runRaw(Thread* t,
                       uint64_t (*function)(Thread*, uintptr_t*),
                       uintptr_t* arguments)
{
  Thread::RunCheckpoint checkpoint(t);
  return vmRun(function, arguments, &checkpoint);
}

inline uint64_t run(Thread* t,
                    uint64_t (*function)(Thread*, uintptr_t*),
                    uintptr_t* arguments)
{
  ENTER(t, Thread::ActiveState);
  return runRaw(t, function, arguments);
}

inline void runJavaThread(Thread* t)
{
  t->m->classpath->runThread(t);
}

void runFinalizeThread(Thread* t);

inline uint64_t runThread(Thread* t, uintptr_t*)
{
  t->m->localThread->set(t);

  checkDaemon(t);

  if (t == t->m->finalizeThread) {
    runFinalizeThread(t);
  } else if (t->javaThread) {
    runJavaThread(t);
  }

  return 1;
}

inline bool startThread(Thread* t, Thread* p)
{
  p->setFlag(Thread::JoinFlag);
  return t->m->system->success(t->m->system->start(&(p->runnable)));
}

inline void addThread(Thread* t, Thread* p)
{
  ACQUIRE_RAW(t, t->m->stateLock);

  assertT(t, p->state == Thread::NoState);
  expect(t,
         t->state == Thread::ActiveState || t->state == Thread::ExclusiveState
         || t->state == Thread::NoState);

  p->state = Thread::IdleState;
  ++t->m->threadCount;
  ++t->m->liveCount;

  p->peer = p->parent->child;
  p->parent->child = p;

  if (p->javaThread) {
    p->javaThread->peer() = reinterpret_cast<jlong>(p);
  }
}

inline void removeThread(Thread* t, Thread* p)
{
  ACQUIRE_RAW(t, t->m->stateLock);

  assertT(t, p->state == Thread::IdleState);

  --t->m->liveCount;
  --t->m->threadCount;

  t->m->stateLock->notifyAll(t->systemThread);

  p->parent->child = p->peer;

  if (p->javaThread) {
    p->javaThread->peer() = 0;
  }

  p->dispose();
}

inline Thread* startThread(Thread* t, GcThread* javaThread)
{
  {
    PROTECT(t, javaThread);

    stress(t);

    ACQUIRE_RAW(t, t->m->stateLock);

    if (t->m->threadCount > t->m->liveCount + ZombieCollectionThreshold) {
      collect(t, Heap::MinorCollection);
    }
  }

  Thread* p = t->m->processor->makeThread(t->m, javaThread, t);

  addThread(t, p);

  if (startThread(t, p)) {
    return p;
  } else {
    removeThread(t, p);
    return 0;
  }
}

inline void registerDaemon(Thread* t)
{
  ACQUIRE_RAW(t, t->m->stateLock);

  t->setFlag(Thread::DaemonFlag);

  ++t->m->daemonCount;

  t->m->stateLock->notifyAll(t->systemThread);
}

inline void checkDaemon(Thread* t)
{
  if (t->javaThread->daemon()) {
    registerDaemon(t);
  }
}

inline uint64_t initAttachedThread(Thread* t, uintptr_t* arguments)
{
  bool daemon = arguments[0];

  t->javaThread = t->m->classpath->makeThread(t, t->m->rootThread);

  t->javaThread->peer() = reinterpret_cast<jlong>(t);

  if (daemon) {
    t->javaThread->daemon() = true;

    registerDaemon(t);
  }

  t->m->localThread->set(t);

  return 1;
}

inline Thread* attachThread(Machine* m, bool daemon)
{
  Thread* t = m->processor->makeThread(m, 0, m->rootThread);
  m->system->attach(&(t->runnable));

  addThread(t, t);

  enter(t, Thread::ActiveState);

  uintptr_t arguments[] = {daemon};

  if (run(t, initAttachedThread, arguments)) {
    enter(t, Thread::IdleState);
    return t;
  } else {
    t->exit();
    return 0;
  }
}

inline GcRoots* roots(Thread* t)
{
  return t->m->roots;
}

inline GcClass* type(Thread* t, Gc::Type type)
{
  return cast<GcClass>(t, t->m->types->body()[type]);
}

inline void setType(Thread* t, Gc::Type type, GcClass* value)
{
  t->m->types->setBodyElement(t, type, value);
}

inline bool objectFixed(Thread*, object o)
{
  return (alias(o, 0) & (~PointerMask)) == FixedMark;
}

inline bool objectExtended(Thread*, object o)
{
  return (alias(o, 0) & (~PointerMask)) == ExtendedMark;
}

inline bool hashTaken(Thread*, object o)
{
  return (alias(o, 0) & (~PointerMask)) == HashTakenMark;
}

inline unsigned baseSize(Thread* t UNUSED, object o, GcClass* class_)
{
  assertT(t, class_->fixedSize() >= BytesPerWord);

  unsigned size = ceilingDivide(class_->fixedSize(), BytesPerWord);
  if (class_->arrayElementSize() > 0) {
    size += ceilingDivide(class_->arrayElementSize()
                       * fieldAtOffset<uintptr_t>(
                             o, class_->fixedSize() - BytesPerWord),
                       BytesPerWord);
  }
  return size;
}

object makeTrace(Thread* t, Processor::StackWalker* walker);

object makeTrace(Thread* t, Thread* target);

inline object makeTrace(Thread* t)
{
  return makeTrace(t, t);
}

inline object makeNew(Thread* t, GcClass* class_)
{
  assertT(t, t->state == Thread::NoState or t->state == Thread::ActiveState);

  PROTECT(t, class_);
  unsigned sizeInBytes = pad(class_->fixedSize());
  assertT(t, sizeInBytes);
  object instance = allocate(t, sizeInBytes, class_->objectMask());
  setObjectClass(t, instance, class_);

  return instance;
}

object makeNewGeneral(Thread* t, GcClass* class_);

inline object make(Thread* t, GcClass* class_)
{
  if (UNLIKELY(class_->vmFlags() & (WeakReferenceFlag | HasFinalizerFlag))) {
    return makeNewGeneral(t, class_);
  } else {
    return makeNew(t, class_);
  }
}

GcByteArray* makeByteArrayV(Thread* t, const char* format, va_list a, int size);

GcByteArray* makeByteArray(Thread* t, const char* format, ...);

GcString* makeString(Thread* t, const char* format, ...);

#ifndef HAVE_StringOffset

inline uint32_t GcString::length(Thread* t)
{
  return cast<GcCharArray>(t, this->data())->length();
}

inline uint32_t GcString::offset(Thread*)
{
  return 0;
}

#ifndef HAVE_StringHash32

inline GcString* makeString(Thread* t, object data, int32_t hash, int32_t)
{
  return makeString(t, data, hash);
}

#endif  // not HAVE_StringHash32

inline GcString* makeString(Thread* t,
                            object odata,
                            unsigned offset,
                            unsigned length,
                            unsigned)
{
  GcCharArray* data = cast<GcCharArray>(t, odata);
  if (offset == 0 and length == data->length()) {
    return makeString(t, reinterpret_cast<object>(data), 0, 0);
  } else {
    PROTECT(t, data);

    GcCharArray* array = makeCharArray(t, length);

    memcpy(array->body().begin(), &data->body()[offset], length * 2);

    return makeString(t, reinterpret_cast<object>(array), 0, 0);
  }
}

#endif  // not HAVE_StringOffset

int stringUTFLength(Thread* t,
                    GcString* string,
                    unsigned start,
                    unsigned length);

inline int stringUTFLength(Thread* t, GcString* string)
{
  return stringUTFLength(t, string, 0, string->length(t));
}

void stringChars(Thread* t,
                 GcString* string,
                 unsigned start,
                 unsigned length,
                 char* chars);

inline void stringChars(Thread* t, GcString* string, char* chars)
{
  stringChars(t, string, 0, string->length(t), chars);
}

void stringChars(Thread* t,
                 GcString* string,
                 unsigned start,
                 unsigned length,
                 uint16_t* chars);

inline void stringChars(Thread* t, GcString* string, uint16_t* chars)
{
  stringChars(t, string, 0, string->length(t), chars);
}

void stringUTFChars(Thread* t,
                    GcString* string,
                    unsigned start,
                    unsigned length,
                    char* chars,
                    unsigned charsLength);

inline void stringUTFChars(Thread* t,
                           GcString* string,
                           char* chars,
                           unsigned charsLength)
{
  stringUTFChars(t, string, 0, string->length(t), chars, charsLength);
}

bool isAssignableFrom(Thread* t, GcClass* a, GcClass* b);

GcMethod* classInitializer(Thread* t, GcClass* class_);

object frameMethod(Thread* t, int frame);

inline uintptr_t& extendedWord(Thread* t UNUSED, object o, unsigned baseSize)
{
  assertT(t, objectExtended(t, o));
  return fieldAtOffset<uintptr_t>(o, baseSize * BytesPerWord);
}

inline unsigned extendedSize(Thread* t, object o, unsigned baseSize)
{
  return baseSize + objectExtended(t, o);
}

inline void markHashTaken(Thread* t, object o)
{
  assertT(t, not objectExtended(t, o));
  assertT(t, not objectFixed(t, o));

  ACQUIRE_RAW(t, t->m->heapLock);

  alias(o, 0) |= HashTakenMark;
  t->m->heap->pad(o);
}

inline uint32_t takeHash(Thread*, object o)
{
  // some broken code implicitly relies on System.identityHashCode
  // always returning a non-negative number (e.g. old versions of
  // com/sun/xml/bind/v2/util/CollisionCheckStack.hash), hence the "&
  // 0x7FFFFFFF":
  return (reinterpret_cast<uintptr_t>(o) / BytesPerWord) & 0x7FFFFFFF;
}

inline uint32_t objectHash(Thread* t, object o)
{
  if (objectExtended(t, o)) {
    return extendedWord(t, o, baseSize(t, o, objectClass(t, o)));
  } else {
    if (not objectFixed(t, o)) {
      markHashTaken(t, o);
    }
    return takeHash(t, o);
  }
}

inline bool objectEqual(Thread*, object a, object b)
{
  return a == b;
}

inline uint32_t byteArrayHash(Thread* t UNUSED, object ao)
{
  GcByteArray* a = cast<GcByteArray>(t, ao);
  return hash(a->body());
}

inline uint32_t charArrayHash(Thread* t UNUSED, object ao)
{
  GcByteArray* a = cast<GcByteArray>(t, ao);
  return hash(a->body());
}

inline bool byteArrayEqual(Thread* t UNUSED, object ao, object bo)
{
  GcByteArray* a = cast<GcByteArray>(t, ao);
  GcByteArray* b = cast<GcByteArray>(t, bo);
  return a == b
         or ((a->length() == b->length())
             and memcmp(a->body().begin(), b->body().begin(), a->length())
                 == 0);
}

inline uint32_t stringHash(Thread* t, object so)
{
  GcString* s = cast<GcString>(t, so);
  if (s->hashCode() == 0 and s->length(t)) {
    if (objectClass(t, s->data()) == type(t, GcByteArray::Type)) {
      s->hashCode() = hash(cast<GcByteArray>(t, s->data())->body().subslice(
          s->offset(t), s->length(t)));
    } else {
      s->hashCode() = hash(cast<GcCharArray>(t, s->data())->body().subslice(
          s->offset(t), s->length(t)));
    }
  }
  return s->hashCode();
}

inline uint16_t stringCharAt(Thread* t, GcString* s, int i)
{
  if (objectClass(t, s->data()) == type(t, GcByteArray::Type)) {
    return cast<GcByteArray>(t, s->data())->body()[s->offset(t) + i];
  } else {
    return cast<GcCharArray>(t, s->data())->body()[s->offset(t) + i];
  }
}

inline bool stringEqual(Thread* t, object ao, object bo)
{
  GcString* a = cast<GcString>(t, ao);
  GcString* b = cast<GcString>(t, bo);
  if (a == b) {
    return true;
  } else if (a->length(t) == b->length(t)) {
    for (unsigned i = 0; i < a->length(t); ++i) {
      if (stringCharAt(t, a, i) != stringCharAt(t, b, i)) {
        return false;
      }
    }
    return true;
  } else {
    return false;
  }
}

inline uint32_t methodHash(Thread* t, object mo)
{
  GcMethod* method = cast<GcMethod>(t, mo);
  return byteArrayHash(t, method->name()) ^ byteArrayHash(t, method->spec());
}

inline bool methodEqual(Thread* t, object ao, object bo)
{
  GcMethod* a = cast<GcMethod>(t, ao);
  GcMethod* b = cast<GcMethod>(t, bo);
  return a == b or (byteArrayEqual(t, a->name(), b->name())
                    and byteArrayEqual(t, a->spec(), b->spec()));
}

class MethodSpecIterator {
 public:
  MethodSpecIterator(Thread* t, const char* s) : t(t), s(s + 1)
  {
  }

  const char* next()
  {
    assertT(t, *s != ')');

    const char* p = s;

    switch (*s) {
    case 'L':
      while (*s and *s != ';')
        ++s;
      ++s;
      break;

    case '[':
      while (*s == '[')
        ++s;
      switch (*s) {
      case 'L':
        while (*s and *s != ';')
          ++s;
        ++s;
        break;

      default:
        ++s;
        break;
      }
      break;

    default:
      ++s;
      break;
    }

    return p;
  }

  bool hasNext()
  {
    return *s != ')';
  }

  const char* returnSpec()
  {
    assertT(t, *s == ')');
    return s + 1;
  }

  Thread* t;
  const char* s;
};

unsigned fieldCode(Thread* t, unsigned javaCode);

unsigned fieldType(Thread* t, unsigned code);

unsigned primitiveSize(Thread* t, unsigned code);

inline unsigned fieldSize(Thread* t, unsigned code)
{
  if (code == ObjectField) {
    return BytesPerWord;
  } else {
    return primitiveSize(t, code);
  }
}

inline unsigned fieldSize(Thread* t, GcField* field)
{
  return fieldSize(t, field->code());
}

inline void scanMethodSpec(Thread* t,
                           const char* s,
                           bool static_,
                           unsigned* parameterCount,
                           unsigned* parameterFootprint,
                           unsigned* returnCode)
{
  unsigned count = 0;
  unsigned footprint = 0;
  MethodSpecIterator it(t, s);
  while (it.hasNext()) {
    ++count;
    switch (*it.next()) {
    case 'J':
    case 'D':
      footprint += 2;
      break;

    default:
      ++footprint;
      break;
    }
  }

  if (not static_) {
    ++footprint;
  }

  *parameterCount = count;
  *parameterFootprint = footprint;
  *returnCode = fieldCode(t, *it.returnSpec());
}

GcClass* findLoadedClass(Thread* t, GcClassLoader* loader, GcByteArray* spec);

GcJclass* getDeclaringClass(Thread* t, GcClass* c);

GcCallSite* resolveDynamic(Thread* t, GcInvocation* invocation);

inline bool emptyMethod(Thread* t UNUSED, GcMethod* method)
{
  return ((method->flags() & ACC_NATIVE) == 0)
         and (method->code()->length() == 1)
         and (method->code()->body()[0] == return_);
}

object parseUtf8(Thread* t, const char* data, unsigned length);

object parseUtf8(Thread* t, GcByteArray* array);

GcClass* parseClass(Thread* t,
                    GcClassLoader* loader,
                    const uint8_t* data,
                    unsigned length,
                    Gc::Type throwType = GcNoClassDefFoundError::Type);

GcClass* resolveClass(Thread* t,
                      GcClassLoader* loader,
                      GcByteArray* name,
                      bool throw_ = true,
                      Gc::Type throwType = GcNoClassDefFoundError::Type);

inline GcClass* resolveClass(Thread* t,
                             GcClassLoader* loader,
                             const char* name,
                             bool throw_ = true,
                             Gc::Type throwType = GcNoClassDefFoundError::Type)
{
  PROTECT(t, loader);
  GcByteArray* n = makeByteArray(t, "%s", name);
  return resolveClass(t, loader, n, throw_, throwType);
}

GcClass* resolveSystemClass(Thread* t,
                            GcClassLoader* loader,
                            GcByteArray* name,
                            bool throw_ = true,
                            Gc::Type throwType = GcNoClassDefFoundError::Type);

inline GcClass* resolveSystemClass(Thread* t,
                                   GcClassLoader* loader,
                                   const char* name)
{
  return resolveSystemClass(t, loader, makeByteArray(t, "%s", name));
}

void linkClass(Thread* t, GcClassLoader* loader, GcClass* class_);

GcMethod* resolveMethod(Thread* t,
                        GcClass* class_,
                        const char* methodName,
                        const char* methodSpec);

inline GcMethod* resolveMethod(Thread* t,
                               GcClassLoader* loader,
                               const char* className,
                               const char* methodName,
                               const char* methodSpec)
{
  return resolveMethod(
      t, resolveClass(t, loader, className), methodName, methodSpec);
}

GcField* resolveField(Thread* t,
                      GcClass* class_,
                      const char* fieldName,
                      const char* fieldSpec);

inline GcField* resolveField(Thread* t,
                             GcClassLoader* loader,
                             const char* className,
                             const char* fieldName,
                             const char* fieldSpec)
{
  return resolveField(
      t, resolveClass(t, loader, className), fieldName, fieldSpec);
}

bool classNeedsInit(Thread* t, GcClass* c);

bool preInitClass(Thread* t, GcClass* c);

void postInitClass(Thread* t, GcClass* c);

void initClass(Thread* t, GcClass* c);

GcClass* resolveObjectArrayClass(Thread* t,
                                 GcClassLoader* loader,
                                 GcClass* elementClass);

object makeObjectArray(Thread* t, GcClass* elementClass, unsigned count);

inline object makeObjectArray(Thread* t, unsigned count)
{
  return makeObjectArray(t, type(t, GcJobject::Type), count);
}

object findFieldInClass(Thread* t,
                        GcClass* class_,
                        GcByteArray* name,
                        GcByteArray* spec);

inline GcField* findFieldInClass2(Thread* t,
                                  GcClass* class_,
                                  const char* name,
                                  const char* spec)
{
  PROTECT(t, class_);
  GcByteArray* n = makeByteArray(t, "%s", name);
  PROTECT(t, n);
  GcByteArray* s = makeByteArray(t, "%s", spec);
  return cast<GcField>(t, findFieldInClass(t, class_, n, s));
}

object findMethodInClass(Thread* t,
                         GcClass* class_,
                         GcByteArray* name,
                         GcByteArray* spec);

inline GcThrowable* makeThrowable(Thread* t,
                                  Gc::Type type,
                                  GcString* message = 0,
                                  object trace = 0,
                                  GcThrowable* cause = 0)
{
  PROTECT(t, message);
  PROTECT(t, trace);
  PROTECT(t, cause);

  if (trace == 0) {
    trace = makeTrace(t);
  }

  GcThrowable* result = cast<GcThrowable>(t, make(t, vm::type(t, type)));

  result->setMessage(t, message);
  result->setTrace(t, trace);
  result->setCause(t, cause);

  return result;
}

inline GcThrowable* makeThrowableV(Thread* t,
                                   Gc::Type type,
                                   const char* format,
                                   va_list a,
                                   int size)
{
  GcByteArray* s = makeByteArrayV(t, format, a, size);

  if (s) {
    GcString* message = t->m->classpath->makeString(t, s, 0, s->length() - 1);

    return makeThrowable(t, type, message);
  } else {
    return 0;
  }
}

inline GcThrowable* makeThrowable(Thread* t,
                                  Gc::Type type,
                                  const char* format,
                                  ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    GcThrowable* r = makeThrowableV(t, type, format, a, size);
    va_end(a);

    if (r) {
      return r;
    } else {
      size *= 2;
    }
  }
}

void popResources(Thread* t);

}  // namespace vm

AVIAN_EXPORT void vmPrintTrace(vm::Thread* t);

AVIAN_EXPORT void vmfPrintTrace(vm::Thread* t, FILE* out);

namespace vm {

void dumpHeap(Thread* t, FILE* out);

inline void NO_RETURN throw_(Thread* t, GcThrowable* e)
{
  assertT(t, t->exception == 0);
  assertT(t, e);

  expect(t, not t->checkpoint->noThrow);

  t->exception = e;

  if (objectClass(t, e) == type(t, GcOutOfMemoryError::Type)) {
    if (not t->m->dumpedHeapOnOOM) {
      t->m->dumpedHeapOnOOM = true;
      const char* path = findProperty(t, "avian.heap.dump");
      if (path) {
        FILE* out = vm::fopen(path, "wb");
        if (out) {
          dumpHeap(t, out);
          fclose(out);
        }
      }
    }

    if (AbortOnOutOfMemoryError) {
      fprintf(stderr, "OutOfMemoryError\n");
      vmPrintTrace(t);
      abort();
    }
  }

  // printTrace(t, e);

  popResources(t);

  t->checkpoint->unwind();

  abort(t);
}

inline void NO_RETURN throwNew(Thread* t,
                               Gc::Type type,
                               GcString* message = 0,
                               object trace = 0,
                               GcThrowable* cause = 0)
{
  throw_(t, makeThrowable(t, type, message, trace, cause));
}

inline void NO_RETURN
    throwNew(Thread* t, Gc::Type type, const char* format, ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    GcThrowable* r = makeThrowableV(t, type, format, a, size);
    va_end(a);

    if (r) {
      throw_(t, r);
    } else {
      size *= 2;
    }
  }
}

object findInHierarchyOrNull(
    Thread* t,
    GcClass* class_,
    GcByteArray* name,
    GcByteArray* spec,
    object (*find)(Thread*, GcClass*, GcByteArray*, GcByteArray*));

inline object findInHierarchy(
    Thread* t,
    GcClass* class_,
    GcByteArray* name,
    GcByteArray* spec,
    object (*find)(Thread*, GcClass*, GcByteArray*, GcByteArray*),
    Gc::Type errorType,
    bool throw_ = true)
{
  object o = findInHierarchyOrNull(t, class_, name, spec, find);

  if (throw_ and o == 0) {
    throwNew(t,
             errorType,
             "%s %s not found in %s",
             name->body().begin(),
             spec->body().begin(),
             class_->name()->body().begin());
  }

  return o;
}

inline GcMethod* findMethod(Thread* t,
                            GcClass* class_,
                            GcByteArray* name,
                            GcByteArray* spec)
{
  return cast<GcMethod>(
      t,
      findInHierarchy(
          t, class_, name, spec, findMethodInClass, GcNoSuchMethodError::Type));
}

inline GcMethod* findMethodOrNull(Thread* t,
                                  GcClass* class_,
                                  const char* name,
                                  const char* spec)
{
  PROTECT(t, class_);
  GcByteArray* n = makeByteArray(t, "%s", name);
  PROTECT(t, n);
  GcByteArray* s = makeByteArray(t, "%s", spec);
  return cast<GcMethod>(
      t, findInHierarchyOrNull(t, class_, n, s, findMethodInClass));
}

inline GcMethod* findVirtualMethod(Thread* t, GcMethod* method, GcClass* class_)
{
  return cast<GcMethod>(
      t, cast<GcArray>(t, class_->virtualTable())->body()[method->offset()]);
}

inline GcMethod* findInterfaceMethod(Thread* t,
                                     GcMethod* method,
                                     GcClass* class_)
{
  if (UNLIKELY(class_->vmFlags() & BootstrapFlag)) {
    PROTECT(t, method);
    PROTECT(t, class_);

    resolveSystemClass(t, roots(t)->bootLoader(), class_->name());
  }

  GcClass* interface = method->class_();
  GcArray* itable = cast<GcArray>(t, class_->interfaceTable());
  for (unsigned i = 0; i < itable->length(); i += 2) {
    if (itable->body()[i] == interface) {
      return cast<GcMethod>(
          t, cast<GcArray>(t, itable->body()[i + 1])->body()[method->offset()]);
    }
  }
  abort(t);
}

inline unsigned objectArrayLength(Thread* t UNUSED, object array)
{
  assertT(t, objectClass(t, array)->fixedSize() == BytesPerWord * 2);
  assertT(t, objectClass(t, array)->arrayElementSize() == BytesPerWord);
  return fieldAtOffset<uintptr_t>(array, BytesPerWord);
}

inline object& objectArrayBody(Thread* t UNUSED, object array, unsigned index)
{
  assertT(t, objectClass(t, array)->fixedSize() == BytesPerWord * 2);
  assertT(t, objectClass(t, array)->arrayElementSize() == BytesPerWord);
  assertT(
      t,
      objectClass(t, array)->objectMask()
      == cast<GcClass>(t, t->m->types->body()[GcArray::Type])->objectMask());
  return fieldAtOffset<object>(array, ArrayBody + (index * BytesPerWord));
}

unsigned parameterFootprint(Thread* t, const char* s, bool static_);

void addFinalizer(Thread* t, object target, void (*finalize)(Thread*, object));

inline bool acquireSystem(Thread* t, Thread* target)
{
  ACQUIRE_RAW(t, t->m->stateLock);

  if (t->state != Thread::JoinedState) {
    target->setFlag(Thread::SystemFlag);
    return true;
  } else {
    return false;
  }
}

inline void releaseSystem(Thread* t, Thread* target)
{
  ACQUIRE_RAW(t, t->m->stateLock);

  assertT(t, t->state != Thread::JoinedState);

  target->clearFlag(Thread::SystemFlag);
}

inline bool atomicCompareAndSwapObject(Thread* t,
                                       object target,
                                       unsigned offset,
                                       object old,
                                       object new_)
{
  if (atomicCompareAndSwap(&fieldAtOffset<uintptr_t>(target, offset),
                           reinterpret_cast<uintptr_t>(old),
                           reinterpret_cast<uintptr_t>(new_))) {
    mark(t, target, offset);
    return true;
  } else {
    return false;
  }
}

// The following two methods (monitorAtomicAppendAcquire and
// monitorAtomicPollAcquire) use the Michael and Scott Non-Blocking
// Queue Algorithm: http://www.cs.rochester.edu/u/michael/PODC96.html

inline void monitorAtomicAppendAcquire(Thread* t,
                                       GcMonitor* monitor,
                                       GcMonitorNode* node)
{
  if (node == 0) {
    PROTECT(t, monitor);

    node = makeMonitorNode(t, t, 0);
  }

  while (true) {
    GcMonitorNode* tail = cast<GcMonitorNode>(t, monitor->acquireTail());

    loadMemoryBarrier();

    object next = tail->next();

    loadMemoryBarrier();

    if (tail == cast<GcMonitorNode>(t, monitor->acquireTail())) {
      if (next) {
        atomicCompareAndSwapObject(t, monitor, MonitorAcquireTail, tail, next);
      } else if (atomicCompareAndSwapObject(
                     t, tail, MonitorNodeNext, 0, node)) {
        atomicCompareAndSwapObject(t, monitor, MonitorAcquireTail, tail, node);
        return;
      }
    }
  }
}

inline Thread* monitorAtomicPollAcquire(Thread* t,
                                        GcMonitor* monitor,
                                        bool remove)
{
  while (true) {
    GcMonitorNode* head = cast<GcMonitorNode>(t, monitor->acquireHead());

    loadMemoryBarrier();

    GcMonitorNode* tail = cast<GcMonitorNode>(t, monitor->acquireTail());

    loadMemoryBarrier();

    GcMonitorNode* next = cast<GcMonitorNode>(t, head->next());

    loadMemoryBarrier();

    if (head == cast<GcMonitorNode>(t, monitor->acquireHead())) {
      if (head == tail) {
        if (next) {
          atomicCompareAndSwapObject(
              t, monitor, MonitorAcquireTail, tail, next);
        } else {
          return 0;
        }
      } else {
        Thread* value = static_cast<Thread*>(next->value());
        if ((not remove) or atomicCompareAndSwapObject(
                                t, monitor, MonitorAcquireHead, head, next)) {
          return value;
        }
      }
    }
  }
}

inline bool monitorTryAcquire(Thread* t, GcMonitor* monitor)
{
  if (monitor->owner() == t
      or (monitorAtomicPollAcquire(t, monitor, false) == 0
          and atomicCompareAndSwap(
                  reinterpret_cast<uintptr_t*>(&monitor->owner()),
                  0,
                  reinterpret_cast<uintptr_t>(t)))) {
    ++monitor->depth();
    return true;
  } else {
    return false;
  }
}

inline void monitorAcquire(Thread* t,
                           GcMonitor* monitor,
                           GcMonitorNode* node = 0)
{
  if (not monitorTryAcquire(t, monitor)) {
    PROTECT(t, monitor);
    PROTECT(t, node);

    ACQUIRE(t, t->lock);

    monitorAtomicAppendAcquire(t, monitor, node);

    // note that we don't try to acquire the lock until we're first in
    // line, both because it's fair and because we don't support
    // removing elements from arbitrary positions in the queue

    while (not(t == monitorAtomicPollAcquire(t, monitor, false)
               and atomicCompareAndSwap(
                       reinterpret_cast<uintptr_t*>(&monitor->owner()),
                       0,
                       reinterpret_cast<uintptr_t>(t)))) {
      ENTER(t, Thread::IdleState);

      t->lock->wait(t->systemThread, 0);
    }

    expect(t, t == monitorAtomicPollAcquire(t, monitor, true));

    ++monitor->depth();
  }

  assertT(t, monitor->owner() == t);
}

inline void monitorRelease(Thread* t, GcMonitor* monitor)
{
  expect(t, monitor->owner() == t);

  if (--monitor->depth() == 0) {
    monitor->owner() = 0;

    storeLoadMemoryBarrier();

    Thread* next = monitorAtomicPollAcquire(t, monitor, false);

    if (next and acquireSystem(t, next)) {
      ACQUIRE(t, next->lock);

      next->lock->notify(t->systemThread);

      releaseSystem(t, next);
    }
  }
}

inline void monitorAppendWait(Thread* t, GcMonitor* monitor)
{
  assertT(t, monitor->owner() == t);

  expect(t, (t->getFlags() & Thread::WaitingFlag) == 0);
  expect(t, t->waitNext == 0);

  t->setFlag(Thread::WaitingFlag);

  if (monitor->waitTail()) {
    static_cast<Thread*>(monitor->waitTail())->waitNext = t;
  } else {
    monitor->waitHead() = t;
  }

  monitor->waitTail() = t;
}

inline void monitorRemoveWait(Thread* t, GcMonitor* monitor)
{
  assertT(t, monitor->owner() == t);

  Thread* previous = 0;
  for (Thread* current = static_cast<Thread*>(monitor->waitHead()); current;
       current = current->waitNext) {
    if (t == current) {
      if (t == monitor->waitHead()) {
        monitor->waitHead() = t->waitNext;
      } else {
        previous->waitNext = t->waitNext;
      }

      if (t == monitor->waitTail()) {
        assertT(t, t->waitNext == 0);
        monitor->waitTail() = previous;
      }

      t->waitNext = 0;
      t->clearFlag(Thread::WaitingFlag);

      return;
    } else {
      previous = current;
    }
  }

  abort(t);
}

inline bool monitorFindWait(Thread* t, GcMonitor* monitor)
{
  assertT(t, monitor->owner() == t);

  for (Thread* current = static_cast<Thread*>(monitor->waitHead()); current;
       current = current->waitNext) {
    if (t == current) {
      return true;
    }
  }

  return false;
}

inline bool monitorWait(Thread* t, GcMonitor* monitor, int64_t time)
{
  expect(t, monitor->owner() == t);

  bool interrupted;
  unsigned depth;

  PROTECT(t, monitor);

  // pre-allocate monitor node so we don't get an OutOfMemoryError
  // when we try to re-acquire the monitor below
  GcMonitorNode* monitorNode = makeMonitorNode(t, t, 0);
  PROTECT(t, monitorNode);

  {
    ACQUIRE(t, t->lock);

    monitorAppendWait(t, monitor);

    depth = monitor->depth();
    monitor->depth() = 1;

    monitorRelease(t, monitor);

    ENTER(t, Thread::IdleState);

    interrupted = t->lock->waitAndClearInterrupted(t->systemThread, time);
  }

  monitorAcquire(t, monitor, monitorNode);

  monitor->depth() = depth;

  if (t->getFlags() & Thread::WaitingFlag) {
    monitorRemoveWait(t, monitor);
  } else {
    expect(t, not monitorFindWait(t, monitor));
  }

  assertT(t, monitor->owner() == t);

  return interrupted;
}

inline Thread* monitorPollWait(Thread* t UNUSED, GcMonitor* monitor)
{
  assertT(t, monitor->owner() == t);

  Thread* next = static_cast<Thread*>(monitor->waitHead());

  if (next) {
    monitor->waitHead() = next->waitNext;
    next->clearFlag(Thread::WaitingFlag);
    next->waitNext = 0;
    if (next == monitor->waitTail()) {
      monitor->waitTail() = 0;
    }
  } else {
    assertT(t, monitor->waitTail() == 0);
  }

  return next;
}

inline bool monitorNotify(Thread* t, GcMonitor* monitor)
{
  expect(t, monitor->owner() == t);

  Thread* next = monitorPollWait(t, monitor);

  if (next) {
    ACQUIRE(t, next->lock);

    next->lock->notify(t->systemThread);

    return true;
  } else {
    return false;
  }
}

inline void monitorNotifyAll(Thread* t, GcMonitor* monitor)
{
  PROTECT(t, monitor);

  while (monitorNotify(t, monitor)) {
  }
}

class ObjectMonitorResource {
 public:
  ObjectMonitorResource(Thread* t, GcMonitor* o)
      : o(o), protector(t, &(this->o))
  {
    monitorAcquire(protector.t, o);
  }

  ~ObjectMonitorResource()
  {
    monitorRelease(protector.t, o);
  }

 private:
  GcMonitor* o;
  Thread::SingleProtector protector;
};

GcMonitor* objectMonitor(Thread* t, object o, bool createNew);

inline void acquire(Thread* t, object o)
{
  unsigned hash;
  if (DebugMonitors) {
    hash = objectHash(t, o);
  }

  GcMonitor* m = objectMonitor(t, o, true);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p acquires %p for %x\n", t, m, hash);
  }

  monitorAcquire(t, m);
}

inline void release(Thread* t, object o)
{
  unsigned hash;
  if (DebugMonitors) {
    hash = objectHash(t, o);
  }

  GcMonitor* m = objectMonitor(t, o, false);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p releases %p for %x\n", t, m, hash);
  }

  monitorRelease(t, m);
}

inline void wait(Thread* t, object o, int64_t milliseconds)
{
  unsigned hash;
  if (DebugMonitors) {
    hash = objectHash(t, o);
  }

  GcMonitor* m = objectMonitor(t, o, false);

  if (DebugMonitors) {
    fprintf(stderr,
            "thread %p waits %d millis on %p for %x\n",
            t,
            static_cast<int>(milliseconds),
            m,
            hash);
  }

  if (m and m->owner() == t) {
    PROTECT(t, m);

    bool interrupted = monitorWait(t, m, milliseconds);

    if (interrupted) {
      if (t->m->alive or (t->getFlags() & Thread::DaemonFlag) == 0) {
        t->m->classpath->clearInterrupted(t);
        throwNew(t, GcInterruptedException::Type);
      } else {
        throw_(t, roots(t)->shutdownInProgress());
      }
    }
  } else {
    throwNew(t, GcIllegalMonitorStateException::Type);
  }

  if (DebugMonitors) {
    fprintf(stderr, "thread %p wakes up on %p for %x\n", t, m, hash);
  }

  stress(t);
}

inline void notify(Thread* t, object o)
{
  unsigned hash;
  if (DebugMonitors) {
    hash = objectHash(t, o);
  }

  GcMonitor* m = objectMonitor(t, o, false);

  if (DebugMonitors) {
    fprintf(stderr, "thread %p notifies on %p for %x\n", t, m, hash);
  }

  if (m and m->owner() == t) {
    monitorNotify(t, m);
  } else {
    throwNew(t, GcIllegalMonitorStateException::Type);
  }
}

inline void notifyAll(Thread* t, object o)
{
  GcMonitor* m = objectMonitor(t, o, false);

  if (DebugMonitors) {
    fprintf(stderr,
            "thread %p notifies all on %p for %x\n",
            t,
            m,
            objectHash(t, o));
  }

  if (m and m->owner() == t) {
    monitorNotifyAll(t, m);
  } else {
    throwNew(t, GcIllegalMonitorStateException::Type);
  }
}

inline void interrupt(Thread* t, Thread* target)
{
  if (acquireSystem(t, target)) {
    target->systemThread->interrupt();
    releaseSystem(t, target);
  }
}

inline bool getAndClearInterrupted(Thread* t, Thread* target)
{
  if (acquireSystem(t, target)) {
    bool result = target->systemThread->getAndClearInterrupted();
    releaseSystem(t, target);
    return result;
  } else {
    return false;
  }
}

inline bool exceptionMatch(Thread* t, GcClass* type, GcThrowable* exception)
{
  return type == 0 or (exception != roots(t)->shutdownInProgress()
                       and instanceOf(t, type, t->exception));
}

object intern(Thread* t, object s);

object clone(Thread* t, object o);

void walk(Thread* t, Heap::Walker* w, object o, unsigned start);

int walkNext(Thread* t, object o, int previous);

void visitRoots(Machine* m, Heap::Visitor* v);

inline jobject makeLocalReference(Thread* t, object o)
{
  return t->m->processor->makeLocalReference(t, o);
}

inline void disposeLocalReference(Thread* t, jobject r)
{
  t->m->processor->disposeLocalReference(t, r);
}

inline bool methodVirtual(Thread* t UNUSED, GcMethod* method)
{
  return (method->flags() & (ACC_STATIC | ACC_PRIVATE)) == 0
         and method->name()->body()[0] != '<';
}

inline unsigned singletonMaskSize(unsigned count, unsigned bitsPerWord)
{
  if (count) {
    return ceilingDivide(count + 2, bitsPerWord);
  }
  return 0;
}

inline unsigned singletonMaskSize(unsigned count)
{
  return singletonMaskSize(count, BitsPerWord);
}

inline unsigned singletonMaskSize(Thread* t UNUSED, GcSingleton* singleton)
{
  unsigned length = singleton->length();
  if (length) {
    return ceilingDivide(length + 2, BitsPerWord + 1);
  }
  return 0;
}

inline unsigned singletonCount(Thread* t, GcSingleton* singleton)
{
  return singleton->length() - singletonMaskSize(t, singleton);
}

inline uint32_t* singletonMask(Thread* t UNUSED, GcSingleton* singleton)
{
  assertT(t, singleton->length());
  return reinterpret_cast<uint32_t*>(
      &singleton->body()[singletonCount(t, singleton)]);
}

inline void singletonMarkObject(uint32_t* mask, unsigned index)
{
  mask[(index + 2) / 32] |= (static_cast<uint32_t>(1) << ((index + 2) % 32));
}

inline void singletonMarkObject(Thread* t,
                                GcSingleton* singleton,
                                unsigned index)
{
  singletonMarkObject(singletonMask(t, singleton), index);
}

inline bool singletonIsObject(Thread* t, GcSingleton* singleton, unsigned index)
{
  assertT(t, index < singletonCount(t, singleton));

  return (singletonMask(t, singleton)[(index + 2) / 32]
          & (static_cast<uint32_t>(1) << ((index + 2) % 32))) != 0;
}

inline object& singletonObject(Thread* t UNUSED,
                               GcSingleton* singleton,
                               unsigned index)
{
  assertT(t, singletonIsObject(t, singleton, index));
  return reinterpret_cast<object&>(singleton->body()[index]);
}

inline uintptr_t& singletonValue(Thread* t UNUSED,
                                 GcSingleton* singleton,
                                 unsigned index)
{
  assertT(t, not singletonIsObject(t, singleton, index));
  return singleton->body()[index];
}

inline GcSingleton* makeSingletonOfSize(Thread* t, unsigned count)
{
  GcSingleton* o = makeSingleton(t, count + singletonMaskSize(count));
  assertT(t, o->length() == count + singletonMaskSize(t, o));
  if (count) {
    singletonMask(t, o)[0] = 1;
  }
  return o;
}

inline void singletonSetBit(Thread* t,
                            GcSingleton* singleton,
                            unsigned start,
                            unsigned index)
{
  singletonValue(t, singleton, start + (index / BitsPerWord))
      |= static_cast<uintptr_t>(1) << (index % BitsPerWord);
}

inline bool singletonBit(Thread* t,
                         GcSingleton* singleton,
                         unsigned start,
                         unsigned index)
{
  return (singletonValue(t, singleton, start + (index / BitsPerWord))
          & (static_cast<uintptr_t>(1) << (index % BitsPerWord))) != 0;
}

inline unsigned poolMaskSize(unsigned count, unsigned bitsPerWord)
{
  return ceilingDivide(count, bitsPerWord);
}

inline unsigned poolMaskSize(unsigned count)
{
  return poolMaskSize(count, BitsPerWord);
}

inline unsigned poolMaskSize(Thread* t, GcSingleton* pool)
{
  return ceilingDivide(singletonCount(t, pool), BitsPerWord + 1);
}

inline unsigned poolSize(Thread* t, GcSingleton* pool)
{
  return singletonCount(t, pool) - poolMaskSize(t, pool);
}

inline GcClass* resolveClassInObject(Thread* t,
                                     GcClassLoader* loader,
                                     object container,
                                     unsigned classOffset,
                                     bool throw_ = true)
{
  object o = fieldAtOffset<object>(container, classOffset);

  loadMemoryBarrier();

  if (objectClass(t, o) == type(t, GcByteArray::Type)) {
    GcByteArray* name = cast<GcByteArray>(t, o);
    PROTECT(t, container);

    GcClass* c = resolveClass(t, loader, name, throw_);

    if (c) {
      storeStoreMemoryBarrier();

      setField(t, container, classOffset, c);
    }

    return c;
  }
  return cast<GcClass>(t, o);
}

inline GcClass* resolveClassInPool(Thread* t,
                                   GcClassLoader* loader,
                                   GcMethod* method,
                                   unsigned index,
                                   bool throw_ = true)
{
  object o = singletonObject(t, method->code()->pool(), index);

  loadMemoryBarrier();

  if (objectClass(t, o) == type(t, GcReference::Type)) {
    PROTECT(t, method);

    GcClass* c
        = resolveClass(t, loader, cast<GcReference>(t, o)->name(), throw_);

    if (c) {
      storeStoreMemoryBarrier();

      method->code()->pool()->setBodyElement(
          t, index, reinterpret_cast<uintptr_t>(c));
    }
    return c;
  }
  return cast<GcClass>(t, o);
}

inline GcClass* resolveClassInPool(Thread* t,
                                   GcMethod* method,
                                   unsigned index,
                                   bool throw_ = true)
{
  return resolveClassInPool(
      t, method->class_()->loader(), method, index, throw_);
}

inline object resolve(
    Thread* t,
    GcClassLoader* loader,
    GcSingleton* pool,
    unsigned index,
    object (*find)(vm::Thread*, GcClass*, GcByteArray*, GcByteArray*),
    Gc::Type errorType,
    bool throw_ = true)
{
  object o = singletonObject(t, pool, index);

  loadMemoryBarrier();

  if (objectClass(t, o) == type(t, GcReference::Type)) {
    PROTECT(t, pool);

    GcReference* reference = cast<GcReference>(t, o);
    PROTECT(t, reference);

    GcClass* class_
        = resolveClassInObject(t, loader, o, ReferenceClass, throw_);

    if (class_) {
      o = findInHierarchy(t,
                          class_,
                          reference->name(),
                          reference->spec(),
                          find,
                          errorType,
                          throw_);

      if (o) {
        storeStoreMemoryBarrier();

        pool->setBodyElement(t, index, reinterpret_cast<uintptr_t>(o));
      }
    } else {
      o = 0;
    }
  }

  return o;
}

inline GcField* resolveField(Thread* t,
                             GcClassLoader* loader,
                             GcMethod* method,
                             unsigned index,
                             bool throw_ = true)
{
  return cast<GcField>(t,
                       resolve(t,
                               loader,
                               method->code()->pool(),
                               index,
                               findFieldInClass,
                               GcNoSuchFieldError::Type,
                               throw_));
}

inline GcField* resolveField(Thread* t,
                             GcMethod* method,
                             unsigned index,
                             bool throw_ = true)
{
  return resolveField(t, method->class_()->loader(), method, index, throw_);
}

inline void acquireFieldForRead(Thread* t, GcField* field)
{
  if (UNLIKELY(
          (field->flags() & ACC_VOLATILE) and BytesPerWord == 4
          and (field->code() == DoubleField or field->code() == LongField))) {
    acquire(t, field);
  }
}

inline void releaseFieldForRead(Thread* t, GcField* field)
{
  if (UNLIKELY(field->flags() & ACC_VOLATILE)) {
    if (BytesPerWord == 4
        and (field->code() == DoubleField or field->code() == LongField)) {
      release(t, field);
    } else {
      loadMemoryBarrier();
    }
  }
}

class FieldReadResource {
 public:
  FieldReadResource(Thread* t, GcField* o) : o(o), protector(t, &(this->o))
  {
    acquireFieldForRead(protector.t, o);
  }

  ~FieldReadResource()
  {
    releaseFieldForRead(protector.t, o);
  }

 private:
  GcField* o;
  Thread::SingleProtector protector;
};

inline void acquireFieldForWrite(Thread* t, GcField* field)
{
  if (UNLIKELY(field->flags() & ACC_VOLATILE)) {
    if (BytesPerWord == 4
        and (field->code() == DoubleField or field->code() == LongField)) {
      acquire(t, field);
    } else {
      storeStoreMemoryBarrier();
    }
  }
}

inline void releaseFieldForWrite(Thread* t, GcField* field)
{
  if (UNLIKELY(field->flags() & ACC_VOLATILE)) {
    if (BytesPerWord == 4
        and (field->code() == DoubleField or field->code() == LongField)) {
      release(t, field);
    } else {
      storeLoadMemoryBarrier();
    }
  }
}

class FieldWriteResource {
 public:
  FieldWriteResource(Thread* t, GcField* o) : o(o), protector(t, &(this->o))
  {
    acquireFieldForWrite(protector.t, o);
  }

  ~FieldWriteResource()
  {
    releaseFieldForWrite(protector.t, o);
  }

 private:
  GcField* o;
  Thread::SingleProtector protector;
};

inline GcMethod* resolveMethod(Thread* t,
                               GcClassLoader* loader,
                               GcMethod* method,
                               unsigned index,
                               bool throw_ = true)
{
  return cast<GcMethod>(t,
                        resolve(t,
                                loader,
                                method->code()->pool(),
                                index,
                                findMethodInClass,
                                GcNoSuchMethodError::Type,
                                throw_));
}

inline GcMethod* resolveMethod(Thread* t,
                               GcMethod* method,
                               unsigned index,
                               bool throw_ = true)
{
  return resolveMethod(t, method->class_()->loader(), method, index, throw_);
}

GcVector* vectorAppend(Thread*, GcVector*, object);

inline GcClassRuntimeData* getClassRuntimeDataIfExists(Thread* t, GcClass* c)
{
  if (c->runtimeDataIndex()) {
    return cast<GcClassRuntimeData>(
        t,
        roots(t)->classRuntimeDataTable()->body()[c->runtimeDataIndex() - 1]);
  } else {
    return 0;
  }
}

inline GcClassRuntimeData* getClassRuntimeData(Thread* t, GcClass* c)
{
  if (c->runtimeDataIndex() == 0) {
    PROTECT(t, c);

    ACQUIRE(t, t->m->classLock);

    if (c->runtimeDataIndex() == 0) {
      GcClassRuntimeData* runtimeData = makeClassRuntimeData(t, 0, 0, 0, 0);

      {
        GcVector* v
            = vectorAppend(t, roots(t)->classRuntimeDataTable(), runtimeData);
        // sequence point, for gc (don't recombine statements)
        roots(t)->setClassRuntimeDataTable(t, v);
      }

      c->runtimeDataIndex() = roots(t)->classRuntimeDataTable()->size();
    }
  }

  return cast<GcClassRuntimeData>(
      t, roots(t)->classRuntimeDataTable()->body()[c->runtimeDataIndex() - 1]);
}

inline GcMethodRuntimeData* getMethodRuntimeData(Thread* t, GcMethod* method)
{
  int index = method->runtimeDataIndex();

  loadMemoryBarrier();

  if (index == 0) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->classLock);

    if (method->runtimeDataIndex() == 0) {
      GcMethodRuntimeData* runtimeData = makeMethodRuntimeData(t, 0);

      {
        GcVector* v
            = vectorAppend(t, roots(t)->methodRuntimeDataTable(), runtimeData);
        // sequence point, for gc (don't recombine statements)
        roots(t)->setMethodRuntimeDataTable(t, v);
      }

      storeStoreMemoryBarrier();

      method->runtimeDataIndex() = roots(t)->methodRuntimeDataTable()->size();
    }
  }

  return cast<GcMethodRuntimeData>(t,
                                   roots(t)->methodRuntimeDataTable()->body()
                                       [method->runtimeDataIndex() - 1]);
}

inline GcJclass* getJClass(Thread* t, GcClass* c)
{
  PROTECT(t, c);

  GcJclass* jclass = cast<GcJclass>(t, getClassRuntimeData(t, c)->jclass());

  loadMemoryBarrier();

  if (jclass == 0) {
    ACQUIRE(t, t->m->classLock);

    jclass = cast<GcJclass>(t, getClassRuntimeData(t, c)->jclass());
    if (jclass == 0) {
      jclass = t->m->classpath->makeJclass(t, c);

      storeStoreMemoryBarrier();

      getClassRuntimeData(t, c)->setJclass(t, jclass);
    }
  }

  return jclass;
}

inline GcClass* primitiveClass(Thread* t, char name)
{
  switch (name) {
  case 'B':
    return type(t, GcJbyte::Type);
  case 'C':
    return type(t, GcJchar::Type);
  case 'D':
    return type(t, GcJdouble::Type);
  case 'F':
    return type(t, GcJfloat::Type);
  case 'I':
    return type(t, GcJint::Type);
  case 'J':
    return type(t, GcJlong::Type);
  case 'S':
    return type(t, GcJshort::Type);
  case 'V':
    return type(t, GcJvoid::Type);
  case 'Z':
    return type(t, GcJboolean::Type);
  default:
    throwNew(t, GcIllegalArgumentException::Type);
  }
}

inline void registerNative(Thread* t, GcMethod* method, void* function)
{
  PROTECT(t, method);

  expect(t, method->flags() & ACC_NATIVE);

  GcNative* native = makeNative(t, function, false);
  PROTECT(t, native);

  GcMethodRuntimeData* runtimeData = getMethodRuntimeData(t, method);

  // ensure other threads only see the methodRuntimeDataNative field
  // populated once the object it points to has been populated:
  storeStoreMemoryBarrier();

  runtimeData->setNative(t, native);
}

inline void unregisterNatives(Thread* t, GcClass* c)
{
  GcArray* table = cast<GcArray>(t, c->methodTable());
  if (table) {
    for (unsigned i = 0; i < table->length(); ++i) {
      GcMethod* method = cast<GcMethod>(t, table->body()[i]);
      if (method->flags() & ACC_NATIVE) {
        getMethodRuntimeData(t, method)->setNative(t, 0);
      }
    }
  }
}

void populateMultiArray(Thread* t,
                        object array,
                        int32_t* counts,
                        unsigned index,
                        unsigned dimensions);

GcMethod* getCaller(Thread* t, unsigned target, bool skipMethodInvoke = false);

GcClass* defineClass(Thread* t,
                     GcClassLoader* loader,
                     const uint8_t* buffer,
                     unsigned length);

inline GcMethod* methodClone(Thread* t, GcMethod* method)
{
  return makeMethod(t,
                    method->vmFlags(),
                    method->returnCode(),
                    method->parameterCount(),
                    method->parameterFootprint(),
                    method->flags(),
                    method->offset(),
                    method->nativeID(),
                    method->runtimeDataIndex(),
                    method->name(),
                    method->spec(),
                    method->addendum(),
                    method->class_(),
                    method->code());
}

inline uint64_t exceptionHandler(uint64_t start,
                                 uint64_t end,
                                 uint64_t ip,
                                 uint64_t catchType)
{
  return (start << 48) | (end << 32) | (ip << 16) | catchType;
}

inline unsigned exceptionHandlerStart(uint64_t eh)
{
  return eh >> 48;
}

inline unsigned exceptionHandlerEnd(uint64_t eh)
{
  return (eh >> 32) & 0xFFFF;
}

inline unsigned exceptionHandlerIp(uint64_t eh)
{
  return (eh >> 16) & 0xFFFF;
}

inline unsigned exceptionHandlerCatchType(uint64_t eh)
{
  return eh & 0xFFFF;
}

inline uint64_t lineNumber(uint64_t ip, uint64_t line)
{
  return (ip << 32) | line;
}

inline unsigned lineNumberIp(uint64_t ln)
{
  return ln >> 32;
}

inline unsigned lineNumberLine(uint64_t ln)
{
  return ln & 0xFFFFFFFF;
}

object interruptLock(Thread* t, GcThread* thread);

void clearInterrupted(Thread* t);

void threadInterrupt(Thread* t, GcThread* thread);

bool threadIsInterrupted(Thread* t, GcThread* thread, bool clear);

inline FILE* errorLog(Thread* t)
{
  if (t->m->errorLog == 0) {
    const char* path = findProperty(t, "avian.error.log");
    if (path) {
      t->m->errorLog = vm::fopen(path, "wb");
    } else {
      t->m->errorLog = stderr;
    }
  }

  return t->m->errorLog;
}

}  // namespace vm

AVIAN_EXPORT void* vmAddressFromLine(vm::Thread* t,
                                     vm::object m,
                                     unsigned line);

#endif  // MACHINE_H
