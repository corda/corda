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
 *	This file is to define some types that is platform independent.
*/

#ifndef _SE_TYPE_H_
#define _SE_TYPE_H_
#include "se_cdefs.h"

#ifdef SE_DRIVER

typedef	INT8	int8_t;
typedef	UINT8	uint8_t;
typedef	INT16	int16_t;
typedef	UINT16	uint16_t;
typedef	INT32	int32_t;
typedef	UINT32	uint32_t;
typedef	INT64	int64_t;
typedef	UINT64	uint64_t;

#else

#include <stdint.h>
#include <unistd.h>

#ifndef TRUE
#define	TRUE 1
#endif

#ifndef FALSE
#define FALSE 0
#endif

#endif

#if defined(SE_64)

#define	PADDED_POINTER(t, p)        t* p
#define	PADDED_DWORD(d)             uint64_t d
#define	PADDED_LONG(l)              int64_t l
#define REG(name)                   r##name
#ifdef SE_SIM_EXCEPTION
#define REG_ALIAS(name)             R##name
#endif
#define REGISTER(name)              uint64_t REG(name)

#else /* !defined(SE_64) */

#define	PADDED_POINTER(t, p) t* p;  void*    ___##p##_pad_to64_bit
#define	PADDED_DWORD(d)             uint32_t d; uint32_t ___##d##_pad_to64_bit
#define	PADDED_LONG(l)              int32_t l;  int32_t  ___##l##_pad_to64_bit

#define REG(name)                   e##name

#ifdef SE_SIM_EXCEPTION
#define REG_ALIAS(name)             E##name
#endif

#define REGISTER(name)              uint32_t REG(name); uint32_t ___##e##name##_pad_to64_bit

#endif /* !defined(SE_64) */

#endif
