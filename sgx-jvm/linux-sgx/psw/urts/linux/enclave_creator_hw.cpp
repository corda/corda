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

  
#include "enclave.h"
#include "enclave_creator_hw.h"
#include "se_trace.h"
#include "se_page_attr.h"
#include "isgx_user.h"
#include "sig_handler.h"
#include "se_error_internal.h"
#include "se_memcpy.h"
#include "se_atomic.h"
#include "se_detect.h"
#include "cpuid.h"
#include "rts.h"
#include <assert.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <sys/mman.h>
#include <stdlib.h>
 
#define SGX_CPUID   0x12

static EnclaveCreatorHW g_enclave_creator_hw;

EnclaveCreator* g_enclave_creator = &g_enclave_creator_hw;
static uint64_t g_eid = 0x1;


EnclaveCreatorHW::EnclaveCreatorHW():
    m_hdevice(-1),
    m_sig_registered(false)
{
    se_mutex_init(&m_sig_mutex);
}

EnclaveCreatorHW::~EnclaveCreatorHW()
{
    close_se_device();
}

int EnclaveCreatorHW::error_driver2urts(int driver_error)
{
    int ret = SGX_ERROR_UNEXPECTED;

    switch(driver_error)
    {
     case SGX_INVALID_ATTRIBUTE:
         ret = SGX_ERROR_INVALID_ATTRIBUTE;
         break;
     case SGX_INVALID_MEASUREMENT:
         ret = SE_ERROR_INVALID_MEASUREMENT;
         break;
     case SGX_INVALID_SIG_STRUCT:
     case SGX_INVALID_SIGNATURE:
         ret = SGX_ERROR_INVALID_SIGNATURE;
         break;
     case SGX_INVALID_LICENSE:
         ret = SE_ERROR_INVALID_LAUNCH_TOKEN;
         break;
     case SGX_INVALID_CPUSVN:
         ret = SGX_ERROR_INVALID_CPUSVN;
         break;
     case SGX_INVALID_ISVSVN:
         ret = SGX_ERROR_INVALID_ISVSVN;
         break;
     case SGX_UNMASKED_EVENT:
         ret = SGX_ERROR_DEVICE_BUSY;
         break;
     case (int)SGX_POWER_LOST_ENCLAVE: // [-Wc++11-narrowing]
         ret = SGX_ERROR_ENCLAVE_LOST;
         break;
     default:
         SE_TRACE(SE_TRACE_WARNING, "unexpected error %#x from driver, should be uRTS/driver bug\n", driver_error);
         ret = SGX_ERROR_UNEXPECTED;
         break;
     }
 
     return ret;
 }
 
int EnclaveCreatorHW::create_enclave(secs_t *secs, sgx_enclave_id_t *enclave_id, void **start_addr, bool ae)
{
    assert(secs != NULL && enclave_id != NULL && start_addr != NULL);
    UNUSED(ae);

    if (false == open_se_device())
        return SGX_ERROR_NO_DEVICE;

    SE_TRACE(SE_TRACE_DEBUG, "\n secs.attibutes.flags = %llx, secs.attributes.xfrm = %llx \n"
            , secs->attributes.flags, secs->attributes.xfrm);

    //SECS:BASEADDR must be naturally aligned on an SECS.SIZE boundary
    //This alignment is guaranteed by driver
    void* enclave_base = mmap(NULL, (size_t)secs->size, PROT_NONE, MAP_SHARED, m_hdevice, 0);
    if(enclave_base == MAP_FAILED)
    {
        SE_TRACE(SE_TRACE_WARNING, "\ncreate enclave: mmap failed, errno = %d\n", errno);
        return SGX_ERROR_OUT_OF_MEMORY;
    }

    secs->base = enclave_base;
  
    struct sgx_enclave_create param = {0};
    param.src = reinterpret_cast<__u64>(secs);
    int ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_CREATE, &param);
    if(ret) 
    {
        SE_TRACE(SE_TRACE_WARNING, "\nSGX_IOC_ENCLAVE_CREATE failed: errno = %d\n", errno);
        return error_driver2urts(ret);
    }
    *enclave_id = se_atomic_inc64(&g_eid);
    *start_addr = secs->base;

    return SGX_SUCCESS;
}
 
int EnclaveCreatorHW::add_enclave_page(sgx_enclave_id_t enclave_id, void *src, uint64_t rva, const sec_info_t &sinfo, uint32_t attr)
{
    assert((rva & ((1<<SE_PAGE_SHIFT)-1)) == 0);
    void* source = src;
    uint8_t color_page[SE_PAGE_SIZE] = { 0 };
    if(NULL == source)
    {
        memset(color_page, 0, SE_PAGE_SIZE);
        source = reinterpret_cast<void*>(&color_page);
    }

    int ret = 0;
    struct sgx_enclave_add_page addp = { 0, 0, 0, 0 };

    addp.addr = enclave_id + rva;
    addp.src = reinterpret_cast<__u64>(source);
    addp.secinfo = reinterpret_cast<__u64>(const_cast<sec_info_t*>(&sinfo));
    if(((1<<DoEEXTEND) & attr))
    addp.mrmask |= 0xFFFF;
    ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_ADD_PAGE, &addp);
    if(ret) {
        SE_TRACE(SE_TRACE_WARNING, "\nAdd Page - %p to %p... FAIL\n", source, rva);
        return error_driver2urts(ret);
    }

    return SGX_SUCCESS;
}
 
int EnclaveCreatorHW::try_init_enclave(sgx_enclave_id_t enclave_id, enclave_css_t *enclave_css, token_t *launch)
{
    int ret = 0;
    struct sgx_enclave_init initp = { 0, 0, 0 };
    initp.addr = enclave_id;
    initp.sigstruct = reinterpret_cast<__u64>(enclave_css);
    //license should NOT be NULL, because it has been checked in urts_com.h::_create_enclave(...)
    assert(launch != NULL);

    initp.einittoken = reinterpret_cast<__u64>(launch);
    ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_INIT, &initp);
    if (ret) {
        SE_TRACE(SE_TRACE_WARNING, "\nSGX_IOC_ENCLAVE_INIT failed error = %d\n", ret);
        return error_driver2urts(ret);
    }

    //register signal handler
    se_mutex_lock(&m_sig_mutex);
    if(false == m_sig_registered)
    {
        reg_sig_handler();
        m_sig_registered = true;
    }
    se_mutex_unlock(&m_sig_mutex);

    return SGX_SUCCESS;
}
 
int EnclaveCreatorHW::destroy_enclave(sgx_enclave_id_t enclave_id, uint64_t enclave_size)
{
    int ret = SGX_SUCCESS;

    ret = munmap((void*)enclave_id, (size_t)enclave_size);

    if (0 != ret) {
        SE_TRACE(SE_TRACE_WARNING, "destroy SGX enclave failed, error = %d\n", errno);
        ret = SGX_ERROR_UNEXPECTED;
    }

    return ret;
}
 
bool EnclaveCreatorHW::get_plat_cap(sgx_misc_attribute_t *misc_attr)
{
    // need to update code to support HyperV ECO
    return get_plat_cap_by_cpuid(misc_attr);
}
 
bool EnclaveCreatorHW::open_se_device()
{
    LockGuard lock(&m_dev_mutex);
    int fd = -1;

    if(-1 != m_hdevice)
    {
        return true;
    }

    fd = open("/dev/isgx", O_RDWR);
    if (-1 == fd) {
        SE_TRACE(SE_TRACE_WARNING, "open isgx device failed\n");
        return false;
    }

    m_hdevice = fd;

    return true;
}
 
void EnclaveCreatorHW::close_se_device()
{
    LockGuard lock(&m_dev_mutex);

    if (m_hdevice != -1)
    {
        close(m_hdevice);
        m_hdevice = -1;
    }
}

int EnclaveCreatorHW::emodpr(uint64_t addr, uint64_t size, uint64_t flag)
{
    sgx_modification_param params;
    memset(&params, 0 ,sizeof(sgx_modification_param));
    params.range.start_addr = (unsigned long)addr;
    params.range.nr_pages = (unsigned int)(size/SE_PAGE_SIZE);
    params.flags = (unsigned long)flag;

    int ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_EMODPR, &params);
    if (ret)
    {
        SE_TRACE(SE_TRACE_ERROR, "SGX_IOC_ENCLAVE_EMODPR failed %d\n", errno);
        return error_driver2urts(ret);
    }

    return SGX_SUCCESS;
}
 
int EnclaveCreatorHW::mktcs(uint64_t tcs_addr)
{
    sgx_range params;
    memset(&params, 0 ,sizeof(sgx_range));
    params.start_addr = (unsigned long)tcs_addr;
    params.nr_pages = 1;

    int ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_MKTCS, &params);
    if (ret)
    {
        SE_TRACE(SE_TRACE_ERROR, "MODIFY_TYPE failed %d\n", errno);
        return error_driver2urts(ret);
    }

    return SGX_SUCCESS;
}
 
int EnclaveCreatorHW::trim_range(uint64_t fromaddr, uint64_t toaddr)
{
    sgx_range params;
    memset(&params, 0 ,sizeof(sgx_range));
    params.start_addr = (unsigned long)fromaddr;
    params.nr_pages = (unsigned int)((toaddr - fromaddr)/SE_PAGE_SIZE);

    int ret= ioctl(m_hdevice, SGX_IOC_ENCLAVE_TRIM, &params);
    if (ret)
    {
        SE_TRACE(SE_TRACE_ERROR, "SGX_IOC_ENCLAVE_TRIM failed %d\n", errno);
        return error_driver2urts(ret);
    }

    return SGX_SUCCESS;

}
 
int EnclaveCreatorHW::trim_accept(uint64_t addr)
{
    sgx_range params;
    memset(&params, 0 ,sizeof(sgx_range));
    params.start_addr = (unsigned long)addr;
    params.nr_pages = 1;

    int ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_NOTIFY_ACCEPT, &params);


    if (ret)
    {
        SE_TRACE(SE_TRACE_ERROR, "TRIM_RANGE_COMMIT failed %d\n", errno);
        return error_driver2urts(ret);
    }

    return SGX_SUCCESS;
}
 
int EnclaveCreatorHW::remove_range(uint64_t fromaddr, uint64_t numpages)
{
    int ret = -1;
    uint64_t i;
    unsigned long start;

    for (i = 0; i < numpages; i++)
    {
        start = (unsigned long)fromaddr + (unsigned long)(i << SE_PAGE_SHIFT);
        ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_PAGE_REMOVE, &start);
        if (ret)
        {
            SE_TRACE(SE_TRACE_ERROR, "PAGE_REMOVE failed %d\n", errno);
            return error_driver2urts(ret);
        }
    }

    return SGX_SUCCESS;
}
 
//EDMM is supported if and only if all of the following requirements are met:
//1. We operate in HW mode
//2. CPU has EDMM support
//3. Driver has EDMM support
//4. Both the uRTS version and enclave (metadata) version are higher than 1.5
bool EnclaveCreatorHW::is_EDMM_supported(sgx_enclave_id_t enclave_id)
{
    bool supported = false, driver_supported = false, cpu_edmm = false;

    CEnclave *enclave = CEnclavePool::instance()->get_enclave(enclave_id);
    if (enclave == NULL)
        return false;

    cpu_edmm = is_cpu_edmm();
    driver_supported = is_driver_compatible();

    //return value of get_enclave_version() considers the version of uRTS and enclave metadata
    supported = use_se_hw() && cpu_edmm && driver_supported && (enclave->get_enclave_version() >= SDK_VERSION_2_0);

    return supported;
}

bool EnclaveCreatorHW::is_cpu_edmm() const
{
    bool cpu_edmm = false;
    int a[4] = {0,0,0,0};

    //Check CPU EDMM capability by CPUID
    __cpuid(a, 0);
    if (a[0] < SGX_CPUID)
        return false;

    __cpuidex(a, SGX_CPUID, 0);
    if (!(a[0] & 1))
        return false;

    cpu_edmm = (a[0] & 2) != 0;
    return cpu_edmm;
}

bool EnclaveCreatorHW::is_driver_compatible()
{
    static bool ret = driver_support_edmm();
    return ret;
}

bool EnclaveCreatorHW::driver_support_edmm()
{
    int ret;
    sgx_modification_param p;
    p.flags = 0;
    p.range.start_addr = 0;
    p.range.nr_pages = 0;

    if (false == open_se_device())
    {
        return false;
    }

    ret = ioctl(m_hdevice, SGX_IOC_ENCLAVE_EMODPR, &p);
    if ((ret == -1) && (errno == ENOTTY))
    {
        return false;
    }
    else
    {
        return true;
    }
}
