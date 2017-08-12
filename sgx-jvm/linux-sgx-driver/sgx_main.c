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
 * Sean Christopherson <sean.j.christopherson@intel.com>
 */

#include "sgx.h"
#include <linux/acpi.h>
#include <linux/file.h>
#include <linux/highmem.h>
#include <linux/miscdevice.h>
#include <linux/module.h>
#include <linux/suspend.h>
#include <linux/hashtable.h>
#include <linux/kthread.h>
#include <linux/platform_device.h>

#define DRV_DESCRIPTION "Intel SGX Driver"
#define DRV_VERSION "0.10"

#define ENCL_SIZE_MAX_64 (64ULL * 1024ULL * 1024ULL * 1024ULL)
#define ENCL_SIZE_MAX_32 (2ULL * 1024ULL * 1024ULL * 1024ULL)

MODULE_DESCRIPTION(DRV_DESCRIPTION);
MODULE_AUTHOR("Jarkko Sakkinen <jarkko.sakkinen@linux.intel.com>");
MODULE_VERSION(DRV_VERSION);
#ifndef X86_FEATURE_SGX
#define X86_FEATURE_SGX (9 * 32 + 2)
#endif

/*
 * Global data.
 */

struct workqueue_struct *sgx_add_page_wq;
#define SGX_MAX_EPC_BANKS 8
struct sgx_epc_bank sgx_epc_banks[SGX_MAX_EPC_BANKS];
int sgx_nr_epc_banks;
u64 sgx_encl_size_max_32 = ENCL_SIZE_MAX_32;
u64 sgx_encl_size_max_64 = ENCL_SIZE_MAX_64;
u64 sgx_xfrm_mask = 0x3;
u32 sgx_ssaframesize_tbl[64];
bool sgx_has_sgx2;

#ifdef CONFIG_COMPAT
long sgx_compat_ioctl(struct file *filep, unsigned int cmd, unsigned long arg)
{
	return sgx_ioctl(filep, cmd, arg);
}
#endif

static int sgx_mmap(struct file *file, struct vm_area_struct *vma)
{
	vma->vm_ops = &sgx_vm_ops;
	vma->vm_flags |= VM_PFNMAP | VM_DONTEXPAND | VM_DONTDUMP | VM_IO |
			 VM_DONTCOPY;

	return 0;
}

static unsigned long sgx_get_unmapped_area(struct file *file,
					   unsigned long addr,
					   unsigned long len,
					   unsigned long pgoff,
					   unsigned long flags)
{
	if (len < 2 * PAGE_SIZE || (len & (len - 1)))
		return -EINVAL;

	/* On 64-bit architecture, allow mmap() to exceed 32-bit encl
	 * limit only if the task is not running in 32-bit compatibility
	 * mode.
	 */
	if (len > sgx_encl_size_max_32)
#ifdef CONFIG_X86_64
		if (test_thread_flag(TIF_ADDR32))
			return -EINVAL;
#else
		return -EINVAL;
#endif

#ifdef CONFIG_X86_64
	if (len > sgx_encl_size_max_64)
		return -EINVAL;
#endif

	addr = current->mm->get_unmapped_area(file, addr, 2 * len, pgoff,
					      flags);
	if (IS_ERR_VALUE(addr))
		return addr;

	addr = (addr + (len - 1)) & ~(len - 1);

	return addr;
}

static const struct file_operations sgx_fops = {
	.owner			= THIS_MODULE,
	.unlocked_ioctl		= sgx_ioctl,
#ifdef CONFIG_COMPAT
	.compat_ioctl		= sgx_compat_ioctl,
#endif
	.mmap			= sgx_mmap,
	.get_unmapped_area	= sgx_get_unmapped_area,
};

static struct miscdevice sgx_dev = {
	.name	= "isgx",
	.fops	= &sgx_fops,
	.mode   = 0666,
};

static int sgx_init_platform(void)
{
	unsigned int eax, ebx, ecx, edx;
	unsigned long size;
	int i;

	cpuid(0, &eax, &ebx, &ecx, &edx);
	if (eax < SGX_CPUID) {
		pr_err("intel_sgx: CPUID is missing the SGX leaf instruction\n");
		return -ENODEV;
	}

	if (!boot_cpu_has(X86_FEATURE_SGX)) {
		pr_err("intel_sgx: CPU is missing the SGX feature\n");
		return -ENODEV;
	}

	cpuid_count(SGX_CPUID, 0x0, &eax, &ebx, &ecx, &edx);
	if (!(eax & 1)) {
		pr_err("intel_sgx: CPU does not support the SGX 1.0 instruction set\n");
		return -ENODEV;
	}

	if (boot_cpu_has(X86_FEATURE_OSXSAVE)) {
		cpuid_count(SGX_CPUID, 0x1, &eax, &ebx, &ecx, &edx);
		sgx_xfrm_mask = (((u64)edx) << 32) + (u64)ecx;
		for (i = 2; i < 64; i++) {
			cpuid_count(0x0D, i, &eax, &ebx, &ecx, &edx);
			if ((1 << i) & sgx_xfrm_mask)
				sgx_ssaframesize_tbl[i] =
					(168 + eax + ebx + PAGE_SIZE - 1) /
					PAGE_SIZE;
		}
	}

	cpuid_count(SGX_CPUID, 0x0, &eax, &ebx, &ecx, &edx);
	if (edx & 0xFFFF) {
#ifdef CONFIG_X86_64
		sgx_encl_size_max_64 = 1ULL << ((edx >> 8) & 0xFF);
#endif
		sgx_encl_size_max_32 = 1ULL << (edx & 0xFF);
	}

	sgx_nr_epc_banks = 0;
	do {
		cpuid_count(SGX_CPUID, sgx_nr_epc_banks + 2,
				&eax, &ebx, &ecx, &edx);
		if (eax & 0xf) {
			sgx_epc_banks[sgx_nr_epc_banks].start =
				(((u64) (ebx & 0xfffff)) << 32) +
				(u64) (eax & 0xfffff000);
			size = (((u64) (edx & 0xfffff)) << 32) +
				(u64) (ecx & 0xfffff000);
			sgx_epc_banks[sgx_nr_epc_banks].end =
				sgx_epc_banks[sgx_nr_epc_banks].start + size;
			if (!sgx_epc_banks[sgx_nr_epc_banks].start)
				return -ENODEV;
			sgx_nr_epc_banks++;
		} else {
			break;
		}
	} while (sgx_nr_epc_banks < SGX_MAX_EPC_BANKS);

	/* There should be at least one EPC area or something is wrong. */
	if (!sgx_nr_epc_banks) {
		WARN_ON(1);
		return 1;
	}

	return 0;
}

static int sgx_pm_suspend(struct device *dev)
{
	struct sgx_tgid_ctx *ctx;
	struct sgx_encl *encl;

	kthread_stop(ksgxswapd_tsk);
	ksgxswapd_tsk = NULL;

	list_for_each_entry(ctx, &sgx_tgid_ctx_list, list) {
		list_for_each_entry(encl, &ctx->encl_list, encl_list) {
			sgx_invalidate(encl, false);
			encl->flags |= SGX_ENCL_SUSPEND;
			flush_work(&encl->add_page_work);
		}
	}

	return 0;
}

static int sgx_pm_resume(struct device *dev)
{
	ksgxswapd_tsk = kthread_run(ksgxswapd, NULL, "kswapd");
	return 0;
}

static SIMPLE_DEV_PM_OPS(sgx_drv_pm, sgx_pm_suspend, sgx_pm_resume);

static int sgx_dev_init(struct device *dev)
{
	unsigned int wq_flags;
	int ret;
	int i;

	pr_info("intel_sgx: " DRV_DESCRIPTION " v" DRV_VERSION "\n");

	if (boot_cpu_data.x86_vendor != X86_VENDOR_INTEL)
		return -ENODEV;

	ret = sgx_init_platform();
	if (ret)
		return ret;

	pr_info("intel_sgx: Number of EPCs %d\n", sgx_nr_epc_banks);

	for (i = 0; i < sgx_nr_epc_banks; i++) {
		pr_info("intel_sgx: EPC memory range 0x%lx-0x%lx\n",
			sgx_epc_banks[i].start, sgx_epc_banks[i].end);
#ifdef CONFIG_X86_64
		sgx_epc_banks[i].mem = ioremap_cache(sgx_epc_banks[i].start,
			sgx_epc_banks[i].end - sgx_epc_banks[i].start);
		if (!sgx_epc_banks[i].mem) {
			sgx_nr_epc_banks = i;
			ret = -ENOMEM;
			goto out_iounmap;
		}
#endif
		ret = sgx_page_cache_init(sgx_epc_banks[i].start,
			sgx_epc_banks[i].end - sgx_epc_banks[i].start);
		if (ret) {
			sgx_nr_epc_banks = i+1;
			goto out_iounmap;
		}
	}

	wq_flags = WQ_UNBOUND | WQ_FREEZABLE;
#ifdef WQ_NON_REENETRANT
	wq_flags |= WQ_NON_REENTRANT;
#endif
	sgx_add_page_wq = alloc_workqueue("intel_sgx-add-page-wq", wq_flags, 1);
	if (!sgx_add_page_wq) {
		pr_err("intel_sgx: alloc_workqueue() failed\n");
		ret = -ENOMEM;
		goto out_iounmap;
	}

	sgx_dev.parent = dev;
	ret = misc_register(&sgx_dev);
	if (ret) {
		pr_err("intel_sgx: misc_register() failed\n");
		goto out_workqueue;
	}

	return 0;
out_workqueue:
	destroy_workqueue(sgx_add_page_wq);
out_iounmap:
#ifdef CONFIG_X86_64
	for (i = 0; i < sgx_nr_epc_banks; i++)
		iounmap(sgx_epc_banks[i].mem);
#endif
	return ret;
}

static atomic_t sgx_init_flag = ATOMIC_INIT(0);

static int sgx_drv_probe(struct platform_device *pdev)
{
	unsigned int eax, ebx, ecx, edx;
	int i;

	if (boot_cpu_data.x86_vendor != X86_VENDOR_INTEL)
		return -ENODEV;

	if (atomic_cmpxchg(&sgx_init_flag, 0, 1)) {
		pr_warn("intel_sgx: second initialization call skipped\n");
		return 0;
	}

	cpuid(0, &eax, &ebx, &ecx, &edx);
	if (eax < SGX_CPUID) {
		pr_err("intel_sgx: CPUID is missing the SGX leaf instruction\n");
		return -ENODEV;
	}

	if (!boot_cpu_has(X86_FEATURE_SGX)) {
		pr_err("intel_sgx: CPU is missing the SGX feature\n");
		return -ENODEV;
	}

	cpuid_count(SGX_CPUID, 0x0, &eax, &ebx, &ecx, &edx);
	if (!(eax & 1)) {
		pr_err("intel_sgx: CPU does not support the SGX 1.0 instruction set\n");
		return -ENODEV;
	}

	sgx_has_sgx2 = (eax & 2) != 0;

	if (boot_cpu_has(X86_FEATURE_OSXSAVE)) {
		cpuid_count(SGX_CPUID, 0x1, &eax, &ebx, &ecx, &edx);
		sgx_xfrm_mask = (((u64)edx) << 32) + (u64)ecx;
		for (i = 2; i < 64; i++) {
			cpuid_count(0x0D, i, &eax, &ebx, &ecx, &edx);
			if ((1 << i) & sgx_xfrm_mask)
				sgx_ssaframesize_tbl[i] =
					(168 + eax + ebx + PAGE_SIZE - 1) /
					PAGE_SIZE;
		}
	}

	cpuid_count(SGX_CPUID, 0x0, &eax, &ebx, &ecx, &edx);
	if (edx & 0xFFFF) {
#ifdef CONFIG_X86_64
		sgx_encl_size_max_64 = 2ULL << (edx & 0xFF);
#endif
		sgx_encl_size_max_32 = 2ULL << ((edx >> 8) & 0xFF);
	}

	return sgx_dev_init(&pdev->dev);
}

static int sgx_drv_remove(struct platform_device *pdev)
{
	int i;

	if (!atomic_cmpxchg(&sgx_init_flag, 1, 0)) {
		pr_warn("intel_sgx: second release call skipped\n");
		return 0;
	}

	misc_deregister(&sgx_dev);
	destroy_workqueue(sgx_add_page_wq);
#ifdef CONFIG_X86_64
	for (i = 0; i < sgx_nr_epc_banks; i++)
		iounmap(sgx_epc_banks[i].mem);
#endif
	sgx_page_cache_teardown();

	return 0;
}

#ifdef CONFIG_ACPI
static struct acpi_device_id sgx_device_ids[] = {
	{"INT0E0C", 0},
	{"", 0},
};
MODULE_DEVICE_TABLE(acpi, sgx_device_ids);
#endif

static struct platform_driver sgx_drv = {
	.probe = sgx_drv_probe,
	.remove = sgx_drv_remove,
	.driver = {
		.name			= "intel_sgx",
		.pm			= &sgx_drv_pm,
		.acpi_match_table	= ACPI_PTR(sgx_device_ids),
	},
};

#if (LINUX_VERSION_CODE >= KERNEL_VERSION(3, 17, 0))
module_platform_driver(sgx_drv);
#else
static struct platform_device *pdev;
int init_sgx_module(void)
{
	platform_driver_register(&sgx_drv);
	pdev = platform_device_register_simple("intel_sgx", 0, NULL, 0);
	if (IS_ERR(pdev))
		pr_err("platform_device_register_simple failed\n");
	return 0;
}

void cleanup_sgx_module(void)
{
	dev_set_uevent_suppress(&pdev->dev, true);
	platform_device_unregister(pdev);
	platform_driver_unregister(&sgx_drv);
}

module_init(init_sgx_module);
module_exit(cleanup_sgx_module);
#endif

MODULE_LICENSE("Dual BSD/GPL");
