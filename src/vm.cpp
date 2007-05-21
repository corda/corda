
namespace {

object
run(Thread* t)
{
  unsigned ip = 0;

#define PUSH(x) t->stack[(t->sp)++] = x
#define POP(x) x = t->stack[--(t->sp)]
#define NEXT ++ ip; goto loop

 loop:
  switch (codeBody(t->code)[ip]) {
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
        t->exception = makeAIOOBException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNPException(t, 0);
      goto throw_;
    }
  } NEXT;

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
        t->exception = makeAIOOBException(t, message);
        goto throw_;
      }
    } else {
      t->exception = makeNPException(t, 0);
      goto throw_;
    }
  } NEXT;

  case aconst_null: {
    PUSH(0);
  } NEXT;

  case aload: {
    PUSH(frameBody(t->frame)[codeBody(t->code)[++ip]]);
  } NEXT;

  case aload_0: {
    PUSH(frameBody(t->frame)[0]);
  } NEXT;

  case aload_1: {
    PUSH(frameBody(t->frame)[1]);
  } NEXT;

  case aload_2: {
    PUSH(frameBody(t->frame)[2]);
  } NEXT;

  case aload_3: {
    PUSH(frameBody(t->frame)[3]);
  } NEXT;

  case anewarray: {
    object count; POP(count);
    int32_t c = intValue(count);

    if (c >= 0) {
      uint8_t index1 = codeBody(t->code)[++ip];
      uint8_t index2 = codeBody(t->code)[++ip];
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolvePoolEntry(t, codePool(t->code), index);
      if (t->exception) goto throw_;
      
      object array = makeObjectArray(t, class_, c);
      memset(objectArrayBody(array), 0, c * 4);
      
      PUSH(array);
    } else {
      object message = makeString(t, "%d", c);
      t->exception = makeNASException(t, message);
      goto throw_;
    }
  } NEXT;

  case areturn: {
    object value; POP(value);
    if (t->sp) {
      POP(t->frame);
      t->code = frameCode(t->frame);
      ip = frameIp(t->frame);
      PUSH(value);
      goto loop;
    } else {
      return value;
    }
  } NEXT;

  case arraylength: {
    object array; POP(array);
    if (array) {
      PUSH(makeInt(t, arrayLength(array)));
    } else {
      t->exception = makeNPException(t, 0);
      goto throw_;
    }
  } UNREACHABLE;

  case astore: {
    object value; POP(value);
    set(t, frameBody(t->frame)[codeBody(t->code)[++ip]], value);
  } NEXT;

  case astore_0: {
    object value; POP(value);
    set(t, frameBody(t->frame)[0], value);
  } NEXT;

  case astore_1: {
    object value; POP(value);
    set(t, frameBody(t->frame)[1], value);
  } NEXT;

  case astore_2: {
    object value; POP(value);
    set(t, frameBody(t->frame)[2], value);
  } NEXT;

  case astore_3: {
    object value; POP(value);
    set(t, frameBody(t->frame)[3], value);
  } NEXT;

  case athrow: {
    POP(t->exception);
    goto throw_;
  } UNREACHABLE;
  }

 throw_:
  for (; t->sp >= 0; --(t->sp)) {
    if (typeOf(t->stack[t->sp]) == FrameType) {
      t->frame = t->stack[t->sp];
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
            ip = exceptionHandlerHandler(eh);
            PUSH(t->exception);
            t->exception = 0;
            goto loop;
          }
        }
      }
    }
  }

  t->code = defaultExceptionHandler(t);
  ip = 0;
  PUSH(t->exception);
  t->exception = 0;
  goto loop;
}

} // namespace
