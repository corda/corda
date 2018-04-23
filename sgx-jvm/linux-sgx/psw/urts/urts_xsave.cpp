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


#include "xsave.h"
#include "util.h"
#include "se_detect.h"
#include "cpuid.h"


#define XFRM_YMM_BITMASK   0x00000004

uint32_t g_xsave_enabled = 0;

extern "C" void set_xsave_info(int xsave_size, int flag);










// init_xsave_info
void init_xsave_info()
{
    int xsave_size = FXSAVE_SIZE; 
    uint64_t xcr0 = 0;
    if(try_read_xcr0(&xcr0))
    {
        // CPUID function 0DH, sub-function 0 
        // EBX enums the size (in bytes) required by XSAVE for all the components currently set in XCR0
        int cpu_info[4] = {0};
        __cpuid(cpu_info, 0xD);
        xsave_size = cpu_info[1];
        g_xsave_enabled = 1;
    }
    set_xsave_info(xsave_size, (xcr0 & XFRM_YMM_BITMASK) ? 1 : 0);
}

