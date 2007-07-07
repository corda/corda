#include "builtin.h"
#include "machine.h"
#include "run.h"

namespace vm {
namespace builtin {

void
loadLibrary(Thread* t, jstring nameString)
{
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
toString(Thread* t, jobject this_)
{
  object s = makeString
    (t, "%s@%p",
     &byteArrayBody(t, className(t, objectClass(t, *this_)), 0),
     *this_);

  return pushReference(t, s);
}

jarray
trace(Thread* t, jint skipCount)
{
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
start(Thread* t, jobject this_)
{
  Thread* p = reinterpret_cast<Thread*>(threadPeer(t, *this_));
  if (p) {
    object message = makeString(t, "thread already started");
    t->exception = makeIllegalStateException(t, message);
  } else {
    p = new (t->vm->system->allocate(sizeof(Thread)))
      Thread(t->vm, t->vm->system, *this_, t);

    enter(p, Thread::ActiveState);

    class Runnable: public System::Runnable {
     public:
      Runnable(Thread* t): t(t) { }

      virtual void run(System::Thread* st) {
        t->systemThread = st;

        vm::run(t, "java/lang/Thread", "run", "()V", t->javaThread);

        t->exit();
      }

      Thread* t;
    } r(p);

    if (not t->vm->system->success(t->vm->system->start(&r))) {
      p->exit();

      object message = makeString(t, "unable to start native thread");
      t->exception = makeRuntimeException(t, message);
    }
  }
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
    { "Java_java_lang_Thread_start",
      reinterpret_cast<void*>(start) },
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
