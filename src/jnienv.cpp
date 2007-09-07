#include "jnienv.h"
#include "machine.h"

using namespace vm;

namespace {

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
  return pushReference(t, objectClass(t, *o));
}

jboolean JNICALL
IsInstanceOf(Thread* t, jobject o, jclass c)
{
  return instanceOf(t, *c, *o);
}

object
findMethod(Thread* t, object class_, const char* name, const char* spec)
{
  object n = makeString(t, "%s", name);
  PROTECT(t, n);

  object s = makeString(t, "%s", spec);
  return vm::findMethod(t, class_, n, s);
}

// jmethodID JNICALL
// GetMethodID(Thread* t, jclass c, const char* name, const char* spec)
// {
//   object method = findMethod(t, *c, name, spec);
//   if (UNLIKELY(t->exception)) return 0;

//   if (classFlags(t, *c) & ACC_INTERFACE) {
//     PROTECT(t, method);

//     ACQUIRE(t, t->vm->referenceLock);
    
//     for (unsigned i = 0; i < vectorSize(t, t->vm->jniInterfaceTable); ++i) {
//       if (method == vectorBody(t, t->vm->jniInterfaceTable, i)) {
//         return i;
//       }
//     }

//     t->vm->jniInterfaceTable
//       = vectorAppend(t, t->vm->jniInterfaceTable, method);

//     return (vectorSize(t, t->vm->jniInterfaceTable) - 1) | (1 << BitsPerWord);
//   } else {
//     return methodOffset(t, method);
//   }
// }

// jmethodID JNICALL
// GetStaticMethodID(Thread* t, jclass c, const char* name, const char* spec)
// {
//   object method = findMethod(t, *c, name, spec);
//   if (UNLIKELY(t->exception)) return 0;

//   return methodOffset(t, method);
// }

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
  object field = findField(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return fieldOffset(t, field);
}

jfieldID JNICALL
GetStaticFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  object field = findField(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return fieldOffset(t, field);
}

jobject JNICALL
GetObjectField(Thread* t, jobject o, jfieldID field)
{
  return pushReference(t, cast<object>(*o, field));
}

jboolean JNICALL
GetBooleanField(Thread*, jobject o, jfieldID field)
{
  return cast<jboolean>(*o, field);
}

jbyte JNICALL
GetByteField(Thread*, jobject o, jfieldID field)
{
  return cast<jbyte>(*o, field);
}

jchar JNICALL
GetCharField(Thread*, jobject o, jfieldID field)
{
  return cast<jchar>(*o, field);
}

jshort JNICALL
GetShortField(Thread*, jobject o, jfieldID field)
{
  return cast<jshort>(*o, field);
}

jint JNICALL
GetIntField(Thread*, jobject o, jfieldID field)
{
  return cast<jint>(*o, field);
}

jlong JNICALL
GetLongField(Thread*, jobject o, jfieldID field)
{
  return cast<jlong>(*o, field);
}

jfloat JNICALL
GetFloatField(Thread*, jobject o, jfieldID field)
{
  return cast<jfloat>(*o, field);
}

jdouble JNICALL
GetDoubleField(Thread*, jobject o, jfieldID field)
{
  return cast<jdouble>(*o, field);
}

void JNICALL
SetObjectField(Thread* t, jobject o, jfieldID field, jobject v)
{
  set(t, cast<object>(*o, field), (v ? *v : 0));
}

void JNICALL
SetBooleanField(Thread*, jobject o, jfieldID field, jboolean v)
{
  cast<jboolean>(*o, field) = v;
}

void JNICALL
SetByteField(Thread*, jobject o, jfieldID field, jbyte v)
{
  cast<jbyte>(*o, field) = v;
}

void JNICALL
SetCharField(Thread*, jobject o, jfieldID field, jchar v)
{
  cast<jchar>(*o, field) = v;
}

void JNICALL
SetShortField(Thread*, jobject o, jfieldID field, jshort v)
{
  cast<jshort>(*o, field) = v;
}

void JNICALL
SetIntField(Thread*, jobject o, jfieldID field, jint v)
{
  cast<jint>(*o, field) = v;
}

void JNICALL
SetLongField(Thread*, jobject o, jfieldID field, jlong v)
{
  cast<jlong>(*o, field) = v;
}

void JNICALL
SetFloatField(Thread*, jobject o, jfieldID field, jfloat v)
{
  cast<jfloat>(*o, field) = v;
}

void JNICALL
SetDoubleField(Thread*, jobject o, jfieldID field, jdouble v)
{
  cast<jdouble>(*o, field) = v;
}

jobject JNICALL
GetStaticObjectField(Thread* t, jclass c, jfieldID field)
{
  return pushReference(t, arrayBody(t, classStaticTable(t, *c), field));
}

jboolean JNICALL
GetStaticBooleanField(Thread* t, jclass c, jfieldID field)
{
  return intValue(t, arrayBody(t, classStaticTable(t, *c), field)) != 0;
}

jbyte JNICALL
GetStaticByteField(Thread* t, jclass c, jfieldID field)
{
  return static_cast<jbyte>
    (intValue(t, arrayBody(t, classStaticTable(t, *c), field)));
}

jchar JNICALL
GetStaticCharField(Thread* t, jclass c, jfieldID field)
{
  return static_cast<jchar>
    (intValue(t, arrayBody(t, classStaticTable(t, *c), field)));
}

jshort JNICALL
GetStaticShortField(Thread* t, jclass c, jfieldID field)
{
  return static_cast<jshort>
    (intValue(t, arrayBody(t, classStaticTable(t, *c), field)));
}

jint JNICALL
GetStaticIntField(Thread* t, jclass c, jfieldID field)
{
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
  jint i = intValue(t, arrayBody(t, classStaticTable(t, *c), field));
  jfloat v; memcpy(&v, &i, 4);
  return v;
}

jdouble JNICALL
GetStaticDoubleField(Thread* t, jclass c, jfieldID field)
{
  jlong i = longValue(t, arrayBody(t, classStaticTable(t, *c), field));
  jdouble v; memcpy(&v, &i, 8);
  return v;
}

void JNICALL
SetStaticObjectField(Thread* t, jclass c, jfieldID field, jobject v)
{
  set(t, arrayBody(t, classStaticTable(t, *c), field), (v ? *v : 0));
}

void JNICALL
SetStaticBooleanField(Thread* t, jclass c, jfieldID field, jboolean v)
{
  object o = makeInt(t, v ? 1 : 0);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticByteField(Thread* t, jclass c, jfieldID field, jbyte v)
{
  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticCharField(Thread* t, jclass c, jfieldID field, jchar v)
{
  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticShortField(Thread* t, jclass c, jfieldID field, jshort v)
{
  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticIntField(Thread* t, jclass c, jfieldID field, jint v)
{
  object o = makeInt(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticLongField(Thread* t, jclass c, jfieldID field, jlong v)
{
  object o = makeLong(t, v);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticFloatField(Thread* t, jclass c, jfieldID field, jfloat v)
{
  jint i; memcpy(&i, &v, 4);
  object o = makeInt(t, i);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
}

void JNICALL
SetStaticDoubleField(Thread* t, jclass c, jfieldID field, jdouble v)
{
  jlong i; memcpy(&i, &v, 8);
  object o = makeLong(t, i);
  set(t, arrayBody(t, classStaticTable(t, *c), field), o);
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
