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


#ifndef _SE_CDEFS_H_
#define _SE_CDEFS_H_


#define SGX_WEAK __attribute__((weak))

# if (__GNUC__ >= 3)
#  define likely(x)	__builtin_expect ((x), 1)
#  define unlikely(x)	__builtin_expect ((x), 0)
# else
#  define likely(x)	(x)
#  define unlikely(x)	(x)
# endif

#ifndef SE_DECLSPEC_EXPORT
#define SE_DECLSPEC_EXPORT __attribute__((visibility("default")))
#endif

#ifndef SE_DECLSPEC_IMPORT
#define SE_DECLSPEC_IMPORT
#endif

#ifndef SE_DECLSPEC_ALIGN
#define SE_DECLSPEC_ALIGN(x) __attribute__((aligned(x)))
#endif

#ifndef SE_DECLSPEC_THREAD
#define SE_DECLSPEC_THREAD /*__thread*/
#endif

/* disable __try, __except on linux */
#ifndef __try
#define __try try
#endif

#ifndef __except
#define __except(x) catch(...)
#endif


#ifndef SE_DRIVER

#	define SE_GNU
#	if defined(__x86_64__)
#		define SE_64
#		define SE_GNU64
#	else
#		define SE_32
#		define SE_GNU32
#	endif

#endif

	#define INITIALIZER(f) \
	static void f(void) __attribute__((constructor));

#ifdef __cplusplus
#define MY_EXTERN extern "C"
#else
#define MY_EXTERN extern
#endif

#define SGX_ACCESS_VERSION(libname, num)                    \
    MY_EXTERN char sgx_##libname##_version[];          \
    MY_EXTERN char * __attribute__((destructor)) libname##_access_version_dummy##num()      \
    {                                                                                       \
        sgx_##libname##_version[0] = 's';                                                   \
        return sgx_##libname##_version;                                                     \
    } 


#endif
