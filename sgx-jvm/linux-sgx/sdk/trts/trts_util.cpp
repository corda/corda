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
    size_t heap_size = g_global_data.heap_size;
    if (EDMM_supported)
    {
        for(uint32_t i = 0; i < g_global_data.layout_entry_num; i++)
        {
            if(g_global_data.layout_table[i].entry.id == LAYOUT_ID_HEAP_MAX)
            {
                heap_size += ((size_t)g_global_data.layout_table[i].entry.page_count << SE_PAGE_SHIFT);
            }
        }
    }
    return heap_size;
}

size_t get_heap_min_size(void)
{
    size_t heap_size = 0;
    for(uint32_t i = 0; i < g_global_data.layout_entry_num; i++)
    {
        if(g_global_data.layout_table[i].entry.id == LAYOUT_ID_HEAP_MIN)
        {
            heap_size = ((size_t)g_global_data.layout_table[i].entry.page_count << SE_PAGE_SHIFT);
            break;
        }
    }
    return heap_size;
}

int * get_errno_addr(void)
{
    thread_data_t *thread_data = get_thread_data();
    return reinterpret_cast<int *>(&thread_data->last_error);
}

//tRTS will receive a pointer to an array of uint64_t which indicates the
//features of the running system. This function can be used to query whether
//a certain feature (such as EDMM) is supported.
//It takes as input the pointer to the array and the feature bit location.
//The feature array coming from uRTS should be dealt with in the following way:
//Every bit except the MSb in each uint64 represents a certain feature.
//The MSb of each uint64_t, if set, indicates this is the last uint64_t to
//search for the feature's existance.
//For example, if we have two uint64_t elements in the array:
//array[0]: xxxxxxxxxxxxxxxx array[1] Xxxxxxxxxxxxxxxx
//MSb of array[1] should already be set to one by uRTS. Shown by capital 'X' here.
//Features listed in array[0], counting from right-most bit  to left-most bit,
//have feature shift values 0 ~ 62, while features listed in array[1], have feature
//shift values 64 ~ 126.

int feature_supported(const uint64_t *feature_set, uint32_t feature_shift)
{
    const uint64_t *f_set = feature_set;
    uint32_t bit_position = 0, i = 0;

    if (!f_set)
        return 0;

    while (((i+1) << 6) <= feature_shift)
    {
        if (f_set[i] & (0x1ULL << 63))
            return 0;
        i++;
    }
    bit_position = feature_shift - (i << 6);
    if (f_set[i] & (0x1ULL << bit_position))
        return 1;
    else
        return 0;
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

