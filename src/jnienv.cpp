#include "jnienv.h"
#include "machine.h"

namespace vm {

namespace jni {

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
populate(JNIEnvVTable* table)
{
  memset(table, 0, sizeof(JNIEnvVTable));

  table->GetStringUTFLength = GetStringUTFLength;
  table->GetStringUTFChars = GetStringUTFChars;
  table->ReleaseStringUTFChars = ReleaseStringUTFChars;
  table->NewStringUTF = NewStringUTF;
}

} // namespace jni

} // namespace vm
