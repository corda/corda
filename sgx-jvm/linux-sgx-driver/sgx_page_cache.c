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
 * Sean Christopherson <sean.j.christopherson@intel.com>
 */

#include "sgx.h"
#include <linux/freezer.h>
#include <linux/highmem.h>
#include <linux/kthread.h>
#include <linux/ratelimit.h>
#if (LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0))
	#include <linux/sched/signal.h>
#else
	#include <linux/signal.h>
#endif
#include <linux/slab.h>

#define SGX_NR_LOW_EPC_PAGES_DEFAULT 32
#define SGX_NR_SWAP_CLUSTER_MAX	16

static LIST_HEAD(sgx_free_list);
static DEFINE_SPINLOCK(sgx_free_list_lock);

LIST_HEAD(sgx_tgid_ctx_list);
DEFINE_MUTEX(sgx_tgid_ctx_mutex);
atomic_t sgx_va_pages_cnt = ATOMIC_INIT(0);
static unsigned int sgx_nr_total_epc_pages;
static unsigned int sgx_nr_free_pages;
static unsigned int sgx_nr_low_pages = SGX_NR_LOW_EPC_PAGES_DEFAULT;
static unsigned int sgx_nr_high_pages;
static struct task_struct *ksgxswapd_tsk;
static DECLARE_WAIT_QUEUE_HEAD(ksgxswapd_waitq);

static int sgx_test_and_clear_young_cb(pte_t *ptep, pgtable_t token,
				       unsigned long addr, void *data)
{
	pte_t pte;
	int ret;

	ret = pte_young(*ptep);
	if (ret) {
		pte = pte_mkold(*ptep);
		set_pte_at((struct mm_struct *)data, addr, ptep, pte);
	}

	return ret;
}

/**
 * sgx_test_and_clear_young() - Test and reset the accessed bit
 * @page:	enclave EPC page to be tested for recent access
 * @encl:	enclave which owns @page
 *
 * Checks the Access (A) bit from the PTE corresponding to the
 * enclave page and clears it.  Returns 1 if the page has been
 * recently accessed and 0 if not.
 */
int sgx_test_and_clear_young(struct sgx_encl_page *page, struct sgx_encl *encl)
{
	struct vm_area_struct *vma;
	int ret;

	ret = sgx_encl_find(encl->mm, page->addr, &vma);
	if (ret)
		return 0;

	if (encl != vma->vm_private_data)
		return 0;

	return apply_to_page_range(vma->vm_mm, page->addr, PAGE_SIZE,
				   sgx_test_and_clear_young_cb, vma->vm_mm);
}

static struct sgx_tgid_ctx *sgx_isolate_tgid_ctx(unsigned long nr_to_scan)
{
	struct sgx_tgid_ctx *ctx = NULL;
	int i;

	mutex_lock(&sgx_tgid_ctx_mutex);

	if (list_empty(&sgx_tgid_ctx_list)) {
		mutex_unlock(&sgx_tgid_ctx_mutex);
		return NULL;
	}

	for (i = 0; i < nr_to_scan; i++) {
		/* Peek TGID context from the head. */
		ctx = list_first_entry(&sgx_tgid_ctx_list,
				       struct sgx_tgid_ctx,
				       list);

		/* Move to the tail so that we do not encounter it in the
		 * next iteration.
		 */
		list_move_tail(&ctx->list, &sgx_tgid_ctx_list);

		/* Non-empty TGID context? */
		if (!list_empty(&ctx->encl_list) &&
		    kref_get_unless_zero(&ctx->refcount))
			break;

		ctx = NULL;
	}

	mutex_unlock(&sgx_tgid_ctx_mutex);

	return ctx;
}

static struct sgx_encl *sgx_isolate_encl(struct sgx_tgid_ctx *ctx,
					       unsigned long nr_to_scan)
{
	struct sgx_encl *encl = NULL;
	int i;

	mutex_lock(&sgx_tgid_ctx_mutex);

	if (list_empty(&ctx->encl_list)) {
		mutex_unlock(&sgx_tgid_ctx_mutex);
		return NULL;
	}

	for (i = 0; i < nr_to_scan; i++) {
		/* Peek encl from the head. */
		encl = list_first_entry(&ctx->encl_list, struct sgx_encl,
					encl_list);

		/* Move to the tail so that we do not encounter it in the
		 * next iteration.
		 */
		list_move_tail(&encl->encl_list, &ctx->encl_list);

		/* Enclave with faulted pages?  */
		if (!list_empty(&encl->load_list) &&
		    kref_get_unless_zero(&encl->refcount))
			break;

		encl = NULL;
	}

	mutex_unlock(&sgx_tgid_ctx_mutex);

	return encl;
}

static void sgx_isolate_pages(struct sgx_encl *encl,
			      struct list_head *dst,
			      unsigned long nr_to_scan)
{
	struct sgx_epc_page *entry;
	int i;

	mutex_lock(&encl->lock);

	if (encl->flags & SGX_ENCL_DEAD)
		goto out;

	for (i = 0; i < nr_to_scan; i++) {
		if (list_empty(&encl->load_list))
			break;

		entry = list_first_entry(&encl->load_list,
					 struct sgx_epc_page,
					 list);

		if (!sgx_test_and_clear_young(entry->encl_page, encl) &&
		    !(entry->encl_page->flags & SGX_ENCL_PAGE_RESERVED)) {
			entry->encl_page->flags |= SGX_ENCL_PAGE_RESERVED;
			list_move_tail(&entry->list, dst);
		} else {
			list_move_tail(&entry->list, &encl->load_list);
		}
	}
out:
	mutex_unlock(&encl->lock);
}

static int __sgx_ewb(struct sgx_encl *encl,
		     struct sgx_encl_page *encl_page)
{
	struct sgx_pageinfo pginfo;
	struct page *backing;
	struct page *pcmd;
	unsigned long pcmd_offset;
	void *epc;
	void *va;
	int ret;

	pcmd_offset = ((encl_page->addr >> PAGE_SHIFT) & 31) * 128;

	backing = sgx_get_backing(encl, encl_page, false);
	if (IS_ERR(backing)) {
		ret = PTR_ERR(backing);
		sgx_warn(encl, "pinning the backing page for EWB failed with %d\n",
			 ret);
		return ret;
	}

	pcmd = sgx_get_backing(encl, encl_page, true);
	if (IS_ERR(pcmd)) {
		ret = PTR_ERR(pcmd);
		sgx_warn(encl, "pinning the pcmd page for EWB failed with %d\n",
			 ret);
		goto out;
	}

	epc = sgx_get_page(encl_page->epc_page);
	va = sgx_get_page(encl_page->va_page->epc_page);

	pginfo.srcpge = (unsigned long)kmap_atomic(backing);
	pginfo.pcmd = (unsigned long)kmap_atomic(pcmd) + pcmd_offset;
	pginfo.linaddr = 0;
	pginfo.secs = 0;
	ret = __ewb(&pginfo, epc,
		    (void *)((unsigned long)va + encl_page->va_offset));
	kunmap_atomic((void *)(unsigned long)(pginfo.pcmd - pcmd_offset));
	kunmap_atomic((void *)(unsigned long)pginfo.srcpge);

	sgx_put_page(va);
	sgx_put_page(epc);
	sgx_put_backing(pcmd, true);

out:
	sgx_put_backing(backing, true);
	return ret;
}

static bool sgx_ewb(struct sgx_encl *encl,
		    struct sgx_encl_page *entry)
{
	int ret = __sgx_ewb(encl, entry);

	if (ret == SGX_NOT_TRACKED) {
		/* slow path, IPI needed */
		sgx_flush_cpus(encl);
		ret = __sgx_ewb(encl, entry);
	}

	if (ret) {
		/* make enclave inaccessible */
		sgx_invalidate(encl, true);
		if (ret > 0)
			sgx_err(encl, "EWB returned %d, enclave killed\n", ret);
		return false;
	}

	return true;
}

static void sgx_evict_page(struct sgx_encl_page *entry,
			   struct sgx_encl *encl)
{
	sgx_ewb(encl, entry);
	sgx_free_page(entry->epc_page, encl);
	entry->epc_page = NULL;
	entry->flags &= ~SGX_ENCL_PAGE_RESERVED;
}

static void sgx_write_pages(struct sgx_encl *encl, struct list_head *src)
{
	struct sgx_epc_page *entry;
	struct sgx_epc_page *tmp;
	struct vm_area_struct *vma;
	int ret;

	if (list_empty(src))
		return;

	entry = list_first_entry(src, struct sgx_epc_page, list);

	mutex_lock(&encl->lock);

	/* EBLOCK */
	list_for_each_entry_safe(entry, tmp, src, list) {
		ret = sgx_encl_find(encl->mm, entry->encl_page->addr, &vma);
		if (!ret && encl == vma->vm_private_data)
			zap_vma_ptes(vma, entry->encl_page->addr, PAGE_SIZE);

		sgx_eblock(encl, entry);
	}

	/* ETRACK */
	sgx_etrack(encl);

	/* EWB */
	while (!list_empty(src)) {
		entry = list_first_entry(src, struct sgx_epc_page, list);
		list_del(&entry->list);
		sgx_evict_page(entry->encl_page, encl);
		encl->secs_child_cnt--;
	}

	if (!encl->secs_child_cnt && (encl->flags & SGX_ENCL_INITIALIZED)) {
		sgx_evict_page(&encl->secs, encl);
		encl->flags |= SGX_ENCL_SECS_EVICTED;
	}

	mutex_unlock(&encl->lock);
}

static void sgx_swap_pages(unsigned long nr_to_scan)
{
	struct sgx_tgid_ctx *ctx;
	struct sgx_encl *encl;
	LIST_HEAD(cluster);

	ctx = sgx_isolate_tgid_ctx(nr_to_scan);
	if (!ctx)
		return;

	encl = sgx_isolate_encl(ctx, nr_to_scan);
	if (!encl)
		goto out;

	down_read(&encl->mm->mmap_sem);
	sgx_isolate_pages(encl, &cluster, nr_to_scan);
	sgx_write_pages(encl, &cluster);
	up_read(&encl->mm->mmap_sem);

	kref_put(&encl->refcount, sgx_encl_release);
out:
	kref_put(&ctx->refcount, sgx_tgid_ctx_release);
}

static int ksgxswapd(void *p)
{
	set_freezable();

	while (!kthread_should_stop()) {
		if (try_to_freeze())
			continue;

		wait_event_freezable(ksgxswapd_waitq,
				     kthread_should_stop() ||
				     sgx_nr_free_pages < sgx_nr_high_pages);

		if (sgx_nr_free_pages < sgx_nr_high_pages)
			sgx_swap_pages(SGX_NR_SWAP_CLUSTER_MAX);
	}

	pr_info("%s: done\n", __func__);
	return 0;
}

int sgx_add_epc_bank(resource_size_t start, unsigned long size, int bank)
{
	unsigned long i;
	struct sgx_epc_page *new_epc_page, *entry;
	struct list_head *parser, *temp;

	for (i = 0; i < size; i += PAGE_SIZE) {
		new_epc_page = kzalloc(sizeof(*new_epc_page), GFP_KERNEL);
		if (!new_epc_page)
			goto err_freelist;
		new_epc_page->pa = (start + i) | bank;

		spin_lock(&sgx_free_list_lock);
		list_add_tail(&new_epc_page->list, &sgx_free_list);
		sgx_nr_total_epc_pages++;
		sgx_nr_free_pages++;
		spin_unlock(&sgx_free_list_lock);
	}

	return 0;
err_freelist:
	list_for_each_safe(parser, temp, &sgx_free_list) {
		spin_lock(&sgx_free_list_lock);
		entry = list_entry(parser, struct sgx_epc_page, list);
		list_del(&entry->list);
		spin_unlock(&sgx_free_list_lock);
		kfree(entry);
	}
	return -ENOMEM;
}

int sgx_page_cache_init(void)
{
	struct task_struct *tmp;

	sgx_nr_high_pages = 2 * sgx_nr_low_pages;

	tmp = kthread_run(ksgxswapd, NULL, "ksgxswapd");
	if (!IS_ERR(tmp))
		ksgxswapd_tsk = tmp;
	return PTR_ERR_OR_ZERO(tmp);
}

void sgx_page_cache_teardown(void)
{
	struct sgx_epc_page *entry;
	struct list_head *parser, *temp;

	if (ksgxswapd_tsk) {
		kthread_stop(ksgxswapd_tsk);
		ksgxswapd_tsk = NULL;
	}

	spin_lock(&sgx_free_list_lock);
	list_for_each_safe(parser, temp, &sgx_free_list) {
		entry = list_entry(parser, struct sgx_epc_page, list);
		list_del(&entry->list);
		kfree(entry);
	}
	spin_unlock(&sgx_free_list_lock);
}

static struct sgx_epc_page *sgx_alloc_page_fast(void)
{
	struct sgx_epc_page *entry = NULL;

	spin_lock(&sgx_free_list_lock);

	if (!list_empty(&sgx_free_list)) {
		entry = list_first_entry(&sgx_free_list, struct sgx_epc_page,
					 list);
		list_del(&entry->list);
		sgx_nr_free_pages--;
	}

	spin_unlock(&sgx_free_list_lock);

	return entry;
}

/**
 * sgx_alloc_page - allocate an EPC page
 * @flags:	allocation flags
 *
 * Try to grab a page from the free EPC page list. If there is a free page
 * available, it is returned to the caller. If called with SGX_ALLOC_ATOMIC,
 * the function will return immediately if the list is empty. Otherwise, it
 * will swap pages up until there is a free page available. Before returning
 * the low watermark is checked and ksgxswapd is waken up if we are below it.
 *
 * Return: an EPC page or a system error code
 */
struct sgx_epc_page *sgx_alloc_page(unsigned int flags)
{
	struct sgx_epc_page *entry;

	for ( ; ; ) {
		entry = sgx_alloc_page_fast();
		if (entry)
			break;

		/* We need at minimum two pages for the #PF handler. */
		if (atomic_read(&sgx_va_pages_cnt) >
		    (sgx_nr_total_epc_pages - 2))
			return ERR_PTR(-ENOMEM);

		if (flags & SGX_ALLOC_ATOMIC) {
			entry = ERR_PTR(-EBUSY);
			break;
		}

		if (signal_pending(current)) {
			entry = ERR_PTR(-ERESTARTSYS);
			break;
		}

		sgx_swap_pages(SGX_NR_SWAP_CLUSTER_MAX);
		schedule();
	}

	if (sgx_nr_free_pages < sgx_nr_low_pages)
		wake_up(&ksgxswapd_waitq);

	return entry;
}

/**
 * sgx_free_page - free an EPC page
 *
 * EREMOVE an EPC page and insert it back to the list of free pages.
 * If EREMOVE fails, the error is printed out loud as a critical error.
 * It is an indicator of a driver bug if that would happen.
 *
 * @entry:	any EPC page
 * @encl:	enclave that owns the given EPC page
 */
void sgx_free_page(struct sgx_epc_page *entry, struct sgx_encl *encl)
{
	void *epc;
	int ret;

	epc = sgx_get_page(entry);
	ret = __eremove(epc);
	sgx_put_page(epc);

	if (ret)
		sgx_crit(encl, "EREMOVE returned %d\n", ret);

	spin_lock(&sgx_free_list_lock);
	list_add(&entry->list, &sgx_free_list);
	sgx_nr_free_pages++;
	spin_unlock(&sgx_free_list_lock);
}

void *sgx_get_page(struct sgx_epc_page *entry)
{
#ifdef CONFIG_X86_32
	return kmap_atomic_pfn(PFN_DOWN(entry->pa));
#else
	int i = ((entry->pa) & ~PAGE_MASK);

	return (void *)(sgx_epc_banks[i].va +
		((entry->pa & PAGE_MASK) - sgx_epc_banks[i].pa));
#endif
}

void sgx_put_page(void *epc_page_vaddr)
{
#ifdef CONFIG_X86_32
	kunmap_atomic(epc_page_vaddr);
#else
#endif
}
