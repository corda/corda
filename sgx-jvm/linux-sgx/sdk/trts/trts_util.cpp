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


#include "trts_util.h"
#include "global_data.h"
#include "util.h"
#include "thread_data.h"

// No need to check the state of enclave or thread.
// The functions should be called within an ECALL, so the enclave and thread must be initialized at that time.
void * get_heap_base(void)
{
    return GET_PTR(void, &__ImageBase, g_global_data.heap_offset);
}

size_t get_heap_size(void)
{
    return g_global_data.heap_size;
}

int * get_errno_addr(void)
{
    thread_data_t *thread_data = get_thread_data();
    return reinterpret_cast<int *>(&thread_data->last_error);
}

bool is_stack_addr(void *address, size_t size)
{
    thread_data_t *thread_data = get_thread_data();
    size_t stack_base = thread_data->stack_base_addr;
    size_t stack_limit  = thread_data->stack_limit_addr;
    size_t addr = (size_t) address;
    return (addr <= (addr + size)) && (stack_base >= (addr + size)) && (stack_limit <= addr);
}

bool is_valid_sp(uintptr_t sp)
{
    return ( !(sp & (sizeof(uintptr_t) - 1))   // sp is expected to be 4/8 bytes aligned
           && is_stack_addr((void*)sp, 0) );   // sp points to the top/bottom of stack are accepted
}


