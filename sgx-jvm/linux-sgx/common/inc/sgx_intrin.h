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



#ifndef _SGX_INTRIN_H_
#define _SGX_INTRIN_H_

#if defined(__STDC__) || defined(__cplusplus) 
# define __STRING(x)    #x
#else
# define __STRING(x)    "x"
#endif

#define _DEPR_MESSAGE(func) __STRING(func)" is deprecated in enclave."

/* Deprecated GCC Built-ins */

# include <x86intrin.h>

/*#pragma GCC diagnostic error "-Wdeprecated-declarations" */
#define _SGX_DEPRECATED(__ret_type, __func_name, ...)         \
    __attribute__((deprecated(_DEPR_MESSAGE(__func_name))))  \
    __ret_type __func_name(__VA_ARGS__)

_SGX_DEPRECATED(void, _writefsbase_u32, unsigned int);
_SGX_DEPRECATED(void, _writefsbase_u64, unsigned long long);
_SGX_DEPRECATED(void, _writegsbase_u32, unsigned int);
_SGX_DEPRECATED(void, _writegsbase_u64, unsigned long long);

_SGX_DEPRECATED(unsigned long long, __rdpmc,  int);
_SGX_DEPRECATED(unsigned long long, __rdtsc,  void);
_SGX_DEPRECATED(unsigned long long, __rdtscp, unsigned int *);


#endif /* _SGX_INTRIN_H_ */
