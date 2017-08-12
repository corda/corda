/*
 * This file is provided under a dual BSD/GPLv2 license.  When using or
 * redistributing this file, you may do so under either license.
 *
 * GPL LICENSE SUMMARY
 *
 * Copyright(c) 2016 Intel Corporation.
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
 * Copyright(c) 2016 Intel Corporation.
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
 * Serge Ayoun <serge.ayoun@intel.com>
 * Shay Katz-zamir <shay.katz-zamir@intel.com>
 */

#ifndef _ASM_X86_SGX_H
#define _ASM_X86_SGX_H

#include <asm/asm.h>
#include <linux/bitops.h>
#include <linux/err.h>
#include <linux/types.h>

#define SGX_CPUID		0x12

enum sgx_page_type {
	SGX_PAGE_TYPE_SECS	= 0x00,
	SGX_PAGE_TYPE_TCS	= 0x01,
	SGX_PAGE_TYPE_REG	= 0x02,
	SGX_PAGE_TYPE_VA	= 0x03,
};

enum sgx_secinfo_flags {
	SGX_SECINFO_R		= 0x01,
	SGX_SECINFO_W		= 0x02,
	SGX_SECINFO_X		= 0x04,
	SGX_SECINFO_SECS	= 0x000ULL,
	SGX_SECINFO_TCS		= 0x100ULL,
	SGX_SECINFO_REG		= 0x200ULL,
};

struct sgx_secinfo {
	u64	flags;
	u64	reserved[7];
} __aligned(128);

struct sgx_einittoken {
	u32	valid;
	u8	reserved1[206];
	u16	isvsvnle;
	u8	reserved2[92];
} __aligned(512);

enum isgx_secs_attributes {
	SGX_SECS_A_DEBUG		= BIT_ULL(1),
	SGX_SECS_A_MODE64BIT		= BIT_ULL(2),
	SGX_SECS_A_PROVISION_KEY	= BIT_ULL(4),
	SGX_SECS_A_LICENSE_KEY		= BIT_ULL(5),
	SGX_SECS_A_RESERVED_MASK	= (BIT_ULL(0) |
					   BIT_ULL(3) |
					   GENMASK_ULL(63, 6)),
};

#define SGX_SECS_RESERVED1_SIZE 28
#define SGX_SECS_RESERVED2_SIZE 32
#define SGX_SECS_RESERVED3_SIZE 96
#define SGX_SECS_RESERVED4_SIZE 3836

struct sgx_secs {
	u64	size;
	u64	base;
	u32	ssaframesize;
	uint8_t reserved1[SGX_SECS_RESERVED1_SIZE];
	u64	flags;
	u64	xfrm;
	u32	mrenclave[8];
	uint8_t	reserved2[SGX_SECS_RESERVED2_SIZE];
	u32	mrsigner[8];
	uint8_t	reserved3[SGX_SECS_RESERVED3_SIZE];
	u16	isvvprodid;
	u16	isvsvn;
	uint8_t	reserved[SGX_SECS_RESERVED4_SIZE];
};

struct sgx_tcs {
	u64 state;
	u64 flags;
	u64 ossa;
	u32 cssa;
	u32 nssa;
	u64 oentry;
	u64 aep;
	u64 ofsbase;
	u64 ogsbase;
	u32 fslimit;
	u32 gslimit;
	u64 reserved[503];
};

enum sgx_secinfo_masks {
	SGX_SECINFO_PERMISSION_MASK	= GENMASK_ULL(2, 0),
	SGX_SECINFO_PAGE_TYPE_MASK	= GENMASK_ULL(15, 8),
	SGX_SECINFO_RESERVED_MASK	= (GENMASK_ULL(7, 3) |
					   GENMASK_ULL(63, 16)),
};

struct sgx_pcmd {
	struct sgx_secinfo secinfo;
	u64 enclave_id;
	u8 reserved[40];
	u8 mac[16];
};

struct sgx_page_info {
	u64 linaddr;
	u64 srcpge;
	union {
		u64 secinfo;
		u64 pcmd;
	};
	u64 secs;
} __aligned(32);

#define SIGSTRUCT_SIZE 1808
#define EINITTOKEN_SIZE 304

enum {
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

#define __encls_ret(rax, rbx, rcx, rdx)			\
	({						\
	int ret;					\
	asm volatile(					\
	"1: .byte 0x0f, 0x01, 0xcf;\n\t"		\
	"2:\n"						\
	".section .fixup,\"ax\"\n"			\
	"3: jmp 2b\n"					\
	".previous\n"					\
	_ASM_EXTABLE(1b, 3b)				\
	: "=a"(ret)					\
	: "a"(rax), "b"(rbx), "c"(rcx), "d"(rdx)	\
	: "memory");					\
	ret;						\
	})

#ifdef CONFIG_X86_64
#define __encls(rax, rbx, rcx, rdx...)			\
	({						\
	int ret;					\
	asm volatile(					\
	"1: .byte 0x0f, 0x01, 0xcf;\n\t"		\
	"   xor %%eax,%%eax;\n"				\
	"2:\n"						\
	".section .fixup,\"ax\"\n"			\
	"3: movq $-1,%%rax\n"				\
	"   jmp 2b\n"					\
	".previous\n"					\
	_ASM_EXTABLE(1b, 3b)				\
	: "=a"(ret), "=b"(rbx), "=c"(rcx)		\
	: "a"(rax), "b"(rbx), "c"(rcx), rdx		\
	: "memory");					\
	ret;						\
	})
#else
#define __encls(rax, rbx, rcx, rdx...)			\
	({						\
	int ret;					\
	asm volatile(					\
	"1: .byte 0x0f, 0x01, 0xcf;\n\t"		\
	"   xor %%eax,%%eax;\n"				\
	"2:\n"						\
	".section .fixup,\"ax\"\n"			\
	"3: mov $-1,%%eax\n"				\
	"   jmp 2b\n"					\
	".previous\n"					\
	_ASM_EXTABLE(1b, 3b)				\
	: "=a"(ret), "=b"(rbx), "=c"(rcx)		\
	: "a"(rax), "b"(rbx), "c"(rcx), rdx		\
	: "memory");					\
	ret;						\
	})
#endif

static inline unsigned long __ecreate(struct sgx_page_info *pginfo, void *secs)
{
	return __encls(ECREATE, pginfo, secs, "d"(0));
}

static inline int __eextend(void *secs, void *epc)
{
	return __encls(EEXTEND, secs, epc, "d"(0));
}

static inline int __eadd(struct sgx_page_info *pginfo, void *epc)
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

static inline int __ewb(struct sgx_page_info *pginfo, void *epc, void *va)
{
	return __encls_ret(EWB, pginfo, epc, va);
}

static inline int __eaug(struct sgx_page_info *pginfo, void *epc)
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

struct sgx_encl;

struct sgx_epc_page {
	resource_size_t	pa;
	struct list_head free_list;
};

extern struct sgx_epc_page *sgx_alloc_page(unsigned int flags);
extern int sgx_free_page(struct sgx_epc_page *entry, struct sgx_encl *encl);
extern void *sgx_get_page(struct sgx_epc_page *entry);
extern void sgx_put_page(void *epc_page_vaddr);

#endif /* _ASM_X86_SGX_H */
