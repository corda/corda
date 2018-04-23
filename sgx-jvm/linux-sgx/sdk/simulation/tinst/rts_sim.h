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


#ifndef _RTS_SIM_H_
#define _RTS_SIM_H_

#include "arch.h"

#ifdef __cplusplus
extern "C" {
#endif

const sgx_cpu_svn_t DEFAULT_CPUSVN = {
    {
        0x48, 0x20, 0xf3, 0x37, 0x6a, 0xe6, 0xb2, 0xf2, 
        0x03, 0x4d, 0x3b, 0x7a, 0x4b, 0x48, 0xa7, 0x78
    }
};
const sgx_cpu_svn_t UPGRADED_CPUSVN = {
    {
        0x53, 0x39, 0xae, 0x8c, 0x93, 0xae, 0x8f, 0x3c,
        0xe4, 0x32, 0xdb, 0x92, 0x4d, 0x0f, 0x07, 0x33
    }
};

const sgx_cpu_svn_t DOWNGRADED_CPUSVN = {
    {
        0x64, 0xea, 0x4f, 0x3f, 0xa0, 0x03, 0x0c, 0x36,
        0x38, 0x3c, 0x32, 0x2d, 0x4f, 0x3a, 0x8d, 0x4f
    }
};


typedef struct _global_data_sim_t
{
    secs_t* secs_ptr; 
    sgx_cpu_svn_t cpusvn_sim;
    uint64_t seed;      /* to initialize the PRNG */
} global_data_sim_t;

#ifdef __cplusplus
}
#endif

#endif
