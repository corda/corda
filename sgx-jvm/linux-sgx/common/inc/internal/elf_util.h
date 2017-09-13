/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
#ifndef _ELF_UTIL_H_
#define _ELF_UTIL_H_

#include "elfheader/elf_common.h"
#include "elfheader/elfstructs.h"

#ifndef __ELF_NATIVE_CLASS
#  if defined(_M_X64) || defined(__x86_64) || defined(__amd64)
#    include <limits.h>     /* for UINT_MAX */
#    define __ELF_NATIVE_CLASS 64
#  else
#    define __ELF_NATIVE_CLASS 32
#  endif
#endif

#if __ELF_NATIVE_CLASS == 64
#    define Rel Rela

#    define RTS_DT_REL    DT_RELA
#    define RTS_DT_RELSZ  DT_RELASZ
#    define RTS_DT_RELENT DT_RELAENT
#  else
#    define RTS_DT_REL    DT_REL
#    define RTS_DT_RELSZ  DT_RELSZ
#    define RTS_DT_RELENT DT_RELENT
#endif

/* We use this macro to refer to ELF types independent of the native wordsize.
   `ElfW(TYPE)' is used in place of `Elf32_TYPE' or `Elf64_TYPE'.  */
#define ElfW(type)      _ElfW (Elf, __ELF_NATIVE_CLASS, type)

/* We use this macro to refer to ELF macro constants.  For example,
   `ELFW(R_SYM)' is used in place of `ELF32_R_SYM' or `ELF64_R_SYM'.  */
#define ELFW(type)      _ElfW (ELF, __ELF_NATIVE_CLASS, type)

#define _ElfW(e,w,t)    _ElfW_1 (e, w, _##t)
#define _ElfW_1(e,w,t)  e##w##t

/* GNU header uses `JUMP_SLOT' while `JMP_SLOT' in FreeBSD. */
#define R_X86_64_JUMP_SLOT  R_X86_64_JMP_SLOT

#endif
