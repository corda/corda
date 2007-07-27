#include "jnienv.h"
#include "machine.h"

using namespace vm;

namespace {

jsize
GetStringUTFLength(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return stringLength(t, *s);
}

const char*
GetStringUTFChars(Thread* t, jstring s, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  char* chars = static_cast<char*>
    (t->vm->system->allocate(stringLength(t, *s) + 1));
  stringChars(t, *s, chars);

  if (isCopy) *isCopy = true;
  return chars;
}

void
ReleaseStringUTFChars(Thread* t, jstring, const char* chars)
{
  t->vm->system->free(chars);
}

jstring
NewStringUTF(Thread* t, const char* chars)
{
  ENTER(t, Thread::ActiveState);

  return pushReference(t, makeString(t, "%s", chars));
}

void
GetByteArrayRegion(Thread* t, jbyteArray array, jint offset, jint length,
                   jbyte* dst)
{
  ENTER(t, Thread::ActiveState);

  memcpy(dst, &byteArrayBody(t, *array, offset), length);
}

void
SetByteArrayRegion(Thread* t, jbyteArray array, jint offset, jint length,
                   const jbyte* src)
{
  ENTER(t, Thread::ActiveState);

  memcpy(&byteArrayBody(t, *array, offset), src, length);
}

jclass
FindClass(Thread* t, const char* name)
{
  ENTER(t, Thread::ActiveState);

  object n = makeByteArray(t, strlen(name) + 1, false);
  memcpy(&byteArrayBody(t, n, 0), name, byteArrayLength(t, n));

  return pushReference(t, resolveClass(t, n));
}

jint
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

jboolean
ExceptionCheck(Thread* t)
{
  return t->exception != 0;
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
}

} // namespace vm
