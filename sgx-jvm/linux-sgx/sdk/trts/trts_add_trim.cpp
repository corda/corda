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


#include <string.h>
#include "sgx_utils.h"
#include "trts_inst.h"
#include "util.h"
#include "trts_trim.h"
#include "trts_util.h"
#include "global_data.h"
#include "se_memcpy.h"
#include "se_page_attr.h"
#include "trts_internal.h"

#ifndef SE_SIM

struct dynamic_flags_attributes
{
    si_flags_t si_flags;
    uint16_t    attributes;
};

// Low level API to EACCEPT pages on grow-up region.
static int sgx_accept_backward(si_flags_t sfl, size_t lo, size_t hi)
{
    size_t addr = hi;
    SE_DECLSPEC_ALIGN(sizeof(sec_info_t)) sec_info_t si;
    si.flags = sfl;
    for (uint16_t i = 0; i < (sizeof(si.reserved)/sizeof(si.reserved[0])); i++)
        si.reserved[i] = 0;

    while (lo < addr)
    {
        int rc = do_eaccept(&si, addr -= SE_PAGE_SIZE);
        if (rc != 0)
            abort();
    }
    return 0;
}

// Low level API to EACCEPT pages on grow-up region during exception handling.
static int sgx_accept_forward_within_exception(size_t lo, size_t hi)
{
    size_t addr = lo;
    SE_DECLSPEC_ALIGN(sizeof(sec_info_t)) sec_info_t si;

#ifdef DEBUG
    unsigned int sp_value = 0;
    asm("mov %%esp, %0;" : "=r" (sp_value) :);
    if ((sp_value & (SE_PAGE_SIZE -1)) <= (SE_PAGE_SIZE - (STATIC_STACK_SIZE % SE_PAGE_SIZE)))
        return SGX_ERROR_UNEXPECTED;
#endif

    si.flags = SI_FLAGS_RW | SI_FLAG_PENDING;
    for (uint16_t i = 0; i < (sizeof(si.reserved)/sizeof(si.reserved[0])); i++)
        si.reserved[i] = 0;

    while (addr < hi)
    {
        int rc = do_eaccept(&si, addr);
        if (rc != 0)
            abort();
        addr += SE_PAGE_SIZE;
    }

    return 0;
}

const volatile layout_t *get_dynamic_layout_by_id(uint16_t id)
{
    for(uint32_t i = 0; i < g_global_data.layout_entry_num; i++)
    {
        if(g_global_data.layout_table[i].entry.id == id)
        {
            return &(g_global_data.layout_table[i]);
        }
    }
    return NULL;
}

// EACCEPT trim requests when the enclave completes initialization.
int accept_post_remove(const volatile layout_t *layout_start, const volatile layout_t *layout_end, size_t offset)
{
    int ret = -1;
    for (const volatile layout_t *layout = layout_start; layout < layout_end; layout++)
    {
        if (!IS_GROUP_ID(layout->group.id) && (layout->entry.attributes & PAGE_ATTR_POST_REMOVE))
        {
            size_t start_addr = (size_t)layout->entry.rva + offset + (size_t)get_enclave_base();
            uint32_t page_count = layout->entry.page_count;

            if (0 != (ret = sgx_accept_forward(SI_FLAG_TRIM | SI_FLAG_MODIFIED, start_addr, start_addr + ((size_t)page_count << SE_PAGE_SHIFT))))
                return ret;
        }
        else if (IS_GROUP_ID(layout->group.id))
        {
            size_t step = 0;
            for(uint32_t j = 0; j < layout->group.load_times; j++)
            {
                step += (size_t)layout->group.load_step;
                if(0 != (ret = accept_post_remove(&layout[-layout->group.entry_count], layout, step)))
                    return ret;
            }
        }
    }
    return 0;
}

static int check_dynamic_entry_range(void *addr, size_t page_count, uint16_t entry_id, size_t entry_offset, struct dynamic_flags_attributes *fa)
{
    const volatile layout_t *layout = NULL, *heap_max_layout;
    size_t entry_start_addr;
    uint32_t entry_page_count;

    if (entry_id < LAYOUT_ID_HEAP_MIN
            || entry_id > LAYOUT_ID_STACK_DYN_MIN
            || (NULL == (layout = get_dynamic_layout_by_id(entry_id))))
    {
        return -1;
    }

    entry_start_addr = (size_t)get_enclave_base() + (size_t)layout->entry.rva + entry_offset;
    entry_page_count = layout->entry.page_count;

    // if there exists LAYOUT_ID_HEAP_MAX, we should include it as well
    if ((entry_id == LAYOUT_ID_HEAP_INIT)
        && (heap_max_layout = get_dynamic_layout_by_id(LAYOUT_ID_HEAP_MAX)))
    {
        entry_page_count += heap_max_layout->entry.page_count;
    }

    if ((size_t)addr >= entry_start_addr
            && (size_t)addr + (page_count << SE_PAGE_SHIFT) <= entry_start_addr + ((size_t)entry_page_count << SE_PAGE_SHIFT))
    {
        if (fa != NULL)
        {
            fa->si_flags = layout->entry.si_flags;
            fa->attributes = layout->entry.attributes;
        }
        return 0;
    }
    else
    {
        return -1;
    }
}

// Verify if the range specified belongs to a dynamic range recorded in metadata.
static int check_dynamic_range(void *addr, size_t page_count, size_t *offset, struct dynamic_flags_attributes *fa)
{
    const volatile layout_t *dt_layout = NULL;

    // check heap range
    if (0 == check_dynamic_entry_range(addr, page_count, LAYOUT_ID_HEAP_INIT, 0, fa))
        return 0;
    
    // check dynamic thread entries range
    if (NULL != (dt_layout = get_dynamic_layout_by_id(LAYOUT_ID_THREAD_GROUP_DYN)))
    {
        for (uint16_t id = LAYOUT_ID_TCS_DYN; id <= LAYOUT_ID_STACK_DYN_MIN; id++)
            for (uint32_t i = 0; i < dt_layout->group.load_times + 1; i++)
            {
                if (0 == check_dynamic_entry_range(addr, page_count, id, i * ((size_t)dt_layout->group.load_step), fa))
                {
                    if (offset != NULL) *offset = i * ((size_t)dt_layout->group.load_step);
                    return 0;
                }
            }
    }
    else
    {
        // LAYOUT_ID_THREAD_GROUP_DYN does not exist, but possibly there is one single dynamic thead
        for (uint16_t id = LAYOUT_ID_TCS_DYN; id <= LAYOUT_ID_STACK_DYN_MIN; id++)
            if (0 == check_dynamic_entry_range(addr, page_count, id, 0, fa))
            {
                if (offset != NULL) *offset = 0;
                return 0;
            }
    }
    return -1;
}

int is_dynamic_thread(void *tcs)
{
    struct dynamic_flags_attributes fa;

    if ((tcs != NULL) && (check_dynamic_range(tcs, 1, NULL, &fa) == 0) &&
            (fa.si_flags == SI_FLAGS_TCS))
    {
        return true;
    }

    return false;
}

uint32_t get_dynamic_stack_max_page()
{
    const volatile layout_t * layout = get_dynamic_layout_by_id(LAYOUT_ID_STACK_DYN_MAX);
    if (!layout)
        return 0;
    else
        return layout->entry.page_count;
}
#endif

int sgx_accept_forward(si_flags_t sfl, size_t lo, size_t hi)
{
#ifdef SE_SIM
    (void)sfl;
    (void)lo;
    (void)hi;
    return 0;
#else
    size_t addr = lo;
    SE_DECLSPEC_ALIGN(sizeof(sec_info_t)) sec_info_t si;
    si.flags = sfl;
    for (uint16_t i = 0; i < (sizeof(si.reserved)/sizeof(si.reserved[0])); i++)
        si.reserved[i] = 0;

    while (addr < hi)
    {
        int rc = do_eaccept(&si, addr);
        if (rc != 0)
            abort();
        addr += SE_PAGE_SIZE;
    }

    return 0;
#endif
}

// High level API to EACCEPT pages, mainly used in exception handling
// to deal with stack expansion. 
int apply_pages_within_exception(void *start_address, size_t page_count)
{
#ifdef SE_SIM
    (void)start_address;
    (void)page_count;
    return 0;
#else
    int rc;

    if (start_address == NULL)
        return -1;
    
    if (check_dynamic_range(start_address, page_count, NULL, NULL) != 0)
        return -1;

    size_t start = (size_t)start_address;
    size_t end = start + (page_count << SE_PAGE_SHIFT);

    rc = sgx_accept_forward_within_exception(start, end);

    return rc;
#endif

}

// High level API to EACCEPT pages
int apply_EPC_pages(void *start_address, size_t page_count)
{
#ifdef SE_SIM
    (void)start_address;
    (void)page_count;
    return 0;
#else
    int rc;
    struct dynamic_flags_attributes fa;

    if (start_address == NULL)
        return -1;
    
    if (check_dynamic_range(start_address, page_count, NULL, &fa) != 0)
        return -1;

    size_t start = (size_t)start_address;
    size_t end = start + (page_count << SE_PAGE_SHIFT);

    if (fa.attributes & PAGE_DIR_GROW_DOWN)
    {
        rc = sgx_accept_forward(SI_FLAGS_RW | SI_FLAG_PENDING, start, end);
    }
    else
    {
        rc = sgx_accept_backward(SI_FLAGS_RW | SI_FLAG_PENDING, start, end);
    }

    return rc;
#endif
}

// High level API to trim previously EAUG-ed pages.
int trim_EPC_pages(void *start_address, size_t page_count)
{
#ifdef SE_SIM
    (void)start_address;
    (void)page_count;
    return 0;
#else
    int rc;

    if (start_address == NULL)
        return -1;

    // check range
    if (check_dynamic_range(start_address, page_count, NULL, NULL) != 0)
        return -1;

    size_t start = (size_t)start_address;
    size_t end = start + (page_count << SE_PAGE_SHIFT);

    // trim ocall
    rc = trim_range_ocall(start, end);
    assert(rc == 0);

    rc = sgx_accept_forward(SI_FLAG_TRIM | SI_FLAG_MODIFIED, start, end);
    assert(rc == 0);
    
    // trim commit ocall
    size_t i = start;
    while (i < end)
    {
        rc = trim_range_commit_ocall(i);
        assert(rc == 0);
        i += SE_PAGE_SIZE;
    }

    return rc;
#endif
}

// Create a thread dynamically.
// It will add necessary pages and transform one of them into type TCS.
sgx_status_t do_add_thread(void *ptcs)
{
#ifdef SE_SIM
    (void)ptcs;
    return SGX_SUCCESS;
#else
    int ret = SGX_ERROR_UNEXPECTED;
    tcs_t *tcs = (tcs_t *)ptcs;
    tcs_t *tcs_template = NULL;
    size_t offset = 0;
    size_t enclave_base = (size_t)get_enclave_base();

    if ( 0 != check_dynamic_range((void *)tcs, 1, &offset, NULL))
        return SGX_ERROR_UNEXPECTED;

    // check if the tcs provided exactly matches the one in signtool
    const volatile layout_t *tcs_layout = get_dynamic_layout_by_id(LAYOUT_ID_TCS_DYN);
    if (!tcs_layout)
        return SGX_ERROR_UNEXPECTED;

    if ((size_t)(enclave_base + tcs_layout->entry.rva + offset) != (size_t)(tcs))
        return SGX_ERROR_UNEXPECTED;

    // adding page for all the dynamic entries
    for (uint16_t id = LAYOUT_ID_TCS_DYN; id <= LAYOUT_ID_STACK_DYN_MIN; id++)
    {
        const volatile layout_t *layout =  get_dynamic_layout_by_id(id);
        if (layout && (layout->entry.attributes & PAGE_ATTR_DYN_THREAD))
        {
            ret = apply_EPC_pages((void *)(enclave_base + layout->entry.rva + offset), layout->entry.page_count);
            if (ret != 0)
                return SGX_ERROR_UNEXPECTED;
        }
    }

    //Copy and initialize TCS
    tcs_template = (tcs_t *)g_global_data.tcs_template;
    memcpy_s(tcs, TCS_SIZE, tcs_template, sizeof(g_global_data.tcs_template));

    //Adjust the tcs fields
    tcs->ossa = (size_t)GET_PTR(size_t, (void *)tcs, tcs->ossa) - enclave_base;
    tcs->ofs_base = (size_t)GET_PTR(size_t, (void *)tcs, tcs->ofs_base) - enclave_base;
    tcs->ogs_base = (size_t)GET_PTR(size_t, (void *)tcs, tcs->ogs_base) - enclave_base;

    //OCALL for MKTCS
    ret = sgx_ocall(0, tcs);
    if (ret != 0)
        return SGX_ERROR_UNEXPECTED;

    //EACCEPT for MKTCS
    ret = sgx_accept_backward(SI_FLAG_TCS | SI_FLAG_MODIFIED, (size_t)tcs, (size_t)tcs + SE_PAGE_SIZE);
    if (ret != 0)
        return SGX_ERROR_UNEXPECTED;

    return SGX_SUCCESS;

#endif
}

