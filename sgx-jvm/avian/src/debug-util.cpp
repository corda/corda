/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/machine.h>

#include "debug-util.h"

namespace avian {
namespace jvm {
namespace debug {

uint16_t read16(uint8_t* code, unsigned& ip)
{
  uint16_t a = code[ip++];
  uint16_t b = code[ip++];
  return (a << 8) | b;
}

uint32_t read32(uint8_t* code, unsigned& ip)
{
  uint32_t b = code[ip++];
  uint32_t a = code[ip++];
  uint32_t c = code[ip++];
  uint32_t d = code[ip++];
  return (a << 24) | (b << 16) | (c << 8) | d;
}

using namespace vm;

int printInstruction(uint8_t* code, unsigned& ip, const char* prefix)
{
  unsigned startIp = ip;

  uint8_t instr = code[ip++];
  switch (instr) {
  case aaload:
    return fprintf(stderr, "aaload");
  case aastore:
    return fprintf(stderr, "aastore");

  case aconst_null:
    return fprintf(stderr, "aconst_null");

  case aload:
    return fprintf(stderr, "aload %2d", code[ip++]);
  case aload_0:
    return fprintf(stderr, "aload_0");
  case aload_1:
    return fprintf(stderr, "aload_1");
  case aload_2:
    return fprintf(stderr, "aload_2");
  case aload_3:
    return fprintf(stderr, "aload_3");

  case anewarray:
    return fprintf(stderr, "anewarray %4d", read16(code, ip));
  case areturn:
    return fprintf(stderr, "areturn");
  case arraylength:
    return fprintf(stderr, "arraylength");

  case astore:
    return fprintf(stderr, "astore %2d", code[ip++]);
  case astore_0:
    return fprintf(stderr, "astore_0");
  case astore_1:
    return fprintf(stderr, "astore_1");
  case astore_2:
    return fprintf(stderr, "astore_2");
  case astore_3:
    return fprintf(stderr, "astore_3");

  case athrow:
    return fprintf(stderr, "athrow");
  case baload:
    return fprintf(stderr, "baload");
  case bastore:
    return fprintf(stderr, "bastore");

  case bipush:
    return fprintf(stderr, "bipush %2d", code[ip++]);
  case caload:
    return fprintf(stderr, "caload");
  case castore:
    return fprintf(stderr, "castore");
  case checkcast:
    return fprintf(stderr, "checkcast %4d", read16(code, ip));
  case d2f:
    return fprintf(stderr, "d2f");
  case d2i:
    return fprintf(stderr, "d2i");
  case d2l:
    return fprintf(stderr, "d2l");
  case dadd:
    return fprintf(stderr, "dadd");
  case daload:
    return fprintf(stderr, "daload");
  case dastore:
    return fprintf(stderr, "dastore");
  case dcmpg:
    return fprintf(stderr, "dcmpg");
  case dcmpl:
    return fprintf(stderr, "dcmpl");
  case dconst_0:
    return fprintf(stderr, "dconst_0");
  case dconst_1:
    return fprintf(stderr, "dconst_1");
  case ddiv:
    return fprintf(stderr, "ddiv");
  case dmul:
    return fprintf(stderr, "dmul");
  case dneg:
    return fprintf(stderr, "dneg");
  case vm::drem:
    return fprintf(stderr, "drem");
  case dsub:
    return fprintf(stderr, "dsub");
  case vm::dup:
    return fprintf(stderr, "dup");
  case dup_x1:
    return fprintf(stderr, "dup_x1");
  case dup_x2:
    return fprintf(stderr, "dup_x2");
  case vm::dup2:
    return fprintf(stderr, "dup2");
  case dup2_x1:
    return fprintf(stderr, "dup2_x1");
  case dup2_x2:
    return fprintf(stderr, "dup2_x2");
  case f2d:
    return fprintf(stderr, "f2d");
  case f2i:
    return fprintf(stderr, "f2i");
  case f2l:
    return fprintf(stderr, "f2l");
  case fadd:
    return fprintf(stderr, "fadd");
  case faload:
    return fprintf(stderr, "faload");
  case fastore:
    return fprintf(stderr, "fastore");
  case fcmpg:
    return fprintf(stderr, "fcmpg");
  case fcmpl:
    return fprintf(stderr, "fcmpl");
  case fconst_0:
    return fprintf(stderr, "fconst_0");
  case fconst_1:
    return fprintf(stderr, "fconst_1");
  case fconst_2:
    return fprintf(stderr, "fconst_2");
  case fdiv:
    return fprintf(stderr, "fdiv");
  case fmul:
    return fprintf(stderr, "fmul");
  case fneg:
    return fprintf(stderr, "fneg");
  case frem:
    return fprintf(stderr, "frem");
  case fsub:
    return fprintf(stderr, "fsub");

  case getfield:
    return fprintf(stderr, "getfield %4d", read16(code, ip));
  case getstatic:
    return fprintf(stderr, "getstatic %4d", read16(code, ip));
  case goto_: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "goto %4d", offset + ip - 3);
  }
  case goto_w: {
    int32_t offset = read32(code, ip);
    return fprintf(stderr, "goto_w %08x", offset + ip - 5);
  }

  case i2b:
    return fprintf(stderr, "i2b");
  case i2c:
    return fprintf(stderr, "i2c");
  case i2d:
    return fprintf(stderr, "i2d");
  case i2f:
    return fprintf(stderr, "i2f");
  case i2l:
    return fprintf(stderr, "i2l");
  case i2s:
    return fprintf(stderr, "i2s");
  case iadd:
    return fprintf(stderr, "iadd");
  case iaload:
    return fprintf(stderr, "iaload");
  case iand:
    return fprintf(stderr, "iand");
  case iastore:
    return fprintf(stderr, "iastore");
  case iconst_m1:
    return fprintf(stderr, "iconst_m1");
  case iconst_0:
    return fprintf(stderr, "iconst_0");
  case iconst_1:
    return fprintf(stderr, "iconst_1");
  case iconst_2:
    return fprintf(stderr, "iconst_2");
  case iconst_3:
    return fprintf(stderr, "iconst_3");
  case iconst_4:
    return fprintf(stderr, "iconst_4");
  case iconst_5:
    return fprintf(stderr, "iconst_5");
  case idiv:
    return fprintf(stderr, "idiv");

  case if_acmpeq: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_acmpeq %4d", offset + ip - 3);
  }
  case if_acmpne: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_acmpne %4d", offset + ip - 3);
  }
  case if_icmpeq: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_icmpeq %4d", offset + ip - 3);
  }
  case if_icmpne: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_icmpne %4d", offset + ip - 3);
  }

  case if_icmpgt: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_icmpgt %4d", offset + ip - 3);
  }
  case if_icmpge: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_icmpge %4d", offset + ip - 3);
  }
  case if_icmplt: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_icmplt %4d", offset + ip - 3);
  }
  case if_icmple: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "if_icmple %4d", offset + ip - 3);
  }

  case ifeq: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "ifeq %4d", offset + ip - 3);
  }
  case ifne: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "ifne %4d", offset + ip - 3);
  }
  case ifgt: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "ifgt %4d", offset + ip - 3);
  }
  case ifge: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "ifge %4d", offset + ip - 3);
  }
  case iflt: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "iflt %4d", offset + ip - 3);
  }
  case ifle: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "ifle %4d", offset + ip - 3);
  }

  case ifnonnull: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "ifnonnull %4d", offset + ip - 3);
  }
  case ifnull: {
    int16_t offset = read16(code, ip);
    return fprintf(stderr, "ifnull %4d", offset + ip - 3);
  }

  case iinc: {
    uint8_t a = code[ip++];
    uint8_t b = code[ip++];
    return fprintf(stderr, "iinc %2d %2d", a, b);
  }

  case iload:
    return fprintf(stderr, "iload %2d", code[ip++]);
  case fload:
    return fprintf(stderr, "fload %2d", code[ip++]);

  case iload_0:
    return fprintf(stderr, "iload_0");
  case fload_0:
    return fprintf(stderr, "fload_0");
  case iload_1:
    return fprintf(stderr, "iload_1");
  case fload_1:
    return fprintf(stderr, "fload_1");

  case iload_2:
    return fprintf(stderr, "iload_2");
  case fload_2:
    return fprintf(stderr, "fload_2");
  case iload_3:
    return fprintf(stderr, "iload_3");
  case fload_3:
    return fprintf(stderr, "fload_3");

  case imul:
    return fprintf(stderr, "imul");
  case ineg:
    return fprintf(stderr, "ineg");

  case instanceof:
    return fprintf(stderr, "instanceof %4d", read16(code, ip));
  case invokeinterface:
    return fprintf(stderr, "invokeinterface %4d", read16(code, ip));
  case invokespecial:
    return fprintf(stderr, "invokespecial %4d", read16(code, ip));
  case invokestatic:
    return fprintf(stderr, "invokestatic %4d", read16(code, ip));
  case invokevirtual:
    return fprintf(stderr, "invokevirtual %4d", read16(code, ip));

  case ior:
    return fprintf(stderr, "ior");
  case irem:
    return fprintf(stderr, "irem");
  case ireturn:
    return fprintf(stderr, "ireturn");
  case freturn:
    return fprintf(stderr, "freturn");
  case ishl:
    return fprintf(stderr, "ishl");
  case ishr:
    return fprintf(stderr, "ishr");

  case istore:
    return fprintf(stderr, "istore %2d", code[ip++]);
  case fstore:
    return fprintf(stderr, "fstore %2d", code[ip++]);

  case istore_0:
    return fprintf(stderr, "istore_0");
  case fstore_0:
    return fprintf(stderr, "fstore_0");
  case istore_1:
    return fprintf(stderr, "istore_1");
  case fstore_1:
    return fprintf(stderr, "fstore_1");
  case istore_2:
    return fprintf(stderr, "istore_2");
  case fstore_2:
    return fprintf(stderr, "fstore_2");
  case istore_3:
    return fprintf(stderr, "istore_3");
  case fstore_3:
    return fprintf(stderr, "fstore_3");

  case isub:
    return fprintf(stderr, "isub");
  case iushr:
    return fprintf(stderr, "iushr");
  case ixor:
    return fprintf(stderr, "ixor");

  case jsr:
    return fprintf(stderr, "jsr %4d", read16(code, ip) + startIp);
  case jsr_w:
    return fprintf(stderr, "jsr_w %08x", read32(code, ip) + startIp);

  case l2d:
    return fprintf(stderr, "l2d");
  case l2f:
    return fprintf(stderr, "l2f");
  case l2i:
    return fprintf(stderr, "l2i");
  case ladd:
    return fprintf(stderr, "ladd");
  case laload:
    return fprintf(stderr, "laload");

  case land:
    return fprintf(stderr, "land");
  case lastore:
    return fprintf(stderr, "lastore");

  case lcmp:
    return fprintf(stderr, "lcmp");
  case lconst_0:
    return fprintf(stderr, "lconst_0");
  case lconst_1:
    return fprintf(stderr, "lconst_1");

  case ldc:
    return fprintf(stderr, "ldc %4d", read16(code, ip));
  case ldc_w:
    return fprintf(stderr, "ldc_w %08x", read32(code, ip));
  case ldc2_w:
    return fprintf(stderr, "ldc2_w %4d", read16(code, ip));

  case ldiv_:
    return fprintf(stderr, "ldiv_");

  case lload:
    return fprintf(stderr, "lload %2d", code[ip++]);
  case dload:
    return fprintf(stderr, "dload %2d", code[ip++]);

  case lload_0:
    return fprintf(stderr, "lload_0");
  case dload_0:
    return fprintf(stderr, "dload_0");
  case lload_1:
    return fprintf(stderr, "lload_1");
  case dload_1:
    return fprintf(stderr, "dload_1");
  case lload_2:
    return fprintf(stderr, "lload_2");
  case dload_2:
    return fprintf(stderr, "dload_2");
  case lload_3:
    return fprintf(stderr, "lload_3");
  case dload_3:
    return fprintf(stderr, "dload_3");

  case lmul:
    return fprintf(stderr, "lmul");
  case lneg:
    return fprintf(stderr, "lneg");

  case lookupswitch: {
    while (ip & 0x3) {
      ip++;
    }
    int32_t default_ = read32(code, ip) + startIp;
    int32_t pairCount = read32(code, ip);
    fprintf(
        stderr, "lookupswitch default: %d pairCount: %d", default_, pairCount);

    for (int i = 0; i < pairCount; i++) {
      int32_t k = read32(code, ip);
      int32_t d = read32(code, ip) + startIp;
      fprintf(stderr, "\n%s  key: %2d dest: %d", prefix, k, d);
    }
    fprintf(stderr, "\n");
    fflush(stderr);
    return 0;
  }

  case lor:
    return fprintf(stderr, "lor");
  case lrem:
    return fprintf(stderr, "lrem");
  case lreturn:
    return fprintf(stderr, "lreturn");
  case dreturn:
    return fprintf(stderr, "dreturn");
  case lshl:
    return fprintf(stderr, "lshl");
  case lshr:
    return fprintf(stderr, "lshr");

  case lstore:
    return fprintf(stderr, "lstore %2d", code[ip++]);
  case dstore:
    return fprintf(stderr, "dstore %2d", code[ip++]);

  case lstore_0:
    return fprintf(stderr, "lstore_0");
  case dstore_0:
    return fprintf(stderr, "dstore_0");
  case lstore_1:
    return fprintf(stderr, "lstore_1");
  case dstore_1:
    return fprintf(stderr, "dstore_1");
  case lstore_2:
    return fprintf(stderr, "lstore_2");
  case dstore_2:
    return fprintf(stderr, "dstore_2");
  case lstore_3:
    return fprintf(stderr, "lstore_3");
  case dstore_3:
    return fprintf(stderr, "dstore_3");

  case lsub:
    return fprintf(stderr, "lsub");
  case lushr:
    return fprintf(stderr, "lushr");
  case lxor:
    return fprintf(stderr, "lxor");

  case monitorenter:
    return fprintf(stderr, "monitorenter");
  case monitorexit:
    return fprintf(stderr, "monitorexit");

  case multianewarray: {
    unsigned type = read16(code, ip);
    return fprintf(stderr, "multianewarray %4d %2d", type, code[ip++]);
  }

  case new_:
    return fprintf(stderr, "new %4d", read16(code, ip));

  case newarray:
    return fprintf(stderr, "newarray %2d", code[ip++]);

  case nop:
    return fprintf(stderr, "nop");
  case pop_:
    return fprintf(stderr, "pop");
  case pop2:
    return fprintf(stderr, "pop2");

  case putfield:
    return fprintf(stderr, "putfield %4d", read16(code, ip));
  case putstatic:
    return fprintf(stderr, "putstatic %4d", read16(code, ip));

  case ret:
    return fprintf(stderr, "ret %2d", code[ip++]);

  case return_:
    return fprintf(stderr, "return_");
  case saload:
    return fprintf(stderr, "saload");
  case sastore:
    return fprintf(stderr, "sastore");

  case sipush:
    return fprintf(stderr, "sipush %4d", read16(code, ip));

  case swap:
    return fprintf(stderr, "swap");

  case tableswitch: {
    while (ip & 0x3) {
      ip++;
    }
    int32_t default_ = read32(code, ip) + startIp;
    int32_t bottom = read32(code, ip);
    int32_t top = read32(code, ip);
    fprintf(stderr,
            "tableswitch default: %d bottom: %d top: %d",
            default_,
            bottom,
            top);

    for (int i = 0; i < top - bottom + 1; i++) {
      int32_t d = read32(code, ip) + startIp;
      fprintf(stderr, "%s  key: %d dest: %d", prefix, i + bottom, d);
    }
    return 0;
  }

  case wide: {
    switch (code[ip++]) {
    case aload:
      return fprintf(stderr, "wide aload %4d", read16(code, ip));

    case astore:
      return fprintf(stderr, "wide astore %4d", read16(code, ip));
    case iinc:
      fprintf(stderr, "wide iinc %4d %4d", read16(code, ip), read16(code, ip));
      /* fallthrough */
    case iload:
      return fprintf(stderr, "wide iload %4d", read16(code, ip));
    case istore:
      return fprintf(stderr, "wide istore %4d", read16(code, ip));
    case lload:
      return fprintf(stderr, "wide lload %4d", read16(code, ip));
    case lstore:
      return fprintf(stderr, "wide lstore %4d", read16(code, ip));
    case ret:
      return fprintf(stderr, "wide ret %4d", read16(code, ip));

    default:
      fprintf(
          stderr, "unknown wide instruction %2d %4d", instr, read16(code, ip));
      break;
    }
  }

  default: {
    return fprintf(stderr, "unknown instruction %2d", instr);
  }
  }
  return ip;
}

void disassembleCode(const char* prefix, uint8_t* code, unsigned length)
{
  unsigned ip = 0;

  while (ip < length) {
    fprintf(stderr, "%s%x:\t", prefix, ip);
    printInstruction(code, ip, prefix);
    fprintf(stderr, "\n");
  }
}

}  // namespace debug
}  // namespace jvm
}  // namespace avian
