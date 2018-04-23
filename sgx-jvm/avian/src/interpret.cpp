/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/common.h"
#include <avian/system/system.h>
#include <avian/system/signal.h>
#include "avian/constants.h"
#include "avian/machine.h"
#include "avian/processor.h"
#include "avian/process.h"
#include "avian/arch.h"

#include <avian/util/runtime-array.h>
#include <avian/util/list.h>
#include <avian/util/slice.h>

using namespace vm;
using namespace avian::system;

namespace local {

const unsigned FrameBaseOffset = 0;
const unsigned FrameNextOffset = 1;
const unsigned FrameMethodOffset = 2;
const unsigned FrameIpOffset = 3;
const unsigned FrameFootprint = 4;

class Thread : public vm::Thread {
 public:
  Thread(Machine* m, GcThread* javaThread, vm::Thread* parent)
      : vm::Thread(m, javaThread, parent),
        ip(0),
        sp(0),
        frame(-1),
        code(0),
        stackPointers(0)
  {
  }

  unsigned ip;
  unsigned sp;
  int frame;
  GcCode* code;
  List<unsigned>* stackPointers;
  uintptr_t stack[0];
};

inline void pushObject(Thread* t, object o)
{
  if (DebugStack) {
    fprintf(stderr, "push object %p at %d\n", o, t->sp);
  }

  assertT(t, t->sp + 1 < stackSizeInWords(t) / 2);
  t->stack[(t->sp * 2)] = ObjectTag;
  t->stack[(t->sp * 2) + 1] = reinterpret_cast<uintptr_t>(o);
  ++t->sp;
}

inline void pushInt(Thread* t, uint32_t v)
{
  if (DebugStack) {
    fprintf(stderr, "push int %d at %d\n", v, t->sp);
  }

  assertT(t, t->sp + 1 < stackSizeInWords(t) / 2);
  t->stack[(t->sp * 2)] = IntTag;
  t->stack[(t->sp * 2) + 1] = v;
  ++t->sp;
}

inline void pushFloat(Thread* t, float v)
{
  pushInt(t, floatToBits(v));
}

inline void pushLong(Thread* t, uint64_t v)
{
  if (DebugStack) {
    fprintf(stderr, "push long %" LLD " at %d\n", v, t->sp);
  }

  pushInt(t, v >> 32);
  pushInt(t, v & 0xFFFFFFFF);
}

inline void pushDouble(Thread* t, double v)
{
  uint64_t w = doubleToBits(v);
  pushLong(t, w);
}

inline object popObject(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr,
            "pop object %p at %d\n",
            reinterpret_cast<object>(t->stack[((t->sp - 1) * 2) + 1]),
            t->sp - 1);
  }

  assertT(t, t->stack[(t->sp - 1) * 2] == ObjectTag);
  return reinterpret_cast<object>(t->stack[((--t->sp) * 2) + 1]);
}

inline uint32_t popInt(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr,
            "pop int %" LD " at %d\n",
            t->stack[((t->sp - 1) * 2) + 1],
            t->sp - 1);
  }

  assertT(t, t->stack[(t->sp - 1) * 2] == IntTag);
  return t->stack[((--t->sp) * 2) + 1];
}

inline float popFloat(Thread* t)
{
  return bitsToFloat(popInt(t));
}

inline uint64_t popLong(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr,
            "pop long %" LLD " at %d\n",
            (static_cast<uint64_t>(t->stack[((t->sp - 2) * 2) + 1]) << 32)
            | static_cast<uint64_t>(t->stack[((t->sp - 1) * 2) + 1]),
            t->sp - 2);
  }

  uint64_t a = popInt(t);
  uint64_t b = popInt(t);
  return (b << 32) | a;
}

inline double popDouble(Thread* t)
{
  uint64_t v = popLong(t);
  return bitsToDouble(v);
}

inline object peekObject(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr,
            "peek object %p at %d\n",
            reinterpret_cast<object>(t->stack[(index * 2) + 1]),
            index);
  }

  assertT(t, index < stackSizeInWords(t) / 2);
  assertT(t, t->stack[index * 2] == ObjectTag);
  return reinterpret_cast<object>(t->stack[(index * 2) + 1]);
}

inline uint32_t peekInt(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(
        stderr, "peek int %" LD " at %d\n", t->stack[(index * 2) + 1], index);
  }

  assertT(t, index < stackSizeInWords(t) / 2);
  assertT(t, t->stack[index * 2] == IntTag);
  return t->stack[(index * 2) + 1];
}

inline uint64_t peekLong(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr,
            "peek long %" LLD " at %d\n",
            (static_cast<uint64_t>(t->stack[(index * 2) + 1]) << 32)
            | static_cast<uint64_t>(t->stack[((index + 1) * 2) + 1]),
            index);
  }

  return (static_cast<uint64_t>(peekInt(t, index)) << 32)
         | static_cast<uint64_t>(peekInt(t, index + 1));
}

inline void pokeObject(Thread* t, unsigned index, object value)
{
  if (DebugStack) {
    fprintf(stderr, "poke object %p at %d\n", value, index);
  }

  t->stack[index * 2] = ObjectTag;
  t->stack[(index * 2) + 1] = reinterpret_cast<uintptr_t>(value);
}

inline void pokeInt(Thread* t, unsigned index, uint32_t value)
{
  if (DebugStack) {
    fprintf(stderr, "poke int %d at %d\n", value, index);
  }

  t->stack[index * 2] = IntTag;
  t->stack[(index * 2) + 1] = value;
}

inline void pokeLong(Thread* t, unsigned index, uint64_t value)
{
  if (DebugStack) {
    fprintf(stderr, "poke long %" LLD " at %d\n", value, index);
  }

  pokeInt(t, index, value >> 32);
  pokeInt(t, index + 1, value & 0xFFFFFFFF);
}

inline object* pushReference(Thread* t, object o)
{
  if (o) {
    expect(t, t->sp + 1 < stackSizeInWords(t) / 2);
    pushObject(t, o);
    return reinterpret_cast<object*>(t->stack + ((t->sp - 1) * 2) + 1);
  } else {
    return 0;
  }
}

inline int frameNext(Thread* t, int frame)
{
  return peekInt(t, frame + FrameNextOffset);
}

inline GcMethod* frameMethod(Thread* t, int frame)
{
  return cast<GcMethod>(t, peekObject(t, frame + FrameMethodOffset));
}

inline unsigned frameIp(Thread* t, int frame)
{
  return peekInt(t, frame + FrameIpOffset);
}

inline unsigned frameBase(Thread* t, int frame)
{
  return peekInt(t, frame + FrameBaseOffset);
}

inline object localObject(Thread* t, unsigned index)
{
  return peekObject(t, frameBase(t, t->frame) + index);
}

inline uint32_t localInt(Thread* t, unsigned index)
{
  return peekInt(t, frameBase(t, t->frame) + index);
}

inline uint64_t localLong(Thread* t, unsigned index)
{
  return peekLong(t, frameBase(t, t->frame) + index);
}

inline void setLocalObject(Thread* t, unsigned index, object value)
{
  pokeObject(t, frameBase(t, t->frame) + index, value);
}

inline void setLocalInt(Thread* t, unsigned index, uint32_t value)
{
  pokeInt(t, frameBase(t, t->frame) + index, value);
}

inline void setLocalLong(Thread* t, unsigned index, uint64_t value)
{
  pokeLong(t, frameBase(t, t->frame) + index, value);
}

void pushFrame(Thread* t, GcMethod* method)
{
  PROTECT(t, method);

  unsigned parameterFootprint = method->parameterFootprint();
  unsigned base = t->sp - parameterFootprint;
  unsigned locals = parameterFootprint;

  if (method->flags() & ACC_SYNCHRONIZED) {
    // Try to acquire the monitor before doing anything else.
    // Otherwise, if we were to push the frame first, we risk trying
    // to release a monitor we never successfully acquired when we try
    // to pop the frame back off.
    if (method->flags() & ACC_STATIC) {
      acquire(t, getJClass(t, method->class_()));
    } else {
      acquire(t, peekObject(t, base));
    }
  }

  if (t->frame >= 0) {
    pokeInt(t, t->frame + FrameIpOffset, t->ip);
  }
  t->ip = 0;

  if ((method->flags() & ACC_NATIVE) == 0) {
    t->code = method->code();

    locals = t->code->maxLocals();

    memset(t->stack + ((base + parameterFootprint) * 2),
           0,
           (locals - parameterFootprint) * BytesPerWord * 2);
  }

  unsigned frame = base + locals;
  pokeInt(t, frame + FrameNextOffset, t->frame);
  t->frame = frame;

  t->sp = frame + FrameFootprint;

  pokeInt(t, frame + FrameBaseOffset, base);
  pokeObject(t, frame + FrameMethodOffset, method);
  pokeInt(t, t->frame + FrameIpOffset, 0);
}

void popFrame(Thread* t)
{
  GcMethod* method = frameMethod(t, t->frame);

  if (method->flags() & ACC_SYNCHRONIZED) {
    if (method->flags() & ACC_STATIC) {
      release(t, getJClass(t, method->class_()));
    } else {
      release(t, peekObject(t, frameBase(t, t->frame)));
    }
  }

  t->sp = frameBase(t, t->frame);
  t->frame = frameNext(t, t->frame);
  if (t->frame >= 0) {
    t->code = frameMethod(t, t->frame)->code();
    t->ip = frameIp(t, t->frame);
  } else {
    t->code = 0;
    t->ip = 0;
  }
}

class MyStackWalker : public Processor::StackWalker {
 public:
  MyStackWalker(Thread* t, int frame) : t(t), frame(frame)
  {
  }

  virtual void walk(Processor::StackVisitor* v)
  {
    for (int frame = this->frame; frame >= 0; frame = frameNext(t, frame)) {
      MyStackWalker walker(t, frame);
      if (not v->visit(&walker)) {
        break;
      }
    }
  }

  virtual GcMethod* method()
  {
    return frameMethod(t, frame);
  }

  virtual int ip()
  {
    return frameIp(t, frame);
  }

  virtual unsigned count()
  {
    unsigned count = 0;
    for (int frame = this->frame; frame >= 0; frame = frameNext(t, frame)) {
      ++count;
    }
    return count;
  }

  Thread* t;
  int frame;
};

inline void checkStack(Thread* t, GcMethod* method)
{
  if (UNLIKELY(t->sp + method->parameterFootprint()
               + method->code()->maxLocals() + FrameFootprint
               + method->code()->maxStack() > stackSizeInWords(t) / 2)) {
    throwNew(t, GcStackOverflowError::Type);
  }
}

void pushResult(Thread* t, unsigned returnCode, uint64_t result, bool indirect)
{
  switch (returnCode) {
  case ByteField:
  case BooleanField:
    if (DebugRun) {
      fprintf(stderr, "result: %d\n", static_cast<int8_t>(result));
    }
    pushInt(t, static_cast<int8_t>(result));
    break;

  case CharField:
    if (DebugRun) {
      fprintf(stderr, "result: %d\n", static_cast<uint16_t>(result));
    }
    pushInt(t, static_cast<uint16_t>(result));
    break;

  case ShortField:
    if (DebugRun) {
      fprintf(stderr, "result: %d\n", static_cast<int16_t>(result));
    }
    pushInt(t, static_cast<int16_t>(result));
    break;

  case FloatField:
  case IntField:
    if (DebugRun) {
      fprintf(stderr, "result: %d\n", static_cast<int32_t>(result));
    }
    pushInt(t, result);
    break;

  case DoubleField:
  case LongField:
    if (DebugRun) {
      fprintf(stderr, "result: %" LLD "\n", result);
    }
    pushLong(t, result);
    break;

  case ObjectField:
    if (indirect) {
      if (DebugRun) {
        fprintf(
            stderr,
            "result: %p at %p\n",
            static_cast<uintptr_t>(result) == 0
                ? 0
                : *reinterpret_cast<object*>(static_cast<uintptr_t>(result)),
            reinterpret_cast<object*>(static_cast<uintptr_t>(result)));
      }
      pushObject(
          t,
          static_cast<uintptr_t>(result) == 0
              ? 0
              : *reinterpret_cast<object*>(static_cast<uintptr_t>(result)));
    } else {
      if (DebugRun) {
        fprintf(stderr, "result: %p\n", reinterpret_cast<object>(result));
      }
      pushObject(t, reinterpret_cast<object>(result));
    }
    break;

  case VoidField:
    break;

  default:
    abort(t);
  }
}

void marshalArguments(Thread* t,
                      uintptr_t* args,
                      uint8_t* types,
                      unsigned sp,
                      GcMethod* method,
                      bool fastCallingConvention)
{
  MethodSpecIterator it(
      t, reinterpret_cast<const char*>(method->spec()->body().begin()));

  unsigned argOffset = 0;
  unsigned typeOffset = 0;

  while (it.hasNext()) {
    unsigned type = fieldType(t, fieldCode(t, *it.next()));
    if (types) {
      types[typeOffset++] = type;
    }

    switch (type) {
    case INT8_TYPE:
    case INT16_TYPE:
    case INT32_TYPE:
    case FLOAT_TYPE:
      args[argOffset++] = peekInt(t, sp++);
      break;

    case DOUBLE_TYPE:
    case INT64_TYPE: {
      uint64_t v = peekLong(t, sp);
      memcpy(args + argOffset, &v, 8);
      argOffset += fastCallingConvention ? 2 : (8 / BytesPerWord);
      sp += 2;
    } break;

    case POINTER_TYPE: {
      if (fastCallingConvention) {
        args[argOffset++] = reinterpret_cast<uintptr_t>(peekObject(t, sp++));
      } else {
        object* v = reinterpret_cast<object*>(t->stack + ((sp++) * 2) + 1);
        if (*v == 0) {
          v = 0;
        }
        args[argOffset++] = reinterpret_cast<uintptr_t>(v);
      }
    } break;

    default:
      abort(t);
    }
  }
}

unsigned invokeNativeSlow(Thread* t, GcMethod* method, void* function)
{
  PROTECT(t, method);

  pushFrame(t, method);

  unsigned footprint = method->parameterFootprint() + 1;
  if (method->flags() & ACC_STATIC) {
    ++footprint;
  }
  unsigned count = method->parameterCount() + 2;

  THREAD_RUNTIME_ARRAY(t, uintptr_t, args, footprint);
  unsigned argOffset = 0;
  THREAD_RUNTIME_ARRAY(t, uint8_t, types, count);
  unsigned typeOffset = 0;

  RUNTIME_ARRAY_BODY(args)[argOffset++] = reinterpret_cast<uintptr_t>(t);
  RUNTIME_ARRAY_BODY(types)[typeOffset++] = POINTER_TYPE;

  GcJclass* jclass = 0;
  PROTECT(t, jclass);

  unsigned sp;
  if (method->flags() & ACC_STATIC) {
    sp = frameBase(t, t->frame);
    jclass = getJClass(t, method->class_());
    RUNTIME_ARRAY_BODY(args)[argOffset++]
        = reinterpret_cast<uintptr_t>(&jclass);
  } else {
    sp = frameBase(t, t->frame);
    object* v = reinterpret_cast<object*>(t->stack + ((sp++) * 2) + 1);
    if (*v == 0) {
      v = 0;
    }
    RUNTIME_ARRAY_BODY(args)[argOffset++] = reinterpret_cast<uintptr_t>(v);
  }
  RUNTIME_ARRAY_BODY(types)[typeOffset++] = POINTER_TYPE;

  marshalArguments(t,
                   RUNTIME_ARRAY_BODY(args) + argOffset,
                   RUNTIME_ARRAY_BODY(types) + typeOffset,
                   sp,
                   method,
                   false);

  unsigned returnCode = method->returnCode();
  unsigned returnType = fieldType(t, returnCode);
  uint64_t result;

  if (DebugRun) {
    signed char *cname = method->class_() && method->class_()->name() ? method->class_()->name()->body().begin() : (signed char*) "?";
    signed char *mname = method->name() ? method->name()->body().begin() : (signed char*) "?";
    fprintf(stderr,
            "invoke native method %s.%s\n",
            cname, mname);
  }

  {
    ENTER(t, Thread::IdleState);

    bool noThrow = t->checkpoint->noThrow;
    t->checkpoint->noThrow = true;
    THREAD_RESOURCE(t, bool, noThrow, t->checkpoint->noThrow = noThrow);

    result = vm::dynamicCall(function,
                             RUNTIME_ARRAY_BODY(args),
                             RUNTIME_ARRAY_BODY(types),
                             count,
                             footprint * BytesPerWord,
                             returnType);
  }

  if (DebugRun) {
    fprintf(stderr,
            "return from native method %s.%s\n",
            frameMethod(t, t->frame)->class_()->name()->body().begin(),
            frameMethod(t, t->frame)->name()->body().begin());
  }

  popFrame(t);

  if (UNLIKELY(t->exception)) {
    GcThrowable* exception = t->exception;
    t->exception = 0;
    throw_(t, exception);
  }

  pushResult(t, returnCode, result, true);

  return returnCode;
}

unsigned invokeNative(Thread* t, GcMethod* method)
{
  PROTECT(t, method);

  resolveNative(t, method);

  GcNative* native = getMethodRuntimeData(t, method)->native();
  if (native->fast()) {
    pushFrame(t, method);

    uint64_t result;
    {
      THREAD_RESOURCE0(t, popFrame(static_cast<Thread*>(t)));

      unsigned footprint = method->parameterFootprint();
      THREAD_RUNTIME_ARRAY(t, uintptr_t, args, footprint);
      unsigned sp = frameBase(t, t->frame);
      unsigned argOffset = 0;
      if ((method->flags() & ACC_STATIC) == 0) {
        RUNTIME_ARRAY_BODY(args)[argOffset++]
            = reinterpret_cast<uintptr_t>(peekObject(t, sp++));
      }

      marshalArguments(
          t, RUNTIME_ARRAY_BODY(args) + argOffset, 0, sp, method, true);

      if(method->returnCode() != VoidField) {
        result = reinterpret_cast<FastNativeFunction>(native->function())(
          t, method, RUNTIME_ARRAY_BODY(args));
      }
      else {
        result = 0;
        reinterpret_cast<FastVoidNativeFunction>(native->function())(
          t, method, RUNTIME_ARRAY_BODY(args));
      }
    }

    pushResult(t, method->returnCode(), result, false);

    return method->returnCode();
  } else {
    return invokeNativeSlow(t, method, native->function());
  }
}

inline void store(Thread* t, unsigned index)
{
  memcpy(t->stack + ((frameBase(t, t->frame) + index) * 2),
         t->stack + ((--t->sp) * 2),
         BytesPerWord * 2);
}

bool isNaN(double v)
{
  return fpclassify(v) == FP_NAN;
}

bool isNaN(float v)
{
  return fpclassify(v) == FP_NAN;
}

uint64_t findExceptionHandler(Thread* t, GcMethod* method, unsigned ip)
{
  PROTECT(t, method);

  GcExceptionHandlerTable* eht = cast<GcExceptionHandlerTable>(
      t, method->code()->exceptionHandlerTable());

  if (eht) {
    for (unsigned i = 0; i < eht->length(); ++i) {
      uint64_t eh = eht->body()[i];

      if (ip - 1 >= exceptionHandlerStart(eh)
          and ip - 1 < exceptionHandlerEnd(eh)) {
        GcClass* catchType = 0;
        if (exceptionHandlerCatchType(eh)) {
          GcThrowable* e = t->exception;
          t->exception = 0;
          PROTECT(t, e);

          PROTECT(t, eht);
          catchType = resolveClassInPool(
              t, method, exceptionHandlerCatchType(eh) - 1);

          if (catchType) {
            eh = eht->body()[i];
            t->exception = e;
          } else {
            // can't find what we're supposed to catch - move on.
            continue;
          }
        }

        if (exceptionMatch(t, catchType, t->exception)) {
          return eh;
        }
      }
    }
  }

  return 0;
}

uint64_t findExceptionHandler(Thread* t, int frame)
{
  return findExceptionHandler(t, frameMethod(t, frame), frameIp(t, frame));
}

void pushField(Thread* t, object target, GcField* field)
{
  switch (field->code()) {
  case ByteField:
  case BooleanField:
    pushInt(t, fieldAtOffset<int8_t>(target, field->offset()));
    break;

  case CharField:
  case ShortField:
    pushInt(t, fieldAtOffset<int16_t>(target, field->offset()));
    break;

  case FloatField:
  case IntField:
    pushInt(t, fieldAtOffset<int32_t>(target, field->offset()));
    break;

  case DoubleField:
  case LongField:
    pushLong(t, fieldAtOffset<int64_t>(target, field->offset()));
    break;

  case ObjectField:
    pushObject(t, fieldAtOffset<object>(target, field->offset()));
    break;

  default:
    abort(t);
  }
}

void safePoint(Thread* t)
{
  if (UNLIKELY(t->m->exclusive)) {
    ENTER(t, Thread::IdleState);
  }
}

object interpret3(Thread* t, const int base)
{
  unsigned instruction = nop;
  unsigned& ip = t->ip;
  unsigned& sp = t->sp;
  int& frame = t->frame;
  GcCode*& code = t->code;
  GcMethod* method = 0;
  PROTECT(t, method);
  GcThrowable*& exception = t->exception;
  uintptr_t* stack = t->stack;

  code = frameMethod(t, frame)->code();

  if (UNLIKELY(exception)) {
    goto throw_;
  }

loop:
  instruction = code->body()[ip++];

  if (DebugRun) {
    GcMethod *method_ = frameMethod(t, frame);
    signed char *cname = method_->class_() && method_->class_()->name() ? method_->class_()->name()->body().begin() : (signed char*) "?";
    signed char *mname = method_->name() ? method_->name()->body().begin() : (signed char*) "?";
    fprintf(stderr,
            "ip: %d; instruction: 0x%x in %s.%s ",
            ip - 1,
            instruction, cname, mname);

    int line = findLineNumber(t, frameMethod(t, frame), ip);
    switch (line) {
    case NativeLine:
      fprintf(stderr, "(native)\n");
      break;
    case UnknownLine:
      fprintf(stderr, "(unknown line)\n");
      break;
    default:
      fprintf(stderr, "(line %d)\n", line);
    }
  }

  switch (instruction) {
  case aaload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0
                 and static_cast<uintptr_t>(index)
                     < objectArrayLength(t, array))) {
        pushObject(t, objectArrayBody(t, array, index));
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  objectArrayLength(t, array));
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case aastore: {
    object value = popObject(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0
                 and static_cast<uintptr_t>(index)
                     < objectArrayLength(t, array))) {
        setField(t, array, ArrayBody + (index * BytesPerWord), value);
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  objectArrayLength(t, array));
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case aconst_null: {
    pushObject(t, 0);
  }
    goto loop;

  case aload: {
    pushObject(t, localObject(t, code->body()[ip++]));
  }
    goto loop;

  case aload_0: {
    pushObject(t, localObject(t, 0));
  }
    goto loop;

  case aload_1: {
    pushObject(t, localObject(t, 1));
  }
    goto loop;

  case aload_2: {
    pushObject(t, localObject(t, 2));
  }
    goto loop;

  case aload_3: {
    pushObject(t, localObject(t, 3));
  }
    goto loop;

  case anewarray: {
    int32_t count = popInt(t);

    if (LIKELY(count >= 0)) {
      uint16_t index = codeReadInt16(t, code, ip);

      GcClass* class_ = resolveClassInPool(t, frameMethod(t, frame), index - 1);

      pushObject(t, makeObjectArray(t, class_, count));
    } else {
      exception
          = makeThrowable(t, GcNegativeArraySizeException::Type, "%d", count);
      goto throw_;
    }
  }
    goto loop;

  case areturn: {
    object result = popObject(t);
    if (frame > base) {
      popFrame(t);
      pushObject(t, result);
      goto loop;
    } else {
      return result;
    }
  }
    goto loop;

  case arraylength: {
    object array = popObject(t);
    if (LIKELY(array)) {
      pushInt(t, fieldAtOffset<uintptr_t>(array, BytesPerWord));
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case astore: {
    store(t, code->body()[ip++]);
  }
    goto loop;

  case astore_0: {
    store(t, 0);
  }
    goto loop;

  case astore_1: {
    store(t, 1);
  }
    goto loop;

  case astore_2: {
    store(t, 2);
  }
    goto loop;

  case astore_3: {
    store(t, 3);
  }
    goto loop;

  case athrow: {
    exception = cast<GcThrowable>(t, popObject(t));
    if (UNLIKELY(exception == 0)) {
      exception = makeThrowable(t, GcNullPointerException::Type);
    }
  }
    goto throw_;

  case baload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (objectClass(t, array) == type(t, GcBooleanArray::Type)) {
        GcBooleanArray* a = cast<GcBooleanArray>(t, array);
        if (LIKELY(index >= 0
                   and static_cast<uintptr_t>(index) < a->length())) {
          pushInt(t, a->body()[index]);
        } else {
          exception = makeThrowable(t,
                                    GcArrayIndexOutOfBoundsException::Type,
                                    "%d not in [0,%d)",
                                    index,
                                    a->length());
          goto throw_;
        }
      } else {
        GcByteArray* a = cast<GcByteArray>(t, array);
        if (LIKELY(index >= 0
                   and static_cast<uintptr_t>(index) < a->length())) {
          pushInt(t, a->body()[index]);
        } else {
          exception = makeThrowable(t,
                                    GcArrayIndexOutOfBoundsException::Type,
                                    "%d not in [0,%d)",
                                    index,
                                    a->length());
          goto throw_;
        }
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case bastore: {
    int8_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (objectClass(t, array) == type(t, GcBooleanArray::Type)) {
        GcBooleanArray* a = cast<GcBooleanArray>(t, array);
        if (LIKELY(index >= 0
                   and static_cast<uintptr_t>(index) < a->length())) {
          a->body()[index] = value;
        } else {
          exception = makeThrowable(t,
                                    GcArrayIndexOutOfBoundsException::Type,
                                    "%d not in [0,%d)",
                                    index,
                                    a->length());
          goto throw_;
        }
      } else {
        GcByteArray* a = cast<GcByteArray>(t, array);
        if (LIKELY(index >= 0
                   and static_cast<uintptr_t>(index) < a->length())) {
          a->body()[index] = value;
        } else {
          exception = makeThrowable(t,
                                    GcArrayIndexOutOfBoundsException::Type,
                                    "%d not in [0,%d)",
                                    index,
                                    a->length());
          goto throw_;
        }
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case bipush: {
    pushInt(t, static_cast<int8_t>(code->body()[ip++]));
  }
    goto loop;

  case caload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcCharArray* a = cast<GcCharArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        pushInt(t, a->body()[index]);
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case castore: {
    uint16_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcCharArray* a = cast<GcCharArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        a->body()[index] = value;
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case checkcast: {
    uint16_t index = codeReadInt16(t, code, ip);

    if (peekObject(t, sp - 1)) {
      GcClass* class_ = resolveClassInPool(t, frameMethod(t, frame), index - 1);
      if (UNLIKELY(exception))
        goto throw_;

      if (not instanceOf(t, class_, peekObject(t, sp - 1))) {
        exception = makeThrowable(
            t,
            GcClassCastException::Type,
            "%s as %s",
            objectClass(t, peekObject(t, sp - 1))->name()->body().begin(),
            class_->name()->body().begin());
        goto throw_;
      }
    }
  }
    goto loop;

  case d2f: {
    pushFloat(t, static_cast<float>(popDouble(t)));
  }
    goto loop;

  case d2i: {
    double f = popDouble(t);
    switch (fpclassify(f)) {
    case FP_NAN:
      pushInt(t, 0);
      break;
    case FP_INFINITE:
      pushInt(t, signbit(f) ? INT32_MIN : INT32_MAX);
      break;
    default:
      pushInt(t,
              f >= INT32_MAX
                  ? INT32_MAX
                  : (f <= INT32_MIN ? INT32_MIN : static_cast<int32_t>(f)));
      break;
    }
  }
    goto loop;

  case d2l: {
    double f = popDouble(t);
    switch (fpclassify(f)) {
    case FP_NAN:
      pushLong(t, 0);
      break;
    case FP_INFINITE:
      pushLong(t, signbit(f) ? INT64_MIN : INT64_MAX);
      break;
    default:
      pushLong(t,
               f >= INT64_MAX
                   ? INT64_MAX
                   : (f <= INT64_MIN ? INT64_MIN : static_cast<int64_t>(f)));
      break;
    }
  }
    goto loop;

  case dadd: {
    double b = popDouble(t);
    double a = popDouble(t);

    pushDouble(t, a + b);
  }
    goto loop;

  case daload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcDoubleArray* a = cast<GcDoubleArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        pushLong(t, a->body()[index]);
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case dastore: {
    double value = popDouble(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcDoubleArray* a = cast<GcDoubleArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        memcpy(&a->body()[index], &value, sizeof(uint64_t));
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case dcmpg: {
    double b = popDouble(t);
    double a = popDouble(t);

    if (isNaN(a) or isNaN(b)) {
      pushInt(t, 1);
    }
    if (a < b) {
      pushInt(t, static_cast<unsigned>(-1));
    } else if (a > b) {
      pushInt(t, 1);
    } else if (a == b) {
      pushInt(t, 0);
    } else {
      pushInt(t, 1);
    }
  }
    goto loop;

  case dcmpl: {
    double b = popDouble(t);
    double a = popDouble(t);

    if (isNaN(a) or isNaN(b)) {
      pushInt(t, static_cast<unsigned>(-1));
    }
    if (a < b) {
      pushInt(t, static_cast<unsigned>(-1));
    } else if (a > b) {
      pushInt(t, 1);
    } else if (a == b) {
      pushInt(t, 0);
    } else {
      pushInt(t, static_cast<unsigned>(-1));
    }
  }
    goto loop;

  case dconst_0: {
    pushDouble(t, 0);
  }
    goto loop;

  case dconst_1: {
    pushDouble(t, 1);
  }
    goto loop;

  case ddiv: {
    double b = popDouble(t);
    double a = popDouble(t);

    pushDouble(t, a / b);
  }
    goto loop;

  case dmul: {
    double b = popDouble(t);
    double a = popDouble(t);

    pushDouble(t, a * b);
  }
    goto loop;

  case dneg: {
    double a = popDouble(t);

    pushDouble(t, -a);
  }
    goto loop;

  case vm::drem: {
    double b = popDouble(t);
    double a = popDouble(t);

    pushDouble(t, fmod(a, b));
  }
    goto loop;

  case dsub: {
    double b = popDouble(t);
    double a = popDouble(t);

    pushDouble(t, a - b);
  }
    goto loop;

  case vm::dup: {
    if (DebugStack) {
      fprintf(stderr, "dup\n");
    }

    memcpy(stack + ((sp)*2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    ++sp;
  }
    goto loop;

  case dup_x1: {
    if (DebugStack) {
      fprintf(stderr, "dup_x1\n");
    }

    memcpy(stack + ((sp)*2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp)*2), BytesPerWord * 2);
    ++sp;
  }
    goto loop;

  case dup_x2: {
    if (DebugStack) {
      fprintf(stderr, "dup_x2\n");
    }

    memcpy(stack + ((sp)*2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 3) * 2), stack + ((sp)*2), BytesPerWord * 2);
    ++sp;
  }
    goto loop;

  case vm::dup2: {
    if (DebugStack) {
      fprintf(stderr, "dup2\n");
    }

    memcpy(stack + ((sp)*2), stack + ((sp - 2) * 2), BytesPerWord * 4);
    sp += 2;
  }
    goto loop;

  case dup2_x1: {
    if (DebugStack) {
      fprintf(stderr, "dup2_x1\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp)*2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 3) * 2), stack + ((sp)*2), BytesPerWord * 4);
    sp += 2;
  }
    goto loop;

  case dup2_x2: {
    if (DebugStack) {
      fprintf(stderr, "dup2_x2\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp)*2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp - 4) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 4) * 2), stack + ((sp)*2), BytesPerWord * 4);
    sp += 2;
  }
    goto loop;

  case f2d: {
    pushDouble(t, popFloat(t));
  }
    goto loop;

  case f2i: {
    float f = popFloat(t);
    switch (fpclassify(f)) {
    case FP_NAN:
      pushInt(t, 0);
      break;
    case FP_INFINITE:
      pushInt(t, signbit(f) ? INT32_MIN : INT32_MAX);
      break;
    default:
      pushInt(t,
              f >= INT32_MAX
                  ? INT32_MAX
                  : (f <= INT32_MIN ? INT32_MIN : static_cast<int32_t>(f)));
      break;
    }
  }
    goto loop;

  case f2l: {
    float f = popFloat(t);
    switch (fpclassify(f)) {
    case FP_NAN:
      pushLong(t, 0);
      break;
    case FP_INFINITE:
      pushLong(t, signbit(f) ? INT64_MIN : INT64_MAX);
      break;
    default:
      pushLong(t, static_cast<int64_t>(f));
      break;
    }
  }
    goto loop;

  case fadd: {
    float b = popFloat(t);
    float a = popFloat(t);

    pushFloat(t, a + b);
  }
    goto loop;

  case faload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcFloatArray* a = cast<GcFloatArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        pushInt(t, a->body()[index]);
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case fastore: {
    float value = popFloat(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcFloatArray* a = cast<GcFloatArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        memcpy(&a->body()[index], &value, sizeof(uint32_t));
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case fcmpg: {
    float b = popFloat(t);
    float a = popFloat(t);

    if (isNaN(a) or isNaN(b)) {
      pushInt(t, 1);
    }
    if (a < b) {
      pushInt(t, static_cast<unsigned>(-1));
    } else if (a > b) {
      pushInt(t, 1);
    } else if (a == b) {
      pushInt(t, 0);
    } else {
      pushInt(t, 1);
    }
  }
    goto loop;

  case fcmpl: {
    float b = popFloat(t);
    float a = popFloat(t);

    if (isNaN(a) or isNaN(b)) {
      pushInt(t, static_cast<unsigned>(-1));
    }
    if (a < b) {
      pushInt(t, static_cast<unsigned>(-1));
    } else if (a > b) {
      pushInt(t, 1);
    } else if (a == b) {
      pushInt(t, 0);
    } else {
      pushInt(t, static_cast<unsigned>(-1));
    }
  }
    goto loop;

  case fconst_0: {
    pushFloat(t, 0);
  }
    goto loop;

  case fconst_1: {
    pushFloat(t, 1);
  }
    goto loop;

  case fconst_2: {
    pushFloat(t, 2);
  }
    goto loop;

  case fdiv: {
    float b = popFloat(t);
    float a = popFloat(t);

    pushFloat(t, a / b);
  }
    goto loop;

  case fmul: {
    float b = popFloat(t);
    float a = popFloat(t);

    pushFloat(t, a * b);
  }
    goto loop;

  case fneg: {
    float a = popFloat(t);

    pushFloat(t, -a);
  }
    goto loop;

  case frem: {
    float b = popFloat(t);
    float a = popFloat(t);

    pushFloat(t, fmodf(a, b));
  }
    goto loop;

  case fsub: {
    float b = popFloat(t);
    float a = popFloat(t);

    pushFloat(t, a - b);
  }
    goto loop;

  case getfield: {
    if (LIKELY(peekObject(t, sp - 1))) {
      uint16_t index = codeReadInt16(t, code, ip);

      GcField* field = resolveField(t, frameMethod(t, frame), index - 1);

      assertT(t, (field->flags() & ACC_STATIC) == 0);

      PROTECT(t, field);

      ACQUIRE_FIELD_FOR_READ(t, field);

      pushField(t, popObject(t), field);
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case getstatic: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcField* field = resolveField(t, frameMethod(t, frame), index - 1);

    assertT(t, field->flags() & ACC_STATIC);

    PROTECT(t, field);

    initClass(t, field->class_());

    ACQUIRE_FIELD_FOR_READ(t, field);

    pushField(t, field->class_()->staticTable(), field);
  }
    goto loop;

  case goto_: {
    int16_t offset = codeReadInt16(t, code, ip);
    ip = (ip - 3) + offset;
  }
    goto back_branch;

  case goto_w: {
    int32_t offset = codeReadInt32(t, code, ip);
    ip = (ip - 5) + offset;
  }
    goto back_branch;

  case i2b: {
    pushInt(t, static_cast<int8_t>(popInt(t)));
  }
    goto loop;

  case i2c: {
    pushInt(t, static_cast<uint16_t>(popInt(t)));
  }
    goto loop;

  case i2d: {
    pushDouble(t, static_cast<double>(static_cast<int32_t>(popInt(t))));
  }
    goto loop;

  case i2f: {
    pushFloat(t, static_cast<float>(static_cast<int32_t>(popInt(t))));
  }
    goto loop;

  case i2l: {
    pushLong(t, static_cast<int32_t>(popInt(t)));
  }
    goto loop;

  case i2s: {
    pushInt(t, static_cast<int16_t>(popInt(t)));
  }
    goto loop;

  case iadd: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a + b);
  }
    goto loop;

  case iaload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcIntArray* a = cast<GcIntArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        pushInt(t, a->body()[index]);
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case iand: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a & b);
  }
    goto loop;

  case iastore: {
    int32_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcIntArray* a = cast<GcIntArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        a->body()[index] = value;
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case iconst_m1: {
    pushInt(t, static_cast<unsigned>(-1));
  }
    goto loop;

  case iconst_0: {
    pushInt(t, 0);
  }
    goto loop;

  case iconst_1: {
    pushInt(t, 1);
  }
    goto loop;

  case iconst_2: {
    pushInt(t, 2);
  }
    goto loop;

  case iconst_3: {
    pushInt(t, 3);
  }
    goto loop;

  case iconst_4: {
    pushInt(t, 4);
  }
    goto loop;

  case iconst_5: {
    pushInt(t, 5);
  }
    goto loop;

  case idiv: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (UNLIKELY(b == 0)) {
      exception = makeThrowable(t, GcArithmeticException::Type);
      goto throw_;
    }

    pushInt(t, a / b);
  }
    goto loop;

  case if_acmpeq: {
    int16_t offset = codeReadInt16(t, code, ip);

    object b = popObject(t);
    object a = popObject(t);

    if (a == b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case if_acmpne: {
    int16_t offset = codeReadInt16(t, code, ip);

    object b = popObject(t);
    object a = popObject(t);

    if (a != b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case if_icmpeq: {
    int16_t offset = codeReadInt16(t, code, ip);

    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (a == b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case if_icmpne: {
    int16_t offset = codeReadInt16(t, code, ip);

    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (a != b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case if_icmpgt: {
    int16_t offset = codeReadInt16(t, code, ip);

    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (a > b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case if_icmpge: {
    int16_t offset = codeReadInt16(t, code, ip);

    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (a >= b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case if_icmplt: {
    int16_t offset = codeReadInt16(t, code, ip);

    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (a < b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case if_icmple: {
    int16_t offset = codeReadInt16(t, code, ip);

    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (a <= b) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case ifeq: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (popInt(t) == 0) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case ifne: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (popInt(t)) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case ifgt: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (static_cast<int32_t>(popInt(t)) > 0) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case ifge: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (static_cast<int32_t>(popInt(t)) >= 0) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case iflt: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (static_cast<int32_t>(popInt(t)) < 0) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case ifle: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (static_cast<int32_t>(popInt(t)) <= 0) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case ifnonnull: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (popObject(t)) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case ifnull: {
    int16_t offset = codeReadInt16(t, code, ip);

    if (popObject(t) == 0) {
      ip = (ip - 3) + offset;
    }
  }
    goto back_branch;

  case iinc: {
    uint8_t index = code->body()[ip++];
    int8_t c = code->body()[ip++];

    setLocalInt(t, index, localInt(t, index) + c);
  }
    goto loop;

  case iload:
  case fload: {
    pushInt(t, localInt(t, code->body()[ip++]));
  }
    goto loop;

  case iload_0:
  case fload_0: {
    pushInt(t, localInt(t, 0));
  }
    goto loop;

  case iload_1:
  case fload_1: {
    pushInt(t, localInt(t, 1));
  }
    goto loop;

  case iload_2:
  case fload_2: {
    pushInt(t, localInt(t, 2));
  }
    goto loop;

  case iload_3:
  case fload_3: {
    pushInt(t, localInt(t, 3));
  }
    goto loop;

  case imul: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a * b);
  }
    goto loop;

  case ineg: {
    pushInt(t, -popInt(t));
  }
    goto loop;

  case instanceof: {
    uint16_t index = codeReadInt16(t, code, ip);

    if (peekObject(t, sp - 1)) {
      GcClass* class_ = resolveClassInPool(t, frameMethod(t, frame), index - 1);

      if (instanceOf(t, class_, popObject(t))) {
        pushInt(t, 1);
      } else {
        pushInt(t, 0);
      }
    } else {
      popObject(t);
      pushInt(t, 0);
    }
  }
    goto loop;

  case invokedynamic: {
    uint16_t index = codeReadInt16(t, code, ip);

    ip += 2;

    GcInvocation* invocation = cast<GcInvocation>(t, singletonObject(t, code->pool(), index - 1));

    GcCallSite* site = invocation->site();

    loadMemoryBarrier();

    if (site == 0) {
      PROTECT(t, invocation);

      invocation->setClass(t, frameMethod(t, frame)->class_());

      site = resolveDynamic(t, invocation);
      PROTECT(t, site);

      storeStoreMemoryBarrier();

      invocation->setSite(t, site);
      site->setInvocation(t, invocation);
    }

    method = site->target()->method();
  } goto invoke;

  case invokeinterface: {
    uint16_t index = codeReadInt16(t, code, ip);

    ip += 2;

    GcMethod* m = resolveMethod(t, frameMethod(t, frame), index - 1);

    unsigned parameterFootprint = m->parameterFootprint();
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      method = findInterfaceMethod(
          t, m, objectClass(t, peekObject(t, sp - parameterFootprint)));
      goto invoke;
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case invokespecial: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcMethod* m = resolveMethod(t, frameMethod(t, frame), index - 1);

    unsigned parameterFootprint = m->parameterFootprint();
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      GcClass* class_ = frameMethod(t, frame)->class_();
      if (isSpecialMethod(t, m, class_)) {
        class_ = class_->super();
        PROTECT(t, m);
        PROTECT(t, class_);

        initClass(t, class_);

        method = findVirtualMethod(t, m, class_);
      } else {
        method = m;
      }

      goto invoke;
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case invokestatic: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcMethod* m = resolveMethod(t, frameMethod(t, frame), index - 1);
    PROTECT(t, m);

    initClass(t, m->class_());

    method = m;
  }
    goto invoke;

  case invokevirtual: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcMethod* m = resolveMethod(t, frameMethod(t, frame), index - 1);

    unsigned parameterFootprint = m->parameterFootprint();
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      GcClass* class_ = objectClass(t, peekObject(t, sp - parameterFootprint));
      PROTECT(t, m);
      PROTECT(t, class_);

      method = findVirtualMethod(t, m, class_);
      goto invoke;
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case ior: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a | b);
  }
    goto loop;

  case irem: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    if (UNLIKELY(b == 0)) {
      exception = makeThrowable(t, GcArithmeticException::Type);
      goto throw_;
    }

    pushInt(t, a % b);
  }
    goto loop;

  case ireturn:
  case freturn: {
    int32_t result = popInt(t);
    if (frame > base) {
      popFrame(t);
      pushInt(t, result);
      goto loop;
    } else {
      return makeInt(t, result);
    }
  }
    goto loop;

  case ishl: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a << (b & 0x1F));
  }
    goto loop;

  case ishr: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a >> (b & 0x1F));
  }
    goto loop;

  case istore:
  case fstore: {
    setLocalInt(t, code->body()[ip++], popInt(t));
  }
    goto loop;

  case istore_0:
  case fstore_0: {
    setLocalInt(t, 0, popInt(t));
  }
    goto loop;

  case istore_1:
  case fstore_1: {
    setLocalInt(t, 1, popInt(t));
  }
    goto loop;

  case istore_2:
  case fstore_2: {
    setLocalInt(t, 2, popInt(t));
  }
    goto loop;

  case istore_3:
  case fstore_3: {
    setLocalInt(t, 3, popInt(t));
  }
    goto loop;

  case isub: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a - b);
  }
    goto loop;

  case iushr: {
    int32_t b = popInt(t);
    uint32_t a = popInt(t);

    pushInt(t, a >> (b & 0x1F));
  }
    goto loop;

  case ixor: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);

    pushInt(t, a ^ b);
  }
    goto loop;

  case jsr: {
    uint16_t offset = codeReadInt16(t, code, ip);

    pushInt(t, ip);
    ip = (ip - 3) + static_cast<int16_t>(offset);
  }
    goto loop;

  case jsr_w: {
    uint32_t offset = codeReadInt32(t, code, ip);

    pushInt(t, ip);
    ip = (ip - 5) + static_cast<int32_t>(offset);
  }
    goto loop;

  case l2d: {
    pushDouble(t, static_cast<double>(static_cast<int64_t>(popLong(t))));
  }
    goto loop;

  case l2f: {
    pushFloat(t, static_cast<float>(static_cast<int64_t>(popLong(t))));
  }
    goto loop;

  case l2i: {
    pushInt(t, static_cast<int32_t>(popLong(t)));
  }
    goto loop;

  case ladd: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    pushLong(t, a + b);
  }
    goto loop;

  case laload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcLongArray* a = cast<GcLongArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        pushLong(t, a->body()[index]);
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case land: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    pushLong(t, a & b);
  }
    goto loop;

  case lastore: {
    int64_t value = popLong(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcLongArray* a = cast<GcLongArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        a->body()[index] = value;
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case lcmp: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    pushInt(t, a > b ? 1 : a == b ? 0 : -1);
  }
    goto loop;

  case lconst_0: {
    pushLong(t, 0);
  }
    goto loop;

  case lconst_1: {
    pushLong(t, 1);
  }
    goto loop;

  case ldc:
  case ldc_w: {
    uint16_t index;

    if (instruction == ldc) {
      index = code->body()[ip++];
    } else {
      index = codeReadInt16(t, code, ip);
    }

    GcSingleton* pool = code->pool();

    if (singletonIsObject(t, pool, index - 1)) {
      object v = singletonObject(t, pool, index - 1);
      if (objectClass(t, v) == type(t, GcReference::Type)) {
        GcClass* class_
            = resolveClassInPool(t, frameMethod(t, frame), index - 1);

        pushObject(t, reinterpret_cast<object>(getJClass(t, class_)));
      } else if (objectClass(t, v) == type(t, GcClass::Type)) {
        pushObject(t,
                   reinterpret_cast<object>(getJClass(t, cast<GcClass>(t, v))));
      } else {
        pushObject(t, v);
      }
    } else {
      pushInt(t, singletonValue(t, pool, index - 1));
    }
  }
    goto loop;

  case ldc2_w: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcSingleton* pool = code->pool();

    uint64_t v;
    memcpy(&v, &singletonValue(t, pool, index - 1), 8);
    pushLong(t, v);
  }
    goto loop;

  case ldiv_: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    if (UNLIKELY(b == 0)) {
      exception = makeThrowable(t, GcArithmeticException::Type);
      goto throw_;
    }

    pushLong(t, a / b);
  }
    goto loop;

  case lload:
  case dload: {
    pushLong(t, localLong(t, code->body()[ip++]));
  }
    goto loop;

  case lload_0:
  case dload_0: {
    pushLong(t, localLong(t, 0));
  }
    goto loop;

  case lload_1:
  case dload_1: {
    pushLong(t, localLong(t, 1));
  }
    goto loop;

  case lload_2:
  case dload_2: {
    pushLong(t, localLong(t, 2));
  }
    goto loop;

  case lload_3:
  case dload_3: {
    pushLong(t, localLong(t, 3));
  }
    goto loop;

  case lmul: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    pushLong(t, a * b);
  }
    goto loop;

  case lneg: {
    pushLong(t, -popLong(t));
  }
    goto loop;

  case lookupswitch: {
    int32_t base = ip - 1;

    ip += 3;
    ip -= (ip % 4);

    int32_t default_ = codeReadInt32(t, code, ip);
    int32_t pairCount = codeReadInt32(t, code, ip);

    int32_t key = popInt(t);

    int32_t bottom = 0;
    int32_t top = pairCount;
    for (int32_t span = top - bottom; span; span = top - bottom) {
      int32_t middle = bottom + (span / 2);
      unsigned index = ip + (middle * 8);

      int32_t k = codeReadInt32(t, code, index);

      if (key < k) {
        top = middle;
      } else if (key > k) {
        bottom = middle + 1;
      } else {
        ip = base + codeReadInt32(t, code, index);
        goto loop;
      }
    }

    ip = base + default_;
  }
    goto loop;

  case lor: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    pushLong(t, a | b);
  }
    goto loop;

  case lrem: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    if (UNLIKELY(b == 0)) {
      exception = makeThrowable(t, GcArithmeticException::Type);
      goto throw_;
    }

    pushLong(t, a % b);
  }
    goto loop;

  case lreturn:
  case dreturn: {
    int64_t result = popLong(t);
    if (frame > base) {
      popFrame(t);
      pushLong(t, result);
      goto loop;
    } else {
      return makeLong(t, result);
    }
  }
    goto loop;

  case lshl: {
    int32_t b = popInt(t);
    int64_t a = popLong(t);

    pushLong(t, a << (b & 0x3F));
  }
    goto loop;

  case lshr: {
    int32_t b = popInt(t);
    int64_t a = popLong(t);

    pushLong(t, a >> (b & 0x3F));
  }
    goto loop;

  case lstore:
  case dstore: {
    setLocalLong(t, code->body()[ip++], popLong(t));
  }
    goto loop;

  case lstore_0:
  case dstore_0: {
    setLocalLong(t, 0, popLong(t));
  }
    goto loop;

  case lstore_1:
  case dstore_1: {
    setLocalLong(t, 1, popLong(t));
  }
    goto loop;

  case lstore_2:
  case dstore_2: {
    setLocalLong(t, 2, popLong(t));
  }
    goto loop;

  case lstore_3:
  case dstore_3: {
    setLocalLong(t, 3, popLong(t));
  }
    goto loop;

  case lsub: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    pushLong(t, a - b);
  }
    goto loop;

  case lushr: {
    int64_t b = popInt(t);
    uint64_t a = popLong(t);

    pushLong(t, a >> (b & 0x3F));
  }
    goto loop;

  case lxor: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);

    pushLong(t, a ^ b);
  }
    goto loop;

  case monitorenter: {
    object o = popObject(t);
    if (LIKELY(o)) {
      acquire(t, o);
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case monitorexit: {
    object o = popObject(t);
    if (LIKELY(o)) {
      release(t, o);
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case multianewarray: {
    uint16_t index = codeReadInt16(t, code, ip);
    uint8_t dimensions = code->body()[ip++];

    GcClass* class_ = resolveClassInPool(t, frameMethod(t, frame), index - 1);
    PROTECT(t, class_);

    THREAD_RUNTIME_ARRAY(t, int32_t, counts, dimensions);
    for (int i = dimensions - 1; i >= 0; --i) {
      RUNTIME_ARRAY_BODY(counts)[i] = popInt(t);
      if (UNLIKELY(RUNTIME_ARRAY_BODY(counts)[i] < 0)) {
        exception = makeThrowable(t,
                                  GcNegativeArraySizeException::Type,
                                  "%d",
                                  RUNTIME_ARRAY_BODY(counts)[i]);
        goto throw_;
      }
    }

    object array = makeArray(t, RUNTIME_ARRAY_BODY(counts)[0]);
    setObjectClass(t, array, class_);
    PROTECT(t, array);

    populateMultiArray(t, array, RUNTIME_ARRAY_BODY(counts), 0, dimensions);

    pushObject(t, array);
  }
    goto loop;

  case new_: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcClass* class_ = resolveClassInPool(t, frameMethod(t, frame), index - 1);
    PROTECT(t, class_);

    initClass(t, class_);

    pushObject(t, make(t, class_));
  }
    goto loop;

  case newarray: {
    int32_t count = popInt(t);

    if (LIKELY(count >= 0)) {
      uint8_t type = code->body()[ip++];

      object array;

      switch (type) {
      case T_BOOLEAN:
        array = makeBooleanArray(t, count);
        break;

      case T_CHAR:
        array = makeCharArray(t, count);
        break;

      case T_FLOAT:
        array = makeFloatArray(t, count);
        break;

      case T_DOUBLE:
        array = makeDoubleArray(t, count);
        break;

      case T_BYTE:
        array = makeByteArray(t, count);
        break;

      case T_SHORT:
        array = makeShortArray(t, count);
        break;

      case T_INT:
        array = makeIntArray(t, count);
        break;

      case T_LONG:
        array = makeLongArray(t, count);
        break;

      default:
        abort(t);
      }

      pushObject(t, array);
    } else {
      exception
          = makeThrowable(t, GcNegativeArraySizeException::Type, "%d", count);
      goto throw_;
    }
  }
    goto loop;

  case nop:
    goto loop;

  case pop_: {
    --sp;
  }
    goto loop;

  case pop2: {
    sp -= 2;
  }
    goto loop;

  case putfield: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcField* field = resolveField(t, frameMethod(t, frame), index - 1);

    assertT(t, (field->flags() & ACC_STATIC) == 0);
    PROTECT(t, field);

    {
      ACQUIRE_FIELD_FOR_WRITE(t, field);

      switch (field->code()) {
      case ByteField:
      case BooleanField:
      case CharField:
      case ShortField:
      case FloatField:
      case IntField: {
        int32_t value = popInt(t);
        object o = popObject(t);
        if (LIKELY(o)) {
          switch (field->code()) {
          case ByteField:
          case BooleanField:
            fieldAtOffset<int8_t>(o, field->offset()) = value;
            break;

          case CharField:
          case ShortField:
            fieldAtOffset<int16_t>(o, field->offset()) = value;
            break;

          case FloatField:
          case IntField:
            fieldAtOffset<int32_t>(o, field->offset()) = value;
            break;
          }
        } else {
          exception = makeThrowable(t, GcNullPointerException::Type);
        }
      } break;

      case DoubleField:
      case LongField: {
        int64_t value = popLong(t);
        object o = popObject(t);
        if (LIKELY(o)) {
          fieldAtOffset<int64_t>(o, field->offset()) = value;
        } else {
          exception = makeThrowable(t, GcNullPointerException::Type);
        }
      } break;

      case ObjectField: {
        object value = popObject(t);
        object o = popObject(t);
        if (LIKELY(o)) {
          setField(t, o, field->offset(), value);
        } else {
          exception = makeThrowable(t, GcNullPointerException::Type);
        }
      } break;

      default:
        abort(t);
      }
    }

    if (UNLIKELY(exception)) {
      goto throw_;
    }
  }
    goto loop;

  case putstatic: {
    uint16_t index = codeReadInt16(t, code, ip);

    GcField* field = resolveField(t, frameMethod(t, frame), index - 1);

    assertT(t, field->flags() & ACC_STATIC);

    PROTECT(t, field);

    ACQUIRE_FIELD_FOR_WRITE(t, field);

    initClass(t, field->class_());

    GcSingleton* table = field->class_()->staticTable();

    switch (field->code()) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField: {
      int32_t value = popInt(t);
      switch (field->code()) {
      case ByteField:
      case BooleanField:
        fieldAtOffset<int8_t>(table, field->offset()) = value;
        break;

      case CharField:
      case ShortField:
        fieldAtOffset<int16_t>(table, field->offset()) = value;
        break;

      case FloatField:
      case IntField:
        fieldAtOffset<int32_t>(table, field->offset()) = value;
        break;
      }
    } break;

    case DoubleField:
    case LongField: {
      fieldAtOffset<int64_t>(table, field->offset()) = popLong(t);
    } break;

    case ObjectField: {
      setField(t, table, field->offset(), popObject(t));
    } break;

    default:
      abort(t);
    }
  }
    goto loop;

  case ret: {
    ip = localInt(t, code->body()[ip]);
  }
    goto loop;

  case return_: {
    GcMethod* method = frameMethod(t, frame);
    if ((method->flags() & ConstructorFlag)
        and (method->class_()->vmFlags() & HasFinalMemberFlag)) {
      storeStoreMemoryBarrier();
    }

    if (frame > base) {
      popFrame(t);
      goto loop;
    } else {
      return 0;
    }
  }
    goto loop;

  case saload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcShortArray* a = cast<GcShortArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        pushInt(t, a->body()[index]);
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case sastore: {
    int16_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      GcShortArray* a = cast<GcShortArray>(t, array);
      if (LIKELY(index >= 0 and static_cast<uintptr_t>(index) < a->length())) {
        a->body()[index] = value;
      } else {
        exception = makeThrowable(t,
                                  GcArrayIndexOutOfBoundsException::Type,
                                  "%d not in [0,%d)",
                                  index,
                                  a->length());
        goto throw_;
      }
    } else {
      exception = makeThrowable(t, GcNullPointerException::Type);
      goto throw_;
    }
  }
    goto loop;

  case sipush: {
    pushInt(t, static_cast<int16_t>(codeReadInt16(t, code, ip)));
  }
    goto loop;

  case swap: {
    uintptr_t tmp[2];
    memcpy(tmp, stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), tmp, BytesPerWord * 2);
  }
    goto loop;

  case tableswitch: {
    int32_t base = ip - 1;

    ip += 3;
    ip -= (ip % 4);

    int32_t default_ = codeReadInt32(t, code, ip);
    int32_t bottom = codeReadInt32(t, code, ip);
    int32_t top = codeReadInt32(t, code, ip);

    int32_t key = popInt(t);

    if (key >= bottom and key <= top) {
      unsigned index = ip + ((key - bottom) * 4);
      ip = base + codeReadInt32(t, code, index);
    } else {
      ip = base + default_;
    }
  }
    goto loop;

  case wide:
    goto wide;

  case impdep1: {
    // this means we're invoking a virtual method on an instance of a
    // bootstrap class, so we need to load the real class to get the
    // real method and call it.

    assertT(t, frameNext(t, frame) >= base);
    popFrame(t);

    assertT(t, code->body()[ip - 3] == invokevirtual);
    ip -= 2;

    uint16_t index = codeReadInt16(t, code, ip);
    GcMethod* method = resolveMethod(t, frameMethod(t, frame), index - 1);

    unsigned parameterFootprint = method->parameterFootprint();
    GcClass* class_ = objectClass(t, peekObject(t, sp - parameterFootprint));
    assertT(t, class_->vmFlags() & BootstrapFlag);

    resolveClass(t, frameMethod(t, frame)->class_()->loader(), class_->name());

    ip -= 3;
  }
    goto loop;

  default:
    abort(t);
  }

wide:
  switch (code->body()[ip++]) {
  case aload: {
    pushObject(t, localObject(t, codeReadInt16(t, code, ip)));
  }
    goto loop;

  case astore: {
    setLocalObject(t, codeReadInt16(t, code, ip), popObject(t));
  }
    goto loop;

  case iinc: {
    uint16_t index = codeReadInt16(t, code, ip);
    int16_t count = codeReadInt16(t, code, ip);

    setLocalInt(t, index, localInt(t, index) + count);
  }
    goto loop;

  case iload: {
    pushInt(t, localInt(t, codeReadInt16(t, code, ip)));
  }
    goto loop;

  case istore: {
    setLocalInt(t, codeReadInt16(t, code, ip), popInt(t));
  }
    goto loop;

  case lload: {
    pushLong(t, localLong(t, codeReadInt16(t, code, ip)));
  }
    goto loop;

  case lstore: {
    setLocalLong(t, codeReadInt16(t, code, ip), popLong(t));
  }
    goto loop;

  case ret: {
    ip = localInt(t, codeReadInt16(t, code, ip));
  }
    goto loop;

  default:
    abort(t);
  }

back_branch:
  safePoint(t);
  goto loop;

invoke : {
  if (method->flags() & ACC_NATIVE) {
    invokeNative(t, method);
  } else {
    if (DebugCalls && method) {
      printf("invoke %s.%s\n", 
                method->class_() && method->class_()->name() ? (const char *)method->class_()->name()->body().begin() : "<?>",
                method->name() ? (const char *)method->name()->body().begin() : "<?>"
      );
    }
    checkStack(t, method);
    pushFrame(t, method);
  }
}
  goto loop;

throw_:
  if (DebugRun || DebugCalls) {
    fprintf(stderr, "throw @ %s\n", frameMethod(t, frame)->name()->body().begin());
  }

  pokeInt(t, t->frame + FrameIpOffset, t->ip);
  for (; frame >= base; popFrame(t)) {
    uint64_t eh = findExceptionHandler(t, frame);
    if (eh) {
      sp = frame + FrameFootprint;
      ip = exceptionHandlerIp(eh);
      pushObject(t, exception);
      exception = 0;
      goto loop;
    }
  }

  return 0;
}

uint64_t interpret2(vm::Thread* t, uintptr_t* arguments)
{
  int base = arguments[0];
  bool* success = reinterpret_cast<bool*>(arguments[1]);

  object r = interpret3(static_cast<Thread*>(t), base);
  *success = true;
  return reinterpret_cast<uint64_t>(r);
}

object interpret(Thread* t)
{
  const int base = t->frame;

  while (true) {
    bool success = false;
    uintptr_t arguments[]
        = {static_cast<uintptr_t>(base), reinterpret_cast<uintptr_t>(&success)};

    uint64_t r = run(t, interpret2, arguments);
    if (success) {
      if (t->exception) {
        GcThrowable* exception = t->exception;
        t->exception = 0;
        throw_(t, exception);
      } else {
        return reinterpret_cast<object>(r);
      }
    }
  }
}

void pushArguments(Thread* t,
                   object this_,
                   const char* spec,
                   bool indirectObjects,
                   va_list a)
{
  if (this_) {
    pushObject(t, this_);
  }

  for (MethodSpecIterator it(t, spec); it.hasNext();) {
    switch (*it.next()) {
    case 'L':
    case '[':
      if (indirectObjects) {
        object* v = va_arg(a, object*);
        pushObject(t, v ? *v : 0);
      } else {
        pushObject(t, va_arg(a, object));
      }
      break;

    case 'J':
    case 'D':
      pushLong(t, va_arg(a, uint64_t));
      break;

    case 'F': {
      pushFloat(t, va_arg(a, double));
    } break;

    default:
      pushInt(t, va_arg(a, uint32_t));
      break;
    }
  }
}

void pushArguments(Thread* t,
                   object this_,
                   const char* spec,
                   const jvalue* arguments)
{
  if (this_) {
    pushObject(t, this_);
  }

  unsigned index = 0;
  for (MethodSpecIterator it(t, spec); it.hasNext();) {
    switch (*it.next()) {
    case 'L':
    case '[': {
      jobject v = arguments[index++].l;
      pushObject(t, v ? *v : 0);
    } break;

    case 'J':
    case 'D':
      pushLong(t, arguments[index++].j);
      break;

    case 'F': {
      pushFloat(t, arguments[index++].f);
    } break;

    default:
      pushInt(t, arguments[index++].i);
      break;
    }
  }
}

void pushArguments(Thread* t, object this_, const char* spec, object a)
{
  if (this_) {
    pushObject(t, this_);
  }

  unsigned index = 0;
  for (MethodSpecIterator it(t, spec); it.hasNext();) {
    switch (*it.next()) {
    case 'L':
    case '[':
      pushObject(t, objectArrayBody(t, a, index++));
      break;

    case 'J':
    case 'D':
      pushLong(t, fieldAtOffset<int64_t>(objectArrayBody(t, a, index++), 8));
      break;

    default:
      pushInt(
          t,
          fieldAtOffset<int32_t>(objectArrayBody(t, a, index++), BytesPerWord));
      break;
    }
  }
}

object invoke(Thread* t, GcMethod* method)
{
  PROTECT(t, method);

  GcClass* class_;
  PROTECT(t, class_);

  if (methodVirtual(t, method)) {
    unsigned parameterFootprint = method->parameterFootprint();
    class_ = objectClass(t, peekObject(t, t->sp - parameterFootprint));

    if (class_->vmFlags() & BootstrapFlag) {
      resolveClass(t, roots(t)->bootLoader(), class_->name());
    }

    if (method->class_()->flags() & ACC_INTERFACE) {
      method = findInterfaceMethod(t, method, class_);
    } else {
      method = findVirtualMethod(t, method, class_);
    }
  } else {
    class_ = method->class_();
  }

  if (method->flags() & ACC_STATIC) {
    initClass(t, class_);
  }

  object result = 0;

  if (method->flags() & ACC_NATIVE) {
    unsigned returnCode = invokeNative(t, method);

    switch (returnCode) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField:
      result = makeInt(t, popInt(t));
      break;

    case LongField:
    case DoubleField:
      result = makeLong(t, popLong(t));
      break;

    case ObjectField:
      result = popObject(t);
      break;

    case VoidField:
      result = 0;
      break;

    default:
      abort(t);
    };
  } else {
    checkStack(t, method);
    pushFrame(t, method);

    result = interpret(t);

    if (LIKELY(t->exception == 0)) {
      popFrame(t);
    } else {
      GcThrowable* exception = t->exception;
      t->exception = 0;
      throw_(t, exception);
    }
  }

  return result;
}

class MyProcessor : public Processor {
 public:
  MyProcessor(System* s, Allocator* allocator, const char* crashDumpDirectory)
      : s(s), allocator(allocator)
  {
    signals.setCrashDumpDirectory(crashDumpDirectory);
  }

  virtual vm::Thread* makeThread(Machine* m,
                                 GcThread* javaThread,
                                 vm::Thread* parent)
  {
    Thread* t = new (m->heap->allocate(sizeof(Thread) + m->stackSizeInBytes))
        Thread(m, javaThread, parent);
    t->init();
    return t;
  }

  virtual GcMethod* makeMethod(vm::Thread* t,
                               uint8_t vmFlags,
                               uint8_t returnCode,
                               uint8_t parameterCount,
                               uint8_t parameterFootprint,
                               uint16_t flags,
                               uint16_t offset,
                               GcByteArray* name,
                               GcByteArray* spec,
                               GcMethodAddendum* addendum,
                               GcClass* class_,
                               GcCode* code)
  {
    return vm::makeMethod(t,
                          vmFlags,
                          returnCode,
                          parameterCount,
                          parameterFootprint,
                          flags,
                          offset,
                          0,
                          0,
                          name,
                          spec,
                          addendum,
                          class_,
                          code);
  }

  virtual GcClass* makeClass(vm::Thread* t,
                             uint16_t flags,
                             uint16_t vmFlags,
                             uint16_t fixedSize,
                             uint8_t arrayElementSize,
                             uint8_t arrayDimensions,
                             GcClass* arrayElementClass,
                             GcIntArray* objectMask,
                             GcByteArray* name,
                             GcByteArray* sourceFile,
                             GcClass* super,
                             object interfaceTable,
                             object virtualTable,
                             object fieldTable,
                             object methodTable,
                             GcClassAddendum* addendum,
                             GcSingleton* staticTable,
                             GcClassLoader* loader,
                             unsigned vtableLength UNUSED)
  {
    return vm::makeClass(t,
                         flags,
                         vmFlags,
                         fixedSize,
                         arrayElementSize,
                         arrayDimensions,
                         arrayElementClass,
                         0,
                         objectMask,
                         name,
                         sourceFile,
                         super,
                         interfaceTable,
                         virtualTable,
                         fieldTable,
                         methodTable,
                         addendum,
                         staticTable,
                         loader,
                         0,
                         0);
  }

  virtual void initVtable(vm::Thread*, GcClass*)
  {
    // ignore
  }

  virtual void visitObjects(vm::Thread* vmt, Heap::Visitor* v)
  {
    Thread* t = static_cast<Thread*>(vmt);

    v->visit(&(t->code));

    for (unsigned i = 0; i < t->sp; ++i) {
      if (t->stack[i * 2] == ObjectTag) {
        v->visit(reinterpret_cast<object*>(t->stack + (i * 2) + 1));
      }
    }
  }

  virtual void walkStack(vm::Thread* vmt, StackVisitor* v)
  {
    Thread* t = static_cast<Thread*>(vmt);

    if (t->frame >= 0) {
      pokeInt(t, t->frame + FrameIpOffset, t->ip);
    }

    MyStackWalker walker(t, t->frame);
    walker.walk(v);
  }

  virtual int lineNumber(vm::Thread* t, GcMethod* method, int ip)
  {
    return findLineNumber(static_cast<Thread*>(t), method, ip);
  }

  virtual object* makeLocalReference(vm::Thread* vmt, object o)
  {
    Thread* t = static_cast<Thread*>(vmt);

    return pushReference(t, o);
  }

  virtual void disposeLocalReference(vm::Thread*, object* r)
  {
    if (r) {
      *r = 0;
    }
  }

  virtual bool pushLocalFrame(vm::Thread* vmt, unsigned capacity)
  {
    Thread* t = static_cast<Thread*>(vmt);

    if (t->sp + capacity < stackSizeInWords(t) / 2) {
      t->stackPointers = new (t->m->heap)
          List<unsigned>(t->sp, t->stackPointers);

      return true;
    } else {
      return false;
    }
  }

  virtual void popLocalFrame(vm::Thread* vmt)
  {
    Thread* t = static_cast<Thread*>(vmt);

    List<unsigned>* f = t->stackPointers;
    t->stackPointers = f->next;
    t->sp = f->item;

    t->m->heap->free(f, sizeof(List<unsigned>));
  }

  virtual object invokeArray(vm::Thread* vmt,
                             GcMethod* method,
                             object this_,
                             object arguments)
  {
    Thread* t = static_cast<Thread*>(vmt);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    if (UNLIKELY(t->sp + method->parameterFootprint() + 1 > stackSizeInWords(t)
                                                            / 2)) {
      throwNew(t, GcStackOverflowError::Type);
    }

    const char* spec = reinterpret_cast<char*>(method->spec()->body().begin());
    pushArguments(t, this_, spec, arguments);

    return local::invoke(t, method);
  }

  virtual object invokeArray(vm::Thread* vmt,
                             GcMethod* method,
                             object this_,
                             const jvalue* arguments)
  {
    Thread* t = static_cast<Thread*>(vmt);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    if (UNLIKELY(t->sp + method->parameterFootprint() + 1 > stackSizeInWords(t)
                                                            / 2)) {
      throwNew(t, GcStackOverflowError::Type);
    }

    const char* spec = reinterpret_cast<char*>(method->spec()->body().begin());
    pushArguments(t, this_, spec, arguments);

    return local::invoke(t, method);
  }

  virtual object invokeList(vm::Thread* vmt,
                            GcMethod* method,
                            object this_,
                            bool indirectObjects,
                            va_list arguments)
  {
    Thread* t = static_cast<Thread*>(vmt);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    if (UNLIKELY(t->sp + method->parameterFootprint() + 1 > stackSizeInWords(t)
                                                            / 2)) {
      throwNew(t, GcStackOverflowError::Type);
    }

    const char* spec = reinterpret_cast<char*>(method->spec()->body().begin());
    pushArguments(t, this_, spec, indirectObjects, arguments);

    return local::invoke(t, method);
  }

  virtual object invokeList(vm::Thread* vmt,
                            GcClassLoader* loader,
                            const char* className,
                            const char* methodName,
                            const char* methodSpec,
                            object this_,
                            va_list arguments)
  {
    Thread* t = static_cast<Thread*>(vmt);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    if (UNLIKELY(t->sp + parameterFootprint(vmt, methodSpec, false)
                 > stackSizeInWords(t) / 2)) {
      throwNew(t, GcStackOverflowError::Type);
    }

    pushArguments(t, this_, methodSpec, false, arguments);

    GcMethod* method
        = resolveMethod(t, loader, className, methodName, methodSpec);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    return local::invoke(t, method);
  }

  virtual object getStackTrace(vm::Thread* t, vm::Thread*)
  {
    // not implemented
    return makeObjectArray(t, 0);
  }

  virtual void initialize(BootImage*, avian::util::Slice<uint8_t>)
  {
    abort(s);
  }

  virtual void addCompilationHandler(CompilationHandler*)
  {
    abort(s);
  }

  virtual void compileMethod(vm::Thread*,
                             Zone*,
                             GcTriple**,
                             GcTriple**,
                             avian::codegen::DelayedPromise**,
                             GcMethod*,
                             OffsetResolver*,
                             JavaVM*)
  {
    abort(s);
  }

  virtual void visitRoots(vm::Thread*, HeapWalker*)
  {
    abort(s);
  }

  virtual void normalizeVirtualThunks(vm::Thread*)
  {
    abort(s);
  }

  virtual unsigned* makeCallTable(vm::Thread*, HeapWalker*)
  {
    abort(s);
  }

  virtual void boot(vm::Thread*, BootImage* image, uint8_t* code)
  {
    expect(s, image == 0 and code == 0);
  }

  virtual void callWithCurrentContinuation(vm::Thread*, object)
  {
    abort(s);
  }

  virtual void dynamicWind(vm::Thread*, object, object, object)
  {
    abort(s);
  }

  virtual void feedResultToContinuation(vm::Thread*, GcContinuation*, object)
  {
    abort(s);
  }

  virtual void feedExceptionToContinuation(vm::Thread*,
                                           GcContinuation*,
                                           GcThrowable*)
  {
    abort(s);
  }

  virtual void walkContinuationBody(vm::Thread*,
                                    Heap::Walker*,
                                    object,
                                    unsigned)
  {
    abort(s);
  }

  virtual void dispose(vm::Thread* t)
  {
    t->m->heap->free(t, sizeof(Thread) + t->m->stackSizeInBytes);
  }

  virtual void dispose()
  {
    signals.setCrashDumpDirectory(0);
    this->~MyProcessor();
    allocator->free(this, sizeof(*this));
  }

  System* s;
  Allocator* allocator;
  SignalRegistrar signals;
};

}  // namespace

namespace vm {

Processor* makeProcessor(System* system,
                         Allocator* allocator,
                         const char* crashDumpDirectory,
                         bool)
{
  return new (allocator->allocate(sizeof(local::MyProcessor)))
      local::MyProcessor(system, allocator, crashDumpDirectory);
}

}  // namespace vm
