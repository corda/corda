#include "jnienv.h"
#include "machine.h"
#include "constants.h"

using namespace vm;

namespace {

const uintptr_t InterfaceMethodID
= (static_cast<uintptr_t>(1) << (BitsPerWord - 1));

jint JNICALL
AttachCurrentThread(Machine* m, Thread** t, void*)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t == 0) {
    *t = new (m->system->allocate(sizeof(Thread))) Thread(m, 0, m->rootThread);

    m->localThread->set(*t);
  }
  return 0;
}

jint JNICALL
DetachCurrentThread(Machine* m)
{
  Thread* t = static_cast<Thread*>(m->localThread->get());
  if (t) {
    t->exit();
    return 0;
  } else {
    return -1;
  }
}

jint JNICALL
GetEnv(Machine* m, Thread** t, jint version)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t) {
    if (version <= JNI_VERSION_1_4) {
      return JNI_OK;
    } else {
      return JNI_EVERSION;
    }
  } else {
    return JNI_EDETACHED;
  }
}

jsize JNICALL
GetStringUTFLength(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return stringLength(t, *s);
}

const char* JNICALL
GetStringUTFChars(Thread* t, jstring s, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  char* chars = static_cast<char*>
    (t->vm->system->allocate(stringLength(t, *s) + 1));
  stringChars(t, *s, chars);

  if (isCopy) *isCopy = true;
  return chars;
}

void JNICALL
ReleaseStringUTFChars(Thread* t, jstring, const char* chars)
{
  t->vm->system->free(chars);
}

jstring JNICALL
NewStringUTF(Thread* t, const char* chars)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeString(t, "%s", chars));
}

jclass JNICALL
FindClass(Thread* t, const char* name)
{
  ENTER(t, Thread::ActiveState);

  object n = makeByteArray(t, strlen(name) + 1, false);
  memcpy(&byteArrayBody(t, n, 0), name, byteArrayLength(t, n));

  return pushReference(t, resolveClass(t, n));
}

jint JNICALL
ThrowNew(Thread* t, jclass c, const char* message)
{
  if (t->exception) {
    return -1;
  }

  ENTER(t, Thread::ActiveState);
  
  object m = 0;
  PROTECT(t, m);

  if (message) {
    m = makeString(t, "%s", message);
  }

  object trace = makeTrace(t);
  PROTECT(t, trace);

  t->exception = make(t, *c);
  set(t, throwableMessageUnsafe(t, t->exception), m);
  set(t, throwableTraceUnsafe(t, t->exception), trace);

  return 0;
}

void JNICALL
DeleteLocalRef(Thread*, jobject)
{
  // do nothing
}

jboolean JNICALL
ExceptionCheck(Thread* t)
{
  return t->exception != 0;
}

jclass JNICALL
GetObjectClass(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, objectClass(t, *o));
}

jboolean JNICALL
IsInstanceOf(Thread* t, jobject o, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return instanceOf(t, *c, *o);
}

object
findMethod(Thread* t, object class_, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object n = makeByteArray(t, "%s", name);
  PROTECT(t, n);

  object s = makeByteArray(t, "%s", spec);
  return vm::findMethod(t, class_, n, s);
}

jmethodID JNICALL
GetMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object method = findMethod(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  if (classFlags(t, *c) & ACC_INTERFACE) {
    PROTECT(t, method);

    ACQUIRE(t, t->vm->referenceLock);
    
    for (unsigned i = 0; i < vectorSize(t, t->vm->jniInterfaceTable); ++i) {
      if (method == vectorBody(t, t->vm->jniInterfaceTable, i)) {
        return i;
      }
    }

    t->vm->jniInterfaceTable
      = vectorAppend(t, t->vm->jniInterfaceTable, method);

    return (vectorSize(t, t->vm->jniInterfaceTable) - 1) | InterfaceMethodID;
  } else {
    return methodOffset(t, method) + 1;
  }
}

jmethodID JNICALL
GetStaticMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object method = findMethod(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return methodOffset(t, method) + 1;
}

inline object
getMethod(Thread* t, object o, jmethodID m)
{
  if (m & InterfaceMethodID) {
    return vectorBody(t, t->vm->jniInterfaceTable, m & (~InterfaceMethodID));
  } else {
    return arrayBody(t, classVirtualTable(t, objectClass(t, o)), m - 1);
  }
}

jobject JNICALL
CallObjectMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, run(t, getMethod(t, *o, m), *o, true, a));
}

jobject JNICALL
CallObjectMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = CallObjectMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jboolean JNICALL
CallBooleanMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  return (t->exception ? 0 : booleanValue(t, r));
}

jboolean JNICALL
CallBooleanMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jboolean r = CallBooleanMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jbyte JNICALL
CallByteMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  return (t->exception ? 0 : byteValue(t, r));
}

jbyte JNICALL
CallByteMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jbyte r = CallByteMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jchar JNICALL
CallCharMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  return (t->exception ? 0 : charValue(t, r));
}

jchar JNICALL
CallCharMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jchar r = CallCharMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jshort JNICALL
CallShortMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  return (t->exception ? 0 : shortValue(t, r));
}

jshort JNICALL
CallShortMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jshort r = CallShortMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jint JNICALL
CallIntMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jint JNICALL
CallIntMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jint r = CallIntMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jlong JNICALL
CallLongMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  return (t->exception ? 0 : longValue(t, r));
}

jlong JNICALL
CallLongMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jlong r = CallLongMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jfloat JNICALL
CallFloatMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  jint i = (t->exception ? 0 : floatValue(t, r));
  jfloat f; memcpy(&f, &i, 4);
  return f;
}

jfloat JNICALL
CallFloatMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jfloat r = CallFloatMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jdouble JNICALL
CallDoubleMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getMethod(t, *o, m), *o, true, a);
  jlong i = (t->exception ? 0 : doubleValue(t, r));
  jdouble f; memcpy(&f, &i, 4);
  return f;
}

jdouble JNICALL
CallDoubleMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jdouble r = CallDoubleMethodV(t, o, m, a);

  va_end(a);

  return r;
}

void JNICALL
CallVoidMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  run(t, getMethod(t, *o, m), *o, true, a);
}

void JNICALL
CallVoidMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  CallVoidMethodV(t, o, m, a);

  va_end(a);
}

inline object
getStaticMethod(Thread* t, object class_, jmethodID m)
{
  return arrayBody(t, classMethodTable(t, class_), m - 1);
}

jobject JNICALL
CallStaticObjectMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
}

jobject JNICALL
CallStaticObjectMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = CallStaticObjectMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jboolean JNICALL
CallStaticBooleanMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  return (t->exception ? 0 : booleanValue(t, r));
}

jboolean JNICALL
CallStaticBooleanMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jboolean r = CallStaticBooleanMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jbyte JNICALL
CallStaticByteMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  return (t->exception ? 0 : byteValue(t, r));
}

jbyte JNICALL
CallStaticByteMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jbyte r = CallStaticByteMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jchar JNICALL
CallStaticCharMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  return (t->exception ? 0 : charValue(t, r));
}

jchar JNICALL
CallStaticCharMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jchar r = CallStaticCharMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jshort JNICALL
CallStaticShortMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  return (t->exception ? 0 : shortValue(t, r));
}

jshort JNICALL
CallStaticShortMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jshort r = CallStaticShortMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jint JNICALL
CallStaticIntMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jint JNICALL
CallStaticIntMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jint r = CallStaticIntMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jlong JNICALL
CallStaticLongMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  return (t->exception ? 0 : longValue(t, r));
}

jlong JNICALL
CallStaticLongMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jlong r = CallStaticLongMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jfloat JNICALL
CallStaticFloatMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  jint i = (t->exception ? 0 : floatValue(t, r));
  jfloat f; memcpy(&f, &i, 4);
  return f;
}

jfloat JNICALL
CallStaticFloatMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jfloat r = CallStaticFloatMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jdouble JNICALL
CallStaticDoubleMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = run(t, getStaticMethod(t, *c, m), 0, true, a);
  jlong i = (t->exception ? 0 : doubleValue(t, r));
  jdouble f; memcpy(&f, &i, 4);
  return f;
}

jdouble JNICALL
CallStaticDoubleMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jdouble r = CallStaticDoubleMethodV(t, c, m, a);

  va_end(a);

  return r;
}

void JNICALL
CallStaticVoidMethodV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  run(t, getStaticMethod(t, *c, m), 0, true, a);
}

void JNICALL
CallStaticVoidMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  CallStaticVoidMethodV(t, c, m, a);

  va_end(a);
}

object
findField(Thread* t, object class_, const char* name, const char* spec)
{
  object n = makeByteArray(t, "%s", name);
  PROTECT(t, n);

  object s = makeByteArray(t, "%s", spec);
  return vm::findField(t, class_, n, s);
}

jfieldID JNICALL
GetFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object field = findField(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return fieldOffset(t, field);
}

jfieldID JNICALL
GetStaticFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object field = findField(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return fieldOffset(t, field);
}

jobject JNICALL
GetObjectField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, cast<object>(*o, field));
}

jboolean JNICALL
GetBooleanField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jboolean>(*o, field);
}

jbyte JNICALL
GetByteField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jbyte>(*o, field);
}

jchar JNICALL
GetCharField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jchar>(*o, field);
}

jshort JNICALL
GetShortField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jshort>(*o, field);
}

jint JNICALL
GetIntField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jint>(*o, field);
}

jlong JNICALL
GetLongField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jlong>(*o, field);
}

jfloat JNICALL
GetFloatField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jfloat>(*o, field);
}

jdouble JNICALL
GetDoubleField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jdouble>(*o, field);
}

void JNICALL
SetObjectField(Thread* t, jobject o, jfieldID field, jobject v)
{
  ENTER(t, Thread::ActiveState);

  set(t, cast<object>(*o, field), (v ? *v : 0));
}

void JNICALL
SetBooleanField(Thread* t, jobject o, jfieldID field, jboolean v)
{
  ENTER(t, Thread::ActiveState);

  cast<jboolean>(*o, field) = v;
}

void JNICALL
SetByteField(Thread* t, jobject o, jfieldID field, jbyte v)
{
  ENTER(t, Thread::ActiveState);

  cast<jbyte>(*o, field) = v;
}

void JNICALL
SetCharField(Thread* t, jobject o, jfieldID field, jchar v)
{
  ENTER(t, Thread::ActiveState);

  cast<jchar>(*o, field) = v;
}

void JNICALL
SetShortField(Thread* t, jobject o, jfieldID field, jshort v)
{
  ENTER(t, Thread::ActiveState);

  cast<jshort>(*o, field) = v;
}

void JNICALL
SetIntField(Thread* t, jobject o, jfieldID field, jint v)
{
  ENTER(t, Thread::ActiveState);

  cast<jint>(*o, field) = v;
}

void JNICALL
SetLongField(Thread* t, jobject o, jfieldID field, jlong v)
{
  ENTER(t, Thread::ActiveState);

  cast<jlong>(*o, field) = v;
}

void JNICALL
SetFloatField(Thread* t, jobject o, jfieldID field, jfloat v)
{
  ENTER(t, Thread::ActiveState);

  cast<jfloat>(*o, field) = v;
}

void JNICALL
SetDoubleField(Thread* t, jobject o, jfieldID field, jdouble v)
{
  ENTER(t, Thread::ActiveState);

  cast<jdouble>(*o, field) = v;
}

jobject JNICALL
GetStaticObjectField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, arrayBody(t, classStaticTable(t, *c), field));
}

jboolean JNICALL
GetStaticBooleanField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  return v ? intValue(t, v) != 0 : false;
}

jbyte JNICALL
GetStaticByteField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  return static_cast<jbyte>(v ? intValue(t, v) : 0);
}

jchar JNICALL
GetStaticCharField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  return static_cast<jchar>(v ? intValue(t, v) : 0);
}

jshort JNICALL
GetStaticShortField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  return static_cast<jshort>(v ? intValue(t, v) : 0);
}

jint JNICALL
GetStaticIntField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  return v ? intValue(t, v) : 0;
}

jlong JNICALL
GetStaticLongField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  return static_cast<jlong>(v ? longValue(t, v) : 0);
}

jfloat JNICALL
GetStaticFloatField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  jint i = v ? intValue(t, v) : 0;
  jfloat f; memcpy(&f, &i, 4);
  return f;
}

jdouble JNICALL
GetStaticDoubleField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  object v = arrayBody(t, classStaticTable(t, *c), field);
  jlong i = v ? longValue(t, v) : 0;
  jdouble f; memcpy(&f, &i, 4);
  return f;
}

void JNICALL
SetStaticObjectField(Thread* t, jclass c, jfieldID field, jobject v)
{
  ENTER(t, Thread::ActiveState);

  set(t, arrayBody(t, classStaticTable(t, *c), field), (v ? *v : 0));
}

void JNICALL
SetStaticBooleanField(Thread* t, jclass c, jfieldID field, jboolean v)
{
  ENTER(t, Thread::ActiveState);

  object o = makeInt(t, v ? 1 : 0);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticByteField(Thread* t, jclass c, jfieldID field, jbyte v)
{
  ENTER(t, Thread::ActiveState);

  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticCharField(Thread* t, jclass c, jfieldID field, jchar v)
{
  ENTER(t, Thread::ActiveState);

  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticShortField(Thread* t, jclass c, jfieldID field, jshort v)
{
  ENTER(t, Thread::ActiveState);

  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticIntField(Thread* t, jclass c, jfieldID field, jint v)
{
  ENTER(t, Thread::ActiveState);

  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticLongField(Thread* t, jclass c, jfieldID field, jlong v)
{
  ENTER(t, Thread::ActiveState);

  object o = makeLong(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticFloatField(Thread* t, jclass c, jfieldID field, jfloat v)
{
  ENTER(t, Thread::ActiveState);

  jint i; memcpy(&i, &v, 4);
  object o = makeInt(t, i);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticDoubleField(Thread* t, jclass c, jfieldID field, jdouble v)
{
  ENTER(t, Thread::ActiveState);

  jlong i; memcpy(&i, &v, 8);
  object o = makeLong(t, i);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

jobject JNICALL
NewGlobalRef(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->vm->referenceLock);
  
  t->vm->jniReferences = new (t->vm->system->allocate(sizeof(Reference)))
    Reference(*o, t->vm->jniReferences);

  return &(t->vm->jniReferences->target);
}

void JNICALL
DeleteGlobalRef(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->vm->referenceLock);
  
  for (Reference** r = &(t->vm->jniReferences); *r;) {
    if (&((*r)->target) == o) {
      *r = (*r)->next;
      break;
    } else {
      r = &((*r)->next);
    }
  }
}

jthrowable JNICALL
ExceptionOccurred(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, t->exception);
}

void JNICALL
ExceptionDescribe(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return printTrace(t, t->exception);
}

void JNICALL
ExceptionClear(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  t->exception = 0;
}

jbooleanArray JNICALL
NewBooleanArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeBooleanArray(t, length, true));
}

jbyteArray JNICALL
NewByteArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeByteArray(t, length, true));
}

jcharArray JNICALL
NewCharArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeCharArray(t, length, true));
}

jshortArray JNICALL
NewShortArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeShortArray(t, length, true));
}

jintArray JNICALL
NewIntArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeIntArray(t, length, true));
}

jlongArray JNICALL
NewLongArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeLongArray(t, length, true));
}

jfloatArray JNICALL
NewFloatArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeFloatArray(t, length, true));
}

jdoubleArray JNICALL
NewDoubleArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeDoubleArray(t, length, true));
}

jboolean* JNICALL
GetBooleanArrayElements(Thread* t, jbooleanArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = booleanArrayLength(t, *array) * sizeof(jboolean);
  jboolean* p = static_cast<jboolean*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &booleanArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jbyte* JNICALL
GetByteArrayElements(Thread* t, jbyteArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = byteArrayLength(t, *array) * sizeof(jbyte);
  jbyte* p = static_cast<jbyte*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &byteArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jchar* JNICALL
GetCharArrayElements(Thread* t, jcharArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = charArrayLength(t, *array) * sizeof(jchar);
  jchar* p = static_cast<jchar*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &charArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jshort* JNICALL
GetShortArrayElements(Thread* t, jshortArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = shortArrayLength(t, *array) * sizeof(jshort);
  jshort* p = static_cast<jshort*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &shortArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jint* JNICALL
GetIntArrayElements(Thread* t, jintArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = intArrayLength(t, *array) * sizeof(jint);
  jint* p = static_cast<jint*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &intArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jlong* JNICALL
GetLongArrayElements(Thread* t, jlongArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = longArrayLength(t, *array) * sizeof(jlong);
  jlong* p = static_cast<jlong*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &longArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jfloat* JNICALL
GetFloatArrayElements(Thread* t, jfloatArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = floatArrayLength(t, *array) * sizeof(jfloat);
  jfloat* p = static_cast<jfloat*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &floatArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jdouble* JNICALL
GetDoubleArrayElements(Thread* t, jdoubleArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = doubleArrayLength(t, *array) * sizeof(jdouble);
  jdouble* p = static_cast<jdouble*>(t->vm->system->allocate(size));
  if (size) {
    memcpy(p, &doubleArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

void JNICALL
ReleaseBooleanArrayElements(Thread* t, jbooleanArray array, jboolean* p,
                            jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = booleanArrayLength(t, *array) * sizeof(jboolean);
    if (size) {
      memcpy(&booleanArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
ReleaseByteArrayElements(Thread* t, jbyteArray array, jbyte* p, jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = byteArrayLength(t, *array) * sizeof(jbyte);
    if (size) {
      memcpy(&byteArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
ReleaseCharArrayElements(Thread* t, jcharArray array, jchar* p, jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = charArrayLength(t, *array) * sizeof(jchar);
    if (size) {
      memcpy(&charArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
ReleaseShortArrayElements(Thread* t, jshortArray array, jshort* p, jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = shortArrayLength(t, *array) * sizeof(jshort);
    if (size) {
      memcpy(&shortArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
ReleaseIntArrayElements(Thread* t, jintArray array, jint* p, jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = intArrayLength(t, *array) * sizeof(jint);
    if (size) {
      memcpy(&intArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
ReleaseLongArrayElements(Thread* t, jlongArray array, jlong* p, jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = longArrayLength(t, *array) * sizeof(jlong);
    if (size) {
      memcpy(&longArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
ReleaseFloatArrayElements(Thread* t, jfloatArray array, jfloat* p, jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = floatArrayLength(t, *array) * sizeof(jfloat);
    if (size) {
      memcpy(&floatArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
ReleaseDoubleArrayElements(Thread* t, jdoubleArray array, jdouble* p,
                           jint mode)
{
  if (mode == 0 or mode == JNI_COMMIT) {
    ENTER(t, Thread::ActiveState);
    
    unsigned size = doubleArrayLength(t, *array) * sizeof(jdouble);
    if (size) {
      memcpy(&doubleArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->vm->system->free(p);
  }
}

void JNICALL
GetBooleanArrayRegion(Thread* t, jbooleanArray array, jint offset, jint length,
                      jboolean* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &booleanArrayBody(t, *array, offset), length);
}

void JNICALL
GetByteArrayRegion(Thread* t, jbyteArray array, jint offset, jint length,
                   jbyte* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &byteArrayBody(t, *array, offset), length);
}

void JNICALL
SetByteArrayRegion(Thread* t, jbyteArray array, jint offset, jint length,
                   const jbyte* src)
{
  ENTER(t, Thread::ActiveState);

  memcpy(&byteArrayBody(t, *array, offset), src, length);
}

void JNICALL
GetCharArrayRegion(Thread* t, jcharArray array, jint offset, jint length,
                   jchar* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &charArrayBody(t, *array, offset), length);
}

void JNICALL
GetShortArrayRegion(Thread* t, jshortArray array, jint offset, jint length,
                   jshort* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &shortArrayBody(t, *array, offset), length);
}

void JNICALL
GetIntArrayRegion(Thread* t, jintArray array, jint offset, jint length,
                  jint* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &intArrayBody(t, *array, offset), length);
}

void JNICALL
GetLongArrayRegion(Thread* t, jlongArray array, jint offset, jint length,
                   jlong* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &longArrayBody(t, *array, offset), length);
}

void JNICALL
GetFloatArrayRegion(Thread* t, jfloatArray array, jint offset, jint length,
                    jfloat* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &floatArrayBody(t, *array, offset), length);
}

void JNICALL
GetDoubleArrayRegion(Thread* t, jdoubleArray array, jint offset, jint length,
                     jdouble* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &doubleArrayBody(t, *array, offset), length);
}

void* JNICALL
GetPrimitiveArrayCritical(Thread* t, jarray array, jboolean* isCopy)
{
  if ((t->criticalLevel ++) == 0) {
    enter(t, Thread::ActiveState);
  }
  
  if (isCopy) {
    *isCopy = true;
  }

  return reinterpret_cast<uintptr_t*>(*array) + 2;
}

void JNICALL
ReleasePrimitiveArrayCritical(Thread* t, jarray, void*, jint)
{
  if ((-- t->criticalLevel) == 0) {
    enter(t, Thread::IdleState);
  }
}

jint JNICALL
GetJavaVM(Thread* t, Machine** m)
{
  *m = t->vm;
  return 0;
}

jboolean JNICALL
IsSameObject(Thread* t, jobject a, jobject b)
{
  ENTER(t, Thread::ActiveState);

  return *a == *b;
}

} // namespace

namespace vm {

void
populateJNITables(JavaVMVTable* vmTable, JNIEnvVTable* envTable)
{
  memset(vmTable, 0, sizeof(JavaVMVTable));

  vmTable->AttachCurrentThread = AttachCurrentThread;
  vmTable->DetachCurrentThread = DetachCurrentThread;
  vmTable->GetEnv = GetEnv;

  memset(envTable, 0, sizeof(JNIEnvVTable));

  envTable->GetStringUTFLength = ::GetStringUTFLength;
  envTable->GetStringUTFChars = ::GetStringUTFChars;
  envTable->ReleaseStringUTFChars = ::ReleaseStringUTFChars;
  envTable->NewStringUTF = ::NewStringUTF;
  envTable->GetByteArrayRegion = ::GetByteArrayRegion;
  envTable->SetByteArrayRegion = ::SetByteArrayRegion;
  envTable->FindClass = ::FindClass;
  envTable->ThrowNew = ::ThrowNew;
  envTable->ExceptionCheck = ::ExceptionCheck;
  envTable->DeleteLocalRef = ::DeleteLocalRef;
  envTable->GetObjectClass = ::GetObjectClass;
  envTable->IsInstanceOf = ::IsInstanceOf;
  envTable->GetFieldID = ::GetFieldID;
  envTable->GetMethodID = ::GetMethodID;
  envTable->GetStaticMethodID = ::GetStaticMethodID;
  envTable->CallObjectMethodV = ::CallObjectMethodV;
  envTable->CallObjectMethod = ::CallObjectMethod;
  envTable->CallBooleanMethodV = ::CallBooleanMethodV;
  envTable->CallBooleanMethod = ::CallBooleanMethod;
  envTable->CallByteMethodV = ::CallByteMethodV;
  envTable->CallByteMethod = ::CallByteMethod;
  envTable->CallCharMethodV = ::CallCharMethodV;
  envTable->CallCharMethod = ::CallCharMethod;
  envTable->CallShortMethodV = ::CallShortMethodV;
  envTable->CallShortMethod = ::CallShortMethod;
  envTable->CallIntMethodV = ::CallIntMethodV;
  envTable->CallIntMethod = ::CallIntMethod;
  envTable->CallLongMethodV = ::CallLongMethodV;
  envTable->CallLongMethod = ::CallLongMethod;
  envTable->CallFloatMethodV = ::CallFloatMethodV;
  envTable->CallFloatMethod = ::CallFloatMethod;
  envTable->CallDoubleMethodV = ::CallDoubleMethodV;
  envTable->CallDoubleMethod = ::CallDoubleMethod;
  envTable->CallVoidMethodV = ::CallVoidMethodV;
  envTable->CallVoidMethod = ::CallVoidMethod;
  envTable->CallStaticObjectMethodV = ::CallStaticObjectMethodV;
  envTable->CallStaticObjectMethod = ::CallStaticObjectMethod;
  envTable->CallStaticBooleanMethodV = ::CallStaticBooleanMethodV;
  envTable->CallStaticBooleanMethod = ::CallStaticBooleanMethod;
  envTable->CallStaticByteMethodV = ::CallStaticByteMethodV;
  envTable->CallStaticByteMethod = ::CallStaticByteMethod;
  envTable->CallStaticCharMethodV = ::CallStaticCharMethodV;
  envTable->CallStaticCharMethod = ::CallStaticCharMethod;
  envTable->CallStaticShortMethodV = ::CallStaticShortMethodV;
  envTable->CallStaticShortMethod = ::CallStaticShortMethod;
  envTable->CallStaticIntMethodV = ::CallStaticIntMethodV;
  envTable->CallStaticIntMethod = ::CallStaticIntMethod;
  envTable->CallStaticLongMethodV = ::CallStaticLongMethodV;
  envTable->CallStaticLongMethod = ::CallStaticLongMethod;
  envTable->CallStaticFloatMethodV = ::CallStaticFloatMethodV;
  envTable->CallStaticFloatMethod = ::CallStaticFloatMethod;
  envTable->CallStaticDoubleMethodV = ::CallStaticDoubleMethodV;
  envTable->CallStaticDoubleMethod = ::CallStaticDoubleMethod;
  envTable->CallStaticVoidMethodV = ::CallStaticVoidMethodV;
  envTable->CallStaticVoidMethod = ::CallStaticVoidMethod;
  envTable->GetStaticFieldID = ::GetStaticFieldID;
  envTable->GetObjectField = ::GetObjectField;
  envTable->GetBooleanField = ::GetBooleanField;
  envTable->GetByteField = ::GetByteField;
  envTable->GetCharField = ::GetCharField;
  envTable->GetShortField = ::GetShortField;
  envTable->GetIntField = ::GetIntField;
  envTable->GetLongField = ::GetLongField;
  envTable->GetFloatField = ::GetFloatField;
  envTable->GetDoubleField = ::GetDoubleField;
  envTable->SetObjectField = ::SetObjectField;
  envTable->SetBooleanField = ::SetBooleanField;
  envTable->SetByteField = ::SetByteField;
  envTable->SetCharField = ::SetCharField;
  envTable->SetShortField = ::SetShortField;
  envTable->SetIntField = ::SetIntField;
  envTable->SetLongField = ::SetLongField;
  envTable->SetFloatField = ::SetFloatField;
  envTable->SetDoubleField = ::SetDoubleField;
  envTable->GetStaticObjectField = ::GetStaticObjectField;
  envTable->GetStaticBooleanField = ::GetStaticBooleanField;
  envTable->GetStaticByteField = ::GetStaticByteField;
  envTable->GetStaticCharField = ::GetStaticCharField;
  envTable->GetStaticShortField = ::GetStaticShortField;
  envTable->GetStaticIntField = ::GetStaticIntField;
  envTable->GetStaticLongField = ::GetStaticLongField;
  envTable->GetStaticFloatField = ::GetStaticFloatField;
  envTable->GetStaticDoubleField = ::GetStaticDoubleField;
  envTable->SetStaticObjectField = ::SetStaticObjectField;
  envTable->SetStaticBooleanField = ::SetStaticBooleanField;
  envTable->SetStaticByteField = ::SetStaticByteField;
  envTable->SetStaticCharField = ::SetStaticCharField;
  envTable->SetStaticShortField = ::SetStaticShortField;
  envTable->SetStaticIntField = ::SetStaticIntField;
  envTable->SetStaticLongField = ::SetStaticLongField;
  envTable->SetStaticFloatField = ::SetStaticFloatField;
  envTable->SetStaticDoubleField = ::SetStaticDoubleField;
  envTable->NewGlobalRef = ::NewGlobalRef;
  envTable->DeleteGlobalRef = ::DeleteGlobalRef;
  envTable->ExceptionOccurred = ::ExceptionOccurred;
  envTable->ExceptionDescribe = ::ExceptionDescribe;
  envTable->ExceptionClear = ::ExceptionClear;
  envTable->NewBooleanArray = ::NewBooleanArray;
  envTable->NewByteArray = ::NewByteArray;
  envTable->NewCharArray = ::NewCharArray;
  envTable->NewShortArray = ::NewShortArray;
  envTable->NewIntArray = ::NewIntArray;
  envTable->NewLongArray = ::NewLongArray;
  envTable->NewFloatArray = ::NewFloatArray;
  envTable->NewDoubleArray = ::NewDoubleArray;
  envTable->GetBooleanArrayElements = ::GetBooleanArrayElements;
  envTable->GetByteArrayElements = ::GetByteArrayElements;
  envTable->GetCharArrayElements = ::GetCharArrayElements;
  envTable->GetShortArrayElements = ::GetShortArrayElements;
  envTable->GetIntArrayElements = ::GetIntArrayElements;
  envTable->GetLongArrayElements = ::GetLongArrayElements;
  envTable->GetFloatArrayElements = ::GetFloatArrayElements;
  envTable->GetDoubleArrayElements = ::GetDoubleArrayElements;
  envTable->ReleaseBooleanArrayElements = ::ReleaseBooleanArrayElements;
  envTable->ReleaseByteArrayElements = ::ReleaseByteArrayElements;
  envTable->ReleaseCharArrayElements = ::ReleaseCharArrayElements;
  envTable->ReleaseShortArrayElements = ::ReleaseShortArrayElements;
  envTable->ReleaseIntArrayElements = ::ReleaseIntArrayElements;
  envTable->ReleaseLongArrayElements = ::ReleaseLongArrayElements;
  envTable->ReleaseFloatArrayElements = ::ReleaseFloatArrayElements;
  envTable->ReleaseDoubleArrayElements = ::ReleaseDoubleArrayElements;
  envTable->GetBooleanArrayRegion = ::GetBooleanArrayRegion;
  envTable->GetByteArrayRegion = ::GetByteArrayRegion;
  envTable->GetCharArrayRegion = ::GetCharArrayRegion;
  envTable->GetShortArrayRegion = ::GetShortArrayRegion;
  envTable->GetIntArrayRegion = ::GetIntArrayRegion;
  envTable->GetLongArrayRegion = ::GetLongArrayRegion;
  envTable->GetFloatArrayRegion = ::GetFloatArrayRegion;
  envTable->GetDoubleArrayRegion = ::GetDoubleArrayRegion;
  envTable->GetPrimitiveArrayCritical = ::GetPrimitiveArrayCritical;
  envTable->ReleasePrimitiveArrayCritical = ::ReleasePrimitiveArrayCritical;
  envTable->GetJavaVM = ::GetJavaVM;
  envTable->IsSameObject = ::IsSameObject;
}

} // namespace vm
