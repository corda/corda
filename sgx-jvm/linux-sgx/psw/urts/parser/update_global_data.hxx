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

namespace {
    layout_entry_t *get_entry_by_id(const metadata_t *const metadata, uint16_t id)
    {
        layout_entry_t *layout_start = GET_PTR(layout_entry_t, metadata, metadata->dirs[DIR_LAYOUT].offset);
        layout_entry_t *layout_end = GET_PTR(layout_entry_t, metadata, metadata->dirs[DIR_LAYOUT].offset + metadata->dirs[DIR_LAYOUT].size);
        for (layout_entry_t *layout = layout_start; layout < layout_end; layout++)
        {
            if(layout->id == id)
                return layout;
        }
        assert(false);
        return NULL;
    }
    bool do_update_global_data(const metadata_t *const metadata,
                                const create_param_t* const create_param,
                               global_data_t* global_data)
    {
        layout_entry_t *layout_heap = get_entry_by_id(metadata, LAYOUT_ID_HEAP_MIN);

        global_data->enclave_size = (sys_word_t)metadata->enclave_size;
        global_data->heap_offset = (sys_word_t)layout_heap->rva;
        global_data->heap_size = (sys_word_t)(create_param->heap_init_size);
        global_data->thread_policy = (sys_word_t)metadata->tcs_policy;
        thread_data_t *thread_data = &global_data->td_template;

        thread_data->stack_limit_addr = (sys_word_t)create_param->stack_limit_addr;
        thread_data->stack_base_addr = (sys_word_t)create_param->stack_base_addr;
        thread_data->last_sp = thread_data->stack_base_addr;
        thread_data->xsave_size = create_param->xsave_size;
        thread_data->first_ssa_gpr = (sys_word_t)create_param->ssa_base_addr + metadata->ssa_frame_size * SE_PAGE_SIZE - (uint32_t)sizeof(ssa_gpr_t);
        // TD address relative to TCS
        thread_data->tls_addr = (sys_word_t)create_param->tls_addr;
        thread_data->self_addr = (sys_word_t)create_param->td_addr;
        thread_data->tls_array = thread_data->self_addr + (sys_word_t)offsetof(thread_data_t, tls_addr);

        // TCS template
        if(0 != memcpy_s(&global_data->tcs_template, sizeof(global_data->tcs_template), 
                          GET_PTR(void, metadata, get_entry_by_id(metadata, LAYOUT_ID_TCS)->content_offset), 
                          get_entry_by_id(metadata, LAYOUT_ID_TCS)->content_size))
        {
            return false;
        }

        // layout table: dynamic heap + dynamic thread group 
        layout_entry_t *layout_start = GET_PTR(layout_entry_t, metadata, metadata->dirs[DIR_LAYOUT].offset);
        layout_entry_t *layout_end = GET_PTR(layout_entry_t, metadata, metadata->dirs[DIR_LAYOUT].offset + metadata->dirs[DIR_LAYOUT].size);
        global_data->layout_entry_num = 0;
        for (layout_entry_t *layout = layout_start; layout < layout_end; layout++)
        {
            if(0 != memcpy_s(&global_data->layout_table[global_data->layout_entry_num], 
                     sizeof(global_data->layout_table) - global_data->layout_entry_num * sizeof(layout_t), 
                     layout, 
                     sizeof(layout_t)))
            {
                return false;
            }
            global_data->layout_entry_num++;
        }
        return true;
    }
}
