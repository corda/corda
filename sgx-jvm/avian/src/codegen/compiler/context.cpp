/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "codegen/compiler/context.h"
#include "codegen/compiler/resource.h"

#include <avian/codegen/architecture.h>

namespace avian {
namespace codegen {
namespace compiler {

Context::Context(vm::System* system,
                 Assembler* assembler,
                 vm::Zone* zone,
                 Compiler::Client* client)
    : system(system),
      assembler(assembler),
      arch(assembler->arch()),
      zone(zone),
      client(client),
      stack(0),
      locals(0),
      saved(0),
      predecessor(0),
      regFile(arch->registerFile()),
      regAlloc(system, arch->registerFile()),
      registerResources(static_cast<RegisterResource*>(zone->allocate(
          sizeof(RegisterResource) * regFile->allRegisters.limit))),
      frameResources(0),
      acquiredResources(0),
      firstConstant(0),
      lastConstant(0),
      machineCode(0),
      firstEvent(0),
      lastEvent(0),
      forkState(0),
      firstBlock(0),
      logicalIp(-1),
      constantCount(0),
      parameterFootprint(0),
      localFootprint(0),
      machineCodeSize(0),
      alignedFrameSize(0),
      availableGeneralRegisterCount(regFile->generalRegisters.limit
                                    - regFile->generalRegisters.start),
      targetInfo(arch->targetInfo())
{
  for (Register i : regFile->generalRegisters) {
    new (registerResources + i.index()) RegisterResource(arch->reserved(i));

    if (registerResources[i.index()].reserved) {
      --availableGeneralRegisterCount;
    }
  }
  for (Register i : regFile->floatRegisters) {
    new (registerResources + i.index()) RegisterResource(arch->reserved(i));
  }
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian
