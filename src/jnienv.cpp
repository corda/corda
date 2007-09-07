#include "jnienv.h"
#include "machine.h"
#include "constants.h"

using namespace vm;

namespace {

const uintptr_t InterfaceMethodID
= (static_cast<uintptr_t>(1) << (BitsPerWord - 1));

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

  object n = makeString(t, "%s", name);
  PROTECT(t, n);

  object s = makeString(t, "%s", spec);
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
    return methodOffset(t, method);
  }
}

jmethodID JNICALL
GetStaticMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object method = findMethod(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return methodOffset(t, method);
}

inline object
getMethod(Thread* t, object o, jmethodID m)
{
  if (m & InterfaceMethodID) {
    return vectorBody(t, t->vm->jniInterfaceTable, m & (~InterfaceMethodID));
  } else {
    return arrayBody(t, classVirtualTable(t, objectClass(t, o)), m);
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

  return booleanValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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

  return byteValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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

  return charValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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

  return shortValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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

  return intValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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

  return longValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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

  return floatValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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

  return doubleValue(t, run(t, getMethod(t, *o, m), *o, true, a));
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
  return arrayBody(t, classMethodTable(t, class_), m);
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

  return booleanValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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

  return byteValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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

  return charValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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

  return shortValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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

  return intValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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

  return longValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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

  return floatValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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

  return doubleValue(t, run(t, getStaticMethod(t, *c, m), 0, true, a));
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
  object n = makeString(t, "%s", name);
  PROTECT(t, n);

  object s = makeString(t, "%s", spec);
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

  return intValue(t, arrayBody(t, classStaticTable(t, *c), field)) != 0;
}

jbyte JNICALL
GetStaticByteField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return static_cast<jbyte>
    (intValue(t, arrayBody(t, classStaticTable(t, *c), field)));
}

jchar JNICALL
GetStaticCharField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return static_cast<jchar>
    (intValue(t, arrayBody(t, classStaticTable(t, *c), field)));
}

jshort JNICALL
GetStaticShortField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return static_cast<jshort>
    (intValue(t, arrayBody(t, classStaticTable(t, *c), field)));
}

jint JNICALL
GetStaticIntField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return intValue(t, arrayBody(t, classStaticTable(t, *c), field));
}

jlong JNICALL
GetStaticLongField(Thread* t, jclass c, jfieldID field)
{
  return longValue(t, arrayBody(t, classStaticTable(t, *c), field));
}

jfloat JNICALL
GetStaticFloatField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  jint i = intValue(t, arrayBody(t, classStaticTable(t, *c), field));
  jfloat v; memcpy(&v, &i, 4);
  return v;
}

jdouble JNICALL
GetStaticDoubleField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  jlong i = longValue(t, arrayBody(t, classStaticTable(t, *c), field));
  jdouble v; memcpy(&v, &i, 8);
  return v;
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
GetJavaVM(Thread* t, JavaVM** vm)
{
#warning todo
}

jboolean JNICALL
IsSameObject(Thread* t, jobject a, jobject b)
{
  ENTER(t, Thread::ActiveState);

  return a == b;
}

} // namespace

namespace vm {

void
populateJNITable(JNIEnvVTable* table)
{
  memset(table, 0, sizeof(JNIEnvVTable));

  table->GetStringUTFLength = ::GetStringUTFLength;
  table->GetStringUTFChars = ::GetStringUTFChars;
  table->ReleaseStringUTFChars = ::ReleaseStringUTFChars;
  table->NewStringUTF = ::NewStringUTF;
  table->GetByteArrayRegion = ::GetByteArrayRegion;
  table->SetByteArrayRegion = ::SetByteArrayRegion;
  table->FindClass = ::FindClass;
  table->ThrowNew = ::ThrowNew;
  table->ExceptionCheck = ::ExceptionCheck;
  table->DeleteLocalRef = ::DeleteLocalRef;
  table->GetObjectClass = ::GetObjectClass;
  table->IsInstanceOf = ::IsInstanceOf;
  table->GetFieldID = ::GetFieldID;
  table->GetStaticFieldID = ::GetStaticFieldID;
  table->GetObjectField = ::GetObjectField;
  table->GetBooleanField = ::GetBooleanField;
  table->GetByteField = ::GetByteField;
  table->GetCharField = ::GetCharField;
  table->GetShortField = ::GetShortField;
  table->GetIntField = ::GetIntField;
  table->GetLongField = ::GetLongField;
  table->GetFloatField = ::GetFloatField;
  table->GetDoubleField = ::GetDoubleField;
  table->SetObjectField = ::SetObjectField;
  table->SetBooleanField = ::SetBooleanField;
  table->SetByteField = ::SetByteField;
  table->SetCharField = ::SetCharField;
  table->SetShortField = ::SetShortField;
  table->SetIntField = ::SetIntField;
  table->SetLongField = ::SetLongField;
  table->SetFloatField = ::SetFloatField;
  table->SetDoubleField = ::SetDoubleField;
  table->GetStaticObjectField = ::GetStaticObjectField;
  table->GetStaticBooleanField = ::GetStaticBooleanField;
  table->GetStaticByteField = ::GetStaticByteField;
  table->GetStaticCharField = ::GetStaticCharField;
  table->GetStaticShortField = ::GetStaticShortField;
  table->GetStaticIntField = ::GetStaticIntField;
  table->GetStaticLongField = ::GetStaticLongField;
  table->GetStaticFloatField = ::GetStaticFloatField;
  table->GetStaticDoubleField = ::GetStaticDoubleField;
  table->SetStaticObjectField = ::SetStaticObjectField;
  table->SetStaticBooleanField = ::SetStaticBooleanField;
  table->SetStaticByteField = ::SetStaticByteField;
  table->SetStaticCharField = ::SetStaticCharField;
  table->SetStaticShortField = ::SetStaticShortField;
  table->SetStaticIntField = ::SetStaticIntField;
  table->SetStaticLongField = ::SetStaticLongField;
  table->SetStaticFloatField = ::SetStaticFloatField;
  table->SetStaticDoubleField = ::SetStaticDoubleField;
}

} // namespace vm
