Operand*
add(Compiler* c, Buffer* objectPool, object o)
{
  unsigned offset;
  Operand* result = c->poolAddress(0, &offset);
  objectPool->appendAddress(offset);
  objectPool->appendAddress(o);
  return result;
}

void
compileThrowNew(MyThread* t, Compiler* c, Buffer* objectPool,
                Machine::Type type)
{
  Operand* class_ = add(c, objectPool, arrayBody(t, t->m->types, type));
  c->indirectCallNoReturn(throwNew, 2, c->thread(), class_);
}

void
pushReturnValue(MyThread* t, Stack* s, unsigned code, Operand* result)
{
  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    s->pushInt(result);
    break;

  case ObjectField:
    s->pushObject(result);
    push(rax);
    stackMapper.pushedObject();
    break;

  case LongField:
  case DoubleField:
    s->pushLong(result);
    break;

  case VoidField:
    break;

  default:
    abort(t);
  }
}

void
compileDirectInvoke(MyThread* t, Compiler* c, Stack* s, object target)
{
  Operand* result = c->alignedCall(compiledCode(methodCompiled(t, target)));

  s->pop(methodParameterFootprint(t, target));

  pushReturnValue(t, s, methodReturnCode(t, target), result);
}

void
compile(MyThread* t, Compiler* c, Stack* initialStack, object method,
        uintptr_t* codeMask, Buffer* objectPool, unsigned ip)
{
  Stack stack(initialStack);
  Stack* s = &stack;

  object code = methodCode(t, method);
  PROTECT(t, code);
    
  while (ip < codeLength(t, code)) {
    if (getBit(codeMask, ip)) {
      // we've already visited this part of the code
      return;
    }

    markBit(mask, ip);

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
      Operand* next = c->label();
      Operand* outOfBounds = c->label();

      Operand* index = s->popInt();
      Operand* array = s->popObject();

      c->cmp(c->constant(0), index);
      c->jl(outOfBounds);

      c->cmp(c->offset(index, ArrayLength), index);
      c->jge(outOfBounds);

      c->add(c->constant(ArrayBody), array);

      switch (instruction) {
      case aaload:
      case faload:
      case iaload:
        c->shl(c->constant(log(BytesPerWord)), index);
        c->add(index, array);

        if (instruction == aaload) {
          s->pushObject(c->dereference(array));
        } else {
          s->pushInt(c->dereference4(array));
        }
        break;

      case baload:
        c->add(index, array);
        s->pushInt(c->dereference1(array));
        break;

      case caload:
        c->shl(c->constant(1), index);
        c->add(index, array);
        s->pushInt(c->dereference2z(array));
        break;

      case daload:
      case laload:
        c->shl(c->constant(3), index);
        c->add(index, array);
        s->pushLong(c->dereference8(array));
        break;

      case saload:
        c->shl(c->constant(1), index);
        c->add(index, array);
        s->pushInt(c->dereference2(array));
        break;
      }

      c->jmp(next);

      c->mark(outOfBounds);
      compileThrowNew(t, c, objectPool,
                      Machine::ArrayIndexOutOfBoundsExceptionType);

      c->mark(next);
    } break;

    case aastore:
    case bastore:
    case castore:
    case dastore:
    case fastore:
    case iastore:
    case lastore:
    case sastore: {
      Operand* next = c->label();
      Operand* outOfBounds = c->label();

      Operand* value;
      if (instruction == dastore or instruction == lastore) {
        value = s->popLong();
      } else if (instruction == aastore) {
        value = s->popObject();
      } else {
        value = s->popInt();
      }

      Operand* index = s->popInt();
      Operand* array = s->popObject();

      c->cmp(c->constant(0), index);
      c->jl(outOfBounds);

      c->cmp(c->offset(index, BytesPerWord), index);
      c->jge(outOfBounds);

      switch (instruction) {
      case aastore:
        c->shl(c->constant(log(BytesPerWord)), index);
        c->add(c->constant(ArrayBody), index);
          
        c->directCall(set, 4, c->thread(), array, index, value);
        break;

      case fastore:
      case iastore:
        c->shl(c->constant(log(BytesPerWord)), index);
        c->add(c->constant(ArrayBody), index);
        c->add(index, array);
        c->mov(value, c->dereference4(array));
        break;

      case bastore:
        c->add(c->constant(ArrayBody), index);
        c->add(index, array);
        c->mov(value, c->dereference1(array));
        break;

      case castore:
      case sastore:
        c->shl(c->constant(1), index);
        c->add(c->constant(ArrayBody), index);
        c->add(index, array);
        c->mov(value, c->dereference2(array));
        break;

      case dastore:
      case lastore:
        c->shl(c->constant(3), index);
        c->add(c->constant(ArrayBody), index);
        c->add(index, array);
        c->mov(value, c->dereference8(array));
        break;
      }

      jmp(next);

      c->mark(outOfBounds);
      compileThrowNew(t, c, objectPool,
                      Machine::ArrayIndexOutOfBoundsExceptionType);

      c->mark(next);
    } break;

    case aconst_null:
      s->pushObject(c->constant(0));
      break;

    case aload:
      s->loadObject(codeBody(t, code, ip++));
      break;

    case aload_0:
      s->loadObject(0);
      break;

    case aload_1:
      s->loadObject(1);
      break;

    case aload_2:
      s->loadObject(2);
      break;

    case aload_3:
      s->loadObject(3);
      break;

    case anewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* nonnegative = c->label();

      Operand* length = s->popInt();
      c->cmp(c->constant(0), length);
      jge(nonnegative);

      compileThrowNew(t, c, objectPool,
                      Machine::NegativeArraySizeExceptionType);

      c->mark(nonnegative);

      c->callIndirect(makeBlankObjectArray, 3,
                      c->thread(), add(c, objectPool, class_), length);

      s->pushObject(array);
    } break;

    case areturn:
      c->epilogue(s->popObject());
      return;

    case arraylength:
      s->pushInt(c->offset(s->popObject(), ArrayLength));
      break;

    case astore:
      s->storeObject(codeBody(t, code, ip++));
      break;

    case astore_0:
      s->storeObject(0);
      break;

    case astore_1:
      s->storeObject(1);
      break;

    case astore_2:
      s->storeObject(2);
      break;

    case astore_3:
      s->storeObject(3);
      break;

    case athrow:
      c->callIndirectNoReturn(throw_, 2, c->thread(), s->popObject());
      break;

    case bipush:
      s->pushInt(c->constant(static_cast<int8_t>(codeBody(t, code, ip++))));
      break;

    case checkcast: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* next = c->label();

      Operand* instance = s->topObject();
      Operand* tmp = c->temporary();

      c->mov(instance, tmp);

      c->cmp(c->constant(0), tmp);
      je(next);

      Operand* class_ = add(c, objectPool, class_);

      c->mov(c->dereference(tmp), tmp);
      c->and_(c->constant(PointerMask), tmp);

      cmp(class_, tmp);
      je(next);

      Operand* result = c->callDirect(isAssignableFrom, 2, class_, tmp);

      cmp(0, result);
      jne(next);
        
      compileThrowNew(t, c, objectPool, Machine::ClassCastExceptionType);

      c->mark(next);
    } break;

    case dadd: {
      Operand* a = s->popLong();
      Operand* b = s->popLong();
      s->pushLong(c->callDirect(addDouble, 2, a, b));
    } break;

    case dcmpg: {
      Operand* a = s->popLong();
      Operand* b = s->popLong();
      s->pushInt(c->callDirect(compareDoublesG, 2, a, b));
    } break;

    case dcmpl: {
      Operand* a = s->popLong();
      Operand* b = s->popLong();
      s->pushInt(c->callDirect(compareDoublesL, 2, a, b));
    } break;

    case dconst_0:
      s->pushLong(c->constant(doubleToBits(0.0)));
      break;
      
    case dconst_1:
      s->pushLong(c->constant(doubleToBits(1.0)));
      break;

    case ddiv: {
      Operand* a = s->popLong();
      Operand* b = s->popLong();
      s->pushLong(c->callDirect(divideDouble, 2, a, b));
    } break;

    case dmul: {
      Operand* a = s->popLong();
      Operand* b = s->popLong();
      s->pushLong(c->callDirect(multiplyDouble, 2, a, b));
    } break;

    case vm::drem: {
      Operand* a = s->popLong();
      Operand* b = s->popLong();
      s->pushLong(c->callDirect(moduloDouble, 2, a, b));
    } break;

    case dsub: {
      Operand* a = s->popLong();
      Operand* b = s->popLong();
      s->pushLong(c->callDirect(subtractDouble, 2, a, b));
    } break;

    case dup:
      s->dup();
      break;

    case dup_x1:
      s->dupX1();
      break;

    case dup_x2:
      s->dupX2();
      break;

    case dup2:
      s->dup2();
      break;

    case dup2_x1:
      s->dup2X1();
      break;

    case dup2_x2:
      s->dup2X2();
      break;

    case fadd: {
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      s->pushInt(c->callDirect(addFloat, 2, a, b));
    } break;

    case fcmpg: {
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      s->pushInt(c->callDirect(compareFloatsG, 2, a, b));
    } break;

    case fcmpl: {
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      s->pushInt(c->callDirect(compareFloatsL, 2, a, b));
    } break;

    case fconst_0:
      s->pushInt(c->constant(floatToBits(0.0)));
      break;
      
    case fconst_1:
      s->pushInt(c->constant(floatToBits(1.0)));
      break;
      
    case fconst_2:
      s->pushInt(c->constant(floatToBits(2.0)));
      break;

    case fdiv: {
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      s->pushInt(c->callDirect(divideFloat, 2, a, b));
    } break;

    case fmul: {
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      s->pushInt(c->callDirect(multiplyFloat, 2, a, b));
    } break;

    case vm::frem: {
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      s->pushInt(c->callDirect(moduloFloat, 2, a, b));
    } break;

    case fsub: {
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      s->pushInt(c->callDirect(subtractFloat, 2, a, b));
    } break;

    case getfield:
    case getstatic: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* table;

      if (instruction == getstatic) {
        initClass(t, fieldClass(t, field));
        if (UNLIKELY(t->exception)) return;

        table = add(c, objectPool, classStaticTable(t, fieldClass(t, field)));
      } else {
        table = s->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        s->pushInt(c->offset1(table, fieldOffset(t, field)));
        break;

      case CharField:
        s->pushInt(c->offset2z(table, fieldOffset(t, field)));
        break;

      case ShortField:
        s->pushInt(c->offset2(table, fieldOffset(t, field)));
        break;

      case FloatField:
      case IntField:
        s->pushInt(c->offset4(table, fieldOffset(t, field)));
        break;

      case DoubleField:
      case LongField:
        s->pushLong(c->offset8(table, fieldOffset(t, field)));
        break;

      case ObjectField:
        s->pushObject(c->offset(table, fieldOffset(t, field)));
        break;

      default:
        abort(t);
      }
    } break;

    case goto_: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->jmp(c->logicalIp(newIp));
      ip = newIp;
    } break;

    case goto_w: {
      int32_t newIp = (ip - 5) + codeReadInt32(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->jmp(c->logicalIp(newIp));
      ip = newIp;
    } break;

    case i2b: {
      Operand* top = s->topInt();
      c->mov(c->select1(top), top);
    } break;

    case i2c:
      Operand* top = s->topInt();
      c->mov(c->select2z(top), top);
      break;

    case i2s:
      Operand* top = s->topInt();
      c->mov(c->select2(top), top);
      break;

    case i2l:
      s->pushLong(s->popInt());
      break;
      
    case iadd: {
      Operand* a = s->popInt();
      c->add(a, s->topInt());
    } break;
      
    case iand: {
      Operand* a = s->popInt();
      c->and_(a, s->topInt());
    } break;

    case iconst_m1:
      s->pushInt(c->constant(-1));
      break;

    case iconst_0:
      s->pushInt(c->constant(0));
      break;

    case iconst_1:
      s->pushInt(c->constant(1));
      break;

    case iconst_2:
      s->pushInt(c->constant(2));
      break;

    case iconst_3:
      s->pushInt(c->constant(3));
      break;

    case iconst_4:
      s->pushInt(c->constant(4));
      break;

    case iconst_5:
      s->pushInt(c->constant(5));
      break;

    case idiv: {
      Operand* a = s->popInt();
      c->div(a, s->topInt());
    } break;

    case if_acmpeq:
    case if_acmpne: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Operand* a = s->popObject();
      Operand* b = s->popObject();
      c->cmp(a, b);

      Operand* target = c->logicalIp(newIp);
      if (instruction == if_acmpeq) {
        c->je(target);
      } else {
        c->jne(target);
      }
      
      Stack stack(s);
      compile(t, c, &stack, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return 0;      
    } break;

    case if_icmpeq:
    case if_icmpne:
    case if_icmpgt:
    case if_icmpge:
    case if_icmplt:
    case if_icmple: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Operand* a = s->popInt();
      Operand* b = s->popInt();
      c->cmp(a, b);

      Operand* target = c->logicalIp(newIp);
      switch (instruction) {
      case if_icmpeq:
        c->je(target);
        break;
      case if_icmpne:
        c->jne(target);
        break;
      case if_icmpgt:
        c->jg(target);
        break;
      case if_icmpge:
        c->jge(target);
        break;
      case if_icmplt:
        c->jl(target);
        break;
      case if_icmple:
        c->jle(target);
        break;
      }
      
      Stack stack(s);
      compile(t, c, &stack, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return 0;      
    } break;

    case ifeq:
    case ifne:
    case ifgt:
    case ifge:
    case iflt:
    case ifle: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->cmp(0, s->popInt());

      Operand* target = c->logicalIp(newIp);
      switch (instruction) {
      case ifeq:
        c->je(target);
        break;
      case ifne:
        c->jne(target);
        break;
      case ifgt:
        c->jg(target);
        break;
      case ifge:
        c->jge(target);
        break;
      case iflt:
        c->jl(target);
        break;
      case ifle:
        c->jle(target);
        break;
      }
      
      Stack stack(s);
      compile(t, c, &stack, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return 0;      
    } break;

    case ifnull:
    case ifnonnull: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->cmp(0, s->popObject());

      Operand* target = c->logicalIp(newIp);
      if (instruction == ifnull) {
        c->je(target);
      } else {
        c->jne(target);
      }
      
      Stack stack(s);
      compile(t, c, &stack, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return 0;      
    } break;

    case iload:
    case fload:
      s->loadInt(codeBody(t, code, ip++));
      break;

    case iload_0:
    case fload_0:
      s->loadInt(0);
      break;

    case iload_1:
    case fload_1:
      s->loadInt(1);
      break;

    case iload_2:
    case fload_2:
      s->loadInt(2);
      break;

    case iload_3:
    case fload_3:
      s->loadInt(3);
      break;

    case imul: {
      Operand* a = s->popInt();
      c->mul(a, s->topInt());
    } break;

    case instanceof: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* call = c->label();
      Operand* next = c->label();
      Operand* zero = c->label();

      Operand* instance = s->topObject();
      Operand* tmp = c->temporary();
      Operand* result = c->temporary();

      c->mov(instance, tmp);

      c->cmp(c->constant(0), tmp);
      je(zero);

      Operand* class_ = add(c, objectPool, class_);

      c->mov(c->dereference(tmp), tmp);
      c->and_(c->constant(PointerMask), tmp);

      cmp(class_, tmp);
      jne(call);

      c->mov(c->constant(1), result);
      jmp(next);

      c->mov(c->callDirect(isAssignableFrom, 2, class_, tmp), result);
      c->jmp(next);
        
      c->mark(zero);

      s->mov(c->constant(0), result);

      c->mark(next);
      s->pushInt(result);
    } break;

    case invokeinterface: {
      uint16_t index = codeReadInt16(t, code, ip);
      ip += 2;

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      unsigned parameterFootprint
        = methodParameterFootprint(t, target) * BytesPerWord;

      unsigned instance = parameterFootprint - BytesPerWord;

      Operand* found = c->callDirect(findInterfaceMethodFromInstance, 3,
                                     t->thread(),
                                     add(c, objectPool, target),
                                     c->offset(t->stack(), instance));

      c->mov(c->offset(found, MethodCompiled), found);

      Operand* result = c->call(c->offset(found, CompiledBody));

      s->pop(methodParameterFootprint(t, target));

      pushReturnValue(t, s, methodReturnCode(t, target), result);
    } break;

    case invokespecial: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      object class_ = methodClass(t, target);
      if (isSpecialMethod(t, target, class_)) {
        target = findMethod(t, target, classSuper(t, class_));
      }

      compileDirectInvoke(t, c, s, target);
    } break;

    case invokestatic: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, target);

      initClass(t, methodClass(t, target));
      if (UNLIKELY(t->exception)) return;

      compileDirectInvoke(t, c, s, target);
    } break;

    case invokevirtual: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      unsigned parameterFootprint
        = methodParameterFootprint(t, target) * BytesPerWord;

      unsigned instance = parameterFootprint - BytesPerWord;

      unsigned offset = ClassVtable + (methodOffset(t, target) * BytesPerWord);

      Operand* instance = c->offset(c->stack(), instance);
      Operand* class_ = c->temporary();
      
      c->mov(c->dereference(instance), class_);
      c->and_(static_cast<int32_t>(PointerMask), class_);

      Operand* result = c->call(c->offset(class_, offset));

      s->pop(methodParameterFootprint(t, target));

      pushReturnValue(t, s, methodReturnCode(t, target), result);
    } break;

    case ior: {
      Operand* a = s->popInt();
      c->or_(a, s->topInt());
    } break;

    case irem: {
      Operand* a = s->popInt();
      c->rem(a, s->topInt());
    } break;

    case ireturn:
    case freturn:
      c->epilogue(s->popInt());
      return;

    case ishl: {
      Operand* a = s->popInt();
      c->shl(a, s->topInt());
    } break;

    case ishr: {
      Operand* a = s->popInt();
      c->shr(a, s->topInt());
    } break;

    case istore:
    case fstore:
      s->storeInt(codeBody(t, code, ip++));
      break;

    case istore_0:
    case fstore_0:
      s->storeInt(0);
      break;

    case istore_1:
    case fstore_1:
      s->storeInt(1);
      break;

    case istore_2:
    case fstore_2:
      s->storeInt(2);
      break;

    case istore_3:
    case fstore_3:
      s->storeInt(3);
      break;

    case isub: {
      Operand* a = s->popInt();
      c->sub(a, s->topInt());
    } break;

    case iushr: {
      Operand* a = s->popInt();
      c->ushr(a, s->topInt());
    } break;

    case l2i:
      s->pushInt(s->popLong());
      break;

    case ladd: {
      Operand* a = s->popLong();
      c->sub(a, s->topLong());
    } break;

    case ldc:
    case ldc_w: {
      uint16_t index;

      if (instruction == ldc) {
        index = codeBody(t, code, ip++);
      } else {
        index = codeReadInt16(t, code, ip);
      }

      object pool = codePool(t, code);

      if (singletonIsObject(t, pool, index - 1)) {
        object v = singletonObject(t, pool, index - 1);
        if (objectClass(t, v)
            == arrayBody(t, t->m->types, Machine::ByteArrayType))
        {
          object class_ = resolveClassInPool(t, pool, index - 1); 
          if (UNLIKELY(exception)) return;

          s->pushObject(add(c, objectPool, class_));
        } else {
          s->pushObject(add(c, objectPool, v));
        }
      } else {
        s->pushInt(c->constant(singletonValue(t, pool, index - 1)));
      }
    } break;

    case ldc2_w: {
      uint16_t index = codeReadInt16(t, code, ip);

      object pool = codePool(t, code);

      uint64_t v;
      memcpy(&v, &singletonValue(t, pool, index - 1), 8);
      s->pushLong(c->constant(v));
    } goto loop;

    case lconst_0:
      s->pushLong(c->constant(0));
      break;

    case lconst_1:
      s->pushLong(c->constant(1));
      break;

    case lcmp: {
      Operand* next = c->label();;
      Operand* less = c->label();;
      Operand* greater = c->label();;

      Operand* a = s->popLong();
      Operand* b = s->popLong();
      Operand* result = c->temporary();
          
      c->cmp(a, b);
      jl(less);
      jg(greater);

      c->mov(c->constant(0), result);
      jmp(next);
          
      c->mark(less);
      c->mov(c->constant(-1), result);
      jmp(next);

      c->mark(greater);
      c->mov(c->constant(1), result);

      c->mark(next);
      s->pushInt(result);
    } break;

    case ldiv: {
      Operand* a = s->popLong();
      c->div(a, s->topLong());
    } break;

    case lload:
    case dload:
      s->loadLong(codeBody(t, code, ip++));
      break;

    case lload_0:
    case dload_0:
      s->loadLong(0);
      break;

    case lload_1:
    case dload_1:
      s->loadLong(1);
      break;

    case lload_2:
    case dload_2:
      s->loadLong(2);
      break;

    case lload_3:
    case dload_3:
      s->loadLong(3);
      break;

    case lmul: {
      Operand* a = s->popLong();
      c->mul(a, s->topLong());
    } break;

    case lneg:
      c->neg(s->topLong());
      break;

    case lrem: {
      Operand* a = s->popLong();
      c->rem(a, s->topLong());
    } break;

    case lreturn:
    case dreturn:
      c->epilogue(s->popLong());
      return;

    case lstore:
    case dstore:
      s->storeLong(codeBody(t, code, ip++));
      break;

    case lstore_0:
    case dstore_0:
      s->storeLong(0);
      break;

    case lstore_1:
    case dstore_1:
      s->storeLong(1);
      break;

    case lstore_2:
    case dstore_2:
      s->storeLong(2);
      break;

    case lstore_3:
    case dstore_3:
      s->storeLong(3);
      break;

    case lsub: {
      Operand* a = s->popLong();
      c->sub(a, s->topLong());
    } break;

    case new_: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, class_);
        
      initClass(t, class_);
      if (UNLIKELY(t->exception)) return;

      Operand* result;
      if (classVmFlags(t, class_) & WeakReferenceFlag) {
        result = c->callIndirect(makeNewWeakReference, 2,
                                 c->thread(),
                                 add(c, objectPool, class_));
      } else {
        result = c->callIndirect(makeNew, 2,
                                 c->thread(),
                                 add(c, objectPool, class_));
      }

      s->pushObject(result);
    } break;

    case newarray: {
      uint8_t type = codeBody(t, code, ip++);

      Operand* nonnegative = c->label();

      Operand* size = s->popInt();
      c->cmp(0, size);
      c->jge(nonnegative);

      compileThrowNew(t, c, objectPool,
                      Machine::NegativeArraySizeExceptionType);

      c->mark(nonnegative);

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

      s->pushObject
        (c->callIndirect(makeBlankArray, 2, c->constant(constructor), size));
    } break;

    case nop: break;

    case pop_:
      s->pop(1);
      break;

    case pop2:
      s->pop(2);
      break;

    case putfield:
    case putstatic: {
      uint16_t index = codeReadInt16(t, code, ip);
    
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) return;

      object staticTable;

      if (instruction == putstatic) {
        PROTECT(t, field);
        initClass(t, fieldClass(t, field));
        if (UNLIKELY(t->exception)) return;  

        staticTable = classStaticTable(t, fieldClass(t, field));      
      }

      Operand* value;
      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
      case CharField:
      case ShortField:
      case FloatField:
      case IntField: {
        Operand* value = s->popInt();
      } break;

      case DoubleField:
      case LongField: {
        Operand* value = s->popLong();
      } break;

      case ObjectField: {
        Operand* value = s->popLong();
      } break;

      default: abort(t);
      }

      Operand* table;

      if (instruction == putstatic) {
        table = add(c, objectPool, staticTable);
      } else {
        table = s->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        c->mov(value, c->offset1(table, fieldOffset(t, field)));
        break;

      case CharField:
      case ShortField:
        c->mov(value, c->offset2(table, fieldOffset(t, field)));
        break;
            
      case FloatField:
      case IntField:
        c->mov(value, c->offset4(table, fieldOffset(t, field)));
        break;

      case DoubleField:
      case LongField:
        c->mov(value, c->offset8(table, fieldOffset(t, field)));
        break;

      case ObjectField:
        c->directCall
          (set, 4, c->thread(), table, fieldOffset(t, field), value);
        break;

      default: abort(t);
      }
    } break;

    case return_:
      c->epilogue();
      return;

    case sipush:
      s->pushInt
        (c->constant(static_cast<int16_t>(codeReadInt16(t, code, ip))));
      break;
    }
  }
}

object
compile(MyThread* t, Compiler* compiler, object method)
{
  PROTECT(t, method);

  object code = methodCode(t, method);
  PROTECT(t, code);
  
  unsigned parameterFootprint
    = methodParameterFootprint(t, method) * BytesPerWord;
  
  unsigned localFootprint = codeMaxLocals(t, code) * BytesPerWord;

  compiler->prologue(parameterFootptrint, localFootprint);

  unsigned codeMaskSize
    = ceiling(codeLength(t, code), BytesPerWord)
    * BytesPerWord;

  uintptr_t* codeMask = static_cast<uintptr_t*>
    (t->m->system->allocate(codeMaskSize));

  RESOURCE(t, codeMask);

  memset(codeMask, 0, codeMaskSize);

  Buffer objectPool;

  class MyProtector: public Thread::Protector {
   public:
    MyProtector(MyThread* t, Buffer* pool): Protector(t), pool(pool) { }

    virtual void visit(Heap::Visitor* v) {
      for (unsigned i = 1; i < pool->length(); i += BytesPerWord * 2) {
        v->visit(reinterpret_cast<object*>(&(pool->getAddress(i))));
      }
    }

    Buffer* pool;
  } protector(t, objectPool);

  Stack stack(compiler);

  compile(t, c, &stack, method, codeMask, &objectPool, 0);
  if (UNLIKELY(t->exception)) return 0;

  object eht = codeExceptionHandlerTable(t, methodCode(t, method));
  if (eht) {
    PROTECT(t, eht);

    for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
      ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);

      assert(t, getBit(codeMask, exceptionHandlerStart(eh)));
        
      Stack stack2(&stack);
      stack2.pushObject();

      compile(t, c, &stack, method, codeMask, &objectPool,
              exceptionHandlerIp(eh));
    }
  }

  unsigned count = ceiling(compiler->size(), BytesPerWord);
  unsigned size = count + singletonMaskSize(count);
  object result = allocate(t, size * BytesPerWord, true, true);
  initSingleton(t, result, size, true); 
  singletonMask(t, o)[0] = 1;

  compiler->writeTo(&singletonValue(t, singleton, 0));

  for (unsigned i = 0; i < objectPool.length(); i += BytesPerWord * 2) {
    uintptr_t index = compiler->poolOffset() + objectPool.getAddress(i);
    object value = reinterpret_cast<object>(objectPool->getAddress(i));

    singletonMarkObject(t, result, index);
    set(t, singletonObject(t, result, index), value);
  }

  return result;
}
