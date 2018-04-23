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


/* 
Some notes for using the profiling macros

1. Define _PROFILE_ before including "sgx_profile.h" will enable the profiling, 
   also need to include sgx_profile.cpp in the compiling process
2. When use it in multi-threaded application, please don't trigger profiling in multiple threads at the same time.  
   The implementation is not thread-safe now,  to avoid additional latency introduced by locks
3. PROFILE_OUTPUT macro should only be used once before application exits
4. PROFILE_START and PROFILE_END should be paired, otherwise PROFILE_OUTPUT will abort the program when detects the mismatch

A simple example to use PROFILE macro

#define _PROFILE_  
#include "sgx_profile.h"

...
PROFILE_INIT();
...

PROFILE_START("func1");
func1();
PROFILE_END("func1");

...

PROFILE_START("func2");
func2();
PROFILE_END("func2");

...

PROFILE_OUTPUT("C:\\work\\output.csv");
...
*/


#ifndef _SGX_PROFILE_H_
#define _SGX_PROFILE_H_


#if defined(_PROFILE_) 
#define PRO_START 0
#define PRO_END 1

#if defined(__cplusplus)
extern "C"
{
#endif

void profile_init();
void profile_start(const char* str); /* 'str' must be global string. Do not use string in stack. */
void profile_end(const char * str);
void profile_output(const char* filename);

#if defined(__cplusplus)
}
#endif

#define PROFILE_INIT()    profile_init()       
#define PROFILE_START(x)   profile_start(x)    
#define PROFILE_END(x)     profile_end(x)
#define PROFILE_OUTPUT(x)  profile_output(x)

#else
#define PROFILE_INIT() 
#define PROFILE_START(x)
#define PROFILE_END(x)
#define PROFILE_OUTPUT(x)
#endif


#endif
