/*
 * This file is provided under a dual BSD/GPLv2 license.  When using or
 * redistributing this file, you may do so under either license.
 *
 * GPL LICENSE SUMMARY
 *
 * Copyright(c) 2016-2017 Intel Corporation.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * Contact Information:
 * Jarkko Sakkinen <jarkko.sakkinen@linux.intel.com>
 * Intel Finland Oy - BIC 0357606-4 - Westendinkatu 7, 02160 Espoo
 *
 * BSD LICENSE
 *
 * Copyright(c) 2016-2017 Intel Corporation.
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
 * Authors:
 *
 * Jarkko Sakkinen <jarkko.sakkinen@linux.intel.com>
 * Suresh Siddha <suresh.b.siddha@intel.com>
 */

#ifndef _ASM_X86_SGX_H
#define _ASM_X86_SGX_H

#include "sgx_arch.h"
#include <asm/asm.h>
#include <linux/bitops.h>
#include <linux/err.h>
#include <linux/types.h>

#define SGX_CPUID 0x12

enum sgx_cpuid {
	SGX_CPUID_CAPABILITIES	= 0,
	SGX_CPUID_ATTRIBUTES	= 1,
	SGX_CPUID_EPC_BANKS	= 2,
};

enum sgx_commands {
	ECREATE	= 0x0,
	EADD	= 0x1,
	EINIT	= 0x2,
	EREMOVE	= 0x3,
	EDGBRD	= 0x4,
	EDGBWR	= 0x5,
	EEXTEND	= 0x6,
	ELDU	= 0x8,
	EBLOCK	= 0x9,
	EPA	= 0xA,
	EWB	= 0xB,
	ETRACK	= 0xC,
	EAUG	= 0xD,
	EMODPR	= 0xE,
	EMODT	= 0xF,
};

#ifdef CONFIG_X86_64
#define XAX "%%rax"
#else
#define XAX "%%eax"
#endif

#define __encls_ret(rax, rbx, rcx, rdx)			\
	({						\
	int ret;					\
	asm volatile(					\
	"1: .byte 0x0f, 0x01, 0xcf;\n\t"		\
	"2:\n"						\
	".section .fixup,\"ax\"\n"			\
	"3: mov $-14,"XAX"\n"				\
	"   jmp 2b\n"					\
	".previous\n"					\
	_ASM_EXTABLE(1b, 3b)				\
	: "=a"(ret)					\
	: "a"(rax), "b"(rbx), "c"(rcx), "d"(rdx)	\
	: "memory");					\
	ret;						\
	})

#define __encls(rax, rbx, rcx, rdx...)			\
	({						\
	int ret;					\
	asm volatile(					\
	"1: .byte 0x0f, 0x01, 0xcf;\n\t"		\
	"   xor "XAX","XAX"\n"				\
	"2:\n"						\
	".section .fixup,\"ax\"\n"			\
	"3: mov $-14,"XAX"\n"				\
	"   jmp 2b\n"					\
	".previous\n"					\
	_ASM_EXTABLE(1b, 3b)				\
	: "=a"(ret), "=b"(rbx), "=c"(rcx)		\
	: "a"(rax), "b"(rbx), "c"(rcx), rdx		\
	: "memory");					\
	ret;						\
	})

static inline unsigned long __ecreate(struct sgx_pageinfo *pginfo, void *secs)
{
	return __encls(ECREATE, pginfo, secs, "d"(0));
}

static inline int __eextend(void *secs, void *epc)
{
	return __encls(EEXTEND, secs, epc, "d"(0));
}

static inline int __eadd(struct sgx_pageinfo *pginfo, void *epc)
{
	return __encls(EADD, pginfo, epc, "d"(0));
}

static inline int __einit(void *sigstruct, struct sgx_einittoken *einittoken,
			  void *secs)
{
	return __encls_ret(EINIT, sigstruct, secs, einittoken);
}

static inline int __eremove(void *epc)
{
	unsigned long rbx = 0;
	unsigned long rdx = 0;

	return __encls_ret(EREMOVE, rbx, epc, rdx);
}

static inline int __edbgwr(void *epc, unsigned long *data)
{
	return __encls(EDGBWR, *data, epc, "d"(0));
}

static inline int __edbgrd(void *epc, unsigned long *data)
{
	unsigned long rbx = 0;
	int ret;

	ret = __encls(EDGBRD, rbx, epc, "d"(0));
	if (!ret)
		*(unsigned long *) data = rbx;

	return ret;
}

static inline int __etrack(void *epc)
{
	unsigned long rbx = 0;
	unsigned long rdx = 0;

	return __encls_ret(ETRACK, rbx, epc, rdx);
}

static inline int __eldu(unsigned long rbx, unsigned long rcx,
			 unsigned long rdx)
{
	return __encls_ret(ELDU, rbx, rcx, rdx);
}

static inline int __eblock(unsigned long rcx)
{
	unsigned long rbx = 0;
	unsigned long rdx = 0;

	return __encls_ret(EBLOCK, rbx, rcx, rdx);
}

static inline int __epa(void *epc)
{
	unsigned long rbx = SGX_PAGE_TYPE_VA;

	return __encls(EPA, rbx, epc, "d"(0));
}

static inline int __ewb(struct sgx_pageinfo *pginfo, void *epc, void *va)
{
	return __encls_ret(EWB, pginfo, epc, va);
}

static inline int __eaug(struct sgx_pageinfo *pginfo, void *epc)
{
	return __encls(EAUG, pginfo, epc, "d"(0));
}

static inline int __emodpr(struct sgx_secinfo *secinfo, void *epc)
{
	unsigned long rdx = 0;

	return __encls_ret(EMODPR, secinfo, epc, rdx);
}

static inline int __emodt(struct sgx_secinfo *secinfo, void *epc)
{
	unsigned long rdx = 0;

	return __encls_ret(EMODT, secinfo, epc, rdx);
}

#endif /* _ASM_X86_SGX_H */
