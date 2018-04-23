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

#include "sgx_trts.h"
#include "sgx_edger8r.h"
#include "trts_inst.h"
#include <stdlib.h>
#include <string.h>
#include "util.h"
#include "thread_data.h"
#include "global_data.h"
#include "trts_internal.h"
#include "internal/rts.h"

#ifdef SE_SIM
#include "t_instructions.h"    /* for `g_global_data_sim' */
#include "sgx_spinlock.h"
#endif



#ifndef SE_SIM

#include "se_cdefs.h"

// add a version to trts
SGX_ACCESS_VERSION(trts, 1);

#endif

// sgx_is_within_enclave()
// Parameters:
//      addr - the start address of the buffer
//      size - the size of the buffer
// Return Value:
//      1 - the buffer is strictly within the enclave
//      0 - the whole buffer or part of the buffer is not within the enclave,
//          or the buffer is wrap around
//
int sgx_is_within_enclave(const void *addr, size_t size)
{
    size_t start = reinterpret_cast<size_t>(addr);
    size_t end = 0;
    size_t enclave_start = (size_t)&__ImageBase;
    size_t enclave_end = enclave_start + g_global_data.enclave_size - 1;
    // g_global_data.enclave_end = enclave_base + enclave_size - 1;
    // so the enclave range is [enclave_start, enclave_end] inclusively

    if(size > 0)
    {
        end = start + size - 1;
    }
    else
    {
        end = start;
    }
    if( (start <= end) && (start >= enclave_start) && (end <= enclave_end) )
    {
        return 1;
    }
    return 0;
}

// sgx_is_outside_enclave()
// Parameters:
//      addr - the start address of the buffer
//      size - the size of the buffer
// Return Value:
//      1 - the buffer is strictly outside the enclave
//      0 - the whole buffer or part of the buffer is not outside the enclave,
//          or the buffer is wrap around
//
int sgx_is_outside_enclave(const void *addr, size_t size)
{
    size_t start = reinterpret_cast<size_t>(addr);
    size_t end = 0;
    size_t enclave_start = (size_t)&__ImageBase;
    size_t enclave_end = enclave_start + g_global_data.enclave_size - 1;
    // g_global_data.enclave_end = enclave_base + enclave_size - 1;
    // so the enclave range is [enclave_start, enclave_end] inclusively

    if(size > 0)
    {
        end = start + size - 1;
    }
    else
    {
        end = start;
    }
    if( (start <= end) && ((end < enclave_start) || (start > enclave_end)) )
    {
        return 1;
    }
    return 0;
}

// sgx_ocalloc()
// Parameters:
//      size - bytes to allocate on the outside stack
// Return Value:
//      the pointer to the allocated space on the outside stack
//      NULL - fail to allocate
//
// sgx_ocalloc allocates memory on the outside stack. It is only used for OCALL, and will be auto freed when ECALL returns.
// To achieve this, the outside stack pointer in SSA is updated when the stack memory is allocated,
// but the outside stack pointer saved in the ECALL stack frame is not changed accordingly.
// When doing an OCALL, the stack pointer is set as the value in SSA and EEXIT.
// When ECALL or exception handling returns, the stack pointer is set as the value in the ECALL stack frame and then EEXIT,
// so the outside stack is automatically unwind.
// In addition, sgx_ocalloc needs perform outside stack probe to make sure it is not allocating beyond the end of the stack.
#define OC_ROUND 16
void * sgx_ocalloc(size_t size)
{
    // read the outside stack address from current SSA
    thread_data_t *thread_data = get_thread_data();
    ssa_gpr_t *ssa_gpr = reinterpret_cast<ssa_gpr_t *>(thread_data->first_ssa_gpr);
    size_t addr = ssa_gpr->REG(sp_u);

    // check u_rsp points to the untrusted address.
    // if the check fails, it should be hacked. call abort directly
    if(!sgx_is_outside_enclave(reinterpret_cast<void *>(addr), sizeof(size_t)))
    {
        abort();
    }

    // size is too large to allocate. call abort() directly.
    if(addr < size)
    {
        abort();
    }

    // calculate the start address for the allocated memory
    addr -= size;
    addr &= ~(static_cast<size_t>(OC_ROUND - 1));  // for stack alignment

    // the allocated memory has overlap with enclave, abort the enclave
    if(!sgx_is_outside_enclave(reinterpret_cast<void *>(addr), size))
    {
        abort();
    }

    // probe the outside stack to ensure that we do not skip over the stack3 guard page
    // we need to probe all the pages including the first page and the last page
    // the first page need to be probed in case uRTS didnot touch that page before EENTER enclave
    // the last page need to be probed in case the enclave didnot touch that page before another OCALLOC
    size_t first_page = TRIM_TO_PAGE(ssa_gpr->REG(sp_u) - 1);
    size_t last_page = TRIM_TO_PAGE(addr);

    // To avoid the dead-loop in the following for(...) loop.
    // Attacker might fake a stack address that is within address 0x4095.
    if (last_page == 0)
    {
        abort();
    }

    // the compiler may optimize the following code to probe the pages in any order
    // while we only expect the probe order should be from higher addr to lower addr
    // so use volatile to avoid optimization by the compiler
    for(volatile size_t page = first_page; page >= last_page; page -= SE_PAGE_SIZE)
    {
        // OS may refuse to commit a physical page if the page fault address is smaller than RSP
        // So update the outside stack address before probe the page
        ssa_gpr->REG(sp_u) = page;

        *reinterpret_cast<uint8_t *>(page) = 0;
    }

    // update the outside stack address in the SSA to the allocated address
    ssa_gpr->REG(sp_u) = addr;

    return reinterpret_cast<void *>(addr);
}

// sgx_ocfree()
// Parameters:
//      N/A
// Return Value:
//      N/A
// sgx_ocfree restores the original outside stack pointer in the SSA.
// Do not call this function if you still need the buffer allocated by sgx_ocalloc within the ECALL.
void sgx_ocfree()
{
    // ECALL stack frame
    //           last_sp -> |             |
    //                       -------------
    //                      | ret_addr    |
    //                      | xbp_u       |
    //                      | xsp_u       |

    thread_data_t *thread_data = get_thread_data();
    ssa_gpr_t *ssa_gpr = reinterpret_cast<ssa_gpr_t *>(thread_data->first_ssa_gpr);
    uintptr_t *addr = reinterpret_cast<uintptr_t *>(thread_data->last_sp);
    uintptr_t usp = *(addr - 3);
    if(!sgx_is_outside_enclave(reinterpret_cast<void *>(usp), sizeof(uintptr_t)))
    {
        abort();
    }
    ssa_gpr->REG(sp_u) = usp;
}

#ifdef SE_SIM
static sgx_spinlock_t g_seed_lock = SGX_SPINLOCK_INITIALIZER;

static uint32_t get_rand_lcg()
{
    sgx_spin_lock(&g_seed_lock);

    uint64_t& seed = g_global_data_sim.seed;
    seed = (uint64_t)(6364136223846793005ULL * seed + 1);
    uint32_t n = (uint32_t)(seed >> 32);

    sgx_spin_unlock(&g_seed_lock);

    return n;
}
#endif

static sgx_status_t  __do_get_rand32(uint32_t* rand_num)
{
#ifndef SE_SIM
    /* We expect the CPU has RDRAND support for HW mode. Otherwise, an exception will be thrown
    * do_rdrand() will try to call RDRAND for 10 times
    */
    if(0 == do_rdrand(rand_num))
        return SGX_ERROR_UNEXPECTED;
#else
    /*  use LCG in simulation mode */
    *rand_num = get_rand_lcg();
#endif
    return SGX_SUCCESS;
}

sgx_status_t sgx_read_rand(unsigned char *rand, size_t length_in_bytes)
{
    // check parameters
    //
    // rand can be within or outside the enclave
    if(!rand || !length_in_bytes)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if(!sgx_is_within_enclave(rand, length_in_bytes) && !sgx_is_outside_enclave(rand, length_in_bytes))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    // loop to rdrand
    uint32_t rand_num = 0;
    while(length_in_bytes > 0)
    {
        sgx_status_t status = __do_get_rand32(&rand_num);
        if(status != SGX_SUCCESS)
        {
            return status;
        }

        size_t size = (length_in_bytes < sizeof(rand_num)) ? length_in_bytes : sizeof(rand_num);
        memcpy(rand, &rand_num, size);

        rand += size;
        length_in_bytes -= size;
    }
    memset_s(&rand_num, sizeof(rand_num), 0, sizeof(rand_num));
    return SGX_SUCCESS;
}

extern uintptr_t __stack_chk_guard;
int check_static_stack_canary(void *tcs)
{
    size_t *canary = TCS2CANARY(tcs);
    if ( *canary != (size_t)__stack_chk_guard)
    {
        return -1;
    }
    return 0;
}

