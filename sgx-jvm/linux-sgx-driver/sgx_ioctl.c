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
#include <linux/slab.h>
#include <linux/hashtable.h>
#include <linux/shmem_fs.h>

static int sgx_get_encl(unsigned long addr, struct sgx_encl **encl)
{
	struct mm_struct *mm = current->mm;
	struct vm_area_struct *vma;
	int ret;

	if (addr & (PAGE_SIZE - 1))
		return -EINVAL;

	down_read(&mm->mmap_sem);

	ret = sgx_encl_find(mm, addr, &vma);
	if (!ret) {
		*encl = vma->vm_private_data;

		if ((*encl)->flags & SGX_ENCL_SUSPEND)
			ret = SGX_POWER_LOST_ENCLAVE;
		else
			kref_get(&(*encl)->refcount);
	}

	up_read(&mm->mmap_sem);
	return ret;
}

/**
 * sgx_ioc_enclave_create - handler for %SGX_IOC_ENCLAVE_CREATE
 * @filep:	open file to /dev/sgx
 * @cmd:	the command value
 * @arg:	pointer to the &struct sgx_enclave_create
 *
 * Validates SECS attributes, allocates an EPC page for the SECS and performs
 * ECREATE.
 *
 * Return:
 * 0 on success,
 * system error on failure
 */
static long sgx_ioc_enclave_create(struct file *filep, unsigned int cmd,
				   unsigned long arg)
{
	struct sgx_enclave_create *createp = (struct sgx_enclave_create *)arg;
	void __user *src = (void __user *)createp->src;
	struct sgx_secs *secs;
	int ret;

	secs = kzalloc(sizeof(*secs),  GFP_KERNEL);
	if (!secs)
		return -ENOMEM;

	ret = copy_from_user(secs, src, sizeof(*secs));
	if (ret) {
		kfree(secs);
		return ret;
	}

	ret = sgx_encl_create(secs);

	kfree(secs);
	return ret;
}

/**
 * sgx_ioc_enclave_add_page - handler for %SGX_IOC_ENCLAVE_ADD_PAGE
 *
 * @filep:	open file to /dev/sgx
 * @cmd:	the command value
 * @arg:	pointer to the &struct sgx_enclave_add_page
 *
 * Creates a new enclave page and enqueues an EADD operation that will be
 * processed by a worker thread later on.
 *
 * Return:
 * 0 on success,
 * system error on failure
 */
static long sgx_ioc_enclave_add_page(struct file *filep, unsigned int cmd,
				     unsigned long arg)
{
	struct sgx_enclave_add_page *addp = (void *)arg;
	unsigned long secinfop = (unsigned long)addp->secinfo;
	struct sgx_secinfo secinfo;
	struct sgx_encl *encl;
	struct page *data_page;
	void *data;
	int ret;

	ret = sgx_get_encl(addp->addr, &encl);
	if (ret)
		return ret;

	if (copy_from_user(&secinfo, (void __user *)secinfop,
			   sizeof(secinfo))) {
		kref_put(&encl->refcount, sgx_encl_release);
		return -EFAULT;
	}

	data_page = alloc_page(GFP_HIGHUSER);
	if (!data_page) {
		kref_put(&encl->refcount, sgx_encl_release);
		return -ENOMEM;
	}

	data = kmap(data_page);

	ret = copy_from_user((void *)data, (void __user *)addp->src, PAGE_SIZE);
	if (ret)
		goto out;

	ret = sgx_encl_add_page(encl, addp->addr, data, &secinfo, addp->mrmask);
	if (ret)
		goto out;

out:
	kref_put(&encl->refcount, sgx_encl_release);
	kunmap(data_page);
	__free_page(data_page);
	return ret;
}

/**
 * sgx_ioc_enclave_init - handler for %SGX_IOC_ENCLAVE_INIT
 *
 * @filep:	open file to /dev/sgx
 * @cmd:	the command value
 * @arg:	pointer to the &struct sgx_enclave_init
 *
 * Flushes the remaining enqueued EADD operations and performs EINIT.
 *
 * Return:
 * 0 on success,
 * system error on failure
 */
static long sgx_ioc_enclave_init(struct file *filep, unsigned int cmd,
				 unsigned long arg)
{
	struct sgx_enclave_init *initp = (struct sgx_enclave_init *)arg;
	unsigned long sigstructp = (unsigned long)initp->sigstruct;
	unsigned long einittokenp = (unsigned long)initp->einittoken;
	unsigned long encl_id = initp->addr;
	struct sgx_sigstruct *sigstruct;
	struct sgx_einittoken *einittoken;
	struct sgx_encl *encl;
	struct page *initp_page;
	int ret;

	initp_page = alloc_page(GFP_HIGHUSER);
	if (!initp_page)
		return -ENOMEM;

	sigstruct = kmap(initp_page);
	einittoken = (struct sgx_einittoken *)
		((unsigned long)sigstruct + PAGE_SIZE / 2);

	ret = copy_from_user(sigstruct, (void __user *)sigstructp,
			     sizeof(*sigstruct));
	if (ret)
		goto out;

	ret = copy_from_user(einittoken, (void __user *)einittokenp,
			     sizeof(*einittoken));
	if (ret)
		goto out;

	ret = sgx_get_encl(encl_id, &encl);
	if (ret)
		goto out;

	ret = sgx_encl_init(encl, sigstruct, einittoken);

	kref_put(&encl->refcount, sgx_encl_release);

out:
	kunmap(initp_page);
	__free_page(initp_page);
	return ret;
}

typedef long (*sgx_ioc_t)(struct file *filep, unsigned int cmd,
			  unsigned long arg);

long sgx_ioctl(struct file *filep, unsigned int cmd, unsigned long arg)
{
	char data[256];
	sgx_ioc_t handler = NULL;
	long ret;

	switch (cmd) {
	case SGX_IOC_ENCLAVE_CREATE:
		handler = sgx_ioc_enclave_create;
		break;
	case SGX_IOC_ENCLAVE_ADD_PAGE:
		handler = sgx_ioc_enclave_add_page;
		break;
	case SGX_IOC_ENCLAVE_INIT:
		handler = sgx_ioc_enclave_init;
		break;
	default:
		return -ENOIOCTLCMD;
	}

	if (copy_from_user(data, (void __user *)arg, _IOC_SIZE(cmd)))
		return -EFAULT;

	ret = handler(filep, cmd, (unsigned long)((void *)data));
	if (!ret && (cmd & IOC_OUT)) {
		if (copy_to_user((void __user *)arg, data, _IOC_SIZE(cmd)))
			return -EFAULT;
	}

	return ret;
}
