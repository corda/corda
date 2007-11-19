Operand*
add(Compiler* c, Buffer* objectPool, object o)
{
  Operand* result = c->append(c->constant(0));
  objectPool->appendAddress(c->poolOffset(result));
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
pushReturnValue(MyThread* t, Frame* f, unsigned code, Operand* result)
{
  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    frame->pushInt(result);
    break;

  case ObjectField:
    frame->pushObject(result);
    push(rax);
    stackMapper.pushedObject();
    break;

  case LongField:
  case DoubleField:
    frame->pushLong(result);
    break;

  case VoidField:
    break;

  default:
    abort(t);
  }
}

void
compileDirectInvoke(MyThread* t, Compiler* c, Frame* frame, object target)
{
  Operand* result = c->alignedCall(compiledCode(methodCompiled(t, target)));

  frame->pop(methodParameterFootprint(t, target));

  pushReturnValue(t, frame, methodReturnCode(t, target), result);
}

void
compile(MyThread* t, Compiler* c, Frame* initialFrame, object method,
        uintptr_t* codeMask, Buffer* objectPool, unsigned ip)
{
  Frame myFrame(initialFrame);
  Frame* frame = &myFrame;

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

      Operand* index = frame->popInt();
      Operand* array = frame->popObject();

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
          frame->pushObject(c->dereference(array));
        } else {
          frame->pushInt(c->dereference4(array));
        }
        break;

      case baload:
        c->add(index, array);
        frame->pushInt(c->dereference1(array));
        break;

      case caload:
        c->shl(c->constant(1), index);
        c->add(index, array);
        frame->pushInt(c->dereference2z(array));
        break;

      case daload:
      case laload:
        c->shl(c->constant(3), index);
        c->add(index, array);
        frame->pushLong(c->dereference8(array));
        break;

      case saload:
        c->shl(c->constant(1), index);
        c->add(index, array);
        frame->pushInt(c->dereference2(array));
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
        value = frame->popLong();
      } else if (instruction == aastore) {
        value = frame->popObject();
      } else {
        value = frame->popInt();
      }

      Operand* index = frame->popInt();
      Operand* array = frame->popObject();

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
      frame->pushObject(c->constant(0));
      break;

    case aload:
      frame->loadObject(codeBody(t, code, ip++));
      break;

    case aload_0:
      frame->loadObject(0);
      break;

    case aload_1:
      frame->loadObject(1);
      break;

    case aload_2:
      frame->loadObject(2);
      break;

    case aload_3:
      frame->loadObject(3);
      break;

    case anewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* nonnegative = c->label();

      Operand* length = frame->popInt();
      c->cmp(c->constant(0), length);
      jge(nonnegative);

      compileThrowNew(t, c, objectPool,
                      Machine::NegativeArraySizeExceptionType);

      c->mark(nonnegative);

      c->indirectCall(makeBlankObjectArray, 3,
                      c->thread(), add(c, objectPool, class_), length);

      frame->pushObject(array);
    } break;

    case areturn:
      c->epilogue(frame->popObject());
      return;

    case arraylength:
      frame->pushInt(c->offset(frame->popObject(), ArrayLength));
      break;

    case astore:
      frame->storeObject(codeBody(t, code, ip++));
      break;

    case astore_0:
      frame->storeObject(0);
      break;

    case astore_1:
      frame->storeObject(1);
      break;

    case astore_2:
      frame->storeObject(2);
      break;

    case astore_3:
      frame->storeObject(3);
      break;

    case athrow:
      c->indirectCallNoReturn(throw_, 2, c->thread(), frame->popObject());
      break;

    case bipush:
      frame->pushInt
        (c->constant(static_cast<int8_t>(codeBody(t, code, ip++))));
      break;

    case checkcast: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* next = c->label();

      Operand* instance = frame->topObject();
      Operand* tmp = c->temporary();

      c->mov(instance, tmp);

      c->cmp(c->constant(0), tmp);
      je(next);

      Operand* class_ = add(c, objectPool, class_);

      c->mov(c->dereference(tmp), tmp);
      c->and_(c->constant(PointerMask), tmp);

      cmp(class_, tmp);
      je(next);

      Operand* result = c->directCall(isAssignableFrom, 2, class_, tmp);

      cmp(0, result);
      jne(next);
        
      compileThrowNew(t, c, objectPool, Machine::ClassCastExceptionType);

      c->mark(next);
    } break;

    case d2f: {
      Operand* a = frame->popLong();
      frame->pushInt(c->directCall(doubleToFloat, 1, a));
    } break;

    case d2i: {
      Operand* a = frame->popLong();
      frame->pushInt(c->directCall(doubleToInt, 1, a));
    } break;

    case d2i: {
      Operand* a = frame->popLong();
      frame->pushLong(c->directCall(doubleToLong, 1, a));
    } break;

    case dadd: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong(c->directCall(addDouble, 2, a, b));
    } break;

    case dcmpg: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushInt(c->directCall(compareDoublesG, 2, a, b));
    } break;

    case dcmpl: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushInt(c->directCall(compareDoublesL, 2, a, b));
    } break;

    case dconst_0:
      frame->pushLong(c->constant(doubleToBits(0.0)));
      break;
      
    case dconst_1:
      frame->pushLong(c->constant(doubleToBits(1.0)));
      break;

    case ddiv: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong(c->directCall(divideDouble, 2, a, b));
    } break;

    case dmul: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong(c->directCall(multiplyDouble, 2, a, b));
    } break;

    case dneg: {
      Operand* a = frame->popLong();
      frame->pushLong(c->directCall(negateDouble, 1, a));
    } break;

    case vm::drem: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong(c->directCall(moduloDouble, 2, a, b));
    } break;

    case dsub: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong(c->directCall(subtractDouble, 2, a, b));
    } break;

    case dup:
      frame->dup();
      break;

    case dup_x1:
      frame->dupX1();
      break;

    case dup_x2:
      frame->dupX2();
      break;

    case dup2:
      frame->dup2();
      break;

    case dup2_x1:
      frame->dup2X1();
      break;

    case dup2_x2:
      frame->dup2X2();
      break;

    case f2d: {
      Operand* a = frame->popInt();
      frame->pushLong(c->directCall(floatToDouble, 1, a));
    } break;

    case f2i: {
      Operand* a = frame->popInt();
      frame->pushInt(c->directCall(floatToInt, 1, a));
    } break;

    case f2l: {
      Operand* a = frame->popInt();
      frame->pushLong(c->directCall(floatToLong, 1, a));
    } break;

    case fadd: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt(c->directCall(addFloat, 2, a, b));
    } break;

    case fcmpg: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt(c->directCall(compareFloatsG, 2, a, b));
    } break;

    case fcmpl: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt(c->directCall(compareFloatsL, 2, a, b));
    } break;

    case fconst_0:
      frame->pushInt(c->constant(floatToBits(0.0)));
      break;
      
    case fconst_1:
      frame->pushInt(c->constant(floatToBits(1.0)));
      break;
      
    case fconst_2:
      frame->pushInt(c->constant(floatToBits(2.0)));
      break;

    case fdiv: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt(c->directCall(divideFloat, 2, a, b));
    } break;

    case fmul: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt(c->directCall(multiplyFloat, 2, a, b));
    } break;

    case fneg: {
      Operand* a = frame->popLong();
      frame->pushLong(c->directCall(negateFloat, 1, a));
    } break;

    case vm::frem: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt(c->directCall(moduloFloat, 2, a, b));
    } break;

    case fsub: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt(c->directCall(subtractFloat, 2, a, b));
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
        table = frame->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        frame->pushInt(c->offset1(table, fieldOffset(t, field)));
        break;

      case CharField:
        frame->pushInt(c->offset2z(table, fieldOffset(t, field)));
        break;

      case ShortField:
        frame->pushInt(c->offset2(table, fieldOffset(t, field)));
        break;

      case FloatField:
      case IntField:
        frame->pushInt(c->offset4(table, fieldOffset(t, field)));
        break;

      case DoubleField:
      case LongField:
        frame->pushLong(c->offset8(table, fieldOffset(t, field)));
        break;

      case ObjectField:
        frame->pushObject(c->offset(table, fieldOffset(t, field)));
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
      Operand* top = frame->topInt();
      c->mov(c->select1(top), top);
    } break;

    case i2c: {
      Operand* top = frame->topInt();
      c->mov(c->select2z(top), top);
    } break;

    case i2d: {
      Operand* a = frame->popInt();
      frame->pushLong(c->directCall(intToDouble, 1, a));
    } break;

    case i2f: {
      Operand* a = frame->popInt();
      frame->pushInt(c->directCall(intToFloat, 1, a));
    } break;

    case i2l:
      frame->pushLong(frame->popInt());
      break;

    case i2s: {
      Operand* top = frame->topInt();
      c->mov(c->select2(top), top);
    } break;
      
    case iadd: {
      Operand* a = frame->popInt();
      c->add(a, frame->topInt());
    } break;
      
    case iand: {
      Operand* a = frame->popInt();
      c->and_(a, frame->topInt());
    } break;

    case iconst_m1:
      frame->pushInt(c->constant(-1));
      break;

    case iconst_0:
      frame->pushInt(c->constant(0));
      break;

    case iconst_1:
      frame->pushInt(c->constant(1));
      break;

    case iconst_2:
      frame->pushInt(c->constant(2));
      break;

    case iconst_3:
      frame->pushInt(c->constant(3));
      break;

    case iconst_4:
      frame->pushInt(c->constant(4));
      break;

    case iconst_5:
      frame->pushInt(c->constant(5));
      break;

    case idiv: {
      Operand* a = frame->popInt();
      c->div(a, frame->topInt());
    } break;

    case if_acmpeq:
    case if_acmpne: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Operand* a = frame->popObject();
      Operand* b = frame->popObject();
      c->cmp(a, b);

      Operand* target = c->logicalIp(newIp);
      if (instruction == if_acmpeq) {
        c->je(target);
      } else {
        c->jne(target);
      }
      
      compile(t, c, frame, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case if_icmpeq:
    case if_icmpne:
    case if_icmpgt:
    case if_icmpge:
    case if_icmplt:
    case if_icmple: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
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
      
      compile(t, c, frame, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case ifeq:
    case ifne:
    case ifgt:
    case ifge:
    case iflt:
    case ifle: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->cmp(0, frame->popInt());

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
      
      compile(t, c, frame, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case ifnull:
    case ifnonnull: {
      int32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->cmp(0, frame->popObject());

      Operand* target = c->logicalIp(newIp);
      if (instruction == ifnull) {
        c->je(target);
      } else {
        c->jne(target);
      }
      
      compile(t, c, frame, method, codeMask, objectPool, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case iinc: {
      uint8_t index = codeBody(t, code, ip++);
      int8_t c = codeBody(t, code, ip++);

      c->add(c->constant(c), frame->topInt());
    } break;

    case iload:
    case fload:
      frame->loadInt(codeBody(t, code, ip++));
      break;

    case iload_0:
    case fload_0:
      frame->loadInt(0);
      break;

    case iload_1:
    case fload_1:
      frame->loadInt(1);
      break;

    case iload_2:
    case fload_2:
      frame->loadInt(2);
      break;

    case iload_3:
    case fload_3:
      frame->loadInt(3);
      break;

    case imul: {
      Operand* a = frame->popInt();
      c->mul(a, frame->topInt());
    } break;

    case instanceof: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* call = c->label();
      Operand* next = c->label();
      Operand* zero = c->label();

      Operand* instance = frame->topObject();
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

      c->mov(c->directCall(isAssignableFrom, 2, class_, tmp), result);
      c->jmp(next);
        
      c->mark(zero);

      frame->mov(c->constant(0), result);

      c->mark(next);
      frame->pushInt(result);
    } break;

    case invokeinterface: {
      uint16_t index = codeReadInt16(t, code, ip);
      ip += 2;

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      unsigned parameterFootprint
        = methodParameterFootprint(t, target) * BytesPerWord;

      unsigned instance = parameterFootprint - BytesPerWord;

      Operand* found = c->directCall(findInterfaceMethodFromInstance, 3,
                                     t->thread(),
                                     add(c, objectPool, target),
                                     c->offset(t->stack(), instance));

      c->mov(c->offset(found, MethodCompiled), found);

      Operand* result = c->call(c->offset(found, CompiledBody));

      frame->pop(methodParameterFootprint(t, target));

      pushReturnValue(t, frame, methodReturnCode(t, target), result);
    } break;

    case invokespecial: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      object class_ = methodClass(t, target);
      if (isSpecialMethod(t, target, class_)) {
        target = findMethod(t, target, classSuper(t, class_));
      }

      compileDirectInvoke(t, c, frame, target);
    } break;

    case invokestatic: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, target);

      initClass(t, methodClass(t, target));
      if (UNLIKELY(t->exception)) return;

      compileDirectInvoke(t, c, frame, target);
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

      frame->pop(methodParameterFootprint(t, target));

      pushReturnValue(t, frame, methodReturnCode(t, target), result);
    } break;

    case ior: {
      Operand* a = frame->popInt();
      c->or_(a, frame->topInt());
    } break;

    case irem: {
      Operand* a = frame->popInt();
      c->rem(a, frame->topInt());
    } break;

    case ireturn:
    case freturn:
      c->epilogue(frame->popInt());
      return;

    case ishl: {
      Operand* a = frame->popInt();
      c->shl(a, frame->topInt());
    } break;

    case ishr: {
      Operand* a = frame->popInt();
      c->shr(a, frame->topInt());
    } break;

    case istore:
    case fstore:
      frame->storeInt(codeBody(t, code, ip++));
      break;

    case istore_0:
    case fstore_0:
      frame->storeInt(0);
      break;

    case istore_1:
    case fstore_1:
      frame->storeInt(1);
      break;

    case istore_2:
    case fstore_2:
      frame->storeInt(2);
      break;

    case istore_3:
    case fstore_3:
      frame->storeInt(3);
      break;

    case isub: {
      Operand* a = frame->popInt();
      c->sub(a, frame->topInt());
    } break;

    case iushr: {
      Operand* a = frame->popInt();
      c->ushr(a, frame->topInt());
    } break;

    case ixor: {
      Operand* a = frame->popInt();
      c->xor_(a, frame->topInt());
    } break;

    case jsr:
    case jsr_w:
    case ret:
      // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4381996
      abort(t);

    case l2i:
      frame->pushInt(frame->popLong());
      break;

    case ladd: {
      Operand* a = frame->popLong();
      c->sub(a, frame->topLong());
    } break;

    case lcmp: {
      Operand* next = c->label();
      Operand* less = c->label();
      Operand* greater = c->label();

      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
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
      frame->pushInt(result);
    } break;

    case lconst_0:
      frame->pushLong(c->constant(0));
      break;

    case lconst_1:
      frame->pushLong(c->constant(1));
      break;

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

          frame->pushObject(add(c, objectPool, class_));
        } else {
          frame->pushObject(add(c, objectPool, v));
        }
      } else {
        frame->pushInt(c->constant(singletonValue(t, pool, index - 1)));
      }
    } break;

    case ldc2_w: {
      uint16_t index = codeReadInt16(t, code, ip);

      object pool = codePool(t, code);

      uint64_t v;
      memcpy(&v, &singletonValue(t, pool, index - 1), 8);
      frame->pushLong(c->constant(v));
    } goto loop;

    case ldiv: {
      Operand* a = frame->popLong();
      c->div(a, frame->topLong());
    } break;

    case lload:
    case dload:
      frame->loadLong(codeBody(t, code, ip++));
      break;

    case lload_0:
    case dload_0:
      frame->loadLong(0);
      break;

    case lload_1:
    case dload_1:
      frame->loadLong(1);
      break;

    case lload_2:
    case dload_2:
      frame->loadLong(2);
      break;

    case lload_3:
    case dload_3:
      frame->loadLong(3);
      break;

    case lmul: {
      Operand* a = frame->popLong();
      c->mul(a, frame->topLong());
    } break;

    case lneg:
      c->neg(frame->topLong());
      break;

    case lookupswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3; // pad to four byte boundary

      Operand* key = frame->popInt();
    
      int32_t defaultIp = base + codeReadInt32(t, code, ip);
      assert(t, defaultIp < codeLength(t, code));

      compile(t, c, frame, method, codeMask, objectPool, defaultIp);
      if (UNLIKELY(t->exception)) return;

      Operand* default_ = c->append(c->logicalIp(defaultIp));

      int32_t pairCount = codeReadInt32(t, code, ip);

      Operand* start;
      for (int32_t i = 0; i < pairCount; ++i) {
        unsigned index = ip + (i * 8);
        int32_t key = codeReadInt32(t, code, index);
        int32_t newIp = base + codeReadInt32(t, code, index);
        assert(t, newIp < codeLength(t, code));

        compile(t, c, frame, method, codeMask, objectPool, newIp);
        if (UNLIKELY(t->exception)) return;

        Operand* result = c->append(c->constant(key));
        c->append(c->logicalIp(newIp));

        if (i == 0) {
          start = result;
        }
      }

      c->jmp(c->directCall
             (lookUpAddress, 4,
              key, start, c->constant(pairCount), default_));
    } return;

    case lor: {
      Operand* a = frame->popLong();
      c->or_(a, frame->topLong());
    } break;

    case lrem: {
      Operand* a = frame->popLong();
      c->rem(a, frame->topLong());
    } break;

    case lreturn:
    case dreturn:
      c->epilogue(frame->popLong());
      return;

    case lshl: {
      Operand* a = frame->popLong();
      c->shl(a, frame->topLong());
    } break;

    case lshr: {
      Operand* a = frame->popLong();
      c->shr(a, frame->topLong());
    } break;

    case lstore:
    case dstore:
      frame->storeLong(codeBody(t, code, ip++));
      break;

    case lstore_0:
    case dstore_0:
      frame->storeLong(0);
      break;

    case lstore_1:
    case dstore_1:
      frame->storeLong(1);
      break;

    case lstore_2:
    case dstore_2:
      frame->storeLong(2);
      break;

    case lstore_3:
    case dstore_3:
      frame->storeLong(3);
      break;

    case lsub: {
      Operand* a = frame->popLong();
      c->sub(a, frame->topLong());
    } break;

    case lushr: {
      Operand* a = frame->popLong();
      c->ushr(a, frame->topLong());
    } break;

    case lxor: {
      Operand* a = frame->popLong();
      c->xor_(a, frame->topLong());
    } break;

    case monitorenter: {
      c->indirectCall(acquireMonitorForObject, 2,
                      c->thread(), frame->popObject());
    } break;

    case monitorexit: {
      c->indirectCall(releaseMonitorForObject, 2,
                      c->thread(), frame->popObject());
    } break;

    case multianewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      uint8_t dimensions = codeBody(t, code, ip++);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) return;
      PROTECT(t, class_);

      Operand* result = c->indirectCall
        (makeMultidimensionalArray, 3,
         c->thread(),
         c->offset(c->stack(), dimensions - 1),
         c->constant(dimensions));

      frame->pop(dimensions);
      frame->pushObject(result);
    } goto loop;

    case new_: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, class_);
        
      initClass(t, class_);
      if (UNLIKELY(t->exception)) return;

      Operand* result;
      if (classVmFlags(t, class_) & WeakReferenceFlag) {
        result = c->indirectCall(makeNewWeakReference, 2,
                                 c->thread(),
                                 add(c, objectPool, class_));
      } else {
        result = c->indirectCall(makeNew, 2,
                                 c->thread(),
                                 add(c, objectPool, class_));
      }

      frame->pushObject(result);
    } break;

    case newarray: {
      uint8_t type = codeBody(t, code, ip++);

      Operand* nonnegative = c->label();

      Operand* size = frame->popInt();
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

      frame->pushObject
        (c->indirectCall(makeBlankArray, 2, c->constant(constructor), size));
    } break;

    case nop: break;

    case pop_:
      frame->pop(1);
      break;

    case pop2:
      frame->pop(2);
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
        Operand* value = frame->popInt();
      } break;

      case DoubleField:
      case LongField: {
        Operand* value = frame->popLong();
      } break;

      case ObjectField: {
        Operand* value = frame->popLong();
      } break;

      default: abort(t);
      }

      Operand* table;

      if (instruction == putstatic) {
        table = add(c, objectPool, staticTable);
      } else {
        table = frame->popObject();
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
      frame->pushInt
        (c->constant(static_cast<int16_t>(codeReadInt16(t, code, ip))));
      break;

    case swap:
      frame->swap();
      break;

    case tableswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3; // pad to four byte boundary

      Operand* key = frame->popInt();

      int32_t defaultIp = base + codeReadInt32(t, code, ip);
      assert(t, defaultIp < codeLength(t, code));

      compile(t, c, frame, method, codeMask, objectPool, defaultIp);
      if (UNLIKELY(t->exception)) return;
      
      Operand* default_ = c->append(c->logicalIp(defaultIp));

      int32_t bottom = codeReadInt32(t, code, ip);
      int32_t top = codeReadInt32(t, code, ip);
        
      Operand* start;
      for (int32_t i = 0; i < bottom - top + 1; ++i) {
        unsigned index = ip + (i * 4);
        int32_t newIp = base + codeReadInt32(t, code, index);
        assert(t, newIp < codeLength(t, code));
        
        compile(t, c, frame, method, codeMask, objectPool, newIp);
        if (UNLIKELY(t->exception)) return;

        Operand* result = c->append(c->logicalIp(newIp));
        if (i == 0) {
          start = result;
        }
      }

      Operand* defaultCase = c->label();
      
      c->cmp(c->constant(bottom), key);
      c->jl(defaultCase);

      c->cmp(c->constant(top), key);
      c->jg(defaultCase);

      c->shl(c->constant(2), key);
      c->jmp(c->offset(start, key));

      c->mark(defaultCase);
      c->jmp(default_);
    } return;

    case wide: {
      switch (codeBody(t, code, ip++)) {
      case aload: {
        frame->loadObject(codeReadInt16(t, code, ip));
      } goto loop;

      case astore: {
        frame->storeObject(codeReadInt16(t, code, ip));
      } goto loop;

      case iinc: {
        uint16_t index = codeReadInt16(t, code, ip);
        uint16_t c = codeReadInt16(t, code, ip);

        c->add(c->constant(c), frame->topInt());
      } goto loop;

      case iload: {
        frame->loadInt(codeReadInt16(t, code, ip));
      } goto loop;

      case istore: {
        frame->storeInt(codeReadInt16(t, code, ip));
      } goto loop;

      case lload: {
        frame->loadLong(codeReadInt16(t, code, ip));
      } goto loop;

      case lstore: {
        frame->storeLoad(codeReadInt16(t, code, ip));
      } goto loop;

      case ret:
        // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4381996
        abort(t);

      default: abort(t);
      }
    } break;
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

  Frame frame(compiler);

  compile(t, c, &stack, method, codeMask, &objectPool, 0);
  if (UNLIKELY(t->exception)) return 0;

  object eht = codeExceptionHandlerTable(t, methodCode(t, method));
  if (eht) {
    PROTECT(t, eht);

    for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
      ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);

      assert(t, getBit(codeMask, exceptionHandlerStart(eh)));
        
      Frame frame2(&frame);
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
