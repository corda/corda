namespace {

void
iterate(Thread* t, Heap::Visitor* v)
{
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
  class Iterator: Heap::Iterator {
   public:
    Iterator(Machine* m): machine(m) { }

    void iterate(Heap::Visitor* v) {
      for (Thread* t = m->rootThread; t; t = t->next) {
        ::iterate(t, v);
      }
    }
    
   private:
    Machine* machine;
  } it(m);

  m->heap.collect(type, &it);
}

object
collectAndAllocate(Thread* t, unsigned size)
{
  if (size > Thread::HeapSize) {
    abort(); // not yet implemented
  }

  LOCK(t->vm->stateLock);

  if (t->vm->exclusive) {
    // enter idle state and wait for collection to finish.
    t->state = Thread::IdleState;
    -- t->vm->activeCount;
    
    t->vm->stateLock->notifyAll();

    while (t->vm->exclusive) {
      t->vm->stateLock->wait();
    }

    // wake up.
    state = Thread::ActiveState;
    ++ t->vm->activeCount;
  } else {
    // enter exclusive state and wait for other threads to go idle.
    t->vm->exclusive = t;
    
    while (t->vm->activeCount > 1) {
      t->vm->stateLock->wait();
    }

    // collect.
    collect(t->vm, Heap::MinorCollection);

    // signal collection finish.
    t->vm->exclusive = 0;
    t->vm->stateLock->notifyAll();
  }

  t->heapIndex += size;
  return t->heap;
}

inline object
allocate(Thread* t, unsigned size)
{
  if (UNLIKELY(size > Thread::HeapSize - t->heapIndex
               or t->vm->exclusive))
  {
    return collectAndAllocate(t, size);
  } else {
    object o = t->heap + t->heapIndex;
    t->heapIndex += size;
    return o;
  }
}

object
run(Thread* t)
{
  unsigned ip = 0;
  unsigned parameterCount = 0;

#define PUSH(x) t->stack[(t->sp)++] = x
#define POP(x) x = t->stack[--(t->sp)]

 loop:
  switch (codeBody(t->code)[ip++]) {
  case aaload: {
    object index; POP(index);
    object array; POP(array);

    if (array) {
      int32_t i = intValue(index);
      if (i >= 0 and i < objectArrayLength(array)) {
        PUSH(objectArrayBody(array)[i]);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    objectArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case aastore: {
    object value; POP(value);
    object index; POP(index);
    object array; POP(array);
    int32_t i = intValue(index);

    if (array) {
      if (i >= 0 and i < objectArrayLength(array)) {
        set(t, objectArrayBody(array)[i], value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
                                    objectArrayLength(array));
        t->exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case aconst_null: {
    PUSH(0);
  } goto loop;

  case aload:
  case iload:
  case lload: {
    PUSH(frameBody(t->frame)[codeBody(t->code)[ip++]]);
  } goto loop;

  case aload_0:
  case iload_0:
  case lload_0: {
    PUSH(frameBody(t->frame)[0]);
  } goto loop;

  case aload_1:
  case iload_1:
  case lload_1: {
    PUSH(frameBody(t->frame)[1]);
  } goto loop;

  case aload_2:
  case iload_2:
  case lload_2: {
    PUSH(frameBody(t->frame)[2]);
  } goto loop;

  case aload_3:
  case iload_3:
  case lload_3: {
    PUSH(frameBody(t->frame)[3]);
  } goto loop;

  case anewarray: {
    object count; POP(count);
    int32_t c = intValue(count);

    if (c >= 0) {
      uint8_t index1 = codeBody(t->code)[ip++];
      uint8_t index2 = codeBody(t->code)[ip++];
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t->code), index);
      if (t->exception) goto throw_;
      
      object array = makeObjectArray(t, class_, c);
      memset(objectArrayBody(array), 0, c * 4);
      
      PUSH(array);
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
      t->code = frameCode(t->frame);
      ip = frameIp(t->frame);
      goto loop;
    } else {
      object value; POP(value);
      t->code = 0;
      return value;
    }
  } goto loop;

  case arraylength: {
    object array; POP(array);
    if (array) {
      PUSH(makeInt(t, arrayLength(array)));
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } UNREACHABLE;

  case astore:
  case istore:
  case lstore: {
    object value; POP(value);
    set(t, frameBody(t->frame)[codeBody(t->code)[ip++]], value);
  } goto loop;

  case astore_0:
  case istore_0:
  case lstore_0: {
    object value; POP(value);
    set(t, frameBody(t->frame)[0], value);
  } goto loop;

  case astore_1:
  case istore_1:
  case lstore_1: {
    object value; POP(value);
    set(t, frameBody(t->frame)[1], value);
  } goto loop;

  case astore_2:
  case istore_2:
  case lstore_2: {
    object value; POP(value);
    set(t, frameBody(t->frame)[2], value);
  } goto loop;

  case astore_3:
  case istore_3:
  case lstore_3: {
    object value; POP(value);
    set(t, frameBody(t->frame)[3], value);
  } goto loop;

  case athrow: {
    POP(t->exception);
    if (t->exception == 0) {
      t->exception = makeNullPointerException(t, 0);      
    }
    goto throw_;
  } UNREACHABLE;

  case baload: {
    object index; POP(index);
    object array; POP(array);

    if (array) {
      int32_t i = intValue(index);
      if (i >= 0 and i < byteArrayLength(array)) {
        PUSH(makeByte(t, byteArrayBody(array)[i]));
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
    object value; POP(value);
    object index; POP(index);
    object array; POP(array);
    int32_t i = intValue(index);

    if (array) {
      if (i >= 0 and i < byteArrayLength(array)) {
        byteArrayBody(array)[i] = intValue(value);
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
    PUSH(makeInt(t, codeBody(t->code)[ip++]));
  } goto loop;

  case caload: {
    object index; POP(index);
    object array; POP(array);

    if (array) {
      int32_t i = intValue(index);
      if (i >= 0 and i < charArrayLength(array)) {
        PUSH(makeInt(t, charArrayBody(array)[i]));
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
    object value; POP(value);
    object index; POP(index);
    object array; POP(array);
    int32_t i = intValue(index);

    if (array) {
      if (i >= 0 and i < charArrayLength(array)) {
        charArrayBody(array)[i] = intValue(value);
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
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];

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
    PUSH(value);
  } goto loop;

  case dup_x1: {
    object first; POP(first);
    object second; POP(second);
    
    PUSH(first);
    PUSH(second);
    PUSH(first);
  } goto loop;

  case dup_x2: {
    object first; POP(first);
    object second; POP(second);
    object third; POP(third);
    
    PUSH(first);
    PUSH(third);
    PUSH(second);
    PUSH(first);
  } goto loop;

  case dup2: {
    object first = t->stack[t->sp - 1];
    if (isLongOrDouble(first)) {
      PUSH(first);
    } else {
      object second = t->stack[t->sp - 2];
      PUSH(second);
      PUSH(first);
    }
  } goto loop;

  case dup2_x1: {
    object first; POP(first);
    object second; POP(second);
    
    if (isLongOrDouble(first)) {
      PUSH(first);
      PUSH(second);
      PUSH(first);
    } else {
      object third; POP(third);
      PUSH(second);
      PUSH(first);
      PUSH(third);
      PUSH(second);
      PUSH(first);
    }
  } goto loop;

  case dup2_x2: {
    object first; POP(first);
    object second; POP(second);
    
    if (isLongOrDouble(first)) {
      if (isLongOrDouble(second)) {
        PUSH(first);
        PUSH(second);
        PUSH(first);
      } else {
        object third; POP(third);
        PUSH(first);
        PUSH(third);
        PUSH(second);
        PUSH(first);
      }
    } else {
      object third; POP(third);
      if (isLongOrDouble(third)) {
        PUSH(second);
        PUSH(first);
        PUSH(third);
        PUSH(second);
        PUSH(first);
      } else {
        object fourth; POP(fourth);
        PUSH(second);
        PUSH(first);
        PUSH(fourth);
        PUSH(third);
        PUSH(second);
        PUSH(first);
      }
    }
  } goto loop;

  case getfield: {
    object instance; POP(instance);
    if (instance) {
      uint8_t index1 = codeBody(t->code)[ip++];
      uint8_t index2 = codeBody(t->code)[ip++];
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t->code), index);
      if (t->exception) goto throw_;
      
      PUSH(getField(instance, field));
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case getstatic: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t->code), index);
    if (t->exception) goto throw_;

    if (not classInitialized(fieldClass(field))) {
      t->code = classInitializer(fieldClass(field));
      ip -= 3;
      parameterCount = 0;
      goto invoke;
    }
      
    PUSH(getStatic(field));
  } goto loop;

  case goto_: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    ip = (ip - 1) + ((offset1 << 8) | offset2);
  } goto loop;
    
  case goto_w: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];
    uint8_t offset3 = codeBody(t->code)[ip++];
    uint8_t offset4 = codeBody(t->code)[ip++];

    ip = (ip - 1)
      + ((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4);
  } goto loop;

  case i2b: {
    object v; POP(v);
    
    PUSH(makeInt(t, static_cast<int8_t>(intValue(v))));
  } goto loop;

  case i2c: {
    object v; POP(v);
    
    PUSH(makeInt(t, static_cast<uint16_t>(intValue(v))));
  } goto loop;

  case i2l: {
    object v; POP(v);
    
    PUSH(makeLong(t, intValue(v)));
  } goto loop;

  case i2s: {
    object v; POP(v);

    PUSH(makeInt(t, static_cast<int16_t>(intValue(v))));
  } goto loop;

  case iadd: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) + intValue(b)));
  } goto loop;

  case iaload: {
    object index; POP(index);
    object array; POP(array);

    if (array) {
      int32_t i = intValue(index);
      if (i >= 0 and i < intArrayLength(array)) {
        PUSH(makeInt(t, intArrayBody(array)[i]));
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
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) & intValue(b)));
  } goto loop;

  case iastore: {
    object value; POP(value);
    object index; POP(index);
    object array; POP(array);
    int32_t i = intValue(index);

    if (array) {
      if (i >= 0 and i < intArrayLength(array)) {
        intArrayBody(array)[i] = intValue(value);
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
    PUSH(makeInt(0));
  } goto loop;

  case iconst_1: {
    PUSH(makeInt(1));
  } goto loop;

  case iconst_2: {
    PUSH(makeInt(2));
  } goto loop;

  case iconst_3: {
    PUSH(makeInt(3));
  } goto loop;

  case iconst_4: {
    PUSH(makeInt(4));
  } goto loop;

  case iconst_5: {
    PUSH(makeInt(5));
  } goto loop;

  case idiv: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) / intValue(b)));
  } goto loop;

  case if_acmpeq: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (a == b) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_acmpne: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (a != b) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpeq: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (intValue(a) == intValue(b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpne: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (intValue(a) != intValue(b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpgt: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (intValue(a) > intValue(b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmpge: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (intValue(a) >= intValue(b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmplt: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (intValue(a) < intValue(b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case if_icmple: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object b; POP(b);
    object a; POP(a);
    
    if (intValue(a) < intValue(b)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifeq: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (intValue(v) == 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifne: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (intValue(v)) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifgt: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (intValue(v) > 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifge: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (intValue(v) >= 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case iflt: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (intValue(v) < 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifle: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (intValue(v) <= 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifnonnull: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (v) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case ifnull: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    object v; POP(v);
    
    if (v == 0) {
      ip = (ip - 1) + ((offset1 << 8) | offset2);
    }
  } goto loop;

  case iinc: {
    uint8_t index = codeBody(t->code)[ip++];
    int8_t c = codeBody(t->code)[ip++];
    
    int32_t v = intValue(frameBody(t->frame)[index]);
    frameBody(t->frame)[index] = makeInt(t, v + c);
  } goto loop;

  case imul: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) * intValue(b)));
  } goto loop;

  case ineg: {
    object v; POP(v);
    
    PUSH(makeInt(t, - intValue(v)));
  } goto loop;

  case instanceof: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];

    if (t->stack[t->sp - 1]) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t->code), index);
      if (t->exception) goto throw_;

      if (instanceOf(t, class_, t->stack[t->sp - 1])) {
        PUSH(makeInt(t, 1));
      } else {
        PUSH(makeInt(t, 0));
      }
    } else {
      PUSH(makeInt(t, 0));
    }
  } goto loop;

  case invokeinterface: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
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
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
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
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
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
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
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
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) | intValue(b)));
  } goto loop;

  case irem: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) % intValue(b)));
  } goto loop;

  case ishl: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) << intValue(b)));
  } goto loop;

  case ishr: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) >> intValue(b)));
  } goto loop;

  case isub: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) - intValue(b)));
  } goto loop;

  case iushr: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, static_cast<uint32_t>(intValue(a)) >> intValue(b)));
  } goto loop;

  case ixor: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, intValue(a) ^ intValue(b)));
  } goto loop;

  case jsr: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];

    PUSH(makeInt(ip));
    ip = (ip - 1) + ((offset1 << 8) | offset2);
  } goto loop;

  case jsr_w: {
    uint8_t offset1 = codeBody(t->code)[ip++];
    uint8_t offset2 = codeBody(t->code)[ip++];
    uint8_t offset3 = codeBody(t->code)[ip++];
    uint8_t offset4 = codeBody(t->code)[ip++];

    PUSH(makeInt(ip));
    ip = (ip - 1)
      + ((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4);
  } goto loop;

  case l2i: {
    object v; POP(v);
    
    PUSH(makeInt(t, static_cast<int32_t>(longValue(v))));
  } goto loop;

  case ladd: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) + longValue(b)));
  } goto loop;

  case laload: {
    object index; POP(index);
    object array; POP(array);

    if (array) {
      int32_t i = intValue(index);
      if (i >= 0 and i < longArrayLength(array)) {
        PUSH(makeLong(t, longArrayBody(array)[i]));
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
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) & longValue(b)));
  } goto loop;

  case lastore: {
    object value; POP(value);
    object index; POP(index);
    object array; POP(array);
    int32_t i = intValue(index);

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
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeInt(t, longValue(a) > longValue(b) ? 1
                 : longValue(a) == longValue(b) ? 0 : -1));
  } goto loop;

  case lconst_0: {
    PUSH(makeLong(0));
  } goto loop;

  case lconst_1: {
    PUSH(makeLong(1));
  } goto loop;

  case ldc: {
    PUSH(codePool(t->code)[codeBody(t->code)[ip++]]);
  } goto loop;

  case ldc_w:
  case ldc2_w: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];

    PUSH(codePool(t->code)[codeBody(t->code)[(offset1 << 8) | offset2]]);
  } goto loop;

  case ldiv: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) / longValue(b)));
  } goto loop;

  case lmul: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) * longValue(b)));
  } goto loop;

  case lneg: {
    object v; POP(v);
    
    PUSH(makeLong(t, - longValue(v)));
  } goto loop;

  case lor: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) | longValue(b)));
  } goto loop;

  case lrem: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) % longValue(b)));
  } goto loop;

  case lshl: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) << longValue(b)));
  } goto loop;

  case lshr: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) >> longValue(b)));
  } goto loop;

  case lsub: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) - longValue(b)));
  } goto loop;

  case lushr: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, static_cast<uint64_t>(longValue(a)) << longValue(b)));
  } goto loop;

  case lxor: {
    object b; POP(b);
    object a; POP(a);
    
    PUSH(makeLong(t, longValue(a) ^ longValue(b)));
  } goto loop;

  case new_: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
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
    memset(static_cast<object*>(instance) + sizeof(object),
           0,
           size - sizeof(object));
    
    PUSH(instance);
  } goto loop;

  case newarray: {
    object count; POP(count);
    int32_t c = intValue(count);

    if (c >= 0) {
      uint8_t type = codeBody(t->code)[ip++];

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

      default: UNREACHABLE;
      }
      
      memset(static_cast<object*>(instance) + (sizeof(object) * 2),
             0,
             c * factor);
      
      PUSH(array);
    } else {
      object message = makeString(t, "%d", c);
      t->exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case nop: goto loop;

  case pop: {
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
    object instance; POP(instance);
    if (instance) {
      uint8_t index1 = codeBody(t->code)[ip++];
      uint8_t index2 = codeBody(t->code)[ip++];
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t->code), index);
      if (t->exception) goto throw_;
      
      object value; POP(value);
      setField(t, instance, field, value);
    } else {
      t->exception = makeNullPointerException(t, 0);
      goto throw_;
    }
  } goto loop;

  case putstatic: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t->code), index);
    if (t->exception) goto throw_;

    if (not classInitialized(fieldClass(field))) {
      t->code = classInitializer(fieldClass(field));
      ip -= 3;
      parameterCount = 0;
      goto invoke;
    }
      
    object value; POP(value);
    setStatic(t, field, value);
  } goto loop;

  case ret: {
    ip = intValue(frameBody(t->frame)[codeBody(t->code)[ip++]]);
  } goto loop;

  case return_: {
    t->frame = frameNext(t->frame);
    if (t->frame) {
      t->code = frameCode(t->frame);
      ip = frameIp(t->frame);
      goto loop;
    } else {
      t->code = 0;
      return 0;
    }
  } goto loop;

  case saload: {
    object index; POP(index);
    object array; POP(array);

    if (array) {
      int32_t i = intValue(index);
      if (i >= 0 and i < shortArrayLength(array)) {
        PUSH(makeShort(t, shortArrayBody(array)[i]));
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
    object value; POP(value);
    object index; POP(index);
    object array; POP(array);
    int32_t i = intValue(index);

    if (array) {
      if (i >= 0 and i < shortArrayLength(array)) {
        shortArrayBody(array)[i] = intValue(value);
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
    uint8_t byte1 = codeBody(t->code)[ip++];
    uint8_t byte2 = codeBody(t->code)[ip++];

    PUSH(makeInt(t, (byte1 << 8) | byte2));
  } goto loop;

  case swap: {
    object tmp = t->stack[t->sp - 1];
    t->stack[t->sp - 1] = t->stack[t->sp - 2];
    t->stack[t->sp - 2] = tmp;
  } goto loop;

  case wide: goto wide;

    default: UNREACHABLE;
  }

 wide:
  switch (codeBody(t->code)[ip++]) {
  case aload:
  case iload:
  case lload: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];

    PUSH(frameBody(t->frame)[(index1 << 8) | index2]);
  } goto loop;

  case astore:
  case istore:
  case lstore: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];

    object value; POP(value);
    set(t, frameBody(t->frame)[(index1 << 8) | index2], value);
  } goto loop;

  case iinc: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];
    uint16_t index = (index1 << 8) | index2;

    uint8_t count1 = codeBody(t->code)[ip++];
    uint8_t count2 = codeBody(t->code)[ip++];
    uint16_t count = (count1 << 8) | count2;
    
    int32_t v = intValue(frameBody(t->frame)[index]);
    frameBody(t->frame)[index] = makeInt(t, v + count);
  } goto loop;

  case ret: {
    uint8_t index1 = codeBody(t->code)[ip++];
    uint8_t index2 = codeBody(t->code)[ip++];

    ip = intValue(frameBody(t->frame)[(index1 << 8) | index2]);
  } goto loop;

    default: UNREACHABLE;
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
    t->code = frameCode(t->frame);
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
          PUSH(t->exception);
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
  PUSH(t->exception);
  t->exception = 0;
  goto loop;
}

} // namespace
