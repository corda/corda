#include "builtin.h"
#include "machine.h"
#include "run.h"

namespace vm {
namespace builtin {

jstring
toString(Thread* t, jobject this_)
{
  object s = makeString
    (t, "%s@%p",
     &byteArrayBody(t, className(t, objectClass(t, *this_)), 0),
     *this_);

  return pushReference(t, s);
}

jclass
getClass(Thread* t, jobject this_)
{
  return pushReference(t, objectClass(t, *this_));
}

void
wait(Thread* t, jobject this_, jlong milliseconds)
{
  vm::wait(t, *this_, milliseconds);
}

void
notify(Thread* t, jobject this_)
{
  vm::notify(t, *this_);
}

void
notifyAll(Thread* t, jobject this_)
{
  vm::notifyAll(t, *this_);
}

void
sleep(Thread* t, jlong milliseconds)
{
  if (milliseconds == 0) milliseconds = INT64_MAX;

  ENTER(t, Thread::IdleState);

  t->vm->system->sleep(milliseconds);
}

void
loadLibrary(Thread* t, jstring nameString)
{
  if (LIKELY(nameString)) {
    object n = *nameString;
    char name[stringLength(t, n) + 1];
    stringChars(t, n, name);

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

void
arraycopy(Thread* t, jobject src, jint srcOffset, jobject dst, jint dstOffset,
          jint length)
{
  if (LIKELY(src and dst)) {
    object s = *src;
    object d = *dst;

    if (LIKELY(objectClass(t, s) == objectClass(t, d))) {
      unsigned elementSize = classArrayElementSize(t, objectClass(t, s));

      if (LIKELY(elementSize)) {
        unsigned offset = 1;

        if (objectClass(t, s)
            == arrayBody(t, t->vm->types, Machine::ObjectArrayType))
        {
          if (LIKELY(objectArrayElementClass(t, s)
                     == objectArrayElementClass(t, d)))
          {
            offset = 2;
          } else {
            t->exception = makeArrayStoreException(t);
            return;
          }
        }

        intptr_t sl = cast<uintptr_t>(s, offset * BytesPerWord);
        intptr_t dl = cast<uintptr_t>(d, offset * BytesPerWord);
        if (LIKELY(srcOffset >= 0 and srcOffset + length <= sl and
                   dstOffset >= 0 and dstOffset + length <= dl))
        {
          uint8_t* sbody = &cast<uint8_t>(s, (offset + 1) * BytesPerWord);
          uint8_t* dbody = &cast<uint8_t>(d, (offset + 1) * BytesPerWord);
          memcpy(dbody + (dstOffset * elementSize),
                 sbody + (srcOffset * elementSize),
                 length * elementSize);
          return;
        }
      }
    }
  } else {
    t->exception = makeNullPointerException(t);
    return;
  }

  t->exception = makeArrayStoreException(t);
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
      Runnable(System* s, Thread* t): s(s), t(t) { }

      virtual void run(System::Thread* st) {
        t->systemThread = st;

        vm::run(t, "java/lang/Thread", "run", "()V", t->javaThread);

        t->exit();
      }

      virtual void dispose() {
        s->free(this);
      }

      System* s;
      Thread* t;
    }* r = new (t->vm->system->allocate(sizeof(Runnable)))
       Runnable(t->vm->system, p);

    if (not t->vm->system->success(t->vm->system->start(r))) {
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
    { "Java_java_lang_Object_getClass",
      reinterpret_cast<void*>(getClass) },
    { "Java_java_lang_Object_wait",
      reinterpret_cast<void*>(wait) },
    { "Java_java_lang_Object_notify",
      reinterpret_cast<void*>(notify) },
    { "Java_java_lang_Object_notifyAll",
      reinterpret_cast<void*>(notifyAll) },
    { "Java_java_lang_Thread_sleep",
      reinterpret_cast<void*>(sleep) },
    { "Java_java_lang_System_loadLibrary",
      reinterpret_cast<void*>(loadLibrary) },
    { "Java_java_lang_System_arraycopy",
      reinterpret_cast<void*>(arraycopy) },
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
