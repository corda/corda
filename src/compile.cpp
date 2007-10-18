#include "common.h"
#include "system.h"
#include "constants.h"
#include "machine.h"
#include "processor.h"
#include "process.h"

using namespace vm;

extern "C" uint64_t
vmInvoke(void* function, void* stack, unsigned stackSize,
         unsigned returnType);

extern "C" void
vmCall();

extern "C" void NO_RETURN
vmJump(void* address, void* base, void* stack);

namespace {

const bool Verbose = true;

const unsigned FrameThread = BytesPerWord * 2;
const unsigned FrameMethod = FrameThread + BytesPerWord;
const unsigned FrameNext = FrameMethod + BytesPerWord;
const unsigned FrameFootprint = BytesPerWord * 3;

class ArgumentList;

class Buffer {
 public:
  Buffer(System* s, unsigned minimumCapacity):
    s(s),
    data(0),
    position(0),
    capacity(0),
    minimumCapacity(minimumCapacity)
  { }

  ~Buffer() {
    if (data) {
      s->free(data);
    }
  }

  void ensure(unsigned space) {
    if (position + space > capacity) {
      unsigned newCapacity = max
        (position + space, max(minimumCapacity, capacity * 2));
      uint8_t* newData = static_cast<uint8_t*>(s->allocate(newCapacity));
      if (data) {
        memcpy(newData, data, position);
        s->free(data);
      }
      data = newData;
    }
  }

  void append(uint8_t v) {
    ensure(1);
    data[position++] = v;
  }

  void append2(uint16_t v) {
    ensure(2);
    memcpy(data + position, &v, 2);
    position += 2;
  }

  void append4(uint32_t v) {
    ensure(4);
    memcpy(data + position, &v, 4);
    position += 4;
  }

  void set2(unsigned offset, uint32_t v) {
    assert(s, offset + 2 <= position);
    memcpy(data + offset, &v, 2);
  }

  void set4(unsigned offset, uint32_t v) {
    assert(s, offset + 4 <= position);
    memcpy(data + offset, &v, 4); 
  }

  uint8_t& get(unsigned offset) {
    assert(s, offset + 1 <= position);
    return data[offset];
  }

  uint16_t& get2(unsigned offset) {
    assert(s, offset + 2 <= position);
    return *reinterpret_cast<uint16_t*>(data + offset);
  }

  uint32_t& get4(unsigned offset) {
    assert(s, offset + 4 <= position);
    return *reinterpret_cast<uint32_t*>(data + offset);
  }

  uintptr_t& getAddress(unsigned offset) {
    assert(s, offset + 4 <= position);
    return *reinterpret_cast<uintptr_t*>(data + offset);
  }

  void appendAddress(uintptr_t v) {
    append4(v);
    if (BytesPerWord == 8) {
      // we have to use the preprocessor here to avoid a warning on
      // 32-bit systems
#ifdef __x86_64__
      append4(v >> 32);
#endif
    }
  }

  unsigned length() {
    return position;
  }

  void copyTo(void* b) {
    if (data) {
      memcpy(b, data, position);
    }
  }

  System* s;
  uint8_t* data;
  unsigned position;
  unsigned capacity;
  unsigned minimumCapacity;
};

class StackMapper {
 public:
  static const uint32_t Infinity = ~static_cast<uint32_t>(0);

  enum {
    Call,
    PushLong,
    PushInt,
    PushObject,
    Duplicate,
    DuplicateX1,
    DuplicateX2,
    Duplicate2,
    Duplicate2X1,
    Duplicate2X2,
    Pop,
    PopLong,
    PopInt,
    PopObject,
    StoreLong,
    StoreInt,
    StoreObject,
    Return,
    Jump,
    Branch,
    End
  };

  StackMapper(Thread* t, object method):
    t(t),
    method(method),
    ip(-1),
    index(codeSize() ? static_cast<uint32_t*>
          (t->m->system->allocate(codeSize() * 4)) : 0),
    log(t->m->system, 1024),
    callCount(0),
    protector(this)
  { }

  ~StackMapper() {
    if (index) {
      t->m->system->free(index);
    }
  }

  unsigned codeSize() {
    return codeLength(t, methodCode(t, method));
  }

  void end(unsigned nextIp) {
    if (ip >= 0) {
      log.append(End);
      log.append2(nextIp);
    }
    ip = nextIp;
  }

  void newIp(unsigned javaIp) {
    assert(t, javaIp < codeSize());

    end(javaIp);
    index[javaIp] = log.length();
  }

  void finish() {
    end(codeSize());
  }

  void called(unsigned nextMachineIp) {
    log.append(Call);
    log.append4(nextMachineIp);
    ++ callCount;
  }

  void pushedLong() {
    log.append(PushLong);
  }

  void pushedInt() {
    log.append(PushInt);
  }

  void pushedObject() {
    log.append(PushObject);
  }

  void dupped() {
    log.append(Duplicate);
  }

  void duppedX1() {
    log.append(DuplicateX1);
  }

  void duppedX2() {
    log.append(DuplicateX2);
  }

  void dupped2() {
    log.append(Duplicate2);
  }

  void dupped2X1() {
    log.append(Duplicate2X1);
  }

  void dupped2X2() {
    log.append(Duplicate2X2);
  }

  void popped(unsigned count) {
    log.append(Pop);
    log.append(count);
  }

  void poppedLong() {
    log.append(PopLong);
  }

  void poppedInt() {
    log.append(PopInt);
  }

  void poppedObject() {
    log.append(PopObject);
  }

  void storedLong(unsigned index) {
    if (index >= parameterFootprint()) {
      log.append(StoreLong);
      log.append2(index - parameterFootprint());
    }
  }

  void storedInt(unsigned index) {
    if (index >= parameterFootprint()) {
      log.append(StoreInt);
      log.append2(index - parameterFootprint());
    }
  }

  void storedObject(unsigned index) {
    if (index >= parameterFootprint()) {
      log.append(StoreObject);
      log.append2(index - parameterFootprint());
    }
  }

  void exited() {
    log.append(Return);
  }

  void jumped(unsigned targetJavaIp) {
    log.append(Jump);
    log.append2(targetJavaIp);
  }

  void branched(unsigned targetJavaIp) {
    log.append(Branch);
    log.append2(targetJavaIp);
  }

  unsigned parameterFootprint() {
    return methodParameterFootprint(t, method);
  }

  unsigned localSize() {
    return codeMaxLocals(t, methodCode(t, method))
      - parameterFootprint();
  }

  unsigned stackSize() {
    return codeMaxStack(t, methodCode(t, method));
  }

  unsigned mapSize() {
    return stackSize() + localSize();
  }

  unsigned mapSizeInWords() {
    return ceiling(mapSize(), BytesPerWord);
  }

  unsigned mapSizeInBytes() {
    return mapSizeInWords() * BytesPerWord;
  }

  unsigned callTableSize() {
    return callCount * (mapSizeInBytes() + BytesPerWord);
  }

  void populateCalls(uintptr_t* calls, unsigned& callIndex, uintptr_t* table,
                     uintptr_t* mask, uintptr_t* initialMap,
                     unsigned initialSp, unsigned ip)
  {
    uintptr_t map[mapSizeInWords()];
    memcpy(map, initialMap, mapSizeInBytes());

    unsigned sp = initialSp;

    while (ip < codeSize()) {
      if (getBit(mask, ip)) {
        return;
      }

      markBit(mask, ip);
      memcpy(table + (ip * mapSizeInWords()), map, mapSizeInBytes());

      unsigned i = index[ip];
      while (true) {
        switch (log.get(i++)) {
        case Call: {
          assert(t, callIndex < callCount);
          unsigned machineIp = log.get4(i); i += 4;
          calls[callIndex * (mapSizeInWords() + 1)] = machineIp;
          memcpy(calls + (callIndex * (mapSizeInWords() + 1)) + 1,
                 map, mapSizeInBytes());
          ++ callIndex;

//           fprintf(stderr,
//                   "call stack and locals 0x%x of size %d at %d of %s.%s\n",
//                   *map,
//                   mapSize(),
//                   machineIp,
//                   &byteArrayBody
//                   (t, className(t, methodClass(t, method)), 0),
//                   &byteArrayBody(t, methodName(t, method), 0));

        } break;

        case PushLong:
          assert(t, sp + 2 <= mapSize());
          assert(t, getBit(map, sp) == 0);
          assert(t, getBit(map, sp + 1) == 0);
          sp += 2;
          break;

        case PushInt:
          assert(t, sp + 1 <= mapSize());
          assert(t, getBit(map, sp) == 0);
          ++ sp;
          break;

        case PushObject:
          assert(t, sp + 1 <= mapSize());
          markBit(map, sp++);
          break;
          
        case Duplicate:
          assert(t, sp + 1 <= mapSize());
          assert(t, sp - 1 >= localSize());
          if (getBit(map, sp - 1)) {
            markBit(map, sp);
          }
          ++ sp;
          break;
          
        case DuplicateX1: {
          assert(t, sp + 1 <= mapSize());
          assert(t, sp - 2 >= localSize());

          unsigned b2 = getBit(map, sp - 2);
          unsigned b1 = getBit(map, sp - 1);

          if (b2) {
            markBit(map, sp - 1);
          } else {
            clearBit(map, sp - 1);
          }

          if (b1) {
            markBit(map, sp - 2);
            markBit(map, sp);
          } else {
            clearBit(map, sp - 2);
          }

          ++ sp;
        } break;
          
        case DuplicateX2: {
          assert(t, sp + 1 <= mapSize());
          assert(t, sp - 3 >= localSize());

          unsigned b3 = getBit(map, sp - 3);
          unsigned b2 = getBit(map, sp - 2);
          unsigned b1 = getBit(map, sp - 1);

          if (b3) {
            markBit(map, sp - 2);
          } else {
            clearBit(map, sp - 2);
          }

          if (b2) {
            markBit(map, sp - 1);
          } else {
            clearBit(map, sp - 1);
          }

          if (b1) {
            markBit(map, sp - 3);
            markBit(map, sp);
          } else {
            clearBit(map, sp - 3);
          }

          ++ sp;
        } break;
          
        case Duplicate2: {
          assert(t, sp + 2 <= mapSize());
          assert(t, sp - 2 >= localSize());

          unsigned b2 = getBit(map, sp - 2);
          unsigned b1 = getBit(map, sp - 1);

          if (b2) {
            markBit(map, sp);
          }

          if (b1) {
            markBit(map, sp + 1);
          }

          sp += 2;
        } break;

        case Duplicate2X1: {
          assert(t, sp + 2 <= mapSize());
          assert(t, sp - 3 >= localSize());

          unsigned b3 = getBit(map, sp - 3);
          unsigned b2 = getBit(map, sp - 2);
          unsigned b1 = getBit(map, sp - 1);

          if (b3) {
            markBit(map, sp - 1);
          } else {
            clearBit(map, sp - 1);
          }

          if (b2) {
            markBit(map, sp - 3);
            markBit(map, sp);
          } else {
            clearBit(map, sp - 3);
          }

          if (b1) {
            markBit(map, sp - 2);
            markBit(map, sp + 1);
          } else {
            clearBit(map, sp - 2);
          }

          sp += 2;
        } break;
          
        case Duplicate2X2: {
          assert(t, sp + 2 <= mapSize());
          assert(t, sp - 4 >= localSize());

          unsigned b4 = getBit(map, sp - 4);
          unsigned b3 = getBit(map, sp - 3);
          unsigned b2 = getBit(map, sp - 2);
          unsigned b1 = getBit(map, sp - 1);

          if (b4) {
            markBit(map, sp - 2);
          } else {
            clearBit(map, sp - 2);
          }

          if (b3) {
            markBit(map, sp - 1);
          } else {
            clearBit(map, sp - 1);
          }

          if (b2) {
            markBit(map, sp - 4);
            markBit(map, sp);
          } else {
            clearBit(map, sp - 4);
          }

          if (b1) {
            markBit(map, sp - 3);
            markBit(map, sp + 1);
          } else {
            clearBit(map, sp - 3);
          }

          sp += 2;
        } break;

        case Pop: {
          unsigned count = log.get(i++);
          assert(t, sp >= count);
          assert(t, sp - count >= localSize());
          while (count) {
            clearBit(map, -- sp);
            -- count;
          }
        } break;

        case PopLong:
          assert(t, sp >= 2);
          assert(t, sp - 2 >= localSize());
          assert(t, getBit(map, sp - 1) == 0);
          assert(t, getBit(map, sp - 2) == 0);
          sp -= 2;
          break;

        case PopInt:
          assert(t, sp >= 1);
          assert(t, sp - 1 >= localSize());
          assert(t, getBit(map, sp - 1) == 0);
          -- sp;
          break;

        case PopObject:
          assert(t, sp >= 1);
          assert(t, sp - 1 >= localSize());
          assert(t, getBit(map, sp - 1) != 0);
          clearBit(map, -- sp);
          break;

        case StoreLong: {
          unsigned index = log.get2(i); i += 2;
          assert(t, index + 1 < localSize());
          clearBit(map, index);
          clearBit(map, index + 1);
        } break;

        case StoreInt: {
          unsigned index = log.get2(i); i += 2;
          assert(t, index < localSize());
          clearBit(map, index);
        } break;

        case StoreObject: {
          unsigned index = log.get2(i); i += 2;
          assert(t, index < localSize());
          markBit(map, index);
        } break;

        case Return:
          ip = codeSize();
          goto loop;

        case Jump:
          ip = log.get2(i); i += 2;
          assert(t, ip < codeSize());
          goto loop;

        case Branch: {
          unsigned target = log.get2(i); i += 2;
          assert(t, target < codeSize());
          populateCalls(calls, callIndex, table, mask, map, sp, target);
        } break;

        case End:
          goto next;

        default:
          abort(t);
        }
      }

    next:
      ip = log.get2(i);

    loop:;
    }
  }

  static int compareCalls(const void* a, const void* b) {
    return (*static_cast<const uintptr_t*>(a) >
            *static_cast<const uintptr_t*>(b) ? 1 : -1);
  }

  void writeCallTableTo(uintptr_t* calls) {
    uintptr_t* table = static_cast<uintptr_t*>
      (t->m->system->allocate(codeSize() * mapSizeInBytes()));

    uintptr_t* mask = static_cast<uintptr_t*>
      (t->m->system->allocate
       (ceiling(codeSize(), BytesPerWord) * BytesPerWord));
    memset(mask, 0, ceiling(codeSize(), BytesPerWord) * BytesPerWord);

    uintptr_t map[mapSizeInWords()];
    memset(map, 0, mapSizeInBytes());

    unsigned callIndex = 0;

    populateCalls(calls, callIndex, table, mask, map, localSize(), 0);

    object eht = codeExceptionHandlerTable(t, methodCode(t, method));
    if (eht) {
      PROTECT(t, eht);

      for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
        ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);

        assert(t, getBit(mask, exceptionHandlerStart(eh)));
        
        memcpy(map,
               table + (exceptionHandlerStart(eh) * mapSizeInWords()),
               mapSizeInBytes());

        for (unsigned j = localSize() + 1; j < mapSize(); ++j) {
          clearBit(map, j);
        }

        markBit(map, localSize());

        populateCalls(calls, callIndex, table, mask, map, localSize() + 1,
                      exceptionHandlerIp(eh));
      }
    }

    t->m->system->free(mask);
    t->m->system->free(table);

    assert(t, callIndex == callCount);
    qsort(calls, callCount, mapSizeInBytes() + BytesPerWord, compareCalls);
  }

  Thread* t;
  object method;
  int ip;
  uint32_t* index;
  Buffer log;
  unsigned callCount;

  class MyProtector: public Thread::Protector {
   public:
    MyProtector(StackMapper* mapper): Protector(mapper->t), mapper(mapper) { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(mapper->method));
    }

    StackMapper* mapper;
  } protector;
};

class MyThread: public Thread {
 public:
  MyThread(Machine* m, object javaThread, vm::Thread* parent):
    vm::Thread(m, javaThread, parent),
    argumentList(0),
    reference(0)
  {
    memset(frameData, 0, BytesPerWord * 2);
  }

  ArgumentList* argumentList;
  void* frameData[2];
  Reference* reference;
};

inline void*
frameBase(void* frame)
{
  return static_cast<void**>(frame)
    [static_cast<int>(- (FrameFootprint / BytesPerWord) - 2)];
}

inline bool
frameValid(void* frame)
{
  return frame != 0;
}

inline void*
frameNext(void* frame)
{
  return static_cast<void**>(frameBase(frame))[FrameNext / BytesPerWord];
}

inline object&
frameMethod(void* frame)
{
  return static_cast<object*>(frameBase(frame))[FrameMethod / BytesPerWord];
}

inline void*
frameAddress(void* frame)
{
  return static_cast<void**>(frame)
    [static_cast<int>(- (FrameFootprint / BytesPerWord) - 1)];
}

inline void*
frameReturnAddress(void* frame)
{
  return static_cast<void**>(frameBase(frame))[1];
}

inline uint8_t*
compiledCode(Compiled* code)
{
  return compiledBody(code);
}

inline unsigned
compiledLineNumberCount(Thread*, Compiled* code)
{
  return compiledLineNumberTableLength(code) / sizeof(NativeLineNumber);
}

inline NativeLineNumber*
compiledLineNumber(Thread* t UNUSED, Compiled* code, unsigned index)
{
  assert(t, index < compiledLineNumberCount(t, code));
  return reinterpret_cast<NativeLineNumber*>
    (compiledBody(code) + pad(compiledCodeLength(code))) + index;
}

inline unsigned
compiledExceptionHandlerCount(Thread*, Compiled* code)
{
  return compiledExceptionHandlerTableLength(code)
    / sizeof(NativeExceptionHandler);
}

inline NativeExceptionHandler*
compiledExceptionHandler(Thread* t UNUSED, Compiled* code, unsigned index)
{
  assert(t, index < compiledExceptionHandlerCount(t, code));
  return reinterpret_cast<NativeExceptionHandler*>
    (compiledBody(code)
     + pad(compiledCodeLength(code))
     + pad(compiledLineNumberTableLength(code))) + index;
}

inline unsigned
compiledStackMapSize(Thread*, Compiled* code)
{
  return ceiling(compiledMaxStack(code)
                 + compiledMaxLocals(code)
                 - compiledParameterFootprint(code),
                 BytesPerWord) + 1;
}

inline unsigned
compiledStackMapCount(Thread* t, Compiled* code)
{
  return compiledStackMapTableLength(code)
    / (compiledStackMapSize(t, code) * BytesPerWord);
}

inline uintptr_t*
compiledStackMap(Thread* t, Compiled* code, unsigned index)
{
  assert(t, index < compiledStackMapCount(t, code));
  return reinterpret_cast<uintptr_t*>
    (compiledBody(code)
     + pad(compiledCodeLength(code))
     + pad(compiledLineNumberTableLength(code))
     + pad(compiledExceptionHandlerTableLength(code)))
    + (index * compiledStackMapSize(t, code));
}

inline Compiled*
makeCompiled(Thread* t, Buffer* code)
{
  Compiled* c = static_cast<Compiled*>
    (t->m->system->allocate(sizeof(Compiled) + pad(code->length())));

  compiledMaxLocals(c) = 0;
  compiledMaxStack(c) = 0;
  compiledParameterFootprint(c) = 0;
  compiledCodeLength(c) = code->length();
  compiledLineNumberTableLength(c) = 0;
  compiledExceptionHandlerTableLength(c) = 0;
  compiledStackMapTableLength(c) = 0;

  if (code->length()) {
    code->copyTo(compiledCode(c));
  }

  return c;
}

inline Compiled*
makeCompiled(Thread* t, object method, Buffer* code,
             NativeLineNumber* lineNumbers,
             NativeExceptionHandler* exceptionHandlers,
             StackMapper* stackMapper)
{
  unsigned lineNumberCount
    = codeLineNumberTable(t, methodCode(t, method)) ?
    lineNumberTableLength
    (t, codeLineNumberTable(t, methodCode(t, method))) : 0;

  unsigned exceptionHandlerCount
    = codeExceptionHandlerTable(t, methodCode(t, method)) ?
    exceptionHandlerTableLength
    (t, codeExceptionHandlerTable(t, methodCode(t, method))) : 0;

  unsigned maxStack = codeMaxStack(t, methodCode(t, method));
  unsigned maxLocals = codeMaxLocals(t, methodCode(t, method));
  unsigned parameterFootprint = methodParameterFootprint(t, method);

  Compiled* c = static_cast<Compiled*>
    (t->m->system->allocate(sizeof(Compiled)
                            + pad(code->length())
                            + pad(lineNumberCount
                                  * sizeof(NativeLineNumber))
                            + pad(exceptionHandlerCount
                                  * sizeof(NativeExceptionHandler))
                            + pad(stackMapper->callTableSize())));

  compiledMaxLocals(c) = maxLocals;
  compiledMaxStack(c) = maxStack;
  compiledParameterFootprint(c) = parameterFootprint;
  compiledCodeLength(c) = code->length();

  compiledLineNumberTableLength(c)
    = lineNumberCount * sizeof(NativeLineNumber);

  compiledExceptionHandlerTableLength(c)
    = exceptionHandlerCount * sizeof(NativeExceptionHandler);

  compiledStackMapTableLength(c) = stackMapper->callTableSize();

  if (code->length()) {
    code->copyTo(compiledCode(c));
  }

  if (lineNumberCount) {
    memcpy(compiledLineNumber(t, c, 0),
           lineNumbers,
           lineNumberCount * sizeof(NativeLineNumber));
  }

  if (exceptionHandlerCount) {
    memcpy(compiledExceptionHandler(t, c, 0),
           exceptionHandlers,
           exceptionHandlerCount * sizeof(NativeExceptionHandler));
  }

  if (stackMapper->callTableSize()) {
    stackMapper->writeCallTableTo(compiledStackMap(t, c, 0));
  }

  return c;
}

inline unsigned
addressOffset(Thread* t, object method, void* address)
{
  Compiled* code = reinterpret_cast<Compiled*>(methodCompiled(t, method));
  return static_cast<uint8_t*>(address) - compiledCode(code);
}

NativeExceptionHandler*
findExceptionHandler(Thread* t, void* frame)
{
  object method = frameMethod(frame);
  Compiled* code = reinterpret_cast<Compiled*>(methodCompiled(t, method));
      
  for (unsigned i = 0; i < compiledExceptionHandlerCount(t, code); ++i) {
    NativeExceptionHandler* handler = compiledExceptionHandler(t, code, i);
    unsigned offset = addressOffset(t, method, frameAddress(frame));

    if (offset - 1 >= nativeExceptionHandlerStart(handler)
        and offset - 1 < nativeExceptionHandlerEnd(handler))
    {
      object catchType;
      if (nativeExceptionHandlerCatchType(handler)) {
        catchType = arrayBody
          (t, methodCode(t, method),
           nativeExceptionHandlerCatchType(handler) - 1);
      } else {
        catchType = 0;
      }

      if (catchType == 0 or instanceOf(t, catchType, t->exception)) {
        if (Verbose) {
          fprintf(stderr, "exception handler match for %d in %s: "
                  "start: %d; end: %d; ip: %d\n",
                  offset,
                  &byteArrayBody(t, methodName(t, frameMethod(frame)), 0),
                  nativeExceptionHandlerStart(handler),
                  nativeExceptionHandlerEnd(handler),
                  nativeExceptionHandlerIp(handler));
        }

        return handler;
      }
    }
  }

  return 0;
}

void*
framePointer(void** data)
{
  return data + (FrameFootprint / BytesPerWord) + 2;
}

void*
frameStart(MyThread* t)
{
  return t->frameData[0] ? framePointer(t->frameData) : 0;
}

void NO_RETURN
unwind(MyThread* t)
{
  for (void* frame = frameStart(t); frameValid(frame);
       frame = frameNext(frame))
  {
    if ((methodFlags(t, frameMethod(frame)) & ACC_NATIVE) == 0) {
      NativeExceptionHandler* eh = findExceptionHandler(t, frame);
      if (eh) {
        object method = frameMethod(frame);
        Compiled* code = reinterpret_cast<Compiled*>
          (methodCompiled(t, method));

        void** stack = static_cast<void**>(frameBase(frame));

        unsigned parameterFootprint = methodParameterFootprint(t, method);
        unsigned localFootprint = compiledMaxLocals(code);

        if (localFootprint > parameterFootprint) {
          stack -= (localFootprint - parameterFootprint);
        }

        *(--stack) = t->exception;
        t->exception = 0;

        vmJump(compiledCode(code) + nativeExceptionHandlerIp(eh),
               frameBase(frame),
               stack);
      }
    }

    void* next = frameNext(frame);
    if (not frameValid(next)
        or methodFlags(t, frameMethod(next)) & ACC_NATIVE)
    {
      vmJump(frameReturnAddress(frame),
             *static_cast<void**>(frameBase(frame)),
             static_cast<void**>(frameBase(frame)) + 2);
    }
  }
  abort(t);
}

void NO_RETURN
throwNew(MyThread* t, object class_)
{
  t->exception = makeNew(t, class_);
  object trace = makeTrace(t);
  set(t, cast<object>(t->exception, ThrowableTrace), trace);
  unwind(t);
}

void NO_RETURN
throw_(MyThread* t, object o)
{
  if (o) {
    t->exception = o;
  } else {
    t->exception = makeNullPointerException(t);
  }
  unwind(t);
}

uintptr_t*
frameStackMap(MyThread* t, void* frame)
{
  Compiled* code = reinterpret_cast<Compiled*>
    (methodCompiled(t, frameMethod(frame)));
  unsigned ip = static_cast<uint8_t*>(frameAddress(frame))
    - compiledCode(code);

  unsigned bottom = 0;
  unsigned top = compiledStackMapCount(t, code);
  for (unsigned span = top - bottom; span; span = top - bottom) {
    unsigned middle = bottom + (span / 2);
    uintptr_t* map = compiledStackMap(t, code, middle);

    if (ip < *map) {
      top = middle;
    } else if (ip > *map) {
      bottom = middle + 1;
    } else {
      return map + 1;
    }
  }

  fprintf(stderr, "%d not found in ", ip);
  for (unsigned i = 0; i < compiledStackMapCount(t, code); ++i) {
    fprintf(stderr, "%"LD" ", *compiledStackMap(t, code, i));
  }
  fprintf(stderr, "\n");

  abort(t);
}

int
localOffset(MyThread* t, int v, object method)
{
  int parameterFootprint
    = methodParameterFootprint(t, method) * BytesPerWord;

  v *= BytesPerWord;
  if (v < parameterFootprint) {
    return (parameterFootprint - v - BytesPerWord) + (BytesPerWord * 2)
      + FrameFootprint;
  } else {
    return -(v + BytesPerWord - parameterFootprint);
  }
}

inline object*
frameLocalObject(MyThread* t, void* frame, unsigned index)
{
  return reinterpret_cast<object*>
    (static_cast<uint8_t*>(frameBase(frame))
     + localOffset(t, index, frameMethod(frame)));
}

void
visitParameters(MyThread* t, Heap::Visitor* v, void* frame)
{
  object method = frameMethod(frame);

  const char* spec = reinterpret_cast<const char*>
    (&byteArrayBody(t, methodSpec(t, method), 0));

  unsigned index = 0;
  if ((methodFlags(t, method) & ACC_STATIC) == 0) {
    v->visit(frameLocalObject(t, frame, index++));        
  }

  for (MethodSpecIterator it(t, spec); it.hasNext();) {
    switch (*it.next()) {
    case 'L':
    case '[':
      v->visit(frameLocalObject(t, frame, index++));
      break;
      
    case 'J':
    case 'D':
      index += 2;
      break;

    default:
      ++ index;
      break;
    }
  }

  assert(t, index == methodParameterFootprint(t, method));
}

void
visitStackAndLocals(MyThread* t, Heap::Visitor* v, void* frame)
{
  Compiled* code = reinterpret_cast<Compiled*>
    (methodCompiled(t, frameMethod(frame)));

  unsigned parameterFootprint = compiledParameterFootprint(code);
  unsigned count = compiledMaxStack(code)
    + compiledMaxLocals(code)
    - parameterFootprint;
      
  if (count) {
    uintptr_t* stackMap = frameStackMap(t, frame);

//     fprintf(stderr, "visit stack and locals 0x%x of size %d, at %d of %s.%s\n",
//             *stackMap,
//             count,
//             static_cast<uint8_t*>(frameAddress(frame))
//             - compiledCode(code),
//             &byteArrayBody
//             (t, className(t, methodClass(t, frameMethod(frame))), 0),
//             &byteArrayBody(t, methodName(t, frameMethod(frame)), 0));

    for (unsigned i = 0; i < count; ++i) {
      if (getBit(stackMap, i)) {
        v->visit(frameLocalObject(t, frame, i + parameterFootprint));
      }
    }
  }
}

void
visitStack(MyThread* t, Heap::Visitor* v)
{
  void* frame = frameStart(t);

  if (frameValid(frame)) {
    v->visit(&frameMethod(frame));
  }

  for (; frameValid(frame); frame = frameNext(frame)) {
    // we only need to visit the parameters of this method if the
    // caller is native.  Otherwise, the caller owns them.
    void* next = frameNext(frame);
    if (frameValid(next)) {
      v->visit(&frameMethod(next));
      
      if (methodFlags(t, frameMethod(next)) & ACC_NATIVE) {
        visitParameters(t, v, frame);
      }
    } else {
      visitParameters(t, v, frame);
    }

    object method = frameMethod(frame);
    Compiled* code = reinterpret_cast<Compiled*>(methodCompiled(t, method));

    if ((methodFlags(t, method) & ACC_NATIVE) == 0
        and code != t->m->processor->methodStub(t))
    {
      visitStackAndLocals(t, v, frame);
    }
  }
}

object
findInterfaceMethodFromInstance(Thread* t, object method, object instance)
{
  return findInterfaceMethod(t, method, objectClass(t, instance));
}

intptr_t
compareDoublesG(uint64_t bi, uint64_t ai)
{
  double a = bitsToDouble(ai);
  double b = bitsToDouble(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return 1;
  }
}

intptr_t
compareDoublesL(uint64_t bi, uint64_t ai)
{
  double a = bitsToDouble(ai);
  double b = bitsToDouble(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return -1;
  }
}

intptr_t
compareFloatsG(uint32_t bi, uint32_t ai)
{
  float a = bitsToFloat(ai);
  float b = bitsToFloat(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return 1;
  }
}

intptr_t
compareFloatsL(uint32_t bi, uint32_t ai)
{
  float a = bitsToFloat(ai);
  float b = bitsToFloat(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return -1;
  }
}

uint64_t
addDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) + bitsToDouble(b));
}

uint64_t
subtractDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) - bitsToDouble(b));
}

uint64_t
multiplyDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) * bitsToDouble(b));
}

uint64_t
divideDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) / bitsToDouble(b));
}

uint64_t
moduloDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(fmod(bitsToDouble(a), bitsToDouble(b)));
}

uint32_t
addFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) + bitsToFloat(b));
}

uint32_t
subtractFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) - bitsToFloat(b));
}

uint32_t
multiplyFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) * bitsToFloat(b));
}

uint32_t
divideFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) / bitsToFloat(b));
}

uint32_t
moduloFloat(uint32_t b, uint32_t a)
{
  return floatToBits(fmod(bitsToFloat(a), bitsToFloat(b)));
}

int64_t
divideLong(int64_t b, int64_t a)
{
  return a / b;
}

int64_t
moduloLong(int64_t b, int64_t a)
{
  return a % b;
}

object
makeBlankObjectArray(Thread* t, object class_, int32_t length)
{
  return makeObjectArray(t, class_, length, true);
}

object
makeBlankArray(Thread* t, object (*constructor)(Thread*, uintptr_t, bool),
               int32_t length)
{
  return constructor(t, length, true);
}

uint64_t
invokeNative2(MyThread* t, object method)
{
  PROTECT(t, method);

  if (objectClass(t, methodCode(t, method))
      == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
    void* function = resolveNativeMethod(t, method);
    if (UNLIKELY(function == 0)) {
      object message = makeString
        (t, "%s", &byteArrayBody(t, methodCode(t, method), 0));
      t->exception = makeUnsatisfiedLinkError(t, message);
      return 0;
    }

    object p = makePointer(t, function);
    set(t, methodCode(t, method), p);
  }

  object class_ = methodClass(t, method);
  PROTECT(t, class_);

  unsigned footprint = methodParameterFootprint(t, method) + 1;
  unsigned count = methodParameterCount(t, method) + 1;
  if (methodFlags(t, method) & ACC_STATIC) {
    ++ footprint;
    ++ count;
  }

  uintptr_t args[footprint];
  unsigned argOffset = 0;
  uint8_t types[count];
  unsigned typeOffset = 0;

  args[argOffset++] = reinterpret_cast<uintptr_t>(t);
  types[typeOffset++] = POINTER_TYPE;

  uintptr_t* sp = static_cast<uintptr_t*>(frameBase(frameStart(t)))
    + (methodParameterFootprint(t, method) + 1)
    + (FrameFootprint / BytesPerWord);

  if (methodFlags(t, method) & ACC_STATIC) {
    args[argOffset++] = reinterpret_cast<uintptr_t>(&class_);
  } else {
    args[argOffset++] = reinterpret_cast<uintptr_t>(sp--);
  }
  types[typeOffset++] = POINTER_TYPE;

  MethodSpecIterator it
    (t, reinterpret_cast<const char*>
     (&byteArrayBody(t, methodSpec(t, method), 0)));
  
  while (it.hasNext()) {
    unsigned type = types[typeOffset++]
      = fieldType(t, fieldCode(t, *it.next()));

    switch (type) {
    case INT8_TYPE:
    case INT16_TYPE:
    case INT32_TYPE:
    case FLOAT_TYPE:
      args[argOffset++] = *(sp--);
      break;

    case INT64_TYPE:
    case DOUBLE_TYPE: {
      if (BytesPerWord == 8) {
        uint64_t a = *(sp--);
        uint64_t b = *(sp--);
        args[argOffset++] = (a << 32) | b;
      } else {
        memcpy(args + argOffset, sp, 8);
        argOffset += 2;
        sp -= 2;
      }
    } break;

    case POINTER_TYPE: {
      args[argOffset++] = reinterpret_cast<uintptr_t>(sp--);
    } break;

    default: abort(t);
    }
  }

  void* function = pointerValue(t, methodCode(t, method));
  unsigned returnType = fieldType(t, methodReturnCode(t, method));
  uint64_t result;
  
  if (Verbose) {
    fprintf(stderr, "invoke native method %s.%s\n",
            &byteArrayBody(t, className(t, methodClass(t, method)), 0),
            &byteArrayBody(t, methodName(t, method), 0));
  }

  { ENTER(t, Thread::IdleState);

    result = t->m->system->call
      (function,
       args,
       types,
       count + 1,
       footprint * BytesPerWord,
       returnType);
  }

  if (Verbose) {
    fprintf(stderr, "return from native method %s.%s\n",
            &byteArrayBody(t, className(t, methodClass(t, method)), 0),
            &byteArrayBody(t, methodName(t, method), 0));
  }

  if (LIKELY(t->exception == 0) and returnType == POINTER_TYPE) {
    return result ? *reinterpret_cast<uintptr_t*>(result) : 0;
  } else {
    return result;
  }
}

uint64_t
invokeNative(MyThread* t, object method)
{
  uint64_t result = invokeNative2(t, method);
  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    return result;
  }
}

void
compileMethod(MyThread* t, object method);

inline bool
isByte(int32_t v)
{
  return v == static_cast<int8_t>(v);
}

inline bool
isInt32(uintptr_t v)
{
  return v == static_cast<uintptr_t>(static_cast<int32_t>(v));
}

enum Register {
  rax = 0,
  rcx = 1,
  rdx = 2,
  rbx = 3,
  rsp = 4,
  rbp = 5,
  rsi = 6,
  rdi = 7,
  r8 = 8,
  r9 = 9,
  r10 = 10,
  r11 = 11,
  r12 = 12,
  r13 = 13,
  r14 = 14,
  r15 = 15,
};

enum SSERegister {
  xmm0 = 0,
  xmm1 = 1,
  xmm2 = 2,
  xmm3 = 3,
  xmm4 = 4,
  xmm5 = 5,
  xmm6 = 6,
  xmm7 = 7
};

class Assembler {
 public:
  class Label {
   public:
    static const unsigned Capacity = 8;

    Label(Assembler* a):
      code(&(a->code)),
      unresolvedCount(0),
      mark_(-1)
    { }

    void reference() {
      if (mark_ == -1) {
        expect(code->s, unresolvedCount < Capacity);
        unresolved[unresolvedCount] = code->length();
        ++ unresolvedCount;

        code->append4(0);
      } else {
        code->append4(mark_ - (code->length() + 4));
      }
    }

    void mark() {
      mark_ = code->length();
      for (unsigned i = 0; i < unresolvedCount; ++i) {
        code->set4(unresolved[i], mark_ - (unresolved[i] + 4));
      }
    }

    Buffer* code;
    unsigned unresolved[Capacity];
    unsigned unresolvedCount;
    int mark_;
  };

  Assembler(System* s):
    code(s, 1024),
    jumps(s, 256)
  { }

  void rex() {
    if (BytesPerWord == 8) {
      code.append(0x48);
    }
  }

  void mov(Register src, Register dst) {
    rex();
    code.append(0x89);
    code.append(0xc0 | (src << 3) | dst);
  }

  void offsetInstruction(uint8_t instruction, uint8_t zeroPrefix,
                         uint8_t bytePrefix, uint8_t wordPrefix,
                         unsigned a, unsigned b, int32_t offset)
  {
    code.append(instruction);

    uint8_t prefix;
    if (offset == 0 and b != rbp) {
      prefix = zeroPrefix;
    } else if (isByte(offset)) {
      prefix = bytePrefix;
    } else {
      prefix = wordPrefix;
    }

    code.append(prefix | (a << 3) | b);

    if (b == rsp) {
      code.append(0x24);
    }

    if (offset == 0 and b != rbp) {
      // do nothing
    } else if (isByte(offset)) {
      code.append(offset);
    } else {
      code.append4(offset);
    }    
  }

  void movz1(Register src, Register dst) {
    code.append(0x0f);
    code.append(0xb6);
    code.append(0xc0 | (dst << 3) | src);
  }

  void movz1(Register src, int32_t srcOffset, Register dst) {
    code.append(0x0f);
    offsetInstruction(0xb6, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void movs1(Register src, Register dst) {
    code.append(0x0f);
    code.append(0xbe);
    code.append(0xc0 | (dst << 3) | src);
  }

  void movs1(Register src, int32_t srcOffset, Register dst) {
    code.append(0x0f);
    offsetInstruction(0xbe, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void movz2(Register src, Register dst) {
    code.append(0x0f);
    code.append(0xb7);
    code.append(0xc0 | (dst << 3) | src);
  }

  void movz2(Register src, int32_t srcOffset, Register dst) {
    code.append(0x0f);
    offsetInstruction(0xb7, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void movs2(Register src, Register dst) {
    code.append(0x0f);
    code.append(0xbf);
    code.append(0xc0 | (dst << 3) | src);
  }

  void movs2(Register src, int32_t srcOffset, Register dst) {
    code.append(0x0f);
    offsetInstruction(0xbf, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void movs4(Register src, int32_t srcOffset, Register dst) {
    rex();
    offsetInstruction(0x63, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void mov4(Register src, int32_t srcOffset, Register dst) {
    offsetInstruction(0x8b, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void mov1(Register src, Register dst, int32_t dstOffset) {
    offsetInstruction(0x88, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void mov2(Register src, Register dst, int32_t dstOffset) {
    code.append(0x66);
    offsetInstruction(0x89, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void mov4(Register src, Register dst, int32_t dstOffset) {
    offsetInstruction(0x89, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void mov(Register src, int32_t srcOffset, SSERegister dst) {
    code.append(0xf3);
    code.append(0x0f);
    offsetInstruction(0x7e, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void mov(Register src, int32_t srcOffset, Register dst) {
    rex();
    mov4(src, srcOffset, dst);
  }

  void mov(Register src, Register dst, int32_t dstOffset) {
    rex();
    mov4(src, dst, dstOffset);
  }

  void mov(int32_t v, Register dst, int32_t dstOffset) {
    rex();
    offsetInstruction(0xc7, 0, 0x40, 0x80, rax, dst, dstOffset);
    code.append4(v);
  }

  void mov(uintptr_t v, Register dst) {
    rex();
    code.append(0xb8 | dst);
    code.appendAddress(v);
  }

  void alignedMov(uintptr_t v, Register dst) {
    while ((code.length() + (BytesPerWord == 8 ? 2 : 1)) % BytesPerWord) {
      nop();
    }
    rex();
    code.append(0xb8 | dst);
    code.appendAddress(v);
  }

  void lea(Register src, int32_t srcOffset, Register dst) {
    rex();
    offsetInstruction(0x8d, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void nop() {
    code.append(0x90);
  }

  void push(Register reg) {
    code.append(0x50 | reg);
  }

  void push(Register reg, int32_t offset) {
    offsetInstruction(0xff, 0x30, 0x70, 0xb0, rax, reg, offset);
  }

  void push(int32_t v) {
    if (isByte(v)) {
      code.append(0x6a);
      code.append(v);
    } else {
      code.append(0x68);
      code.append4(v);
    }
  }

  void push4(Register reg, int32_t offset) {
    if (BytesPerWord == 8) {
      movs4(reg, offset, rsi);
      push(rsi);
    } else {
      push(reg, offset);
    }
  }

  void pushAddress(uintptr_t v) {
    if (BytesPerWord == 8 and not isInt32(v)) {
      mov(v, rsi);
      push(rsi);
    } else {
      push(v);
    }
  }

  void pop(Register dst) {
    code.append(0x58 | dst);
  }

  void pop(Register dst, int32_t offset) {
    offsetInstruction(0x8f, 0, 0x40, 0x80, rax, dst, offset);
  }

  void pop4(Register reg, int32_t offset) {
    if (BytesPerWord == 8) {
      pop(rsi);
      mov4(rsi, reg, offset);
    } else {
      pop(reg, offset);
    }
  }

  void add(Register src, Register dst) {
    rex();
    code.append(0x01);
    code.append(0xc0 | (src << 3) | dst);
  }

  void add(int32_t v, Register dst) {
    assert(code.s, isByte(v)); // todo

    rex();
    code.append(0x83);
    code.append(0xc0 | dst);
    code.append(v);
  }

  void add(int32_t v, Register dst, unsigned offset) {
    rex();
    unsigned i = (isByte(v) ? 0x83 : 0x81);
    offsetInstruction(i, 0, 0x40, 0x80, rax, dst, offset);
    if (isByte(v)) {
      code.append(v);
    } else {
      code.append4(v);
    }
  }

  void add(Register src, Register dst, unsigned dstOffset) {
    rex();
    offsetInstruction(0x01, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void adc(int32_t v, Register dst) {
    assert(code.s, isByte(v)); // todo

    rex();
    code.append(0x83);
    code.append(0xd0 | dst);
    code.append(v);
  }

  void adc(Register src, Register dst, unsigned dstOffset) {
    rex();
    offsetInstruction(0x11, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void sub(Register src, Register dst, unsigned dstOffset) {
    rex();
    offsetInstruction(0x29, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void sub(Register src, Register dst) {
    rex();
    code.append(0x29);
    code.append(0xc0 | (src << 3) | dst);
  }

  void sub(int32_t v, Register dst) {
    assert(code.s, isByte(v)); // todo

    rex();
    code.append(0x83);
    code.append(0xe8 | dst);
    code.append(v);
  }

  void sbb(Register src, Register dst, unsigned dstOffset) {
    rex();
    offsetInstruction(0x19, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void or_(Register src, Register dst) {
    rex();
    code.append(0x09);
    code.append(0xc0 | (src << 3) | dst);
  }

  void or_(Register src, Register dst, unsigned dstOffset) {
    rex();
    offsetInstruction(0x09, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void or_(int32_t v, Register dst) {
    assert(code.s, isByte(v)); // todo

    rex();
    code.append(0x83);
    code.append(0xc8 | dst);
    code.append(v);
  }

  void and_(Register src, Register dst) {
    rex();
    code.append(0x21);
    code.append(0xc0 | (src << 3) | dst);
  }

  void and_(Register src, Register dst, unsigned dstOffset) {
    rex();
    offsetInstruction(0x21, 0, 0x40, 0x80, src, dst, dstOffset);
  }

  void and_(int32_t v, Register dst) {
    rex();
    if (isByte(v)) {
      code.append(0x83);
      code.append(0xe0 | dst);
      code.append(v);
    } else {
      code.append(0x81);
      code.append(0xe0 | dst);
      code.append(v);
    }
  }

  void shl(int8_t v, Register dst) {
    rex();
    if (v == 1) {
      code.append(0xd1);
      code.append(0xe0 | dst);
    } else {
      code.append(0xc1);
      code.append(0xe0 | dst);
      code.append(v);
    }
  }

  void shl4_cl(Register dst, unsigned dstOffset) {
    offsetInstruction(0xd3, 0x20, 0x60, 0xa0, rax, dst, dstOffset);
  }

  void sar4_cl(Register dst, unsigned dstOffset) {
    offsetInstruction(0xd3, 0x38, 0x7d, 0xb8, rax, dst, dstOffset);
  }

  void shr4_cl(Register dst, unsigned dstOffset) {
    offsetInstruction(0xd3, 0x28, 0x6d, 0xa8, rax, dst, dstOffset);
  }

  void ret() {
    code.append(0xc3);
  }

  void jmp(Label& label) {
    code.append(0xE9);
    label.reference();
  }

  void jmp(unsigned javaIP) {
    code.append(0xE9);

    jumps.append4(javaIP);
    jumps.append4(code.length());

    code.append4(0);
  }

  void jmp(Register reg) {
    code.append(0xff);
    code.append(0xe0 | reg);
  }

  void conditional(Label& label, unsigned condition) {
    code.append(0x0f);
    code.append(condition);
    label.reference();
  }

  void conditional(unsigned javaIP, unsigned condition) {
    code.append(0x0f);
    code.append(condition);

    jumps.append4(javaIP);
    jumps.append4(code.length());

    code.append4(0);
  }

  void je(Label& label) {
    conditional(label, 0x84);
  }

  void je(unsigned javaIP) {
    conditional(javaIP, 0x84);
  }

  void jne(Label& label) {
    conditional(label, 0x85);
  }

  void jne(unsigned javaIP) {
    conditional(javaIP, 0x85);
  }

  void jg(Label& label) {
    conditional(label, 0x8f);
  }

  void jg(unsigned javaIP) {
    conditional(javaIP, 0x8f);
  }

  void jge(Label& label) {
    conditional(label, 0x8d);
  }

  void jge(unsigned javaIP) {
    conditional(javaIP, 0x8d);
  }

  void jl(Label& label) {
    conditional(label, 0x8c);
  }

  void jl(unsigned javaIP) {
    conditional(javaIP, 0x8c);
  }

  void jle(Label& label) {
    conditional(label, 0x8e);
  }

  void jle(unsigned javaIP) {
    conditional(javaIP, 0x8e);
  }

  void jb(Label& label) {
    conditional(label, 0x82);
  }

  void ja(Label& label) {
    conditional(label, 0x87);
  }

  void cmp(int v, Register reg) {
    assert(code.s, isByte(v)); // todo

    rex();
    code.append(0x83);
    code.append(0xf8 | reg);
    code.append(v);
  }

  void cmp(Register a, Register b) {
    rex();
    code.append(0x39);
    code.append(0xc0 | (a << 3) | b);
  }

  void call(Register reg) {
    code.append(0xff);
    code.append(0xd0 | reg);
  }

  void cdq() {
    code.append(0x99);
  }

  void cqo() {
    rex();
    cdq();
  }

  void imul4(Register src, unsigned srcOffset, Register dst) {
    code.append(0x0f);
    offsetInstruction(0xaf, 0, 0x40, 0x80, dst, src, srcOffset);
  }

  void imul(Register src, unsigned srcOffset, Register dst) {
    rex();
    imul4(src, srcOffset, dst);
  }

  void imul(Register src) {
    rex();
    code.append(0xf7);
    code.append(0xe8 | src);
  }

  void idiv(Register src) {
    rex();
    code.append(0xf7);
    code.append(0xf8 | src);
  }

  void mul(Register src, unsigned offset) {
    rex();
    offsetInstruction(0xf7, 0x20, 0x60, 0xa0, rax, src, offset);
  }

  void neg(Register reg, unsigned offset) {
    rex();
    offsetInstruction(0xf7, 0x10, 0x50, 0x90, rax, reg, offset);
  }

  void neg(Register reg) {
    rex();
    code.append(0xf7);
    code.append(0xd8 | reg);
  }

  void int3() {
    code.append(0xcc);
  }

  Buffer code;
  Buffer jumps;
};

Register
gpRegister(Thread* t, unsigned index)
{
  switch (index) {
  case 0:
    return rdi;
  case 1:
    return rsi;
  case 2:
    return rdx;
  case 3:
    return rcx;
  case 4:
    return r8;
  case 5:
    return r9;
  default:
    abort(t);
  }
}

SSERegister
sseRegister(Thread* t UNUSED, unsigned index)
{
  assert(t, index < 8);
  return static_cast<SSERegister>(index);
}

unsigned
parameterOffset(unsigned index)
{
  return FrameFootprint + ((index + 2) * BytesPerWord);
}

Compiled*
caller(MyThread* t);

class Compiler: public Assembler {
 public:
  Compiler(MyThread* t):
    Assembler(t->m->system),
    t(t)
  { }

  unsigned threadFrameDataOffset() {
    return reinterpret_cast<uintptr_t>(&(t->frameData))
      - reinterpret_cast<uintptr_t>(t);
  }

  Compiled* compileStub() {
    push(rbp);
    mov(rsp, rbp);

    if (BytesPerWord == 4) {
      push(rbp, FrameMethod);
      push(rbp, FrameThread);
    } else {
      mov(rbp, FrameMethod, rsi);
      mov(rbp, FrameThread, rdi);
    }

    mov(reinterpret_cast<uintptr_t>(compileMethod), rbx);
    callAddress(compiledCode(caller(t)));

    if (BytesPerWord == 4) {
      add(BytesPerWord * 2, rsp);
    }

    mov(rbp, FrameMethod, rax);
    mov(rax, MethodCompiled, rax);           // load compiled code

    mov(rbp, rsp);
    pop(rbp);
    
    add(CompiledBody, rax);
    jmp(rax);                                // call compiled code

    return finish();
  }

  Compiled* compileNativeInvoker() {
    push(rbp);
    mov(rsp, rbp);

    if (BytesPerWord == 4) {
      push(rbp, FrameMethod);
      push(rbp, FrameThread);
    } else {
      mov(rbp, FrameMethod, rsi);
      mov(rbp, FrameThread, rdi);
    }

    mov(reinterpret_cast<uintptr_t>(invokeNative), rbx);
    callAddress(compiledCode(caller(t)));

    if (BytesPerWord == 4) {
      add(BytesPerWord * 2, rsp);
    }

    mov(rbp, rsp);
    pop(rbp);
    ret();

    return finish();
  }

  Compiled* compileCaller() {
    mov(rbp, FrameThread, rdi);
    mov(rbp, rdi, threadFrameDataOffset());
    mov(rsp, 0, rcx);
    mov(rcx, rdi, threadFrameDataOffset() + BytesPerWord);

    jmp(rbx);

    return finish();
  }

  Compiled* finish() {
    return makeCompiled(t, &code);
  }

  void callAddress(void* function) {
    mov(reinterpret_cast<uintptr_t>(function), rax);
    call(rax);
  }

  void callAlignedAddress(void* function) {
    alignedMov(reinterpret_cast<uintptr_t>(function), rax);
    call(rax);
  }

  MyThread* t;
};

class JavaCompiler: public Compiler {
 public:
  JavaCompiler(MyThread* t, object method):
    Compiler(t),
    method(method),
    stackMapper(t, method),
    poolRegisterClobbered(true),
    machineIPs(static_cast<uint32_t*>
               (t->m->system->allocate
                (codeLength(t, methodCode(t, method)) * 4))),
    lineNumbers(codeLineNumberTable(t, methodCode(t, method)) ?
                static_cast<NativeLineNumber*>
                (t->m->system->allocate
                 (lineNumberTableLength
                  (t, codeLineNumberTable(t, methodCode(t, method)))
                  * sizeof(NativeLineNumber))) : 0),
    exceptionHandlers(codeExceptionHandlerTable(t, methodCode(t, method)) ?
                      static_cast<NativeExceptionHandler*>
                      (t->m->system->allocate
                       (exceptionHandlerTableLength
                        (t, codeExceptionHandlerTable
                         (t, methodCode(t, method)))
                        * sizeof(NativeExceptionHandler))) : 0),
    pool(t->m->system, 256),
    protector(this)
  { }

  ~JavaCompiler() {
    if (machineIPs) {
      t->m->system->free(machineIPs);
    }

    if (lineNumbers) {
      t->m->system->free(lineNumbers);
    }

    if (exceptionHandlers) {
      t->m->system->free(exceptionHandlers);
    }
  }

  void pushInt() {
    sub(BytesPerWord, rsp);
    stackMapper.pushedInt();
  }

  void pushInt(int32_t v) {
    push(v);
    stackMapper.pushedInt();
  }

  void pushInt(Register r) {
    push(r);
    stackMapper.pushedInt();
  }

  void pushInt(Register r, int32_t offset) {
    push(r, offset);
    stackMapper.pushedInt();
  }

  void pushInt4(Register r, int32_t offset) {
    push4(r, offset);
    stackMapper.pushedInt();
  }

  void pushObject(int32_t v) {
    push(v);
    stackMapper.pushedObject();
  }

  void pushObject(Register r) {
    push(r);
    stackMapper.pushedObject();
  }

  void pushObject(Register r, int32_t offset) {
    push(r, offset);
    stackMapper.pushedObject();
  }

  void pushLongQuiet(uint64_t v) {
    if (BytesPerWord == 8) {
      pushAddress(v);
      sub(8, rsp);
    } else {
      push((v >> 32) & 0xFFFFFFFF);
      push((v      ) & 0xFFFFFFFF);
    }
  }

  void pushLong(uint64_t v) {
    pushLongQuiet(v);
    stackMapper.pushedLong();
  }

  void pushLong(Register r) {
    assert(t, BytesPerWord == 8);
    push(r);
    sub(8, rsp);
    stackMapper.pushedLong();
  }

  void pushLongQuiet(Register r, int32_t offset) {
    assert(t, BytesPerWord == 8);
    push(r, offset);
    sub(8, rsp);
  }

  void pushLong(Register r, int32_t offset) {
    pushLongQuiet(r, offset);
    stackMapper.pushedLong();
  }

  void pushLong(Register low, Register high) {
    assert(t, BytesPerWord == 4);
    push(high);
    push(low);
    stackMapper.pushedLong();
  }

  void pushLongQuiet(Register low, int32_t lowOffset,
                     Register high, int32_t highOffset)
  {
    assert(t, BytesPerWord == 4);
    push(high, highOffset);
    push(low, lowOffset);
  }

  void pushLong(Register low, int32_t lowOffset,
                Register high, int32_t highOffset)
  {
    pushLongQuiet(low, lowOffset, high, highOffset);
    stackMapper.pushedLong();
  }

  void popInt() {
    add(BytesPerWord, rsp);
    stackMapper.poppedInt();
  }

  void popInt(Register r) {
    pop(r);
    stackMapper.poppedInt();
  }

  void popInt(Register r, int32_t offset) {
    pop(r, offset);
    stackMapper.poppedInt();
  }

  void popInt4(Register r, int32_t offset) {
    pop4(r, offset);
    stackMapper.poppedInt();
  }

  void popObject(Register r) {
    pop(r);
    stackMapper.poppedObject();
  }

  void popObject(Register r, int32_t offset) {
    pop(r, offset);
    stackMapper.poppedObject();
  }

  void popLong() {
    add(BytesPerWord * 2, rsp);
    stackMapper.poppedLong();
  }

  void popLong(Register r) {
    assert(t, BytesPerWord == 8);
    add(BytesPerWord, rsp);
    pop(r);
    stackMapper.poppedLong();
  }

  void popLong(Register r, int32_t offset) {
    assert(t, BytesPerWord == 8);
    add(BytesPerWord, rsp);
    pop(r, offset);
    stackMapper.poppedLong();
  }

  void popLong(Register low, Register high) {
    assert(t, BytesPerWord == 4);
    pop(low);
    pop(high);
    stackMapper.poppedLong();
  }

  void popLong(Register low, int32_t lowOffset,
               Register high, int32_t highOffset)
  {
    assert(t, BytesPerWord == 4);
    pop(low, lowOffset);
    pop(high, highOffset);
    stackMapper.poppedLong();
  }

  void loadInt(unsigned index) {
    pushInt(rbp, localOffset(t, index, method));
  }

  void loadObject(unsigned index) {
    pushObject(rbp, localOffset(t, index, method));
  }

  void loadLong(unsigned index) {
    if (BytesPerWord == 8) {
      pushLong(rbp, localOffset(t, index, method));
    } else {
      pushLong(rbp, localOffset(t, index + 1, method),
               rbp, localOffset(t, index, method));
    }
  }

  void storeInt(unsigned index) {
    popInt(rbp, localOffset(t, index, method));
    stackMapper.storedInt(index);
  }

  void storeObject(unsigned index) {
    popObject(rbp, localOffset(t, index, method));
    stackMapper.storedObject(index);
  }

  void storeLong(unsigned index) {
    if (BytesPerWord == 8) {
      popLong(rbp, localOffset(t, index, method));
    } else {
      popLong(rbp, localOffset(t, index, method),
              rbp, localOffset(t, index + 1, method));
    }
    stackMapper.storedLong(index);
  }

  void pushReturnValue(unsigned code) {
    switch (code) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField:
      push(rax);
      stackMapper.pushedInt();
      break;

    case ObjectField:
      push(rax);
      stackMapper.pushedObject();
      break;

    case LongField:
    case DoubleField:
      push(rax);
      push(rdx);
      stackMapper.pushedLong();
      break;

    case VoidField:
      break;

    default:
      abort(t);
    }
  }

  void compileDirectInvoke(object target) {
    unsigned footprint = FrameFootprint
      + (methodParameterFootprint(t, target) * BytesPerWord);

    Compiled* code = reinterpret_cast<Compiled*>(methodCompiled(t, target));
        
    push(rsp);
    push(poolRegister(), poolReference(target));
    push(rbp, FrameThread);

    callAlignedAddress(compiledCode(code));

    stackMapper.called(this->code.length());
    poolRegisterClobbered = true;

    add(footprint, rsp); // pop arguments
    stackMapper.popped(methodParameterFootprint(t, target));

    pushReturnValue(methodReturnCode(t, target));
  }

  void compileThrowNew(Machine::Type type) {
    object class_ = arrayBody(t, t->m->types, type);
          
    if (BytesPerWord == 8) {
      mov(poolRegister(), poolReference(class_), rsi);
      mov(rbp, FrameThread, rdi);
    } else {
      push(poolRegister(), poolReference(class_));
      push(rbp, FrameThread);
    }

    indirectCall(reinterpret_cast<void*>(throwNew));    
  }

  void directCall(void* function) {
    callAddress(function);

    poolRegisterClobbered = true;    
  }

  void indirectCall(void* function) {
    mov(reinterpret_cast<uintptr_t>(function), rbx);
    callAddress(compiledCode(caller(t)));

    stackMapper.called(code.length());
    poolRegisterClobbered = true;    
  }

  void compileCall(bool direct, void* function, Register arg1, Register arg2) {
    if (BytesPerWord == 8) {
      mov(arg2, rdx);
      mov(arg1, rsi);
      mov(rbp, FrameThread, rdi);
    } else {
      push(arg2);
      push(arg1);
      push(rbp, FrameThread);
    }

    if (direct) {
      directCall(function);
    } else {
      indirectCall(function);
    }

    if (BytesPerWord == 4) {
      add(BytesPerWord * 3, rsp);
    }
  }

  void compileCall(bool direct, void* function, uintptr_t arg1, Register arg2)
  {
    if (BytesPerWord == 8) {
      mov(arg2, rdx);
      mov(arg1, rsi);
      mov(rbp, FrameThread, rdi);
    } else {
      push(arg2);
      push(arg1);
      push(rbp, FrameThread);
    }

    if (direct) {
      directCall(function);
    } else {
      indirectCall(function);
    }

    if (BytesPerWord == 4) {
      add(BytesPerWord * 3, rsp);
    }
  }

  void compileCall(bool direct, void* function, object arg1, Register arg2) {
    if (BytesPerWord == 8) {
      mov(arg2, rdx);
      mov(poolRegister(), poolReference(arg1), rsi);
      mov(rbp, FrameThread, rdi);
    } else {
      push(arg2);
      push(poolRegister(), poolReference(arg1));
      push(rbp, FrameThread);
    }

    if (direct) {
      directCall(function);
    } else {
      indirectCall(function);
    }

    if (BytesPerWord == 4) {
      add(BytesPerWord * 3, rsp);
    }
  }

  void compileCall(bool direct, void* function, object arg1, Register arg2,
                   int32_t arg2offset)
  {
    if (BytesPerWord == 8) {
      mov(arg2, arg2offset, rdx);
      mov(poolRegister(), poolReference(arg1), rsi);
      mov(rbp, FrameThread, rdi);
    } else {
      push(arg2, arg2offset);
      push(poolRegister(), poolReference(arg1));
      push(rbp, FrameThread);
    }

    if (direct) {
      directCall(function);
    } else {
      indirectCall(function);
    }

    if (BytesPerWord == 4) {
      add(BytesPerWord * 3, rsp);
    }
  }

  void compileCall(bool direct, void* function, object arg1) {
    if (BytesPerWord == 8) {
      mov(poolRegister(), poolReference(arg1), rsi);
      mov(rbp, FrameThread, rdi);
    } else {
      push(poolRegister(), poolReference(arg1));
      push(rbp, FrameThread);
    }

    if (direct) {
      directCall(function);
    } else {
      indirectCall(function);
    }

    if (BytesPerWord == 4) {
      add(BytesPerWord * 2, rsp);
    }
  }

  Compiled* compile() {
    object code = methodCode(t, method);
    PROTECT(t, code);

    unsigned parameterFootprint
      = methodParameterFootprint(t, method) * BytesPerWord;

    unsigned localFootprint = codeMaxLocals(t, code) * BytesPerWord;

    push(rbp);
    mov(rsp, rbp);

    if (localFootprint > parameterFootprint) {
      // reserve space for local variables
      sub(localFootprint - parameterFootprint, rsp);
    }

    int lineNumberIndex;
    object lnt = codeLineNumberTable(t, code);
    if (lnt and lineNumberTableLength(t, lnt)) {
      lineNumberIndex = 0;
    } else {
      lineNumberIndex = -1;
    }
    
    for (unsigned ip = 0; ip < codeLength(t, code);) {
      stackMapper.newIp(ip);
      machineIPs[ip] = this->code.length();

      if (lineNumberIndex >= 0) {
        object lnt = codeLineNumberTable(t, code);
        LineNumber* ln = lineNumberTableBody(t, lnt, lineNumberIndex);

        if (lineNumberIp(ln) == ip) {
          nativeLineNumberIp(lineNumbers + lineNumberIndex)
            = this->code.length();
          nativeLineNumberLine(lineNumbers + lineNumberIndex)
            = lineNumberLine(ln);
          if (static_cast<unsigned>(lineNumberIndex) + 1
              < lineNumberTableLength(t, lnt))
          {
            ++ lineNumberIndex;
          } else {
            lineNumberIndex = -1;
          }
        }
      }

      unsigned instruction = codeBody(t, code, ip++);

      switch (instruction) {
      case aaload:
      case baload:
      case caload:
      case daload:
      case faload:
      case iaload:
      case laload:
      case saload: {
        Label next(this);
        Label outOfBounds(this);

        popInt(rcx);
        popObject(rax);

        cmp(0, rcx);
        jl(outOfBounds);

        mov(rax, BytesPerWord, rdx);
        cmp(rdx, rcx);
        jge(outOfBounds);

        add(BytesPerWord * 2, rax);

        switch (instruction) {
        case aaload:
        case faload:
        case iaload:
          shl(log(BytesPerWord), rcx);
          add(rcx, rax);

          if (instruction == aaload) {
            pushObject(rax, 0);
          } else {
            pushInt4(rax, 0);
          }
          break;

        case baload:
          add(rcx, rax);
          movs1(rax, 0, rax);
          pushInt(rax);
          break;

        case caload:
          shl(1, rcx);
          add(rcx, rax);
          movz2(rax, 0, rax);
          pushInt(rax);
          break;

        case daload:
        case laload:
          shl(3, rcx);
          add(rcx, rax);
          pushLong(rax, 0);
          break;

        case saload:
          shl(1, rcx);
          add(rcx, rax);
          movs2(rax, 0, rax);
          pushInt(rax);
          break;
        }

        jmp(next);

        outOfBounds.mark();
        compileThrowNew(Machine::ArrayIndexOutOfBoundsExceptionType);

        next.mark();
      } break;

      case aastore:
      case bastore:
      case castore:
      case dastore:
      case fastore:
      case iastore:
      case lastore:
      case sastore: {
        Label next(this);
        Label outOfBounds(this);

        if (instruction == dastore or instruction == lastore) {
          popLong(rdx, rbx);
        } else if (instruction == aastore) {
          popObject(rbx);
        } else {
          popInt(rbx);
        }

        popInt(rcx);
        popObject(rax);

        cmp(0, rcx);
        jl(outOfBounds);

        mov(rax, BytesPerWord, rsi);
        cmp(rsi, rcx);
        jge(outOfBounds);

        add(BytesPerWord * 2, rax);

        switch (instruction) {
        case aastore:
          shl(log(BytesPerWord), rcx);
          add(rcx, rax);
          compileCall(true, reinterpret_cast<void*>(set), rax, rbx);
          break;

        case fastore:
        case iastore:
          shl(log(BytesPerWord), rcx);
          add(rcx, rax);
          mov(rbx, rax, 0);
          break;

        case bastore:
          add(rcx, rax);
          mov1(rbx, rax, 0);
          break;

        case castore:
        case sastore:
          shl(1, rcx);
          add(rcx, rax);
          mov2(rbx, rax, 0);
          break;

        case dastore:
        case lastore:
          shl(3, rcx);
          add(rcx, rax);
          mov4(rbx, rax, 0);
          mov4(rdx, rax, 4);
          break;
        }

        jmp(next);

        outOfBounds.mark();
        compileThrowNew(Machine::ArrayIndexOutOfBoundsExceptionType);

        next.mark();
      } break;

      case aconst_null:
        pushObject(0);
        break;

      case aload:
        loadObject(codeBody(t, code, ip++));
        break;

      case aload_0:
        loadObject(0);
        break;

      case aload_1:
        loadObject(1);
        break;

      case aload_2:
        loadObject(2);
        break;

      case aload_3:
        loadObject(3);
        break;

      case anewarray: {
        uint16_t index = codeReadInt16(t, code, ip);
      
        object class_ = resolveClass(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;

        Label nonnegative(this);

        popInt(rax);
        cmp(0, rax);
        jge(nonnegative);

        compileThrowNew(Machine::NegativeArraySizeExceptionType);

        nonnegative.mark();

        compileCall(false, reinterpret_cast<void*>(makeBlankObjectArray),
                    class_, rax);

        pushObject(rax);
      } break;

      case areturn:
        popObject(rax);
        mov(rbp, rsp);
        pop(rbp);
        ret();
        stackMapper.exited();
        break;

      case arraylength:
        popObject(rax);
        pushInt(rax, BytesPerWord);
        break;

      case astore:
        storeObject(codeBody(t, code, ip++));
        break;

      case astore_0:
        storeObject(0);
        break;

      case astore_1:
        storeObject(1);
        break;

      case astore_2:
        storeObject(2);
        break;

      case astore_3:
        storeObject(3);
        break;

      case athrow:
        if (BytesPerWord == 8) {
          popObject(rsi);
          mov(rbp, FrameThread, rdi);
        } else {
          push(rbp, FrameThread);
        }

        indirectCall(reinterpret_cast<void*>(throw_));
        stackMapper.exited();
        break;

      case bipush: {
        pushInt(static_cast<int8_t>(codeBody(t, code, ip++)));
      } break;

      case checkcast: {
        uint16_t index = codeReadInt16(t, code, ip);

        object class_ = resolveClass(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;

        Label next(this);
        
        mov(rsp, 0, rax);
        cmp(0, rax);
        je(next);

        mov(poolRegister(), poolReference(class_), rcx);
        mov(rax, 0, rax);
        and_(PointerMask, rax);
        cmp(rcx, rax);
        je(next);

        compileCall(true, reinterpret_cast<void*>(isAssignableFrom), rcx, rax);

        cmp(0, rax);
        jne(next);
        
        compileThrowNew(Machine::ClassCastExceptionType);

        next.mark();        
      } break;

      case dadd: {
        if (BytesPerWord == 8) {
          popLong(rdi);
          popLong(rsi);
          directCall(reinterpret_cast<void*>(addDouble));
          pushLong(rax);
        } else {
          directCall(reinterpret_cast<void*>(addDouble));
          add(BytesPerWord * 4, rsp);
          stackMapper.poppedLong();
          stackMapper.poppedLong();
          pushLong(rax, rdx);
        }
      } break;

      case dcmpg: {
        if (BytesPerWord == 8) {
          popLong(rdi);
          popLong(rsi);
          directCall(reinterpret_cast<void*>(compareDoublesG));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(compareDoublesG));
          add(BytesPerWord * 4, rsp);
          stackMapper.poppedLong();
          stackMapper.poppedLong();
          pushInt(rax);
        }
      } break;

      case dcmpl: {
        if (BytesPerWord == 8) {
          popLong(rdi);
          popLong(rsi);
          directCall(reinterpret_cast<void*>(compareDoublesL));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(compareDoublesL));
          add(BytesPerWord * 4, rsp);
          stackMapper.poppedLong();
          stackMapper.poppedLong();
          pushInt(rax);
        }
      } break;

      case dconst_0: {
        pushLong(doubleToBits(0.0));
      } break;

      case dconst_1: {
        pushLong(doubleToBits(1.0));
      } break;

      case ddiv: {
        if (BytesPerWord == 8) {
          popLong(rdi);
          popLong(rsi);
          directCall(reinterpret_cast<void*>(divideDouble));
          pushLong(rax);
        } else {
          directCall(reinterpret_cast<void*>(divideDouble));
          add(BytesPerWord * 4, rsp);
          stackMapper.poppedLong();
          stackMapper.poppedLong();
          pushLong(rax, rdx);
        }
      } break;

      case dmul: {
        if (BytesPerWord == 8) {
          popLong(rdi);
          popLong(rsi);
          directCall(reinterpret_cast<void*>(multiplyDouble));
          pushLong(rax);
        } else {
          directCall(reinterpret_cast<void*>(multiplyDouble));
          add(BytesPerWord * 4, rsp);
          stackMapper.poppedLong();
          stackMapper.poppedLong();
          pushLong(rax, rdx);
        }
      } break;

      case vm::drem: {
        if (BytesPerWord == 8) {
          popLong(rdi);
          popLong(rsi);
          directCall(reinterpret_cast<void*>(moduloDouble));
          pushLong(rax);
        } else {
          directCall(reinterpret_cast<void*>(moduloDouble));
          add(BytesPerWord * 4, rsp);
          stackMapper.poppedLong();
          stackMapper.poppedLong();
          pushLong(rax, rdx);
        }
      } break;

      case dsub: {
        if (BytesPerWord == 8) {
          popLong(rdi);
          popLong(rsi);
          directCall(reinterpret_cast<void*>(subtractDouble));
          pushLong(rax);
        } else {
          directCall(reinterpret_cast<void*>(subtractDouble));
          add(BytesPerWord * 4, rsp);
          stackMapper.poppedLong();
          stackMapper.poppedLong();
          pushLong(rax, rdx);
        }
      } break;

      case dup:
        push(rsp, 0);
        stackMapper.dupped();
        break;

      case dup_x1:
        mov(rsp, BytesPerWord, rcx);
        mov(rsp, 0, rax);
        mov(rax, rsp, BytesPerWord);
        mov(rcx, rsp, 0);
        push(rax);
        stackMapper.duppedX1();
        break;

      case dup_x2:
        mov(rsp, BytesPerWord * 2, rdx);
        mov(rsp, BytesPerWord, rcx);
        mov(rsp, 0, rax);
        mov(rax, rsp, BytesPerWord * 2);
        mov(rdx, rsp, BytesPerWord);
        mov(rcx, rsp, 0);
        push(rax);
        stackMapper.duppedX2();
        break;

      case dup2:
        push(rsp, BytesPerWord);
        push(rsp, BytesPerWord);
        stackMapper.dupped2();
        break;

      case dup2_x1:
        mov(rsp, BytesPerWord * 2, rdx);
        mov(rsp, BytesPerWord, rcx);
        mov(rsp, 0, rax);
        mov(rcx, rsp, BytesPerWord * 2);
        mov(rax, rsp, BytesPerWord);
        mov(rdx, rsp, 0);
        push(rcx);
        push(rax);
        stackMapper.dupped2X1();
        break;

      case dup2_x2:
        mov(rsp, BytesPerWord * 3, rbx);
        mov(rsp, BytesPerWord * 2, rdx);
        mov(rsp, BytesPerWord, rcx);
        mov(rsp, 0, rax);
        mov(rcx, rsp, BytesPerWord * 3);
        mov(rax, rsp, BytesPerWord * 2);
        mov(rbx, rsp, BytesPerWord);
        mov(rdx, rsp, 0);
        push(rcx);
        push(rax);
        stackMapper.dupped2X2();
        break;

      case fadd: {
        if (BytesPerWord == 8) {
          popInt(rdi);
          popInt(rsi);
          directCall(reinterpret_cast<void*>(addFloat));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(addFloat));
          add(BytesPerWord * 2, rsp);
          stackMapper.poppedInt();
          stackMapper.poppedInt();
          pushInt(rax);
        }
      } break;

      case fcmpg: {
        if (BytesPerWord == 8) {
          popInt(rdi);
          popInt(rsi);
          directCall(reinterpret_cast<void*>(compareFloatsG));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(compareFloatsG));
          add(BytesPerWord * 2, rsp);
          stackMapper.poppedInt();
          stackMapper.poppedInt();
          pushInt(rax);
        }
      } break;

      case fcmpl: {
        if (BytesPerWord == 8) {
          popInt(rdi);
          popInt(rsi);
          directCall(reinterpret_cast<void*>(compareFloatsL));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(compareFloatsL));
          add(BytesPerWord * 2, rsp);
          stackMapper.poppedInt();
          stackMapper.poppedInt();
          pushInt(rax);
        }
      } break;

      case fconst_0: {
        pushInt(floatToBits(0.0));
      } break;

      case fconst_1: {
        pushInt(floatToBits(1.0));
      } break;

      case fconst_2: {
        pushInt(floatToBits(2.0));
      } break;

      case fdiv: {
        if (BytesPerWord == 8) {
          popInt(rdi);
          popInt(rsi);
          directCall(reinterpret_cast<void*>(divideFloat));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(divideFloat));
          add(BytesPerWord * 2, rsp);
          stackMapper.poppedInt();
          stackMapper.poppedInt();
          pushInt(rax);
        }
      } break;

      case fmul: {
        if (BytesPerWord == 8) {
          popInt(rdi);
          popInt(rsi);
          directCall(reinterpret_cast<void*>(multiplyFloat));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(multiplyFloat));
          add(BytesPerWord * 2, rsp);
          stackMapper.poppedInt();
          stackMapper.poppedInt();
          pushInt(rax);
        }
      } break;

      case frem: {
        if (BytesPerWord == 8) {
          popInt(rdi);
          popInt(rsi);
          directCall(reinterpret_cast<void*>(moduloFloat));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(moduloFloat));
          add(BytesPerWord * 2, rsp);
          stackMapper.poppedInt();
          stackMapper.poppedInt();
          pushInt(rax);
        }
      } break;

      case fsub: {
        if (BytesPerWord == 8) {
          popInt(rdi);
          popInt(rsi);
          directCall(reinterpret_cast<void*>(subtractFloat));
          pushInt(rax);
        } else {
          directCall(reinterpret_cast<void*>(subtractFloat));
          add(BytesPerWord * 2, rsp);
          stackMapper.poppedInt();
          stackMapper.poppedInt();
          pushInt(rax);
        }
      } break;

      case getfield: {
        uint16_t index = codeReadInt16(t, code, ip);
        
        object field = resolveField(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;
      
        popObject(rax);

        switch (fieldCode(t, field)) {
        case ByteField:
        case BooleanField:
          movs1(rax, fieldOffset(t, field), rax);
          pushInt(rax);
          break;

        case CharField:
          movz2(rax, fieldOffset(t, field), rax);
          pushInt(rax);
          break;

        case ShortField:
          movs2(rax, fieldOffset(t, field), rax);
          pushInt(rax);
          break;

        case FloatField:
        case IntField:
          pushInt4(rax, fieldOffset(t, field));
          break;

        case DoubleField:
        case LongField:
          pushLong(rax, fieldOffset(t, field));
          break;

        case ObjectField:
          pushObject(rax, fieldOffset(t, field));
          break;

        default:
          abort(t);
        }
      } break;

      case getstatic: {
        uint16_t index = codeReadInt16(t, code, ip);
        
        object field = resolveField(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;
        PROTECT(t, field);
        
        initClass(t, fieldClass(t, field));
        if (UNLIKELY(t->exception)) return 0;
        
        object table = classStaticTable(t, fieldClass(t, field));

        mov(poolRegister(), poolReference(table), rax);
        add((fieldOffset(t, field) * BytesPerWord) + ArrayBody, rax);
        
        switch (fieldCode(t, field)) {
        case ByteField:
        case BooleanField:
        case CharField:
        case ShortField:
        case FloatField:
        case IntField: {
          Label zero(this);
          Label next(this);

          cmp(0, rax);
          je(zero);

          push4(rax, IntValue);
          jmp(next);

          zero.mark();
          push(0);

          next.mark();
          stackMapper.pushedInt();
        } break;

        case DoubleField:
        case LongField: {
          Label zero(this);
          Label next(this);

          cmp(0, rax);
          je(zero);

          if (BytesPerWord == 8) {
            pushLongQuiet(rax, LongValue);
          } else {
            pushLongQuiet(rax, LongValue);
            pushLongQuiet(rax, LongValue + 4);
          }
          jmp(next);

          zero.mark();
          pushLongQuiet(0);

          next.mark();
          stackMapper.pushedLong();
        } break;

        case ObjectField: {
          pushObject(rax, 0);
        } break;

        default: abort(t);
        }
      } break;

      case goto_: {
        int16_t offset = codeReadInt16(t, code, ip);
        jmp((ip - 3) + offset);
        stackMapper.jumped((ip - 3) + offset);
      } break;

      case goto_w: {
        int32_t offset = codeReadInt32(t, code, ip);
        jmp((ip - 5) + offset);
        stackMapper.jumped((ip - 5) + offset);
      } break;

      case i2b:
        mov(rsp, 0, rax);
        movs1(rax, rax);
        mov(rax, rsp, 0);
        break;

      case i2c:
        mov(rsp, 0, rax);
        movz2(rax, rax);
        mov(rax, rsp, 0);
        break;

      case i2s:
        mov(rsp, 0, rax);
        movs2(rax, rax);
        mov(rax, rsp, 0);
        break;

      case i2l:
        if (BytesPerWord == 8) {
          pushInt();
        } else {
          popInt(rax);
          cdq();
          pushLong(rax, rdx);
        }
        break;

      case iadd:
        popInt(rax);
        popInt(rcx);
        add(rcx, rax);
        pushInt(rax);
        break;

      case iand:
        popInt(rax);
        and_(rax, rsp, 0);
        break;

      case iconst_m1:
        pushInt(-1);
        break;

      case iconst_0:
        pushInt(0);
        break;

      case iconst_1:
        pushInt(1);
        break;

      case iconst_2:
        pushInt(2);
        break;

      case iconst_3:
        pushInt(3);
        break;

      case iconst_4:
        pushInt(4);
        break;

      case iconst_5:
        pushInt(5);
        break;

      case vm::idiv:
        popInt(rcx);
        popInt(rax);
        cqo();
        Assembler::idiv(rcx);
        pushInt(rax);
        break;

      case if_acmpeq: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popObject(rax);
        popObject(rcx);
        cmp(rax, rcx);
        je((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case if_acmpne: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popObject(rax);
        popObject(rcx);
        cmp(rax, rcx);
        jne((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case if_icmpeq: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        popInt(rcx);
        cmp(rax, rcx);
        je((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case if_icmpne: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        popInt(rcx);
        cmp(rax, rcx);
        jne((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case if_icmpgt: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        popInt(rcx);
        cmp(rax, rcx);
        jg((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case if_icmpge: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        popInt(rcx);
        cmp(rax, rcx);
        jge((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case if_icmplt: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        popInt(rcx);
        cmp(rax, rcx);
        jl((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case if_icmple: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        popInt(rcx);
        cmp(rax, rcx);
        jle((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case ifeq: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        cmp(0, rax);
        je((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case ifnull: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popObject(rax);
        cmp(0, rax);
        je((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case ifne: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        cmp(0, rax);
        jne((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case ifnonnull: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popObject(rax);
        cmp(0, rax);
        jne((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case ifgt: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        cmp(0, rax);
        jg((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case ifge: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        cmp(0, rax);
        jge((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case iflt: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        cmp(0, rax);
        jl((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case ifle: {
        int16_t offset = codeReadInt16(t, code, ip);
        
        popInt(rax);
        cmp(0, rax);
        jle((ip - 3) + offset);
        stackMapper.branched((ip - 3) + offset);
      } break;

      case iinc: {
        uint8_t index = codeBody(t, code, ip++);
        int8_t c = codeBody(t, code, ip++);
    
        add(c, rbp, localOffset(t, index, method));
      } break;

      case iload:
      case fload:
        loadInt(codeBody(t, code, ip++));
        break;

      case iload_0:
      case fload_0:
        loadInt(0);
        break;

      case iload_1:
      case fload_1:
        loadInt(1);
        break;

      case iload_2:
      case fload_2:
        loadInt(2);
        break;

      case iload_3:
      case fload_3:
        loadInt(3);
        break;

      case vm::imul:
        popInt(rax);
        popInt(rcx);
        Assembler::imul(rcx);
        pushInt(rax);
        break;

      case ineg:
        neg(rsp, 0);
        break;

      case instanceof: {
        uint16_t index = codeReadInt16(t, code, ip);

        object class_ = resolveClass(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;

        Label call(this);
        Label zero(this);
        Label next(this);
        
        popObject(rax);
        cmp(0, rax);
        je(zero);

        mov(poolRegister(), poolReference(class_), rcx);
        mov(rax, 0, rax);
        and_(PointerMask, rax);
        cmp(rcx, rax);
        jne(call);
        
        push(1);
        jmp(next);

        call.mark();

        compileCall(true, reinterpret_cast<void*>(isAssignableFrom), rcx, rax);

        push(rax);
        jmp(next);

        zero.mark();
        push(0);

        next.mark();

        stackMapper.pushedInt();
      } break;

      case invokeinterface: {
        uint16_t index = codeReadInt16(t, code, ip);
        ip += 2;

        object target = resolveMethod(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;

        unsigned parameterFootprint
          = methodParameterFootprint(t, target) * BytesPerWord;

        unsigned instance = parameterFootprint - BytesPerWord;

        unsigned footprint = FrameFootprint + parameterFootprint;

        compileCall(false,
                    reinterpret_cast<void*>(findInterfaceMethodFromInstance),
                    target, rsp, instance);

        push(rsp);
        push(rax);
        push(rbp, FrameThread);

        mov(rax, MethodCompiled, rax);    // load compiled code
        add(CompiledBody, rax);
        call(rax);                        // call compiled code

        stackMapper.called(this->code.length());
        poolRegisterClobbered = true;

        add(footprint, rsp);              // pop arguments
        stackMapper.popped(methodParameterFootprint(t, target));

        pushReturnValue(methodReturnCode(t, target));
      } break;

      case invokespecial: {
        uint16_t index = codeReadInt16(t, code, ip);

        object target = resolveMethod(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;

        object class_ = methodClass(t, target);
        if (isSpecialMethod(t, target, class_)) {
          target = findMethod(t, target, classSuper(t, class_));
        }

        compileDirectInvoke(target);
      } break;

      case invokestatic: {
        uint16_t index = codeReadInt16(t, code, ip);

        object target = resolveMethod(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;
        PROTECT(t, target);

        initClass(t, methodClass(t, target));
        if (UNLIKELY(t->exception)) return 0;

        compileDirectInvoke(target);
      } break;

      case invokevirtual: {
        uint16_t index = codeReadInt16(t, code, ip);
        
        object target = resolveMethod(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;

        unsigned parameterFootprint
          = methodParameterFootprint(t, target) * BytesPerWord;

        unsigned instance = parameterFootprint - BytesPerWord;

        unsigned footprint = FrameFootprint + parameterFootprint;

        unsigned offset = ArrayBody + (methodOffset(t, target) * BytesPerWord);
                
        mov(rsp, instance, rax);          // load instance
        mov(rax, 0, rax);                 // load class
        and_(PointerMask, rax);           // clean pointer
        mov(rax, ClassVirtualTable, rax); // load vtable
        mov(rax, offset, rax);            // load method

        push(rsp);
        push(rax);
        push(rbp, FrameThread);

        mov(rax, MethodCompiled, rax);    // load compiled code
        add(CompiledBody, rax);
        call(rax);                        // call compiled code

        stackMapper.called(this->code.length());
        poolRegisterClobbered = true;

        add(footprint, rsp);              // pop arguments
        stackMapper.popped(methodParameterFootprint(t, target));

        pushReturnValue(methodReturnCode(t, target));
      } break;

      case ior:
        popInt(rax);
        or_(rax, rsp, 0);
        break;

      case irem:
        popInt(rcx);
        popInt(rax);
        cqo();
        Assembler::idiv(rcx);
        pushInt(rdx);
        break;

      case ireturn:
      case freturn:
        popInt(rax);
        mov(rbp, rsp);
        pop(rbp);
        ret();
        stackMapper.exited();
        break;

      case ishl:
        popInt(rcx);
        shl4_cl(rsp, 0);
        break;

      case ishr:
        popInt(rcx);
        sar4_cl(rsp, 0);
        break;

      case istore:
      case fstore:
        storeInt(codeBody(t, code, ip++));
        break;

      case istore_0:
      case fstore_0:
        storeInt(0);
        break;

      case istore_1:
      case fstore_1:
        storeInt(1);
        break;

      case istore_2:
      case fstore_2:
        storeInt(2);
        break;

      case istore_3:
      case fstore_3:
        storeInt(3);
        break;

      case isub:
        popInt(rax);
        sub(rax, rsp, 0);
        break;

      case iushr:
        popInt(rcx);
        shr4_cl(rsp, 0);
        break;

      case l2i:
        if (BytesPerWord == 8) {
          popInt();
        } else {
          popInt(rax);
          mov(rax, rsp, 0);
        }
        break;

      case ladd:
        if (BytesPerWord == 8) {
          popLong(rax);
          add(rax, rsp, BytesPerWord);
        } else {
          popLong(rax, rdx);
          add(rax, rsp, 0);
          adc(rdx, rsp, BytesPerWord);
        }
        break;

      case ldc:
      case ldc_w: {
        uint16_t index;

        if (instruction == ldc) {
          index = codeBody(t, code, ip++);
        } else {
          index = codeReadInt16(t, code, ip);
        }

        object v = arrayBody(t, codePool(t, code), index - 1);

        if (objectClass(t, v) == arrayBody(t, t->m->types, Machine::IntType)) {
          pushInt(intValue(t, v));
        } else if (objectClass(t, v)
                   == arrayBody(t, t->m->types, Machine::FloatType))
        {
          pushInt(floatValue(t, v));
        } else if (objectClass(t, v)
                   == arrayBody(t, t->m->types, Machine::StringType))
        {
          pushObject(poolRegister(), poolReference(v));
        } else {
          object class_ = resolveClass(t, codePool(t, code), index - 1);

          pushObject(poolRegister(), poolReference(class_));
        }
      } break;

      case ldc2_w: {
        uint16_t index = codeReadInt16(t, code, ip);

        object v = arrayBody(t, codePool(t, code), index - 1);

        if (objectClass(t, v) == arrayBody(t, t->m->types, Machine::LongType))
        {
          pushLong(longValue(t, v));
        } else if (objectClass(t, v)
                   == arrayBody(t, t->m->types, Machine::DoubleType))
        {
          pushLong(doubleValue(t, v));
        } else {
          abort(t);
        }
      } break;

      case lconst_0:
        pushLong(0);
        break;

      case lconst_1:
        pushLong(1);
        break;

      case lcmp: {
        Label next(this);
        Label less(this);
        Label greater(this);

        if (BytesPerWord == 8) {
          popLong(rax);
          popLong(rcx);
          
          cmp(rax, rcx);
          jl(less);
          jg(greater);

          push(0);
          jmp(next);
          
          less.mark();
          push(-1);
          jmp(next);

          greater.mark();
          push(1);

          next.mark();

          stackMapper.pushedInt();
        } else {
          popLong(rax, rdx);
          popLong(rcx, rbx);

          cmp(rdx, rbx);
          jl(less);
          jg(greater);
          
          cmp(rax, rcx);
          jb(less);
          ja(greater);

          push(0);
          jmp(next);

          less.mark();
          push(-1);
          jmp(next);

          greater.mark();
          push(1);

          next.mark();

          stackMapper.pushedInt();
        }
      } break;

      case ldiv_:
        if (BytesPerWord == 8) {
          popLong(rcx);
          popLong(rax);
          cqo();
          Assembler::idiv(rcx);
          pushLong(rax);
        } else {
          directCall(reinterpret_cast<void*>(divideLong));
          popLong();
          mov(rax, rsp, 0);
          mov(rdx, rsp, 4);
        }
        break;

      case lload:
      case dload:
        loadLong(codeBody(t, code, ip++));
        break;

      case lload_0:
      case dload_0:
        loadLong(0);
        break;

      case lload_1:
      case dload_1:
        loadLong(1);
        break;

      case lload_2:
      case dload_2:
        loadLong(2);
        break;

      case lload_3:
      case dload_3:
        loadLong(3);
        break;

      case lmul:
        if (BytesPerWord == 8) {
          popLong(rax);
          popLong(rcx);
          Assembler::imul(rcx);
          pushLong(rax);
        } else {
          mov(rsp, 4, rcx);
          Assembler::imul(rsp, 8, rcx);
          mov(rsp, 12, rax);
          Assembler::imul(rsp, 0, rax);
          add(rax, rcx);
          mov(rsp, 8, rax);
          mul(rsp, 0);
          add(rcx, rdx);

          popLong();
          mov(rax, rsp, 0);
          mov(rdx, rsp, 4);
        }
        break;

      case lneg:
        if (BytesPerWord == 8) {
          neg(rsp, 8);
        } else {
          mov(rsp, 0, rax);
          mov(rsp, 4, rdx);
          neg(rax);
          adc(0, rdx);
          neg(rdx);
          
          mov(rax, rsp, 0);
          mov(rdx, rsp, 4);
        }
        break;

      case lrem:
        if (BytesPerWord == 8) {
          popLong(rcx);
          popLong(rax);
          cqo();
          Assembler::idiv(rcx);
          pushLong(rdx);
        } else {
          directCall(reinterpret_cast<void*>(moduloLong));
          popLong();
          mov(rax, rsp, 0);
          mov(rdx, rsp, 4);
        }
        break;

      case lreturn:
      case dreturn:
        if (BytesPerWord == 8) {
          popLong(rax);
        } else {
          popLong(rax, rdx);
        }
        mov(rbp, rsp);
        pop(rbp);
        ret();
        stackMapper.exited();
        break;

      case lstore:
      case dstore:
        storeLong(codeBody(t, code, ip++));
        break;

      case lstore_0:
      case dstore_0:
        storeLong(0);
        break;

      case lstore_1:
      case dstore_1:
        storeLong(1);
        break;

      case lstore_2:
      case dstore_2:
        storeLong(2);
        break;

      case lstore_3:
      case dstore_3:
        storeLong(3);
        break;

      case lsub:
        if (BytesPerWord == 8) {
          popLong(rax);
          sub(rax, rsp, BytesPerWord);
        } else {
          popLong(rax, rdx);
          sub(rax, rsp, 0);
          sbb(rdx, rsp, BytesPerWord);
        }
        break;

      case new_: {
        uint16_t index = codeReadInt16(t, code, ip);
        
        object class_ = resolveClass(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;
        PROTECT(t, class_);
        
        initClass(t, class_);
        if (UNLIKELY(t->exception)) return 0;

        if (classVmFlags(t, class_) & WeakReferenceFlag) {
          compileCall(false, reinterpret_cast<void*>(makeNewWeakReference),
                      class_);
        } else {
          compileCall(false, reinterpret_cast<void*>(makeNew), class_);
        }

        pushObject(rax);
      } break;

      case newarray: {
        uint8_t type = codeBody(t, code, ip++);

        Label nonnegative(this);

        popInt(rax);
        cmp(0, rax);
        jge(nonnegative);

        compileThrowNew(Machine::NegativeArraySizeExceptionType);

        nonnegative.mark();

        object (*constructor)(Thread*, uintptr_t, bool);
        switch (type) {
        case T_BOOLEAN:
          constructor = makeBooleanArray;
          break;

        case T_CHAR:
          constructor = makeCharArray;
          break;

        case T_FLOAT:
          constructor = makeFloatArray;
          break;

        case T_DOUBLE:
          constructor = makeDoubleArray;
          break;

        case T_BYTE:
          constructor = makeByteArray;
          break;

        case T_SHORT:
          constructor = makeShortArray;
          break;

        case T_INT:
          constructor = makeIntArray;
          break;

        case T_LONG:
          constructor = makeLongArray;
          break;

        default: abort(t);
        }

        compileCall(false, reinterpret_cast<void*>(makeBlankArray),
                    reinterpret_cast<uintptr_t>(constructor), rax);

        pushObject(rax);
      } break;

      case pop_: {
        add(BytesPerWord, rsp);
        stackMapper.popped(1);
      } break;

      case putfield: {
        uint16_t index = codeReadInt16(t, code, ip);
    
        object field = resolveField(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;
      
        switch (fieldCode(t, field)) {
        case ByteField:
        case BooleanField:
        case CharField:
        case ShortField:
        case FloatField:
        case IntField: {
          popInt(rcx);
          popObject(rax);

          switch (fieldCode(t, field)) {
          case ByteField:
          case BooleanField:
            mov1(rcx, rax, fieldOffset(t, field));
            break;
            
          case CharField:
          case ShortField:
            mov2(rcx, rax, fieldOffset(t, field));
            break;
            
          case FloatField:
          case IntField:
            mov4(rcx, rax, fieldOffset(t, field));
            break;
          }
        } break;

        case DoubleField:
        case LongField: {
          if (BytesPerWord == 8) {
            popLong(rcx);
            popObject(rax);
            mov(rcx, rax, fieldOffset(t, field));
          } else {
            popLong(rcx, rbx);
            popObject(rax);
            mov(rcx, rax, fieldOffset(t, field));
            mov(rbx, rax, fieldOffset(t, field) + 4);
          }
        } break;

        case ObjectField: {
          if (BytesPerWord == 8) {
            popObject(rdx);
            popObject(rsi);
            add(fieldOffset(t, field), rsi);
            mov(rbp, FrameThread, rdi);
          } else {
            popObject(rdx);
            popObject(rsi);
            add(fieldOffset(t, field), rsi);
            push(rdx);
            push(rsi);
            push(rbp, FrameThread);
          }

          directCall(reinterpret_cast<void*>(set));

          if (BytesPerWord == 4) {
            add(BytesPerWord * 3, rsp);
          }
        } break;

        default: abort(t);
        }
      } break;

      case putstatic: {
        uint16_t index = codeReadInt16(t, code, ip);
        
        object field = resolveField(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return 0;

        initClass(t, fieldClass(t, field));
        if (UNLIKELY(t->exception)) return 0;
        
        object table = classStaticTable(t, fieldClass(t, field));
        unsigned offset = (fieldOffset(t, field) * BytesPerWord) + ArrayBody;
        
        switch (fieldCode(t, field)) {
        case ByteField:
        case BooleanField:
        case CharField:
        case ShortField:
        case FloatField:
        case IntField: {
          object intType = arrayBody(t, t->m->types, Machine::IntType);
          
          if (BytesPerWord == 8) {
            mov(poolRegister(), poolReference(intType), rsi);
            mov(rbp, FrameThread, rdi);
          } else {
            push(poolRegister(), poolReference(intType));
            push(rbp, FrameThread);
          }

          indirectCall(reinterpret_cast<void*>(makeNew));

          if (BytesPerWord == 4) {
            add(BytesPerWord * 2, rsp);
          }

          popInt4(rax, IntValue);

          if (BytesPerWord == 8) {
            mov(rax, rdx);
            mov(poolRegister(), poolReference(table), rsi);
            add(offset, rsi);
            mov(rbp, FrameThread, rdi);
          } else {
            push(rax);
            mov(poolRegister(), poolReference(table), rsi);
            add(offset, rsi);
            push(rsi);
            push(rbp, FrameThread);
          }

          directCall(reinterpret_cast<void*>(set));

          if (BytesPerWord == 4) {
            add(BytesPerWord * 3, rsp);
          }
        } break;

        case DoubleField:
        case LongField: {
          object longType = arrayBody(t, t->m->types, Machine::LongType);
          
          if (BytesPerWord == 8) {
            mov(poolRegister(), poolReference(longType), rsi);
            mov(rbp, FrameThread, rdi);
          } else {
            push(poolRegister(), poolReference(longType));
            push(rbp, FrameThread);
          }

          indirectCall(reinterpret_cast<void*>(makeNew));

          if (BytesPerWord == 8) {
            popLong(rax, LongValue);
          } else {
            add(BytesPerWord * 2, rsp);
            popLong(rax, LongValue,
                    rax, LongValue + 4);
          }

          if (BytesPerWord == 8) {
            mov(rax, rdx);
            mov(poolRegister(), poolReference(table), rsi);
            add(offset, rsi);
            mov(rbp, FrameThread, rdi);
          } else {
            push(rax);
            mov(poolRegister(), poolReference(table), rsi);
            add(offset, rsi);
            push(rsi);
            push(rbp, FrameThread);
          }

          directCall(reinterpret_cast<void*>(set));

          if (BytesPerWord == 4) {
            add(BytesPerWord * 3, rsp);
          }
        } break;

        case ObjectField:
          if (BytesPerWord == 8) {
            popObject(rdx);
            mov(poolRegister(), poolReference(table), rsi);
            add(offset, rsi);
            mov(rbp, FrameThread, rdi);
          } else {
            mov(poolRegister(), poolReference(table), rsi);
            add(offset, rsi);
            push(rsi);
            push(rbp, FrameThread);
          }

          directCall(reinterpret_cast<void*>(set));

          if (BytesPerWord == 4) {
            add(BytesPerWord * 3, rsp);
            stackMapper.poppedObject();
          }
          break;

        default: abort(t);
        }
      } break;

      case return_:
        mov(rbp, rsp);
        pop(rbp);
        ret();
        stackMapper.exited();
        break;

      case sipush: {
        pushInt(static_cast<int16_t>(codeReadInt16(t, code, ip)));
      } break;

      default:
        abort(t);
      }
    }

    resolveJumps();
    buildExceptionHandlerTable(code);

    return finish();
  }

  void resolveJumps() {
    for (unsigned i = 0; i < jumps.length(); i += 8) {
      uint32_t ip = jumps.get4(i);
      uint32_t offset = jumps.get4(i + 4);

      code.set4(offset, machineIPs[ip] - (offset + 4));
    }
  }

  void buildExceptionHandlerTable(object code) {
    PROTECT(t, code);

    object eht = codeExceptionHandlerTable(t, code);
    if (eht) {
      PROTECT(t, eht);

      for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
        ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
      
        nativeExceptionHandlerStart(exceptionHandlers + i)
          = machineIPs[exceptionHandlerStart(eh)];

        nativeExceptionHandlerEnd(exceptionHandlers + i)
          = machineIPs[exceptionHandlerEnd(eh)];

        nativeExceptionHandlerIp(exceptionHandlers + i)
          = machineIPs[exceptionHandlerIp(eh)];

        unsigned ct = exceptionHandlerCatchType(eh);
        object catchType;
        if (ct) {
          catchType = resolveClass
            (t, codePool(t, code), exceptionHandlerCatchType(eh) - 1);
        } else {
          catchType = 0;
        }

        nativeExceptionHandlerCatchType(exceptionHandlers + i)
          = (catchType ? (poolReference(catchType) / BytesPerWord) - 1 : 0);
      }
    }
  }

  Compiled* finish() {
    stackMapper.finish();
    return makeCompiled(t, method, &code, lineNumbers, exceptionHandlers,
                        &stackMapper);
  }

  object makePool() {
    if (pool.length()) {
      object array = makeArray(t, pool.length() / BytesPerWord, false);
      pool.copyTo(&arrayBody(t, array, 0));
      return array;
    } else {
      return 0;
    }
  }

  Register poolRegister() {
    return rdi;
  }

  uint32_t poolReference(object o) {
    if (poolRegisterClobbered) {
      mov(rbp, FrameMethod, rdi);
      mov(rdi, MethodCode, rdi);
      //poolRegisterClobbered = false;
    }
    pool.appendAddress(reinterpret_cast<uintptr_t>(o));
    return pool.length() + BytesPerWord;
  }

  object method;
  StackMapper stackMapper;
  bool poolRegisterClobbered;
  uint32_t* machineIPs;
  NativeLineNumber* lineNumbers;
  NativeExceptionHandler* exceptionHandlers;
  Buffer pool;

  class MyProtector: public Thread::Protector {
   public:
    MyProtector(JavaCompiler* c): Protector(c->t), c(c) { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(c->method));

      for (unsigned i = 0; i < c->pool.length(); i += BytesPerWord) {
        v->visit(reinterpret_cast<object*>(&(c->pool.getAddress(i))));
      }
    }

    JavaCompiler* c;
  } protector;
};

void
compileMethod2(MyThread* t, object method)
{
  if (reinterpret_cast<Compiled*>(methodCompiled(t, method))
      == t->m->processor->methodStub(t))
  {
    PROTECT(t, method);

    ACQUIRE(t, t->m->classLock);
    
    if (reinterpret_cast<Compiled*>(methodCompiled(t, method))
        == t->m->processor->methodStub(t))
    {
      if (Verbose) {
        fprintf(stderr, "compiling %s.%s\n",
                &byteArrayBody(t, className(t, methodClass(t, method)), 0),
                &byteArrayBody(t, methodName(t, method), 0));
      }            

      JavaCompiler c(t, method);
      Compiled* code = c.compile();
    
      if (Verbose) {
        fprintf(stderr, "compiled %s.%s from %p to %p\n",
                &byteArrayBody(t, className(t, methodClass(t, method)), 0),
                &byteArrayBody(t, methodName(t, method), 0),
                compiledCode(code),
                compiledCode(code) + compiledCodeLength(code));
      }

      if (false and
          strcmp(reinterpret_cast<const char*>
                 (&byteArrayBody(t, className(t, methodClass(t, method)), 0)),
                 "Memory") == 0 and
          strcmp(reinterpret_cast<const char*>
                 (&byteArrayBody(t, methodName(t, method), 0)),
                 "runningSum") == 0)
      {
        noop();
      }

      object pool = c.makePool();
      set(t, methodCode(t, method), pool);

      methodCompiled(t, method) = reinterpret_cast<uint64_t>(code);
    }
  }
}

void
updateCaller(MyThread* t, object method)
{
  uintptr_t stub = reinterpret_cast<uintptr_t>
    (compiledCode(static_cast<Compiled*>(t->m->processor->methodStub(t))));

  Assembler a(t->m->system);
  a.mov(stub, rax);
  unsigned offset = a.code.length() - BytesPerWord;

  a.call(rax);

  uint8_t* caller = static_cast<uint8_t*>(frameAddress(frameStart(t)))
    - a.code.length();
  if (memcmp(a.code.data, caller, a.code.length()) == 0) {
    // it's a direct call - update caller to point to new code

    // address must be aligned on a word boundary for this write to
    // be atomic
    assert(t, reinterpret_cast<uintptr_t>(caller + offset)
           % BytesPerWord == 0);

    *reinterpret_cast<void**>(caller + offset)
      = compiledCode(reinterpret_cast<Compiled*>(methodCompiled(t, method)));
  }
}

void
compileMethod(MyThread* t, object method)
{
  { PROTECT(t, method);
    compileMethod2(t, method);
    
    if (LIKELY(t->exception == 0)) {
      if (not methodVirtual(t, method)) {
        updateCaller(t, method);
      }
      return;
    }
  }

  unwind(t);
}

class ArgumentList {
 public:
  ArgumentList(Thread* t, uintptr_t* array, bool* objectMask, object this_,
               const char* spec, bool indirectObjects, va_list arguments):
    t(static_cast<MyThread*>(t)),
    next(this->t->argumentList),
    array(array),
    objectMask(objectMask),
    position(0),
    protector(this)
  {
    this->t->argumentList = this;

    addInt(reinterpret_cast<uintptr_t>(t));
    addObject(0); // reserve space for method
    addInt(0); // reserve space for frame

    if (this_) {
      addObject(this_);
    }

    for (MethodSpecIterator it(t, spec); it.hasNext();) {
      switch (*it.next()) {
      case 'L':
      case '[':
        if (indirectObjects) {
          object* v = va_arg(arguments, object*);
          addObject(v ? *v : 0);
        } else {
          addObject(va_arg(arguments, object));
        }
        break;
      
      case 'J':
      case 'D':
        addLong(va_arg(arguments, uint64_t));
        break;

      default:
        addInt(va_arg(arguments, uint32_t));
        break;        
      }
    }
  }

  ArgumentList(Thread* t, uintptr_t* array, bool* objectMask, object this_,
               const char* spec, object arguments):
    t(static_cast<MyThread*>(t)),
    next(this->t->argumentList),
    array(array),
    objectMask(objectMask),
    position(0),
    protector(this)
  {
    this->t->argumentList = this;

    addInt(0); // reserve space for trace pointer
    addObject(0); // reserve space for method pointer

    if (this_) {
      addObject(this_);
    }

    unsigned index = 0;
    for (MethodSpecIterator it(t, spec); it.hasNext();) {
      switch (*it.next()) {
      case 'L':
      case '[':
        addObject(objectArrayBody(t, arguments, index++));
        break;
      
      case 'J':
      case 'D':
        addLong(cast<int64_t>(objectArrayBody(t, arguments, index++),
                              BytesPerWord));
        break;

      default:
        addInt(cast<int32_t>(objectArrayBody(t, arguments, index++),
                             BytesPerWord));
        break;        
      }
    }
  }

  ~ArgumentList() {
    t->argumentList = next;
  }

  void addObject(object v) {
    array[position] = reinterpret_cast<uintptr_t>(v);
    objectMask[position] = true;
    ++ position;
  }

  void addInt(uintptr_t v) {
    array[position] = v;
    objectMask[position] = false;
    ++ position;
  }

  void addLong(uint64_t v) {
    memcpy(array + position, &v, 8);
    objectMask[position] = false;
    objectMask[position] = false;
    position += 2;
  }

  MyThread* t;
  ArgumentList* next;
  uintptr_t* array;
  bool* objectMask;
  unsigned position;

  class MyProtector: public Thread::Protector {
   public:
    MyProtector(ArgumentList* list): Protector(list->t), list(list) { }

    virtual void visit(Heap::Visitor* v) {
      for (unsigned i = 0; i < list->position; ++i) {
        if (list->objectMask[i]) {
          v->visit(reinterpret_cast<object*>(list->array + i));
        }
      }
    }

    ArgumentList* list;
  } protector;
};

object
invoke(Thread* thread, object method, ArgumentList* arguments)
{
  MyThread* t = static_cast<MyThread*>(thread);

  arguments->array[1] = reinterpret_cast<uintptr_t>(method);
  
  unsigned returnCode = methodReturnCode(t, method);
  unsigned returnType = fieldType(t, returnCode);

  Reference* reference = t->reference;

  void* frameData[2];
  memcpy(frameData, t->frameData, BytesPerWord * 2);

  if (frameData[0]) {
    arguments->array[2] = reinterpret_cast<uintptr_t>(framePointer(frameData));
  }

  Compiled* code = reinterpret_cast<Compiled*>(methodCompiled(t, method));
  uint64_t result = vmInvoke
    (compiledCode(code), arguments->array, arguments->position * BytesPerWord,
     returnType);

  while (t->reference != reference) {
    dispose(t, t->reference);
  }

  memcpy(t->frameData, frameData, BytesPerWord * 2);

  object r;
  switch (returnCode) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    r = makeInt(t, result);
    break;

  case LongField:
  case DoubleField:
    r = makeLong(t, result);
    break;

  case ObjectField:
    r = (result == 0 ? 0 :
         *reinterpret_cast<object*>(static_cast<uintptr_t>(result)));
    break;

  case VoidField:
    r = 0;
    break;

  default:
    abort(t);
  };

  return r;
}

class MyProcessor: public Processor {
 public:
  MyProcessor(System* s):
    s(s),
    methodStub_(0),
    nativeInvoker_(0)
  { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent)
  {
    return new (s->allocate(sizeof(MyThread))) MyThread(m, javaThread, parent);
  }

  virtual void*
  methodStub(Thread* t)
  {
    if (methodStub_ == 0) {
      Compiler c(static_cast<MyThread*>(t));
      methodStub_ = c.compileStub();

      if (Verbose) {
        fprintf(stderr, "compiled method stub from %p to %p\n",
                compiledCode(methodStub_),
                compiledCode(methodStub_) + compiledCodeLength(methodStub_));
      }
    }
    return methodStub_;
  }

  virtual void*
  nativeInvoker(Thread* t)
  {
    if (nativeInvoker_ == 0) {
      Compiler c(static_cast<MyThread*>(t));
      nativeInvoker_ = c.compileNativeInvoker();

      if (Verbose) {
        fprintf(stderr, "compiled native invoker from %p to %p\n",
                compiledCode(nativeInvoker_),
                compiledCode(nativeInvoker_)
                + compiledCodeLength(nativeInvoker_));
      }
    }
    return nativeInvoker_;
  }

  Compiled*
  caller(Thread* t)
  {
    if (caller_ == 0) {
      Compiler c(static_cast<MyThread*>(t));
      caller_ = c.compileCaller();

      if (Verbose) {
        fprintf(stderr, "compiled caller from %p to %p\n",
                compiledCode(caller_),
                compiledCode(caller_) + compiledCodeLength(caller_));
      }
    }
    return caller_;
  }

  virtual unsigned
  parameterFootprint(vm::Thread* t, const char* s, bool static_)
  {
    unsigned footprint = 0;
    for (MethodSpecIterator it(t, s); it.hasNext();) {
      switch (*it.next()) {
      case 'J':
      case 'D':
        footprint += 2;
        break;

      default:
        ++ footprint;
        break;        
      }
    }

    if (not static_) {
      ++ footprint;
    }
    return footprint;
  }

  virtual void
  initClass(Thread* t, object c)
  {
    PROTECT(t, c);
    
    ACQUIRE(t, t->m->classLock);
    if (classVmFlags(t, c) & NeedInitFlag
        and (classVmFlags(t, c) & InitFlag) == 0)
    {
      classVmFlags(t, c) |= InitFlag;
      invoke(t, classInitializer(t, c), 0);
      if (t->exception) {
        t->exception = makeExceptionInInitializerError(t, t->exception);
      }
      classVmFlags(t, c) &= ~(NeedInitFlag | InitFlag);
    }
  }

  virtual void
  visitObjects(Thread* vmt, Heap::Visitor* v)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    if (t->m->active) {
      for (Reference* r = t->reference; r; r = r->next) {
        v->visit(&(r->target));
      }

      visitStack(t, v);
    }
  }

  virtual uintptr_t
  frameStart(Thread* vmt)
  {
    return reinterpret_cast<uintptr_t>
      (::frameStart(static_cast<MyThread*>(vmt)));
  }

  virtual uintptr_t
  frameNext(Thread*, uintptr_t frame)
  {
    return reinterpret_cast<uintptr_t>
      (::frameNext(reinterpret_cast<void*>(frame)));
  }

  virtual bool
  frameValid(Thread*, uintptr_t frame)
  {
    return ::frameValid(reinterpret_cast<void*>(frame));
  }

  virtual object
  frameMethod(Thread*, uintptr_t frame)
  {
    return ::frameMethod(reinterpret_cast<void*>(frame));
  }

  virtual unsigned
  frameIp(Thread* t, uintptr_t frame)
  {
    void* f = reinterpret_cast<void*>(frame);
    return addressOffset(t, ::frameMethod(f), ::frameAddress(f));
  }

  virtual int
  lineNumber(Thread* t, object method, unsigned ip)
  {
    if (methodFlags(t, method) & ACC_NATIVE) {
      return NativeLine;
    }

    Compiled* code = reinterpret_cast<Compiled*>(methodCompiled(t, method));
    if (compiledLineNumberCount(t, code)) {
      unsigned bottom = 0;
      unsigned top = compiledLineNumberCount(t, code);
      for (unsigned span = top - bottom; span; span = top - bottom) {
        unsigned middle = bottom + (span / 2);
        NativeLineNumber* ln = compiledLineNumber(t, code, middle);

        if (ip >= nativeLineNumberIp(ln)
            and (middle + 1 == compiledLineNumberCount(t, code)
                 or ip < nativeLineNumberIp
                 (compiledLineNumber(t, code, middle + 1))))
        {
          return nativeLineNumberLine(ln);
        } else if (ip < nativeLineNumberIp(ln)) {
          top = middle;
        } else if (ip > nativeLineNumberIp(ln)) {
          bottom = middle + 1;
        }
      }

      abort(t);
    } else {
      return UnknownLine;
    }
  }

  virtual object*
  makeLocalReference(Thread* vmt, object o)
  {
    if (o) {
      MyThread* t = static_cast<MyThread*>(vmt);

      Reference* r = new (t->m->system->allocate(sizeof(Reference)))
        Reference(o, &(t->reference));

      return &(r->target);
    } else {
      return 0;
    }
  }

  virtual void
  disposeLocalReference(Thread* t, object* r)
  {
    if (r) {
      vm::dispose(t, reinterpret_cast<Reference*>(r));
    }
  }

  virtual object
  invokeArray(Thread* t, object method, object this_, object arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

    const char* spec = reinterpret_cast<char*>
      (&byteArrayBody(t, methodSpec(t, method), 0));

    unsigned size = methodParameterFootprint(t, method) + FrameFootprint;
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list(t, array, objectMask, this_, spec, arguments);
    
    return ::invoke(t, method, &list);
  }

  virtual object
  invokeList(Thread* t, object method, object this_, bool indirectObjects,
             va_list arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));
    
    const char* spec = reinterpret_cast<char*>
      (&byteArrayBody(t, methodSpec(t, method), 0));

    unsigned size = methodParameterFootprint(t, method) + FrameFootprint;
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list
      (t, array, objectMask, this_, spec, indirectObjects, arguments);

    return ::invoke(t, method, &list);
  }

  virtual object
  invokeList(Thread* t, const char* className, const char* methodName,
             const char* methodSpec, object this_, va_list arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    unsigned size = parameterFootprint(t, methodSpec, false) + FrameFootprint;
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list
      (t, array, objectMask, this_, methodSpec, false, arguments);

    object method = resolveMethod(t, className, methodName, methodSpec);
    if (LIKELY(t->exception == 0)) {
      assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

      return ::invoke(t, method, &list);
    } else {
      return 0;
    }
  }

  virtual void dispose() {
    if (methodStub_) {
      s->free(methodStub_);
    }

    if (nativeInvoker_) {
      s->free(nativeInvoker_);
    }

    if (caller_) {
      s->free(caller_);
    }

    s->free(this);
  }
  
  System* s;
  Compiled* methodStub_;
  Compiled* nativeInvoker_;
  Compiled* caller_;
};

Compiled*
caller(MyThread* t)
{
  return static_cast<MyProcessor*>(t->m->processor)->caller(t);
}

} // namespace

namespace vm {

void
printJavaTrace(Thread* t, void* base, void* ip)
{
  void* top[] = { base, ip };

  printTrace
    (t, makeRuntimeException
     (t, 0, makeTrace
      (t, reinterpret_cast<uintptr_t>
       (top + (FrameFootprint / BytesPerWord) + 2)), 0));
}

Processor*
makeProcessor(System* system)
{
  return new (system->allocate(sizeof(MyProcessor))) MyProcessor(system);
}

} // namespace vm

