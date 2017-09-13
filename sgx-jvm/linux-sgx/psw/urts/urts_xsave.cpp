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
#include "sgx_attributes.h"

#define XFRM_YMM_BITMASK   0x00000004

// save_and_clean_xfeature_regs()
//      do fwait, fxsave, and then clear the upper bits of YMM regs before executing EENTER.
// Parameters:
//      buffer - if the pointer is not NULL, save the CPU state to the buffer
// Return Value:
//      none








void save_and_clean_xfeature_regs(uint8_t *buffer)
{
    // XCR0 is not supposed to be changed. So query it once.
    static uint64_t xcr0 = 0;
    if(xcr0 == 0)
    {
        if(false == try_read_xcr0(&xcr0))
        {
            xcr0 = SGX_XFRM_LEGACY;
        }
    }

    // do fwait to flush the float-point exceptions
    do_fwait();

    // do fxsave to save the CPU state before ecall
    // no need to save the CPU state before oret
    if(buffer != 0)
    {
        uint8_t *buf = (uint8_t*)ROUND_TO((size_t)buffer, FXSAVE_ALIGN_SIZE);
        do_fxsave(buf);
    }

    // clean the upper bits of the YMM regs
    if(xcr0 & XFRM_YMM_BITMASK)
    {
        do_vzeroupper();
    }
}

void restore_xfeature_regs(const uint8_t *buffer)
{
    if (buffer)
    {
        uint8_t *buf = (uint8_t*)ROUND_TO((size_t)buffer, FXSAVE_ALIGN_SIZE);
        do_fxrstor(buf);
    }
}
