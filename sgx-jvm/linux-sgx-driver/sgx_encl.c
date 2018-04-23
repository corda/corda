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
#include <asm/mman.h>
#include <linux/delay.h>
#include <linux/file.h>
#include <linux/highmem.h>
#include <linux/ratelimit.h>
#if (LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0))
	#include <linux/sched/signal.h>
#else
	#include <linux/signal.h>
#endif
#include "linux/file.h"
#include <linux/slab.h>
#include <linux/hashtable.h>
#include <linux/shmem_fs.h>

struct sgx_add_page_req {
	struct sgx_encl *encl;
	struct sgx_encl_page *encl_page;
	struct sgx_secinfo secinfo;
	u16 mrmask;
	struct list_head list;
};

/**
 * sgx_encl_find - find an enclave
 * @mm:		mm struct of the current process
 * @addr:	address in the ELRANGE
 * @vma:	the resulting VMA
 *
 * Finds an enclave identified by the given address. Gives back the VMA, that
 * is part of the enclave, located in that address. The VMA is given back if it
 * is a proper enclave VMA even if a &struct sgx_encl instance does not exist
 * yet (enclave creation has not been performed).
 *
 * Return:
 * 0 on success,
 * -EINVAL if an enclave was not found,
 * -ENOENT if the enclave has not been created yet
 */
int sgx_encl_find(struct mm_struct *mm, unsigned long addr,
		  struct vm_area_struct **vma)
{
	struct vm_area_struct *result;
	struct sgx_encl *encl;

	result = find_vma(mm, addr);
	if (!result || result->vm_ops != &sgx_vm_ops || addr < result->vm_start)
		return -EINVAL;

	encl = result->vm_private_data;
	*vma = result;

	return encl ? 0 : -ENOENT;
}

static struct sgx_tgid_ctx *sgx_find_tgid_ctx(struct pid *tgid)
{
	struct sgx_tgid_ctx *ctx;

	list_for_each_entry(ctx, &sgx_tgid_ctx_list, list)
		if (pid_nr(ctx->tgid) == pid_nr(tgid))
			return ctx;

	return NULL;
}

static int sgx_add_to_tgid_ctx(struct sgx_encl *encl)
{
	struct sgx_tgid_ctx *ctx;
	struct pid *tgid = get_pid(task_tgid(current));

	mutex_lock(&sgx_tgid_ctx_mutex);

	ctx = sgx_find_tgid_ctx(tgid);
	if (ctx) {
		if (kref_get_unless_zero(&ctx->refcount)) {
			encl->tgid_ctx = ctx;
			mutex_unlock(&sgx_tgid_ctx_mutex);
			put_pid(tgid);
			return 0;
		} else {
			list_del_init(&ctx->list);
		}
	}

	ctx = kzalloc(sizeof(*ctx), GFP_KERNEL);
	if (!ctx) {
		mutex_unlock(&sgx_tgid_ctx_mutex);
		put_pid(tgid);
		return -ENOMEM;
	}

	ctx->tgid = tgid;
	kref_init(&ctx->refcount);
	INIT_LIST_HEAD(&ctx->encl_list);

	list_add(&ctx->list, &sgx_tgid_ctx_list);

	encl->tgid_ctx = ctx;

	mutex_unlock(&sgx_tgid_ctx_mutex);
	return 0;
}

void sgx_tgid_ctx_release(struct kref *ref)
{
	struct sgx_tgid_ctx *pe =
		container_of(ref, struct sgx_tgid_ctx, refcount);
	mutex_lock(&sgx_tgid_ctx_mutex);
	list_del(&pe->list);
	mutex_unlock(&sgx_tgid_ctx_mutex);
	put_pid(pe->tgid);
	kfree(pe);
}

static int sgx_measure(struct sgx_epc_page *secs_page,
		       struct sgx_epc_page *epc_page,
		       u16 mrmask)
{
	void *secs;
	void *epc;
	int ret = 0;
	int i, j;

	for (i = 0, j = 1; i < 0x1000 && !ret; i += 0x100, j <<= 1) {
		if (!(j & mrmask))
			continue;

		secs = sgx_get_page(secs_page);
		epc = sgx_get_page(epc_page);

		ret = __eextend(secs, (void *)((unsigned long)epc + i));

		sgx_put_page(epc);
		sgx_put_page(secs);
	}

	return ret;
}

static int sgx_eadd(struct sgx_epc_page *secs_page,
		    struct sgx_epc_page *epc_page,
		    unsigned long linaddr,
		    struct sgx_secinfo *secinfo,
		    struct page *backing)
{
	struct sgx_pageinfo pginfo;
	void *epc_page_vaddr;
	int ret;

	pginfo.srcpge = (unsigned long)kmap_atomic(backing);
	pginfo.secs = (unsigned long)sgx_get_page(secs_page);
	epc_page_vaddr = sgx_get_page(epc_page);

	pginfo.linaddr = linaddr;
	pginfo.secinfo = (unsigned long)secinfo;
	ret = __eadd(&pginfo, epc_page_vaddr);

	sgx_put_page(epc_page_vaddr);
	sgx_put_page((void *)(unsigned long)pginfo.secs);
	kunmap_atomic((void *)(unsigned long)pginfo.srcpge);

	return ret;
}

static bool sgx_process_add_page_req(struct sgx_add_page_req *req,
				     struct sgx_epc_page *epc_page)
{
	struct page *backing;
	struct sgx_encl_page *encl_page = req->encl_page;
	struct sgx_encl *encl = req->encl;
	struct vm_area_struct *vma;
	int ret;

	if (encl->flags & (SGX_ENCL_SUSPEND | SGX_ENCL_DEAD))
		return false;

	ret = sgx_encl_find(encl->mm, encl_page->addr, &vma);
	if (ret)
		return false;

	backing = sgx_get_backing(encl, encl_page, false);
	if (IS_ERR(backing))
		return false;

	/* Do not race with do_exit() */
	if (!atomic_read(&encl->mm->mm_users)) {
		sgx_put_backing(backing, 0);
		return false;
	}

	ret = vm_insert_pfn(vma, encl_page->addr, PFN_DOWN(epc_page->pa));
	if (ret) {
		sgx_put_backing(backing, 0);
		return false;
	}

	ret = sgx_eadd(encl->secs.epc_page, epc_page, encl_page->addr,
		       &req->secinfo, backing);

	sgx_put_backing(backing, 0);
	if (ret) {
		sgx_warn(encl, "EADD returned %d\n", ret);
		zap_vma_ptes(vma, encl_page->addr, PAGE_SIZE);
		return false;
	}

	encl->secs_child_cnt++;

	ret = sgx_measure(encl->secs.epc_page, epc_page, req->mrmask);
	if (ret) {
		sgx_warn(encl, "EEXTEND returned %d\n", ret);
		zap_vma_ptes(vma, encl_page->addr, PAGE_SIZE);
		return false;
	}

	epc_page->encl_page = encl_page;
	encl_page->epc_page = epc_page;
	sgx_test_and_clear_young(encl_page, encl);
	list_add_tail(&epc_page->list, &encl->load_list);

	return true;
}

static void sgx_add_page_worker(struct work_struct *work)
{
	struct sgx_encl *encl;
	struct sgx_add_page_req *req;
	struct sgx_epc_page *epc_page;
	bool skip_rest = false;
	bool is_empty = false;

	encl = container_of(work, struct sgx_encl, add_page_work);

	do {
		schedule();

		if (encl->flags & SGX_ENCL_DEAD)
			skip_rest = true;

		mutex_lock(&encl->lock);
		req = list_first_entry(&encl->add_page_reqs,
				       struct sgx_add_page_req, list);
		list_del(&req->list);
		is_empty = list_empty(&encl->add_page_reqs);
		mutex_unlock(&encl->lock);

		if (skip_rest)
			goto next;

		epc_page = sgx_alloc_page(0);
		if (IS_ERR(epc_page)) {
			skip_rest = true;
			goto next;
		}

		down_read(&encl->mm->mmap_sem);
		mutex_lock(&encl->lock);

		if (!sgx_process_add_page_req(req, epc_page)) {
			sgx_free_page(epc_page, encl);
			skip_rest = true;
		}

		mutex_unlock(&encl->lock);
		up_read(&encl->mm->mmap_sem);

next:
		kfree(req);
	} while (!kref_put(&encl->refcount, sgx_encl_release) && !is_empty);
}

static u32 sgx_calc_ssaframesize(u32 miscselect, u64 xfrm)
{
	u32 size_max = PAGE_SIZE;
	u32 size;
	int i;

	for (i = 2; i < 64; i++) {
		if (!((1 << i) & xfrm))
			continue;

		size = SGX_SSA_GPRS_SIZE + sgx_xsave_size_tbl[i];
		if (miscselect & SGX_MISC_EXINFO)
			size += SGX_SSA_MISC_EXINFO_SIZE;

		if (size > size_max)
			size_max = size;
	}

	return (size_max + PAGE_SIZE - 1) >> PAGE_SHIFT;
}

static int sgx_validate_secs(const struct sgx_secs *secs,
			     unsigned long ssaframesize)
{
	int i;

	if (secs->size < (2 * PAGE_SIZE) ||
	    (secs->size & (secs->size - 1)) != 0)
		return -EINVAL;

	if (secs->base & (secs->size - 1))
		return -EINVAL;

	if (secs->attributes & SGX_ATTR_RESERVED_MASK ||
	    secs->miscselect & sgx_misc_reserved)
		return -EINVAL;

	if (secs->attributes & SGX_ATTR_MODE64BIT) {
#ifdef CONFIG_X86_64
		if (secs->size > sgx_encl_size_max_64)
			return -EINVAL;
#else
		return -EINVAL;
#endif
	} else {
		/* On 64-bit architecture allow 32-bit encls only in
		 * the compatibility mode.
		 */
#ifdef CONFIG_X86_64
		if (!test_thread_flag(TIF_ADDR32))
			return -EINVAL;
#endif
		if (secs->size > sgx_encl_size_max_32)
			return -EINVAL;
	}

	if ((secs->xfrm & 0x3) != 0x3 || (secs->xfrm & ~sgx_xfrm_mask))
		return -EINVAL;

	/* Check that BNDREGS and BNDCSR are equal. */
	if (((secs->xfrm >> 3) & 1) != ((secs->xfrm >> 4) & 1))
		return -EINVAL;

	if (!secs->ssaframesize || ssaframesize > secs->ssaframesize)
		return -EINVAL;

	for (i = 0; i < SGX_SECS_RESERVED1_SIZE; i++)
		if (secs->reserved1[i])
			return -EINVAL;

	for (i = 0; i < SGX_SECS_RESERVED2_SIZE; i++)
		if (secs->reserved2[i])
			return -EINVAL;

	for (i = 0; i < SGX_SECS_RESERVED3_SIZE; i++)
		if (secs->reserved3[i])
			return -EINVAL;

	for (i = 0; i < SGX_SECS_RESERVED4_SIZE; i++)
		if (secs->reserved4[i])
			return -EINVAL;

	return 0;
}

static void sgx_mmu_notifier_release(struct mmu_notifier *mn,
				     struct mm_struct *mm)
{
	struct sgx_encl *encl =
		container_of(mn, struct sgx_encl, mmu_notifier);

	mutex_lock(&encl->lock);
	encl->flags |= SGX_ENCL_DEAD;
	mutex_unlock(&encl->lock);
}

static const struct mmu_notifier_ops sgx_mmu_notifier_ops = {
	.release	= sgx_mmu_notifier_release,
};

static int sgx_init_page(struct sgx_encl *encl, struct sgx_encl_page *entry,
			 unsigned long addr, unsigned int alloc_flags)
{
	struct sgx_va_page *va_page;
	struct sgx_epc_page *epc_page = NULL;
	unsigned int va_offset = PAGE_SIZE;
	void *vaddr;
	int ret = 0;

	list_for_each_entry(va_page, &encl->va_pages, list) {
		va_offset = sgx_alloc_va_slot(va_page);
		if (va_offset < PAGE_SIZE)
			break;
	}

	if (va_offset == PAGE_SIZE) {
		va_page = kzalloc(sizeof(*va_page), GFP_KERNEL);
		if (!va_page)
			return -ENOMEM;

		epc_page = sgx_alloc_page(alloc_flags);
		if (IS_ERR(epc_page)) {
			kfree(va_page);
			return PTR_ERR(epc_page);
		}

		vaddr = sgx_get_page(epc_page);
		if (!vaddr) {
			sgx_warn(encl, "kmap of a new VA page failed %d\n",
				 ret);
			sgx_free_page(epc_page, encl);
			kfree(va_page);
			return -EFAULT;
		}

		ret = __epa(vaddr);
		sgx_put_page(vaddr);

		if (ret) {
			sgx_warn(encl, "EPA returned %d\n", ret);
			sgx_free_page(epc_page, encl);
			kfree(va_page);
			return -EFAULT;
		}

		atomic_inc(&sgx_va_pages_cnt);

		va_page->epc_page = epc_page;
		va_offset = sgx_alloc_va_slot(va_page);

		mutex_lock(&encl->lock);
		list_add(&va_page->list, &encl->va_pages);
		mutex_unlock(&encl->lock);
	}

	entry->va_page = va_page;
	entry->va_offset = va_offset;
	entry->addr = addr;

	return 0;
}

/**
 * sgx_encl_alloc - allocate memory for an enclave and set attributes
 *
 * @secs:	SECS data (must be page aligned)
 *
 * Allocates a new &struct sgx_encl instance. Validates SECS attributes, creates
 * backing storage for the enclave and sets enclave attributes to sane initial
 * values.
 *
 * Return:
 * &struct sgx_encl instance on success,
 * system error on failure
 */
static struct sgx_encl *sgx_encl_alloc(struct sgx_secs *secs)
{
	unsigned long ssaframesize;
	struct sgx_encl *encl;
	struct file *backing;
	struct file *pcmd;

	ssaframesize = sgx_calc_ssaframesize(secs->miscselect, secs->xfrm);
	if (sgx_validate_secs(secs, ssaframesize))
		return ERR_PTR(-EINVAL);

	backing = shmem_file_setup("[dev/sgx]", secs->size + PAGE_SIZE,
				   VM_NORESERVE);
	if (IS_ERR(backing))
		return (void *)backing;

	pcmd = shmem_file_setup("[dev/sgx]", (secs->size + PAGE_SIZE) >> 5,
				VM_NORESERVE);
	if (IS_ERR(pcmd)) {
		fput(backing);
		return (void *)pcmd;
	}

	encl = kzalloc(sizeof(*encl), GFP_KERNEL);
	if (!encl) {
		fput(backing);
		fput(pcmd);
		return ERR_PTR(-ENOMEM);
	}

	encl->attributes = secs->attributes;
	encl->xfrm = secs->xfrm;

	kref_init(&encl->refcount);
	INIT_LIST_HEAD(&encl->add_page_reqs);
	INIT_LIST_HEAD(&encl->va_pages);
	INIT_RADIX_TREE(&encl->page_tree, GFP_KERNEL);
	INIT_LIST_HEAD(&encl->load_list);
	INIT_LIST_HEAD(&encl->encl_list);
	mutex_init(&encl->lock);
	INIT_WORK(&encl->add_page_work, sgx_add_page_worker);

	encl->mm = current->mm;
	encl->base = secs->base;
	encl->size = secs->size;
	encl->ssaframesize = secs->ssaframesize;
	encl->backing = backing;
	encl->pcmd = pcmd;

	return encl;
}

/**
 * sgx_encl_create - create an enclave
 *
 * @secs:	page aligned SECS data
 *
 * Validates SECS attributes, allocates an EPC page for the SECS and creates
 * the enclave by performing ECREATE.
 *
 * Return:
 * 0 on success,
 * system error on failure
 */
int sgx_encl_create(struct sgx_secs *secs)
{
	struct sgx_pageinfo pginfo;
	struct sgx_secinfo secinfo;
	struct sgx_encl *encl;
	struct sgx_epc_page *secs_epc;
	struct vm_area_struct *vma;
	void *secs_vaddr;
	long ret;

	encl = sgx_encl_alloc(secs);
	if (IS_ERR(encl))
		return PTR_ERR(encl);

	secs_epc = sgx_alloc_page(0);
	if (IS_ERR(secs_epc)) {
		ret = PTR_ERR(secs_epc);
		goto out;
	}

	encl->secs.epc_page = secs_epc;

	ret = sgx_add_to_tgid_ctx(encl);
	if (ret)
		goto out;

	ret = sgx_init_page(encl, &encl->secs, encl->base + encl->size, 0);
	if (ret)
		goto out;

	secs_vaddr = sgx_get_page(secs_epc);

	pginfo.srcpge = (unsigned long)secs;
	pginfo.linaddr = 0;
	pginfo.secinfo = (unsigned long)&secinfo;
	pginfo.secs = 0;
	memset(&secinfo, 0, sizeof(secinfo));
	ret = __ecreate((void *)&pginfo, secs_vaddr);

	sgx_put_page(secs_vaddr);

	if (ret) {
		sgx_dbg(encl, "ECREATE returned %ld\n", ret);
		ret = -EFAULT;
		goto out;
	}

	if (secs->attributes & SGX_ATTR_DEBUG)
		encl->flags |= SGX_ENCL_DEBUG;

	encl->mmu_notifier.ops = &sgx_mmu_notifier_ops;
	ret = mmu_notifier_register(&encl->mmu_notifier, encl->mm);
	if (ret) {
		if (ret == -EINTR)
			ret = -ERESTARTSYS;
		encl->mmu_notifier.ops = NULL;
		goto out;
	}

	down_read(&current->mm->mmap_sem);
	ret = sgx_encl_find(current->mm, secs->base, &vma);
	if (ret != -ENOENT) {
		if (!ret)
			ret = -EINVAL;
		up_read(&current->mm->mmap_sem);
		goto out;
	}

	if (vma->vm_start != secs->base ||
	    vma->vm_end != (secs->base + secs->size)
	    /* vma->vm_pgoff != 0 */) {
		ret = -EINVAL;
		up_read(&current->mm->mmap_sem);
		goto out;
	}

	vma->vm_private_data = encl;
	up_read(&current->mm->mmap_sem);

	mutex_lock(&sgx_tgid_ctx_mutex);
	list_add_tail(&encl->encl_list, &encl->tgid_ctx->encl_list);
	mutex_unlock(&sgx_tgid_ctx_mutex);

	return 0;
out:
	if (encl)
		kref_put(&encl->refcount, sgx_encl_release);
	return ret;
}

static int sgx_validate_secinfo(struct sgx_secinfo *secinfo)
{
	u64 perm = secinfo->flags & SGX_SECINFO_PERMISSION_MASK;
	u64 page_type = secinfo->flags & SGX_SECINFO_PAGE_TYPE_MASK;
	int i;

	if ((secinfo->flags & SGX_SECINFO_RESERVED_MASK) ||
	    ((perm & SGX_SECINFO_W) && !(perm & SGX_SECINFO_R)) ||
	    (page_type != SGX_SECINFO_TCS &&
	     page_type != SGX_SECINFO_REG))
		return -EINVAL;

	for (i = 0; i < sizeof(secinfo->reserved) / sizeof(u64); i++)
		if (secinfo->reserved[i])
			return -EINVAL;

	return 0;
}

static bool sgx_validate_offset(struct sgx_encl *encl, unsigned long offset)
{
	if (offset & (PAGE_SIZE - 1))
		return false;

	if (offset >= encl->size)
		return false;

	return true;
}

static int sgx_validate_tcs(struct sgx_encl *encl, struct sgx_tcs *tcs)
{
	int i;

	if (tcs->flags & SGX_TCS_RESERVED_MASK) {
		sgx_dbg(encl, "%s: invalid TCS flags = 0x%lx\n",
			__func__, (unsigned long)tcs->flags);
		return -EINVAL;
	}

	if (tcs->flags & SGX_TCS_DBGOPTIN) {
		sgx_dbg(encl, "%s: DBGOPTIN TCS flag is set, EADD will clear it\n",
			__func__);
		return -EINVAL;
	}

	if (!sgx_validate_offset(encl, tcs->ossa)) {
		sgx_dbg(encl, "%s: invalid OSSA: 0x%lx\n", __func__,
			(unsigned long)tcs->ossa);
		return -EINVAL;
	}

	if (!sgx_validate_offset(encl, tcs->ofsbase)) {
		sgx_dbg(encl, "%s: invalid OFSBASE: 0x%lx\n", __func__,
			(unsigned long)tcs->ofsbase);
		return -EINVAL;
	}

	if (!sgx_validate_offset(encl, tcs->ogsbase)) {
		sgx_dbg(encl, "%s: invalid OGSBASE: 0x%lx\n", __func__,
			(unsigned long)tcs->ogsbase);
		return -EINVAL;
	}

	if ((tcs->fslimit & 0xFFF) != 0xFFF) {
		sgx_dbg(encl, "%s: invalid FSLIMIT: 0x%x\n", __func__,
			tcs->fslimit);
		return -EINVAL;
	}

	if ((tcs->gslimit & 0xFFF) != 0xFFF) {
		sgx_dbg(encl, "%s: invalid GSLIMIT: 0x%x\n", __func__,
			tcs->gslimit);
		return -EINVAL;
	}

	for (i = 0; i < sizeof(tcs->reserved) / sizeof(u64); i++)
		if (tcs->reserved[i])
			return -EINVAL;

	return 0;
}

static int __sgx_encl_add_page(struct sgx_encl *encl,
			       struct sgx_encl_page *encl_page,
			       unsigned long addr,
			       void *data,
			       struct sgx_secinfo *secinfo,
			       unsigned int mrmask)
{
	u64 page_type = secinfo->flags & SGX_SECINFO_PAGE_TYPE_MASK;
	struct page *backing;
	struct sgx_add_page_req *req = NULL;
	int ret;
	int empty;
	void *backing_ptr;

	if (sgx_validate_secinfo(secinfo))
		return -EINVAL;

	if (page_type == SGX_SECINFO_TCS) {
		ret = sgx_validate_tcs(encl, data);
		if (ret)
			return ret;
	}

	ret = sgx_init_page(encl, encl_page, addr, 0);
	if (ret)
		return ret;

	mutex_lock(&encl->lock);

	if (encl->flags & (SGX_ENCL_INITIALIZED | SGX_ENCL_DEAD)) {
		ret = -EINVAL;
		goto out;
	}

	if (radix_tree_lookup(&encl->page_tree, addr >> PAGE_SHIFT)) {
		ret = -EEXIST;
		goto out;
	}

	req = kzalloc(sizeof(*req), GFP_KERNEL);
	if (!req) {
		ret = -ENOMEM;
		goto out;
	}

	backing = sgx_get_backing(encl, encl_page, false);
	if (IS_ERR((void *)backing)) {
		ret = PTR_ERR((void *)backing);
		goto out;
	}

	ret = radix_tree_insert(&encl->page_tree, encl_page->addr >> PAGE_SHIFT,
				encl_page);
	if (ret) {
		sgx_put_backing(backing, false /* write */);
		goto out;
	}

	backing_ptr = kmap(backing);
	memcpy(backing_ptr, data, PAGE_SIZE);
	kunmap(backing);

	if (page_type == SGX_SECINFO_TCS)
		encl_page->flags |= SGX_ENCL_PAGE_TCS;

	memcpy(&req->secinfo, secinfo, sizeof(*secinfo));

	req->encl = encl;
	req->encl_page = encl_page;
	req->mrmask = mrmask;
	empty = list_empty(&encl->add_page_reqs);
	kref_get(&encl->refcount);
	list_add_tail(&req->list, &encl->add_page_reqs);
	if (empty)
		queue_work(sgx_add_page_wq, &encl->add_page_work);

	sgx_put_backing(backing, true /* write */);

	mutex_unlock(&encl->lock);
	return 0;
out:
	kfree(req);
	sgx_free_va_slot(encl_page->va_page, encl_page->va_offset);
	mutex_unlock(&encl->lock);
	return ret;
}

/**
 * sgx_encl_add_page - add a page to the enclave
 *
 * @encl:	an enclave
 * @addr:	page address in the ELRANGE
 * @data:	page data
 * @secinfo:	page permissions
 * @mrmask:	bitmask to select the 256 byte chunks to be measured
 *
 * Creates a new enclave page and enqueues an EADD operation that will be
 * processed by a worker thread later on.
 *
 * Return:
 * 0 on success,
 * system error on failure
 */
int sgx_encl_add_page(struct sgx_encl *encl, unsigned long addr, void *data,
		      struct sgx_secinfo *secinfo, unsigned int mrmask)
{
	struct sgx_encl_page *page;
	int ret;

	page = kzalloc(sizeof(*page), GFP_KERNEL);
	if (!page)
		return -ENOMEM;

	ret = __sgx_encl_add_page(encl, page, addr, data, secinfo, mrmask);

	if (ret)
		kfree(page);

	return ret;
}

static int sgx_einit(struct sgx_encl *encl, struct sgx_sigstruct *sigstruct,
		     struct sgx_einittoken *token)
{
	struct sgx_epc_page *secs_epc = encl->secs.epc_page;
	void *secs_va;
	int ret;

	secs_va = sgx_get_page(secs_epc);
	ret = __einit(sigstruct, token, secs_va);
	sgx_put_page(secs_va);

	return ret;
}

/**
 * sgx_encl_init - perform EINIT for the given enclave
 *
 * @encl:	an enclave
 * @sigstruct:	SIGSTRUCT for the enclave
 * @token:	EINITTOKEN for the enclave
 *
 * Retries a few times in order to perform EINIT operation on an enclave
 * because there could be potentially an interrupt storm.
 *
 * Return:
 * 0 on success,
 * -FAULT on a CPU exception during EINIT,
 * SGX error code
 */
int sgx_encl_init(struct sgx_encl *encl, struct sgx_sigstruct *sigstruct,
		  struct sgx_einittoken *token)
{
	int ret;
	int i;
	int j;

	flush_work(&encl->add_page_work);

	mutex_lock(&encl->lock);

	if (encl->flags & SGX_ENCL_INITIALIZED) {
		mutex_unlock(&encl->lock);
		return 0;
	}

	for (i = 0; i < SGX_EINIT_SLEEP_COUNT; i++) {
		for (j = 0; j < SGX_EINIT_SPIN_COUNT; j++) {
			ret = sgx_einit(encl, sigstruct, token);

			if (ret == SGX_UNMASKED_EVENT)
				continue;
			else
				break;
		}

		if (ret != SGX_UNMASKED_EVENT)
			break;

		msleep_interruptible(SGX_EINIT_SLEEP_TIME);
		if (signal_pending(current)) {
			mutex_unlock(&encl->lock);
			return -ERESTARTSYS;
		}
	}

	mutex_unlock(&encl->lock);

	if (ret) {
		if (ret > 0)
			sgx_dbg(encl, "EINIT returned %d\n", ret);
		return ret;
	}

	encl->flags |= SGX_ENCL_INITIALIZED;
	return 0;
}

void sgx_encl_release(struct kref *ref)
{
	struct sgx_encl_page *entry;
	struct sgx_va_page *va_page;
	struct sgx_encl *encl = container_of(ref, struct sgx_encl, refcount);
	struct radix_tree_iter iter;
	void **slot;

	mutex_lock(&sgx_tgid_ctx_mutex);
	if (!list_empty(&encl->encl_list))
		list_del(&encl->encl_list);
	mutex_unlock(&sgx_tgid_ctx_mutex);

	if (encl->mmu_notifier.ops)
		mmu_notifier_unregister_no_release(&encl->mmu_notifier,
						   encl->mm);

	radix_tree_for_each_slot(slot, &encl->page_tree, &iter, 0) {
		entry = *slot;
		if (entry->epc_page) {
			list_del(&entry->epc_page->list);
			sgx_free_page(entry->epc_page, encl);
		}
		radix_tree_delete(&encl->page_tree, entry->addr >> PAGE_SHIFT);
		kfree(entry);
	}

	while (!list_empty(&encl->va_pages)) {
		va_page = list_first_entry(&encl->va_pages,
					   struct sgx_va_page, list);
		list_del(&va_page->list);
		sgx_free_page(va_page->epc_page, encl);
		kfree(va_page);
		atomic_dec(&sgx_va_pages_cnt);
	}

	if (encl->secs.epc_page)
		sgx_free_page(encl->secs.epc_page, encl);

	if (encl->tgid_ctx)
		kref_put(&encl->tgid_ctx->refcount, sgx_tgid_ctx_release);

	if (encl->backing)
		fput(encl->backing);

	if (encl->pcmd)
		fput(encl->pcmd);

	kfree(encl);
}
