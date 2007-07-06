#include "vm-builtin.h"

namespace vm {
namespace builtin {

void
loadLibrary(JNIEnv* e, jstring nameString)
{
  Thread* t = static_cast<Thread*>(e);

  if (LIKELY(nameString)) {
    object n = *nameString;
    char name[stringLength(t, n) + 1];
    memcpy(name,
           &byteArrayBody(t, stringBytes(t, n), stringOffset(t, n)),
           stringLength(t, n));
    name[stringLength(t, n)] = 0;

    System::Library* lib;
    if (LIKELY(t->vm->system->success
               (t->vm->system->load(&lib, name, t->vm->libraries))))
    {
      t->vm->libraries = lib;
    } else {
      object message = makeString(t, "library not found: %s", name);
      t->exception = makeRuntimeException(t, message);
    }
  } else {
    t->exception = makeNullPointerException(t);
  }
}

jstring
toString(JNIEnv* e, jobject this_)
{
  Thread* t = static_cast<Thread*>(e);

  object s = makeString
    (t, "%s@%p",
     &byteArrayBody(t, className(t, objectClass(t, *this_)), 0),
     *this_);

  return pushReference(t, s);
}

jarray
trace(JNIEnv* e, jint skipCount)
{
  Thread* t = static_cast<Thread*>(e);

  int frame = t->frame;
  while (skipCount-- and frame >= 0) {
    frame = frameNext(t, frame);
  }
  
  if (methodClass(t, frameMethod(t, frame))
      == arrayBody(t, t->vm->types, Machine::ThrowableType))
  {
    // skip Throwable constructors
    while (strcmp(reinterpret_cast<const int8_t*>("<init>"),
                  &byteArrayBody(t, methodName(t, frameMethod(t, frame)), 0))
           == 0)
    {
      frame = frameNext(t, frame);
    }
  }

  return pushReference(t, makeTrace(t, frame));
}

void
populate(Thread* t, object map)
{
  struct {
    const char* key;
    void* value;
  } builtins[] = {
    { "Java_java_lang_Object_toString",
      reinterpret_cast<void*>(toString) },
    { "Java_java_lang_System_loadLibrary",
      reinterpret_cast<void*>(loadLibrary) },
    { "Java_java_lang_Throwable_trace",
      reinterpret_cast<void*>(trace) },
    { 0, 0 }
  };

  for (unsigned i = 0; builtins[i].key; ++i) {
    object key = makeByteArray(t, builtins[i].key);
    PROTECT(t, key);
    object value = makePointer(t, builtins[i].value);

    hashMapInsert(t, map, key, value, byteArrayHash);
  }
}

} // namespace builtin
} // namespace vm
