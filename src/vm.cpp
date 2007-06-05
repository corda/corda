#include "common.h"
#include "system.h"
#include "heap.h"

namespace {

typedef void* object;
typedef unsigned Type;

#include "opcodes.h"

enum ObjectType {
  NullType,
  CollectedType,

#include "type-enums.h"

  OtherType
};

class Thread;

Type typeOf(object);

object& objectClass(object);
void assert(Thread* t, bool v);

#include "type-header.h"

class Machine {
 public:
  System* sys;
  Heap* heap;
  Thread* rootThread;
  Thread* exclusive;
  unsigned activeCount;
  unsigned liveCount;
  System::Monitor* stateLock;
};

class Thread {
 public:
  enum State {
    NoState,
    ActiveState,
    IdleState,
    ZombieState,
    ExclusiveState,
    ExitState
  };

  static const unsigned HeapSize = 64 * 1024;
  static const unsigned StackSize = 64 * 1024;

  Machine* vm;
  Thread* next;
  Thread* child;
  State state;
  object frame;
  object code;
  object exception;
  unsigned sp;
  unsigned heapIndex;
  object stack[StackSize];
  object heap[HeapSize];
};

inline void NO_RETURN
abort(Thread* t)
{
  t->vm->sys->abort();
}

inline void
assert(Thread* t, bool v)
{
  if (UNLIKELY(not v)) abort(t);
}

void
init(Machine* m, System* sys, Heap* heap)
{
  sys->zero(m, sizeof(Machine));
  m->sys = sys;
  m->heap = heap;
  if (not sys->success(sys->make(&(m->stateLock)))) {
    sys->abort();
  }
}

void
dispose(Machine* m)
{
  m->stateLock->dispose();
}

void
init(Thread* t, Machine* m)
{
  m->sys->zero(m, sizeof(Thread));
  t->vm = m;
  m->rootThread = t;
  t->state = Thread::NoState;
}

void
iterate(Thread* t, Heap::Visitor* v)
{
  t->heapIndex = 0;

  v->visit(&(t->frame));
  v->visit(&(t->code));
  v->visit(&(t->exception));

  for (unsigned i = 0; i < t->sp; ++i) {
    v->visit(t->stack + t->sp);
  }

  for (Thread* t = t->child; t; t = t->next) {
    iterate(t, v);
  }
}

void
collect(Machine* m, Heap::CollectionType type)
{
  class Iterator: public Heap::Iterator {
   public:
    Iterator(Machine* m): machine(m) { }

    void iterate(Heap::Visitor* v) {
      for (Thread* t = machine->rootThread; t; t = t->next) {
        ::iterate(t, v);
      }
    }
    
   private:
    Machine* machine;
  } it(m);

  m->heap->collect(type, &it);
}

void
enter(Thread* t, Thread::State s)
{
  if (s == t->state) return;

  ACQUIRE(t->vm->stateLock);

  switch (s) {
  case Thread::ExclusiveState: {
    assert(t, t->state == Thread::ActiveState);

    while (t->vm->exclusive) {
      // another thread got here first.
      enter(t, Thread::IdleState);
      enter(t, Thread::ActiveState);
    }

    t->state = Thread::ExclusiveState;
    t->vm->exclusive = t;
      
    while (t->vm->activeCount > 1) {
      t->vm->stateLock->wait();
    }
  } break;

  case Thread::IdleState:
  case Thread::ZombieState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->vm->exclusive == t);
      t->vm->exclusive = 0;
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    -- t->vm->activeCount;
    if (s == Thread::ZombieState) {
      -- t->vm->liveCount;
    }
    t->state = s;

    t->vm->stateLock->notifyAll();
  } break;

  case Thread::ActiveState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->vm->exclusive == t);

      t->state = s;
      t->vm->exclusive = 0;

      t->vm->stateLock->notifyAll();
    } break;

    case Thread::NoState:
    case Thread::IdleState: {
      while (t->vm->exclusive) {
        t->vm->stateLock->wait();
      }

      ++ t->vm->activeCount;
      if (t->state == Thread::NoState) {
        ++ t->vm->liveCount;
      }
      t->state = s;
    } break;

    default: abort(t);
    }
  } break;

  case Thread::ExitState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->vm->exclusive == t);
      t->vm->exclusive = 0;
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }
      
    -- t->vm->activeCount;
    t->state = s;

    while (t->vm->liveCount > 1) {
      t->vm->stateLock->wait();
    }
  } break;

  default: abort(t);
  }
}

void
maybeYieldAndMaybeCollect(Thread* t, unsigned size)
{
  if (size > Thread::HeapSize) {
    // large object support not yet implemented.
    abort(t);
  }

  ACQUIRE(t->vm->stateLock);

  while (t->vm->exclusive) {
    // another thread wants to enter the exclusive state, either for a
    // collection or some other reason.  We give it a chance here.
    enter(t, Thread::IdleState);
    enter(t, Thread::ActiveState);
  }

  if (t->heapIndex + size >= Thread::HeapSize) {
    enter(t, Thread::ExclusiveState);
    collect(t->vm, Heap::MinorCollection);
    enter(t, Thread::ActiveState);
  }
}

inline object
allocate(Thread* t, unsigned size)
{
  if (UNLIKELY(t->heapIndex + size >= Thread::HeapSize
               or t->vm->exclusive))
  {
    maybeYieldAndMaybeCollect(t, size);
  }

  object o = t->heap + t->heapIndex;
  t->heapIndex += size;
  return o;
}

inline void
push(Thread* t, object o)
{
  t->stack[(t->sp)++] = o;
}

inline object
pop(Thread* t)
{
  return t->stack[--(t->sp)];
}

object
run(Thread* t)
{
  unsigned ip = 0;
  unsigned parameterCount = 0;

 loop:
  switch (codeBody(t, t->code)[ip++]) {
  case aaload: {
    object index = pop(t);
    object array = pop(t);

    if (array) {
      int32_t i = intValue(t, index);
      if (i >= 0 and i < objectArrayLength(t, array)) {
        push(t, objectArrayBody(t, array)[i]);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    objectArrayLength(t, array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case aastore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (array) {
      if (i >= 0 and i < objectArrayLength(t, array)) {
        set(t, objectArrayBody(array)[i], value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    objectArrayLength(t, array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case aconst_null: {
    push(t, 0);
  } goto loop;

  case aload:
  case iload:
  case lload: {
    push(t, frameBody(t, t->frame)[codeBody(t, t->code)[ip++]]);
  } goto loop;

  case aload_0:
  case iload_0:
  case lload_0: {
    push(t, frameBody(t, t->frame)[0]);
  } goto loop;

  case aload_1:
  case iload_1:
  case lload_1: {
    push(t, frameBody(t, t->frame)[1]);
  } goto loop;

  case aload_2:
  case iload_2:
  case lload_2: {
    push(t, frameBody(t, t->frame)[2]);
  } goto loop;

  case aload_3:
  case iload_3:
  case lload_3: {
    push(t, frameBody(t, t->frame)[3]);
  } goto loop;

  case anewarray: {
    object count = pop(t);
    int32_t c = intValue(t, count);

    if (c >= 0) {
      uint8_t index1 = codeBody(t, t->code)[ip++];
      uint8_t index2 = codeBody(t, t->code)[ip++];
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t->code), index);
      if (t->exception) goto throw_;
      
      object array = makeObjectArray(t, class_, c);
      t->vm->sys->zero(objectArrayBody(array), c * 4);
      
      push(t, array);
    } else {
      object message = makeString(t, "%d", c);
      t->exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case areturn:
  case ireturn:
  case lreturn: {
    t->frame = frameNext(t->frame);
    if (t->frame) {
      t->code = methodCode(frameMethod(t->frame));
      ip = frameIp(t->frame);
      goto loop;
    } else {
      object value = pop(t);
      t->code = 0;
      return value;
    }
  } goto loop;

  case arraylength: {
    object array = pop(t);
    if (array) {
      push(t, makeInt(t, arrayLength(array)));
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } abort(t);

  case astore:
  case istore:
  case lstore: {
    object value = pop(t);
    set(t, frameBody(t, t->frame)[codeBody(t, t->code)[ip++]], value);
  } goto loop;

  case astore_0:
  case istore_0:
  case lstore_0: {
    object value = pop(t);
    set(t, frameBody(t, t->frame)[0], value);
  } goto loop;

  case astore_1:
  case istore_1:
  case lstore_1: {
    object value = pop(t);
    set(t, frameBody(t, t->frame)[1], value);
  } goto loop;

  case astore_2:
  case istore_2:
  case lstore_2: {
    object value = pop(t);
    set(t, frameBody(t, t->frame)[2], value);
  } goto loop;

  case astore_3:
  case istore_3:
  case lstore_3: {
    object value = pop(t);
    set(t, frameBody(t, t->frame)[3], value);
  } goto loop;

  case athrow: {
    t->exception = pop(t);
    if (t->exception == 0) {
      t->exception = makeNullPointerException(t, 0);      
    }
    goto throw_;
  } abort(t);

  case baload: {
    object index = pop(t);
    object array = pop(t);

    if (array) {
      int32_t i = intValue(t, index);
      if (i >= 0 and i < byteArrayLength(array)) {
        push(t, makeByte(t, byteArrayBody(array)[i]));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    byteArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case bastore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (array) {
      if (i >= 0 and i < byteArrayLength(array)) {
        byteArrayBody(array)[i] = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    byteArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case bipush: {
    push(t, makeInt(t, codeBody(t, t->code)[ip++]));
  } goto loop;

  case caload: {
    object index = pop(t);
    object array = pop(t);

    if (array) {
      int32_t i = intValue(t, index);
      if (i >= 0 and i < charArrayLength(array)) {
        push(t, makeInt(t, charArrayBody(array)[i]));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    charArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case castore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (array) {
      if (i >= 0 and i < charArrayLength(array)) {
        charArrayBody(array)[i] = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    charArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case checkcast: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];

    if (t->stack[t->sp - 1]) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t->code), index);
      if (t->exception) goto throw_;

      if (not instanceOf(t, class_, t->stack[t->sp - 1])) {
        t->exception = makeClassCastException(t, 0);
        goto throw_;
      }
    }
  } goto loop;

  case dup: {
    object value = t->stack[t->sp - 1];
    push(t, value);
  } goto loop;

  case dup_x1: {
    object first = pop(t);
    object second = pop(t);
    
    push(t, first);
    push(t, second);
    push(t, first);
  } goto loop;

  case dup_x2: {
    object first = pop(t);
    object second = pop(t);
    object third = pop(t);
    
    push(t, first);
    push(t, third);
    push(t, second);
    push(t, first);
  } goto loop;

  case dup2: {
    object first = t->stack[t->sp - 1];
    if (isLongOrDouble(first)) {
      push(t, first);
    } else {
      object second = t->stack[t->sp - 2];
      push(t, second);
      push(t, first);
    }
  } goto loop;

  case dup2_x1: {
    object first = pop(t);
    object second = pop(t);
    
    if (isLongOrDouble(first)) {
      push(t, first);
      push(t, second);
      push(t, first);
    } else {
      object third = pop(t);
      push(t, second);
      push(t, first);
      push(t, third);
      push(t, second);
      push(t, first);
    }
  } goto loop;

  case dup2_x2: {
    object first = pop(t);
    object second = pop(t);
    
    if (isLongOrDouble(first)) {
      if (isLongOrDouble(second)) {
        push(t, first);
        push(t, second);
        push(t, first);
      } else {
        object third = pop(t);
        push(t, first);
        push(t, third);
        push(t, second);
        push(t, first);
      }
    } else {
      object third = pop(t);
      if (isLongOrDouble(third)) {
        push(t, second);
        push(t, first);
        push(t, third);
        push(t, second);
        push(t, first);
      } else {
        object fourth = pop(t);
        push(t, second);
        push(t, first);
        push(t, fourth);
        push(t, third);
        push(t, second);
        push(t, first);
      }
    }
  } goto loop;

  case getfield: {
    object instance = pop(t);
    if (instance) {
      uint8_t index1 = codeBody(t, t->code)[ip++];
      uint8_t index2 = codeBody(t, t->code)[ip++];
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t->code), index);
      if (t->exception) goto throw_;
      
      push(t, getField(instance, field));
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case getstatic: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t->code), index);
    if (t->exception) goto throw_;

    if (not classInitialized(fieldClass(field))) {
      t->code = classInitializer(fieldClass(field));
      ip -= 3;
      parameterCount = 0;
      goto invoke;
    }
      
    push(t, getStatic(field));
  } goto loop;

  case goto_: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    ip = (ip - 1) + ((offset1 << 8) | offset2);
  } goto loop;
    
  case goto_w: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];
    uint8_t offset3 = codeBody(t, t->code)[ip++];
    uint8_t offset4 = codeBody(t, t->code)[ip++];

    ip = (ip - 1)
      + ((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4);
  } goto loop;

  case i2b: {
    object v = pop(t);
    
    push(t, makeInt(t, static_cast<int8_t>(intValue(t, v))));
  } goto loop;

  case i2c: {
    object v = pop(t);
    
    push(t, makeInt(t, static_cast<uint16_t>(intValue(t, v))));
  } goto loop;

  case i2l: {
    object v = pop(t);
    
    push(t, makeLong(t, intValue(t, v)));
  } goto loop;

  case i2s: {
    object v = pop(t);

    push(t, makeInt(t, static_cast<int16_t>(intValue(t, v))));
  } goto loop;

  case iadd: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) + intValue(t, b)));
  } goto loop;

  case iaload: {
    object index = pop(t);
    object array = pop(t);

    if (array) {
      int32_t i = intValue(t, index);
      if (i >= 0 and i < intArrayLength(array)) {
        push(t, makeInt(t, intArrayBody(array)[i]));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    intArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case iand: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) & intValue(t, b)));
  } goto loop;

  case iastore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (array) {
      if (i >= 0 and i < intArrayLength(array)) {
        intArrayBody(array)[i] = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    intArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case iconst_0: {
    push(t, makeInt(0));
  } goto loop;

  case iconst_1: {
    push(t, makeInt(1));
  } goto loop;

  case iconst_2: {
    push(t, makeInt(2));
  } goto loop;

  case iconst_3: {
    push(t, makeInt(3));
  } goto loop;

  case iconst_4: {
    push(t, makeInt(4));
  } goto loop;

  case iconst_5: {
    push(t, makeInt(5));
  } goto loop;

  case idiv: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) / intValue(t, b)));
  } goto loop;

  case if_acmpeq: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (a == b) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_acmpne: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (a != b) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpeq: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) == intValue(t, b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpne: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) != intValue(t, b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpgt: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) > intValue(t, b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpge: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) >= intValue(t, b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmplt: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) < intValue(t, b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmple: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) < intValue(t, b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifeq: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (intValue(t, v) == 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifne: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (intValue(t, v)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifgt: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (intValue(t, v) > 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifge: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (intValue(t, v) >= 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case iflt: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (intValue(t, v) < 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifle: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (intValue(t, v) <= 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifnonnull: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (v) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifnull: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    object v = pop(t);
    
    if (v == 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case iinc: {
    uint8_t index = codeBody(t, t->code)[ip++];
    int8_t c = codeBody(t, t->code)[ip++];
    
    int32_t v = intValue(t, frameBody(t, t->frame)[index]);
    frameBody(t, t->frame)[index] = makeInt(t, v + c);
  } goto loop;

  case imul: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) * intValue(t, b)));
  } goto loop;

  case ineg: {
    object v = pop(t);
    
    push(t, makeInt(t, - intValue(t, v)));
  } goto loop;

  case instanceof: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];

    if (t->stack[t->sp - 1]) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t->code), index);
      if (t->exception) goto throw_;

      if (instanceOf(t, class_, t->stack[t->sp - 1])) {
        push(t, makeInt(t, 1));
      } else {
        push(t, makeInt(t, 0));
      }
    } else {
      push(t, makeInt(t, 0));
    }
  } goto loop;

  case invokeinterface: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;
      
    ip += 2;

    object method = resolveMethod(t, codePool(t->code), index);
    if (t->exception) goto throw_;
    
    parameterCount = methodParameterCount(method);
    if (t->stack[t->sp - parameterCount]) {    
      t->code = methodCode
        (findInterfaceMethod(t, method, t->stack[t->sp - parameterCount]));
      if (t->exception) goto throw_;

      goto invoke;
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case invokespecial: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t->code), index);
    if (t->exception) goto throw_;
    
    parameterCount = methodParameterCount(method);
    if (t->stack[t->sp - parameterCount]) {
      if (isSpecialMethod(method, t->stack[t->sp - parameterCount])) {
        t->code = methodCode
          (findSpecialMethod(t, method, t->stack[t->sp - parameterCount]));
        if (t->exception) goto throw_;
      } else {
        t->code = methodCode(method);
      }
      
      goto invoke;
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case invokestatic: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t->code), index);
    if (t->exception) goto throw_;
    
    if (not classInitialized(methodClass(method))) {
      t->code = classInitializer(methodClass(method));
      ip -= 2;
      parameterCount = 0;
      goto invoke;
    }

    parameterCount = methodParameterCount(method);
    t->code = methodCode(method);
  } goto invoke;

  case invokevirtual: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t->code), index);
    if (t->exception) goto throw_;
    
    parameterCount = methodParameterCount(method);
    if (t->stack[t->sp - parameterCount]) {
      t->code = methodCode
        (findVirtualMethod(t, method, t->stack[t->sp - parameterCount]));
      if (t->exception) goto throw_;
      
      goto invoke;
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case ior: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) | intValue(t, b)));
  } goto loop;

  case irem: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) % intValue(t, b)));
  } goto loop;

  case ishl: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) << intValue(t, b)));
  } goto loop;

  case ishr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) >> intValue(t, b)));
  } goto loop;

  case isub: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) - intValue(t, b)));
  } goto loop;

  case iushr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, static_cast<uint32_t>(intValue(t, a)) >> intValue(t, b)));
  } goto loop;

  case ixor: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) ^ intValue(t, b)));
  } goto loop;

  case jsr: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];

    push(t, makeInt(ip));
    ip = (ip - 1) + ((offset1 << 8) | offset2);
  } goto loop;

  case jsr_w: {
    uint8_t offset1 = codeBody(t, t->code)[ip++];
    uint8_t offset2 = codeBody(t, t->code)[ip++];
    uint8_t offset3 = codeBody(t, t->code)[ip++];
    uint8_t offset4 = codeBody(t, t->code)[ip++];

    push(t, makeInt(ip));
    ip = (ip - 1)
      + ((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4);
  } goto loop;

  case l2i: {
    object v = pop(t);
    
    push(t, makeInt(t, static_cast<int32_t>(longValue(v))));
  } goto loop;

  case ladd: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) + longValue(b)));
  } goto loop;

  case laload: {
    object index = pop(t);
    object array = pop(t);

    if (array) {
      int32_t i = intValue(t, index);
      if (i >= 0 and i < longArrayLength(array)) {
        push(t, makeLong(t, longArrayBody(array)[i]));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    longArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case land: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) & longValue(b)));
  } goto loop;

  case lastore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (array) {
      if (i >= 0 and i < longArrayLength(array)) {
        longArrayBody(array)[i] = longValue(value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    longArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case lcmp: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, longValue(a) > longValue(b) ? 1
                 : longValue(a) == longValue(b) ? 0 : -1));
  } goto loop;

  case lconst_0: {
    push(t, makeLong(0));
  } goto loop;

  case lconst_1: {
    push(t, makeLong(1));
  } goto loop;

  case ldc: {
    push(t, codePool(t->code)[codeBody(t, t->code)[ip++]]);
  } goto loop;

  case ldc_w:
  case ldc2_w: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];

    push(t, codePool(t->code)[codeBody(t, t->code)[(offset1 << 8) | offset2]]);
  } goto loop;

  case ldiv: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) / longValue(b)));
  } goto loop;

  case lmul: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) * longValue(b)));
  } goto loop;

  case lneg: {
    object v = pop(t);
    
    push(t, makeLong(t, - longValue(v)));
  } goto loop;

  case lor: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) | longValue(b)));
  } goto loop;

  case lrem: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) % longValue(b)));
  } goto loop;

  case lshl: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) << longValue(b)));
  } goto loop;

  case lshr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) >> longValue(b)));
  } goto loop;

  case lsub: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) - longValue(b)));
  } goto loop;

  case lushr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, static_cast<uint64_t>(longValue(a)) << longValue(b)));
  } goto loop;

  case lxor: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(a) ^ longValue(b)));
  } goto loop;

  case new_: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;
    
    object class_ = resolveClass(t, codePool(t->code), index);
    if (t->exception) goto throw_;
      
    if (not classInitialized(class_)) {
      t->code = classInitializer(class_);
      ip -= 3;
      parameterCount = 0;
      goto invoke;
    }

    unsigned size = instanceSize(class_);
    object instance = allocate(t, size);
    *static_cast<object*>(instance) = class_;
    t->vm->sys->zero(static_cast<object*>(instance) + sizeof(object),
                     size - sizeof(object));
    
    push(t, instance);
  } goto loop;

  case newarray: {
    object count = pop(t);
    int32_t c = intValue(t, count);

    if (c >= 0) {
      uint8_t type = codeBody(t, t->code)[ip++];

      object array;
      unsigned factor;

      switch (type) {
      case T_BOOLEAN:
        array = makeBooleanArray(t, c);
        factor = 1;
        break;

      case T_CHAR:
        array = makeCharArray(t, c);
        factor = 2;
        break;

      case T_FLOAT:
        array = makeFloatArray(t, c);
        factor = 4;
        break;

      case T_DOUBLE:
        array = makeDoubleArray(t, c);
        factor = 8;
        break;

      case T_BYTE:
        array = makeByteArray(t, c);
        factor = 1;
        break;

      case T_SHORT:
        array = makeShortArray(t, c);
        factor = 2;
        break;

      case T_INT:
        array = makeIntArray(t, c);
        factor = 4;
        break;

      case T_LONG:
        array = makeLongArray(t, c);
        factor = 8;
        break;

      default: abort(t);
      }
      
      t->vm->sys->zero(static_cast<object*>(instance) + (sizeof(object) * 2),
                       c * factor);
      
      push(t, array);
    } else {
      object message = makeString(t, "%d", c);
      t->exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case nop: goto loop;

  case pop_: {
    -- (t->sp);
  } goto loop;

  case pop2: {
    object top = t->stack[t->sp - 1];
    if (isLongOrDouble(top)) {
      -- (t->sp);
    } else {
      t->sp -= 2;
    }
  } goto loop;

  case putfield: {
    object instance = pop(t);
    if (instance) {
      uint8_t index1 = codeBody(t, t->code)[ip++];
      uint8_t index2 = codeBody(t, t->code)[ip++];
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t->code), index);
      if (t->exception) goto throw_;
      
      object value = pop(t);
      setField(t, instance, field, value);
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case putstatic: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t->code), index);
    if (t->exception) goto throw_;

    if (not classInitialized(fieldClass(field))) {
      t->code = classInitializer(fieldClass(field));
      ip -= 3;
      parameterCount = 0;
      goto invoke;
    }
      
    object value = pop(t);
    setStatic(t, field, value);
  } goto loop;

  case ret: {
    ip = intValue(t, frameBody(t, t->frame)[codeBody(t, t->code)[ip++]]);
  } goto loop;

  case return_: {
    t->frame = frameNext(t->frame);
    if (t->frame) {
      t->code = methodCode(frameMethod(t->frame));
      ip = frameIp(t->frame);
      goto loop;
    } else {
      t->code = 0;
      return 0;
    }
  } goto loop;

  case saload: {
    object index = pop(t);
    object array = pop(t);

    if (array) {
      int32_t i = intValue(t, index);
      if (i >= 0 and i < shortArrayLength(array)) {
        push(t, makeShort(t, shortArrayBody(array)[i]));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    shortArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case sastore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (array) {
      if (i >= 0 and i < shortArrayLength(array)) {
        shortArrayBody(array)[i] = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    shortArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case sipush: {
    uint8_t byte1 = codeBody(t, t->code)[ip++];
    uint8_t byte2 = codeBody(t, t->code)[ip++];

    push(t, makeInt(t, (byte1 << 8) | byte2));
  } goto loop;

  case swap: {
    object tmp = t->stack[t->sp - 1];
    t->stack[t->sp - 1] = t->stack[t->sp - 2];
    t->stack[t->sp - 2] = tmp;
  } goto loop;

  case wide: goto wide;

  default: abort(t);
  }

 wide:
  switch (codeBody(t, t->code)[ip++]) {
  case aload:
  case iload:
  case lload: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];

    push(t, frameBody(t, t->frame)[(index1 << 8) | index2]);
  } goto loop;

  case astore:
  case istore:
  case lstore: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];

    object value = pop(t);
    set(t, frameBody(t, t->frame)[(index1 << 8) | index2], value);
  } goto loop;

  case iinc: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    uint8_t count1 = codeBody(t, t->code)[ip++];
    uint8_t count2 = codeBody(t, t->code)[ip++];
    uint16_t count = (count1 << 8) | count2;
    
    int32_t v = intValue(t, frameBody(t, t->frame)[index]);
    frameBody(t, t->frame)[index] = makeInt(t, v + count);
  } goto loop;

  case ret: {
    uint8_t index1 = codeBody(t, t->code)[ip++];
    uint8_t index2 = codeBody(t, t->code)[ip++];

    ip = intValue(t, frameBody(t, t->frame)[(index1 << 8) | index2]);
  } goto loop;

  default: abort(t);
  }

 invoke:
  if (codeMaxStack(t->code) + t->sp - parameterCount > Thread::StackSize) {
    t->exception = makeStackOverflowException(t, 0);
    goto throw_;      
  }
  
  frameIp(t->frame) = ip;
  
  t->frame = makeFrame(t, t->code, t->frame);
  memcpy(frameLocals(t->frame),
         t->stack + t->sp - parameterCount,
         parameterCount);
  t->sp -= parameterCount;
  ip = 0;
  goto loop;

 throw_:
  for (; t->frame; t->frame = frameNext(t->frame)) {
    t->code = methodCode(frameMethod(t->frame));
    object eht = codeExceptionHandlerTable(t->code);
    if (eht) {
      for (unsigned i = 0; i < exceptionHandleTableLength(eht); ++i) {
        ExceptionHandler* eh = exceptionHandlerTableBody(eht)[i];
        uint16_t catchType = exceptionHandlerCatchType(eh);
        if (catchType == 0 or
            instanceOf(rawArrayBody(codePool(t->code))[catchType],
                       t->exception))
        {
          t->sp = frameStackBase(t->frame);
          ip = exceptionHandlerIp(eh);
          push(t, t->exception);
          t->exception = 0;
          goto loop;
        }
      }
    }
  }

  t->code = defaultExceptionHandler(t);
  t->frame = makeFrame(t, t->code);
  t->sp = 0;
  ip = 0;
  push(t, t->exception);
  t->exception = 0;
  goto loop;
}

} // namespace
