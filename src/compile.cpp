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

namespace {

const unsigned FrameThread = BytesPerWord * 2;
const unsigned FrameMethod = FrameThread + BytesPerWord;
const unsigned FrameNext = FrameNext + BytesPerWord;
const unsigned FrameFootprint = BytesPerWord * 3;

class Rope {
 public:
  class Node {
   public:
    static const unsigned Size = 32;

    Node():
      next(0)
    { }

    Node* next;
    uint8_t data[Size];
  };

  Rope() { }

  Rope(System* s):
    s(s),
    front(0),
    rear(0),
    count(0),
    position(Node::Size)
  { }

  void append(uint8_t v) {
    if (position == Node::Size) {
      if (front == 0 or rear->next == 0) {
        Node* n = new (s->allocate(sizeof(Node))) Node;
        if (front == 0) {
          front = rear = n;
        } else {
          rear->next = n;
          rear = n;
        }
      } else {
        rear = rear->next;
      }
      position = 0;
      ++ count;
    }

    rear->data[position++] = v;
  }

  void append4(uint32_t v) {
    append((v >>  0) & 0xFF);
    append((v >>  8) & 0xFF);
    append((v >> 16) & 0xFF);
    append((v >> 24) & 0xFF);
  }

  void appendAddress(uintptr_t v) {
    append4(v);
    if (BytesPerWord == 8) {
      append4(v >> 32);
    }
  }

  unsigned length() {
    return (count * Node::Size) + position;
  }

  void copyTo(uint8_t* b) {
    if (front) {
      Node* n = front;
      while (true) {
        if (n == rear) {
          memcpy(b, n->data, position);
          break;
        } else {
          memcpy(b, n->data, Node::Size);
          b += Node::Size;
          n = n->next;
        }
      }
    }
  }

  System* s;
  Node* front;
  Node* rear;
  unsigned count;
  unsigned position;
};

class ArgumentList;

class MyThread: public Thread {
 public:
  MyThread(Machine* m, object javaThread, vm::Thread* parent):
    vm::Thread(m, javaThread, parent),
    argumentList(0),
    frame(0)
  { }

  ArgumentList* argumentList;
  void* frame;
};

inline bool
isByte(int32_t v)
{
  return v == static_cast<int8_t>(v);
}

class Assembler {
 public:
  class Label {
   public:
    class Snapshot {
     public:
      Rope code;
      unsigned ip;
    };

    static const unsigned Capacity = 8;

    Label(Assembler* a):
      code(&(a->code)),
      unresolvedCount(0),
      mark_(-1)
    { }

    void reference(unsigned ip) {
      if (mark_ == -1) {
        expect(code->s, unresolvedCount < Capacity);
        unresolved[unresolvedCount].code = *code;
        unresolved[unresolvedCount].ip = ip;
        ++ unresolvedCount;

        code->appendAddress(0);
      } else {
        code->appendAddress(mark_ - ip);
      }
    }

    void mark() {
      mark_ = code->length();
      for (unsigned i = 0; i < unresolvedCount; ++i) {
        unresolved[i].code.appendAddress(mark_ - unresolved[i].ip);
      }
    }

    Rope* code;
    Snapshot unresolved[Capacity];
    unsigned unresolvedCount;
    int mark_;
  };

  enum Register {
    rax = 0,
    rcx = 1,
    rdx = 2,
    rbx = 3,
    rsp = 4,
    rbp = 5,
    rsi = 6,
    rdi = 7
  };

  Assembler(System* s):
    code(s)
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

  void mov(Register src, int32_t srcOffset, Register dst) {
    rex();
    code.append(0x8b);
    if (srcOffset) {
      if (isByte(srcOffset)) {
        code.append(0x40 | (dst << 3) | src);
        code.append(srcOffset);
      } else {
        code.append(0x80 | (dst << 3) | src);
        code.append4(srcOffset);
      }
    } else {
      code.append((dst << 3) | src);
    }
  }

  void mov(Register src, Register dst, int32_t dstOffset) {
    rex();
    code.append(0x89);
    if (dstOffset) {
      if (isByte(dstOffset)) {
        code.append(0x40 | (src << 3) | dst);
        code.append(dstOffset);
      } else {
        code.append(0x80 | (src << 3) | dst);
        code.append4(dstOffset);
      }
    } else {
      code.append((src << 3) | dst);
    }
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

  void nop() {
    code.append(0x90);
  }

  void push(Register reg) {
    code.append(0x50 | reg);
  }

  void push(Register reg, int32_t offset) {
    assert(code.s, isByte(offset)); // todo

    code.append(0xff);
    code.append(0x70 | reg);
    code.append(offset);
  }

  void push(int32_t v) {
    assert(code.s, isByte(v)); // todo

    code.append(0x6a);
    code.append(v);
  }

  void pop(Register dst) {
    code.append(0x58 | dst);
  }

  void pop(Register dst, int32_t offset) {
    assert(code.s, isByte(offset)); // todo

    code.append(0x8f);
    code.append(0x40 | dst);
    code.append(offset);
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

  void or_(Register src, Register dst) {
    rex();
    code.append(0x09);
    code.append(0xc0 | (src << 3) | dst);
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

  void and_(int32_t v, Register dst) {
    assert(code.s, isByte(v)); // todo

    rex();
    code.append(0x83);
    code.append(0xe0 | dst);
    code.append(v);
  }

  void ret() {
    code.append(0xc3);
  }

  void jmp(Label& label) {
    code.append(0xE9);
    label.reference(code.length() + BytesPerWord);
  }

  void jmp(Register reg) {
    code.append(0xff);
    code.append(0xe0 | reg);
  }

//   void jmp(Register reg, int offset) {
//     code.append(0xff);
//     code.append(0x60 | reg);
//     code.append(offset);
//   }

  void jz(Label& label) {
    code.append(0x0F);
    code.append(0x84);
    label.reference(code.length() + BytesPerWord);
  }

  void je(Label& label) {
    jz(label);
  }

  void jnz(Label& label) {
    code.append(0x0F);
    code.append(0x85);
    label.reference(code.length() + BytesPerWord);
  }

  void jne(Label& label) {
    jnz(label);
  }

  void cmp(int v, Register reg) {
    code.append(0x83);
    code.append(0xf8 | reg);
    code.append(v);
  }

  void call(Register reg) {
    code.append(0xff);
    code.append(0xd0 | reg);
  }

  Rope code;
};

void
compileMethod(MyThread* t, object method);

int
localOffset(int v, int parameterFootprint)
{
  v *= BytesPerWord;
  if (v < parameterFootprint) {
    return v + (BytesPerWord * 2) + FrameFootprint;
  } else {
    return -(v + BytesPerWord - parameterFootprint);
  }
}

class Compiler: public Assembler {
 public:
  Compiler(System* s):
    Assembler(s)
  { }

  void pushReturnValue(Thread* t, unsigned code) {
    switch (code) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField:
    case ObjectField:
      push(rax);
      break;

    case LongField:
    case DoubleField:
      push(rax);
      push(rdx);
      break;

    case VoidField:
      break;

    default:
      abort(t);
    }
  }

  void compile(Thread* t, object method) {
    PROTECT(t, method);

    push(rbp);
    mov(rsp, rbp);

    object code = methodCode(t, method);
    PROTECT(t, code);

    unsigned parameterFootprint
      = methodParameterFootprint(t, method) * BytesPerWord;

    unsigned localFootprint = codeMaxLocals(t, code) * BytesPerWord;

    // reserve space for local variables
    sub(localFootprint - parameterFootprint, rsp);
    
    for (unsigned ip = 0; ip < codeLength(t, code);) {
      unsigned instruction = codeBody(t, code, ip++);

      switch (instruction) {
      case areturn:
        pop(rax);
        mov(rbp, rsp);
        pop(rbp);
        ret();
        break;

      case dup:
        push(rsp, 4);
        break;

      case getstatic: {
        uint16_t index = codeReadInt16(t, code, ip);
        
        object field = resolveField(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return;
        PROTECT(t, field);
        
        initClass(t, fieldClass(t, field));
        if (UNLIKELY(t->exception)) return;
        
        object table = classStaticTable(t, fieldClass(t, field));

        mov(reinterpret_cast<uintptr_t>(table), rax);
        add(fieldOffset(t, field), rax);
        
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

          push(rax, IntValue);
          jmp(next);

          zero.mark();
          push(0);

          next.mark();
        } break;

        case DoubleField:
        case LongField: {
          Label zero(this);
          Label next(this);

          cmp(0, rax);
          je(zero);

          push(rax, LongValue);
          push(rax, LongValue + 4);
          jmp(next);

          zero.mark();
          push(0);
          push(0);

          next.mark();
        } break;

        case ObjectField: {
          push(rax);
        } break;

        default: abort(t);
        }
      } break;

      case iadd:
        pop(rax);
        pop(rdx);
        add(rax, rdx);
        push(rdx);
        break;

      case iconst_m1:
        push(-1);
        break;

      case iconst_0:
        push(0);
        break;

      case iconst_1:
        push(1);
        break;

      case iconst_2:
        push(2);
        break;

      case iconst_3:
        push(3);
        break;

      case iconst_4:
        push(4);
        break;

      case iconst_5:
        push(5);
        break;

      case iload_0:
      case fload_0:
        push(rbp, localOffset(0, parameterFootprint));
        break;

      case iload_1:
      case fload_1:
        push(rbp, localOffset(1, parameterFootprint));
        break;

      case iload_2:
      case fload_2:
        push(rbp, localOffset(2, parameterFootprint));
        break;

      case iload_3:
      case fload_3:
        push(rbp, localOffset(3, parameterFootprint));
        break;

      case invokespecial: {
        uint16_t index = codeReadInt16(t, code, ip);

        object target = resolveMethod(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return;

        object class_ = methodClass(t, method);
        if (isSpecialMethod(t, target, class_)) {
          class_ = classSuper(t, class_);
          target = findMethod(t, target, class_);
        }

        unsigned footprint = FrameFootprint
          + methodParameterFootprint(t, target) * BytesPerWord;

        uint8_t* code = &compiledBody(t, methodCompiled(t, target), 0);
        
        push(rbp, 0);
        push(reinterpret_cast<uintptr_t>(target));
        push(rbp, FrameThread);

        alignedMov(reinterpret_cast<uintptr_t>(code), rax);
        call(rax);

        add(footprint, rsp);              // pop arguments

        pushReturnValue(t, methodReturnCode(t, method));
      } break;

      case invokevirtual: {
        uint16_t index = codeReadInt16(t, code, ip);
        
        object target = resolveMethod(t, codePool(t, code), index - 1);
        if (UNLIKELY(t->exception)) return;

        unsigned footprint = FrameFootprint
          + methodParameterFootprint(t, target) * BytesPerWord;

        unsigned offset = ArrayBody + (methodOffset(t, target) * BytesPerWord);
        
        push(rbp, 0);
        push(reinterpret_cast<uintptr_t>(target));
        push(rbp, FrameThread);
        
        mov(rsp, BytesPerWord * 3, rax);  // load target object
        mov(rax, 0, rax);                 // load target class
        mov(rax, ClassVirtualTable, rax); // load vtable
        mov(rax, offset, rax);            // load method
        mov(rax, MethodCompiled, rax);    // load compiled code
        add(CompiledBody, rax);
        call(rax);                        // call compiled code

        add(footprint, rsp);              // pop arguments

        pushReturnValue(t, methodReturnCode(t, method));
      } break;

      case istore_0:
      case fstore_0:
        pop(rbp, localOffset(0, parameterFootprint));
        break;

      case istore_1:
      case fstore_1:
        pop(rbp, localOffset(1, parameterFootprint));
        break;

      case istore_2:
      case fstore_2:
        pop(rbp, localOffset(2, parameterFootprint));
        break;

      case istore_3:
      case fstore_3:
        pop(rbp, localOffset(3, parameterFootprint));
        break;

      case ldc:
      case ldc_w: {
        uint16_t index;

        if (instruction == ldc) {
          index = codeBody(t, code, ip++);
        } else {
          uint8_t index1 = codeBody(t, code, ip++);
          uint8_t index2 = codeBody(t, code, ip++);
          index = (index1 << 8) | index2;
        }

        object v = arrayBody(t, codePool(t, code), index - 1);

        if (objectClass(t, v) == arrayBody(t, t->m->types, Machine::IntType)) {
          push(intValue(t, v));
        } else if (objectClass(t, v)
                   == arrayBody(t, t->m->types, Machine::FloatType))
        {
          push(floatValue(t, v));
        } else if (objectClass(t, v)
                   == arrayBody(t, t->m->types, Machine::StringType))
        {
          push(reinterpret_cast<uintptr_t>(v));
        } else {
          object class_ = resolveClass(t, codePool(t, code), index - 1);

          push(reinterpret_cast<uintptr_t>(class_));
        }
      } break;

      case pop_: {
        add(BytesPerWord, rsp);
      } break;

      case return_:
        mov(rbp, rsp);
        pop(rbp);
        ret();
        break;

      default:
        abort(t);
      }
    }
  }

  void compileStub(MyThread* t) {
    unsigned frameOffset = reinterpret_cast<uintptr_t>(&(t->frame))
      - reinterpret_cast<uintptr_t>(t);

    push(rbp);
    mov(rsp, rbp);

    mov(rbp, FrameThread, rax);
    mov(rbp, rax, frameOffset);              // set thread frame to current

    if (BytesPerWord == 4) {
      push(rbp, FrameMethod);
      push(rbp, FrameThread);
    } else {
      mov(rbp, FrameMethod, rsi);
      mov(rbp, FrameThread, rdi);
    }

    mov(reinterpret_cast<uintptr_t>(compileMethod), rax);
    call(rax);

    if (BytesPerWord == 4) {
      add(BytesPerWord * 2, rsp);
    }

    mov(rbp, FrameMethod, rax);
    mov(rax, MethodCompiled, rax);           // load compiled code

    mov(rbp, rsp);
    pop(rbp);
    
    add(CompiledBody, rax);
    jmp(rax);                                // call compiled code
  }
};

void
compileMethod2(MyThread* t, object method)
{
  if (methodCompiled(t, method) == t->m->processor->methodStub(t)) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->classLock);
    
    if (methodCompiled(t, method) == t->m->processor->methodStub(t)) {
      Compiler c(t->m->system);
      c.compile(t, method);
    
      object compiled = makeCompiled(t, 0, c.code.length(), false);
      if (UNLIKELY(t->exception)) return;

      c.code.copyTo(&compiledBody(t, compiled, 0));
    
      set(t, methodCompiled(t, method), compiled);
    }
  }
}

void
updateCaller(MyThread* t, object method)
{
  uintptr_t stub = reinterpret_cast<uintptr_t>
    (&compiledBody(t, t->m->processor->methodStub(t), 0));

  Assembler a(t->m->system);
  a.mov(stub, Assembler::rax);
  unsigned offset = a.code.length() - BytesPerWord;

  a.call(Assembler::rax);

  uint8_t* caller = static_cast<uint8_t**>(t->frame)[1] - a.code.length();
  if (memcmp(a.code.front->data, caller, a.code.length()) == 0) {
    // it's a direct call - update caller to point to new code

    // address must be aligned on a word boundary for this write to
    // be atomic
    assert(t, reinterpret_cast<uintptr_t>(caller + offset)
           % BytesPerWord == 0);

    *reinterpret_cast<void**>(caller + offset)
      = &compiledBody(t, methodCompiled(t, method), 0);
  }
}

void
unwind(Thread* t)
{
  // todo
  abort(t);
}

void
compileMethod(MyThread* t, object method)
{
  if (methodVirtual(t, method)) {
    object this_ = static_cast<object*>
      (t->frame)[2 + (FrameFootprint / BytesPerWord)];
    method = findMethod(t, method, objectClass(t, this_));
  }

  compileMethod2(t, method);
  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    updateCaller(t, method);
  }
}

object
compileStub(Thread* t)
{
  Compiler c(t->m->system);
  c.compileStub(static_cast<MyThread*>(t));
  
  object stub = makeCompiled(t, 0, c.code.length(), false);
  c.code.copyTo(&compiledBody(t, stub, 0));

  return stub;
}

class ArgumentList {
 public:
  ArgumentList(Thread* t, uintptr_t* array, bool* objectMask, object this_,
               const char* spec, bool indirectObjects, va_list arguments):
    t(static_cast<MyThread*>(t)),
    next(this->t->argumentList),
    array(array),
    objectMask(objectMask),
    position(0)
  {
    this->t->argumentList = this;

    addInt(reinterpret_cast<uintptr_t>(t));
    addObject(0); // reserve space for method
    addInt(reinterpret_cast<uintptr_t>(this->t->frame));

    if (this_) {
      addObject(this_);
    }

    const char* s = spec;
    ++ s; // skip '('
    while (*s and *s != ')') {
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;

        if (indirectObjects) {
          object* v = va_arg(arguments, object*);
          addObject(v ? *v : 0);
        } else {
          addObject(va_arg(arguments, object));
        }
        break;

      case '[':
        while (*s == '[') ++ s;
        switch (*s) {
        case 'L':
          while (*s and *s != ';') ++ s;
          ++ s;
          break;

        default:
          ++ s;
          break;
        }

        if (indirectObjects) {
          object* v = va_arg(arguments, object*);
          addObject(v ? *v : 0);
        } else {
          addObject(va_arg(arguments, object));
        }
        break;
      
      case 'J':
      case 'D':
        ++ s;
        addLong(va_arg(arguments, uint64_t));
        break;
          
      default:
        ++ s;
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
    position(0)
  {
    this->t->argumentList = this;

    addInt(0); // reserve space for trace pointer
    addObject(0); // reserve space for method pointer

    if (this_) {
      addObject(this_);
    }

    unsigned index = 0;
    const char* s = spec;
    ++ s; // skip '('
    while (*s and *s != ')') {
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;
        addObject(objectArrayBody(t, arguments, index++));
        break;

      case '[':
        while (*s == '[') ++ s;
        switch (*s) {
        case 'L':
          while (*s and *s != ';') ++ s;
          ++ s;
          break;

        default:
          ++ s;
          break;
        }
        addObject(objectArrayBody(t, arguments, index++));
        break;
      
      case 'J':
      case 'D':
        ++ s;
        addLong(cast<int64_t>(objectArrayBody(t, arguments, index++),
                              BytesPerWord));
        break;

      default:
        ++ s;
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

  void addInt(uint32_t v) {
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
};

object
invoke(Thread* thread, object method, ArgumentList* arguments)
{
  MyThread* t = static_cast<MyThread*>(thread);

  arguments->array[1] = reinterpret_cast<uintptr_t>(method);
  
  unsigned returnCode = methodReturnCode(t, method);
  unsigned returnType = fieldType(t, returnCode);

  uint64_t result = vmInvoke
    (&compiledBody(t, methodCompiled(t, method), 0), arguments->array,
     arguments->position * BytesPerWord, returnType);

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
    stub(0)
  { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent)
  {
    return new (s->allocate(sizeof(MyThread))) MyThread(m, javaThread, parent);
  }

  virtual object
  methodStub(Thread* t)
  {
    if (stub == 0) {
      stub = compileStub(t);
    }
    return stub;
  }

  virtual unsigned
  parameterFootprint(vm::Thread*, const char* s, bool static_)
  {
    unsigned footprint = 0;
    ++ s; // skip '('
    while (*s and *s != ')') {
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;
        break;

      case '[':
        while (*s == '[') ++ s;
        switch (*s) {
        case 'L':
          while (*s and *s != ';') ++ s;
          ++ s;
          break;

        default:
          ++ s;
          break;
        }
        break;
      
      case 'J':
      case 'D':
        ++ s;
        if (BytesPerWord == 4) {
          ++ footprint;
        }
        break;

      default:
        ++ s;
        break;
      }

      ++ footprint;
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
      invoke(t, classInitializer(t, c), 0);
      if (t->exception) {
        t->exception = makeExceptionInInitializerError(t, t->exception);
      }
      classVmFlags(t, c) &= ~(NeedInitFlag | InitFlag);
    }
  }

  virtual void
  visitObjects(Thread* t, Heap::Visitor*)
  {
    abort(t);
  }

  virtual uintptr_t
  frameStart(Thread* t)
  {
    abort(t);
  }

  virtual uintptr_t
  frameNext(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual bool
  frameValid(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual object
  frameMethod(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual unsigned
  frameIp(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual object*
  makeLocalReference(Thread* t, object)
  {
    abort(t);
  }

  virtual void
  disposeLocalReference(Thread* t, object*)
  {
    abort(t);
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
    s->free(this);
  }
  
  System* s;
  object stub;
};

} // namespace

namespace vm {

Processor*
makeProcessor(System* system)
{
  return new (system->allocate(sizeof(MyProcessor))) MyProcessor(system);
}

} // namespace vm

