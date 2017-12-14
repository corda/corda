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
 * Serge Ayoun <serge.ayoun@intel.com>
 * Shay Katz-zamir <shay.katz-zamir@intel.com>
 */

#ifndef __ARCH_INTEL_SGX_H__
#define __ARCH_INTEL_SGX_H__

#include "sgx_asm.h"
#include <linux/kref.h>
#include <linux/version.h>
#include <linux/rbtree.h>
#include <linux/rwsem.h>
#include <linux/sched.h>
#include <linux/workqueue.h>
#include <linux/mmu_notifier.h>
#include <linux/radix-tree.h>
#include "sgx_arch.h"
#include "sgx_user.h"

#define SGX_EINIT_SPIN_COUNT	20
#define SGX_EINIT_SLEEP_COUNT	50
#define SGX_EINIT_SLEEP_TIME	20

#define SGX_VA_SLOT_COUNT 512

struct sgx_epc_page {
	resource_size_t	pa;
	struct list_head list;
	struct sgx_encl_page *encl_page;
};

enum sgx_alloc_flags {
	SGX_ALLOC_ATOMIC	= BIT(0),
};

struct sgx_va_page {
	struct sgx_epc_page *epc_page;
	DECLARE_BITMAP(slots, SGX_VA_SLOT_COUNT);
	struct list_head list;
};

static inline unsigned int sgx_alloc_va_slot(struct sgx_va_page *page)
{
	int slot = find_first_zero_bit(page->slots, SGX_VA_SLOT_COUNT);

	if (slot < SGX_VA_SLOT_COUNT)
		set_bit(slot, page->slots);

	return slot << 3;
}

static inline void sgx_free_va_slot(struct sgx_va_page *page,
				    unsigned int offset)
{
	clear_bit(offset >> 3, page->slots);
}

enum sgx_encl_page_flags {
	SGX_ENCL_PAGE_TCS	= BIT(0),
	SGX_ENCL_PAGE_RESERVED	= BIT(1),
};

struct sgx_encl_page {
	unsigned long addr;
	unsigned int flags;
	struct sgx_epc_page *epc_page;
	struct sgx_va_page *va_page;
	unsigned int va_offset;
};

struct sgx_tgid_ctx {
	struct pid *tgid;
	struct kref refcount;
	struct list_head encl_list;
	struct list_head list;
};

enum sgx_encl_flags {
	SGX_ENCL_INITIALIZED	= BIT(0),
	SGX_ENCL_DEBUG		= BIT(1),
	SGX_ENCL_SECS_EVICTED	= BIT(2),
	SGX_ENCL_SUSPEND	= BIT(3),
	SGX_ENCL_DEAD		= BIT(4),
};

struct sgx_encl {
	unsigned int flags;
	uint64_t attributes;
	uint64_t xfrm;
	unsigned int secs_child_cnt;
	struct mutex lock;
	struct mm_struct *mm;
	struct file *backing;
	struct file *pcmd;
	struct list_head load_list;
	struct kref refcount;
	unsigned long base;
	unsigned long size;
	unsigned long ssaframesize;
	struct list_head va_pages;
	struct radix_tree_root page_tree;
	struct list_head add_page_reqs;
	struct work_struct add_page_work;
	struct sgx_encl_page secs;
	struct sgx_tgid_ctx *tgid_ctx;
	struct list_head encl_list;
	struct mmu_notifier mmu_notifier;
};

struct sgx_epc_bank {
	unsigned long pa;
#ifdef CONFIG_X86_64
	unsigned long va;
#endif
	unsigned long size;
};

extern struct workqueue_struct *sgx_add_page_wq;
extern struct sgx_epc_bank sgx_epc_banks[];
extern int sgx_nr_epc_banks;
extern u64 sgx_encl_size_max_32;
extern u64 sgx_encl_size_max_64;
extern u64 sgx_xfrm_mask;
extern u32 sgx_misc_reserved;
extern u32 sgx_xsave_size_tbl[64];

extern const struct vm_operations_struct sgx_vm_ops;

#define sgx_pr_ratelimited(level, encl, fmt, ...)			  \
	pr_ ## level ## _ratelimited("intel_sgx: [%d:0x%p] " fmt,	  \
				     pid_nr((encl)->tgid_ctx->tgid),	  \
				     (void *)(encl)->base, ##__VA_ARGS__)

#define sgx_dbg(encl, fmt, ...) \
	sgx_pr_ratelimited(debug, encl, fmt, ##__VA_ARGS__)
#define sgx_info(encl, fmt, ...) \
	sgx_pr_ratelimited(info, encl, fmt, ##__VA_ARGS__)
#define sgx_warn(encl, fmt, ...) \
	sgx_pr_ratelimited(warn, encl, fmt, ##__VA_ARGS__)
#define sgx_err(encl, fmt, ...) \
	sgx_pr_ratelimited(err, encl, fmt, ##__VA_ARGS__)
#define sgx_crit(encl, fmt, ...) \
	sgx_pr_ratelimited(crit, encl, fmt, ##__VA_ARGS__)

int sgx_encl_find(struct mm_struct *mm, unsigned long addr,
		  struct vm_area_struct **vma);
void sgx_tgid_ctx_release(struct kref *ref);
int sgx_encl_create(struct sgx_secs *secs);
int sgx_encl_add_page(struct sgx_encl *encl, unsigned long addr, void *data,
		      struct sgx_secinfo *secinfo, unsigned int mrmask);
int sgx_encl_init(struct sgx_encl *encl, struct sgx_sigstruct *sigstruct,
		  struct sgx_einittoken *einittoken);
void sgx_encl_release(struct kref *ref);

long sgx_ioctl(struct file *filep, unsigned int cmd, unsigned long arg);
#ifdef CONFIG_COMPAT
long sgx_compat_ioctl(struct file *filep, unsigned int cmd, unsigned long arg);
#endif

/* Utility functions */
int sgx_test_and_clear_young(struct sgx_encl_page *page, struct sgx_encl *encl);
struct page *sgx_get_backing(struct sgx_encl *encl,
			     struct sgx_encl_page *entry,
			     bool pcmd);
void sgx_put_backing(struct page *backing, bool write);
void sgx_insert_pte(struct sgx_encl *encl,
		    struct sgx_encl_page *encl_page,
		    struct sgx_epc_page *epc_page,
		    struct vm_area_struct *vma);
int sgx_eremove(struct sgx_epc_page *epc_page);
void sgx_zap_tcs_ptes(struct sgx_encl *encl,
		      struct vm_area_struct *vma);
void sgx_invalidate(struct sgx_encl *encl, bool flush_cpus);
void sgx_flush_cpus(struct sgx_encl *encl);

enum sgx_fault_flags {
	SGX_FAULT_RESERVE	= BIT(0),
};

struct sgx_encl_page *sgx_fault_page(struct vm_area_struct *vma,
				     unsigned long addr,
				     unsigned int flags);


extern struct mutex sgx_tgid_ctx_mutex;
extern struct list_head sgx_tgid_ctx_list;
extern atomic_t sgx_va_pages_cnt;

int sgx_add_epc_bank(resource_size_t start, unsigned long size, int bank);
int sgx_page_cache_init(void);
void sgx_page_cache_teardown(void);
struct sgx_epc_page *sgx_alloc_page(unsigned int flags);
void sgx_free_page(struct sgx_epc_page *entry, struct sgx_encl *encl);
void *sgx_get_page(struct sgx_epc_page *entry);
void sgx_put_page(void *epc_page_vaddr);
void sgx_eblock(struct sgx_encl *encl, struct sgx_epc_page *epc_page);
void sgx_etrack(struct sgx_encl *encl);

#endif /* __ARCH_X86_INTEL_SGX_H__ */
