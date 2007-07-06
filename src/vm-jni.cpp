#include "vm-jni.h"
#include "vm-declarations.h"

namespace vm {
namespace jni {

jsize
GetStringUTFLength(JNIEnv* e, jstring s)
{
  Thread* t = static_cast<Thread*>(e);

  ENTER(t, Thread::ActiveState);

  jsize length = 0;
  if (LIKELY(s)) {
    length = stringLength(t, *s);
  } else {
    t->exception = makeNullPointerException(t);
  }

  return length;
}

const char*
GetStringUTFChars(JNIEnv* e, jstring s, jboolean* isCopy)
{
  Thread* t = static_cast<Thread*>(e);

  ENTER(t, Thread::ActiveState);

  char* chars = 0;
  if (LIKELY(s)) {
    chars = static_cast<char*>
      (t->vm->system->allocate(stringLength(t, *s) + 1));

    memcpy(chars,
           &byteArrayBody(t, stringBytes(t, *s), stringOffset(t, *s)),
           stringLength(t, *s));

    chars[stringLength(t, *s)] = 0;
  } else {
    t->exception = makeNullPointerException(t);
  }

  if (isCopy) *isCopy = true;
  return chars;
}

void
ReleaseStringUTFChars(JNIEnv* e, jstring, const char* chars)
{
  static_cast<Thread*>(e)->vm->system->free(chars);
}

void
populate(JNIEnvVTable* table)
{
  memset(table, 0, sizeof(JNIEnvVTable));

  table->GetStringUTFLength = jni::GetStringUTFLength;
  table->GetStringUTFChars = jni::GetStringUTFChars;
  table->ReleaseStringUTFChars = jni::ReleaseStringUTFChars;
}

} // namespace jni
} // namespace vm
