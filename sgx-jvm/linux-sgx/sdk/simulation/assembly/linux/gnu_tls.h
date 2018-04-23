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
#ifndef GNU_TLS_H__
#define GNU_TLS_H__

#include <stddef.h>


/* Type for the dtv.  */
typedef union
{
  size_t counter;
  struct
  {
    void *val;
    int is_static;
  } pointer;
} dtv_t;


typedef struct
{
  void *tcb;            /* Pointer to the TCB.  Not necessarily the
                           thread descriptor used by libpthread.  */
  dtv_t *dtv;
  void *self;           /* Pointer to the thread descriptor.  */

  /* We are not interested in the other fields. */
} tcbhead_t;

/* ------------------------------------------------------------ */

#if defined(__amd64) || defined(__amd64__) || defined (__x86_64)
/* x86_64 uses %fs as the thread register */
#define GET_DTV()                               \
  ({ dtv_t* __dtv;                              \
     __asm__ ("mov %%fs:%c1, %0" : "=r"(__dtv)      \
          : "i" (offsetof (tcbhead_t, dtv)));   \
     __dtv; })

#define GET_FS_GS_0()                                    \
  ({  uintptr_t __orig;                                  \
      __asm__ volatile ("mov %%fs:0x0, %0" : "=r"(__orig));  \
      __orig; })

#define SET_FS_GS_0(val)                                 \
  ({ __asm__ volatile ("mov %0, %%fs:0x0" : :"r"(val));})

#elif defined(__i386) || defined(__i386__)
/* IA32 uses %gs as the thread register */
#define GET_DTV()                               \
  ({ dtv_t* __dtv;                              \
     __asm__ ("mov %%gs:%c1, %0" : "=r"(__dtv)      \
          : "i" (offsetof (tcbhead_t, dtv)));   \
     __dtv; })

#define GET_FS_GS_0()                                    \
  ({  uintptr_t __orig;                                  \
      __asm__ volatile ("mov %%gs:0x0, %0" : "=r"(__orig));  \
     __orig; })

#define SET_FS_GS_0(val)                                 \
  ({ __asm__ volatile ("mov %0, %%gs:0x0" : :"r"(val));})

#endif

#define read_dtv_val(dtv) (dtv->pointer.val)
#define set_dtv_val(dtv, v) \
    do { dtv->pointer.val = (void*)(size_t)v; } while (0)


#endif /* !GNU_TLS_H__ */
