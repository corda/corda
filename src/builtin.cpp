#include "builtin.h"
#include "machine.h"
#include "constants.h"
#include "run.h"

using namespace vm;

namespace {

object
doInvoke(Thread* t, object this_, object instance, object arguments)
{
  object v = pushReference(t, run2(t, this_, instance, arguments));
  if (t->exception) {
    t->exception = makeInvocationTargetException(t, t->exception);
  }
  return v;
}

inline void
replace(char a, char b, char* c)
{
  for (; *c; ++c) if (*c == a) *c = b;
}

} // namespace

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

jclass
forName(Thread* t, jclass, jstring name)
{
  if (LIKELY(name)) {
    object n = makeByteArray(t, stringLength(t, *name) + 1, false);
    char* s = reinterpret_cast<char*>(&byteArrayBody(t, n, 0));
    stringChars(t, *name, s);
    
    replace('.', '/', s);

    object c = resolveClass(t, n);
    if (t->exception) {
      return 0;
    }

    object clinit = classInitializer(t, c);
    if (clinit) {
      PROTECT(t, c);

      set(t, classInitializer(t, c), 0);
      run(t, clinit, 0); 
    }

    return pushReference(t, c);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

jboolean
isAssignableFrom(Thread* t, jobject this_, jclass that)
{
  if (LIKELY(that)) {
    return vm::isAssignableFrom(t, *this_, *that);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

jobject
get(Thread* t, jobject this_, jobject instancep)
{
  object field = *this_;

  if (fieldFlags(t, field) & ACC_STATIC) {
    return pushReference
      (t, arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                    fieldOffset(t, field)));
  } else if (instancep) {
    object instance = *instancep;

    if (instanceOf(t, fieldClass(t, this_), instance)) {
      switch (fieldCode(t, field)) {
      case ByteField:
        return pushReference
          (t, makeByte(t, cast<int8_t>(instance, fieldOffset(t, field))));

      case BooleanField:
        return pushReference
          (t, makeBoolean(t, cast<uint8_t>(instance, fieldOffset(t, field))));

      case CharField:
        return pushReference
          (t, makeChar(t, cast<uint16_t>(instance, fieldOffset(t, field))));

      case ShortField:
        return pushReference
          (t, makeShort(t, cast<int16_t>(instance, fieldOffset(t, field))));

      case FloatField:
        return pushReference
          (t, makeFloat(t, cast<uint32_t>(instance, fieldOffset(t, field))));

      case IntField:
        return pushReference
          (t, makeInt(t, cast<int32_t>(instance, fieldOffset(t, field))));

      case DoubleField:
        return pushReference
          (t, makeDouble(t, cast<uint64_t>(instance, fieldOffset(t, field))));

      case LongField:
        return pushReference
          (t, makeLong(t, cast<int64_t>(instance, fieldOffset(t, field))));

      case ObjectField:
        return pushReference
          (t, cast<object>(instance, fieldOffset(t, field)));

      default:
        abort(t);
      }
    } else {
      t->exception = makeIllegalArgumentException(t);
      return 0;
    }
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

jobject
invoke(Thread* t, jobject this_, jobject instancep, jobjectArray argumentsp)
{
  object method = *this_;

  if (argumentsp) {
    object arguments = *argumentsp;

    if (methodFlags(t, method) & ACC_STATIC) {
      if (objectArrayLength(t, arguments)
          == methodParameterCount(t, method))
      {
        return pushReference(t, doInvoke(t, method, 0, arguments));
      } else {
        t->exception = makeArrayIndexOutOfBoundsException(t, 0);
      }
    } else if (instancep) {
      object instance = *instancep;

      if (instanceOf(t, methodClass(t, method), instance)) {
        if (objectArrayLength(t, arguments)
            == static_cast<unsigned>(methodParameterCount(t, method) - 1))
        {
          return pushReference(t, doInvoke(t, method, instance, arguments));
        } else {
          t->exception = makeArrayIndexOutOfBoundsException(t, 0);
        }
      }
    } else {
      t->exception = makeNullPointerException(t);
    }
  } else {
    t->exception = makeNullPointerException(t);
  }

  return 0;
}

jobject
currentThread(Thread* t, jclass)
{
  return pushReference(t, t->javaThread);
}

void
sleep(Thread* t, jclass, jlong milliseconds)
{
  if (milliseconds == 0) milliseconds = INT64_MAX;

  ENTER(t, Thread::IdleState);

  t->vm->system->sleep(milliseconds);
}

void
arraycopy(Thread* t, jclass, jobject src, jint srcOffset, jobject dst,
          jint dstOffset, jint length)
{
  if (LIKELY(src and dst)) {
    object s = *src;
    object d = *dst;

    if (LIKELY(objectClass(t, s) == objectClass(t, d))) {
      unsigned elementSize = classArrayElementSize(t, objectClass(t, s));

      if (LIKELY(elementSize)) {
        intptr_t sl = cast<uintptr_t>(s, BytesPerWord);
        intptr_t dl = cast<uintptr_t>(d, BytesPerWord);
        if (LIKELY(srcOffset >= 0 and srcOffset + length <= sl and
                   dstOffset >= 0 and dstOffset + length <= dl))
        {
          uint8_t* sbody = &cast<uint8_t>(s, 2 * BytesPerWord);
          uint8_t* dbody = &cast<uint8_t>(d, 2 * BytesPerWord);
          if (src == dst) {
            memmove(dbody + (dstOffset * elementSize),
                    sbody + (srcOffset * elementSize),
                    length * elementSize);
          } else {
            memcpy(dbody + (dstOffset * elementSize),
                   sbody + (srcOffset * elementSize),
                   length * elementSize);
          }
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

jlong
currentTimeMillis(Thread* t, jclass)
{
  return t->vm->system->now();
}

void
loadLibrary(Thread* t, jobject, jstring name)
{
  if (LIKELY(name)) {
    char n[stringLength(t, *name) + 1];
    stringChars(t, *name, n);

    for (System::Library* lib = t->vm->libraries; lib; lib = lib->next()) {
      if (::strcmp(lib->name(), n) == 0) {
        // already loaded
        return;
      }
    }

    System::Library* lib;
    if (LIKELY(t->vm->system->success
               (t->vm->system->load(&lib, n, t->vm->libraries))))
    {
      t->vm->libraries = lib;
    } else {
      object message = makeString(t, "library not found: %s", n);
      t->exception = makeRuntimeException(t, message);
    }
  } else {
    t->exception = makeNullPointerException(t);
  }
}

void
gc(Thread* t, jobject)
{
  ENTER(t, Thread::ExclusiveState);

  collect(t, Heap::MajorCollection);
}

void
exit(Thread* t, jobject, jint code)
{
  t->vm->system->exit(code);
}

jobject
trace(Thread* t, jclass, jint skipCount)
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

jarray
resolveTrace(Thread* t, jclass, jobject trace)
{
  unsigned length = arrayLength(t, *trace);
  object array = makeObjectArray
    (t, arrayBody(t, t->vm->types, Machine::StackTraceElementType),
     length, true);
  PROTECT(t, array);

  object e = 0;
  PROTECT(t, e);

  object class_ = 0;
  PROTECT(t, class_);

  for (unsigned i = 0; i < length; ++i) {
    e = arrayBody(t, *trace, i);

    class_ = className(t, methodClass(t, traceElementMethod(t, e)));
    class_ = makeString(t, class_, 0, byteArrayLength(t, class_) - 1, 0);

    object method = methodName(t, traceElementMethod(t, e));
    method = makeString(t, method, 0, byteArrayLength(t, method) - 1, 0);

    unsigned line = lineNumber
      (t, traceElementMethod(t, e), traceElementIp(t, e));

    object ste = makeStackTraceElement(t, class_, method, 0, line);
    set(t, objectArrayBody(t, array, i), ste);
  }

  return pushReference(t, array);
}

void
start(Thread* t, jobject this_)
{
  Thread* p = reinterpret_cast<Thread*>(threadPeer(t, *this_));
  if (p) {
    object message = makeString(t, "thread already started");
    t->exception = makeIllegalStateException(t, message);
  } else {
    p = new (t->vm->system->allocate(sizeof(Thread))) Thread(t->vm, *this_, t);

    enter(p, Thread::ActiveState);

    class Runnable: public System::Runnable {
     public:
      Runnable(System* s, Thread* t): s(s), t(t) { }

      virtual void run(System::Thread* st) {
        t->systemThread = st;

        vm::run(t, "java/lang/Thread", "run", "()V", t->javaThread);

        if (t->exception) {
          printTrace(t, t->exception);
        }

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
    { "Java_java_lang_Class_forName",
      reinterpret_cast<void*>(forName) },
    { "Java_java_lang_Class_isAssignableFrom",
      reinterpret_cast<void*>(isAssignableFrom) },

    { "Java_java_lang_System_arraycopy",
      reinterpret_cast<void*>(arraycopy) },

    { "Java_java_lang_Runtime_loadLibrary",
      reinterpret_cast<void*>(loadLibrary) },
    { "Java_java_lang_Runtime_gc",
      reinterpret_cast<void*>(gc) },
    { "Java_java_lang_Runtiime_exit",
      reinterpret_cast<void*>(exit) },

    { "Java_java_lang_Thread_doStart",
      reinterpret_cast<void*>(start) },
    { "Java_java_lang_Thread_currentThread",
      reinterpret_cast<void*>(currentThread) },
    { "Java_java_lang_Thread_sleep",
      reinterpret_cast<void*>(sleep) },

    { "Java_java_lang_Throwable_resolveTrace",
      reinterpret_cast<void*>(resolveTrace) },
    { "Java_java_lang_Throwable_trace",
      reinterpret_cast<void*>(trace) },

    { "Java_java_lang_Object_getClass",
      reinterpret_cast<void*>(getClass) },
    { "Java_java_lang_Object_notify",
      reinterpret_cast<void*>(notify) },
    { "Java_java_lang_Object_notifyAll",
      reinterpret_cast<void*>(notifyAll) },
    { "Java_java_lang_Object_toString",
      reinterpret_cast<void*>(toString) },
    { "Java_java_lang_Object_wait",
      reinterpret_cast<void*>(wait) },

    { "Java_java_lang_reflect_Field_get",
      reinterpret_cast<void*>(get) },

    { "Java_java_lang_reflect_Method_invoke",
      reinterpret_cast<void*>(invoke) },

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
