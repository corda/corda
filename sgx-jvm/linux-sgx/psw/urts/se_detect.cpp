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


#include "se_detect.h"
#include "cpuid.h"

bool is_se_supported()
{
    int cpu_info[4] = {0, 0, 0, 0};
    __cpuidex(cpu_info, CPUID_FEATURE_FLAGS, 0);
    if (!(cpu_info[1] & (1<<SE_FEATURE_SHIFT)))
    {
        return false;
    }
    __cpuidex(cpu_info, SE_LEAF, 0);
    if(!(cpu_info[0] & (1 << SE1_SHIFT)))
        return false;
    return true;
}

#include "read_xcr0.h"
bool try_read_xcr0(uint64_t *value)
{
    // set to default value
    *value = SGX_XFRM_LEGACY;

    //check if xgetbv instruction is supported
    int cpu_info[4] = {0, 0, 0, 0};
    __cpuid(cpu_info, 1);
    if(!(cpu_info[2] & (1<<XSAVE_SHIFT)) || !(cpu_info[2] & (1<<OSXSAVE_SHIFT))) //ecx[27:26] indicate whether supoort xsave/xrstor, and whether enable xgetbv, xsetbv
        return false;
    *value = read_xcr0();

    // check if xsavec is supported
    // Assume that XSAVEC is always supported if XSAVE is supported
    cpu_info[0] = cpu_info[1] = cpu_info[2] = cpu_info[3] = 0;
    __cpuidex(cpu_info, 0xD, 1);
    if(!(cpu_info[0] & (1<<XSAVEC_SHIFT)))
        return false;

    return true;
}

bool get_plat_cap_by_cpuid(sgx_misc_attribute_t *se_misc_attr)
{
    int cpu_info[4] = {0, 0, 0, 0};

    if(!is_se_supported())
        return false;
    __cpuidex(cpu_info, SE_LEAF, 1);
    //enclave capability
    se_misc_attr->secs_attr.flags = ((uint64_t)cpu_info[1] << 32) | cpu_info[0];

    if(false == try_read_xcr0(&se_misc_attr->secs_attr.xfrm))
    {
        // if XSAVE is supported, while XSAVEC is not supported,
        // set secs_attr.xfrm to legacy, because XSAVEC cannot be executed within enclave.
        se_misc_attr->secs_attr.xfrm = SGX_XFRM_LEGACY;
    }
    //If x-feature is supported and enabled by OS, we need make sure it is also supported in se.
    else
    {
        se_misc_attr->secs_attr.xfrm &= (((uint64_t)cpu_info[3] << 32) | cpu_info[2]);
    }
    // use cpuid to get the misc_select
    __cpuidex(cpu_info, SE_LEAF, 0);
    se_misc_attr->misc_select = cpu_info[1];

    return true;
}


