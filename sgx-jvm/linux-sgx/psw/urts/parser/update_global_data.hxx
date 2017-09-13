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
    void do_update_global_data(const create_param_t* const create_param,
                               global_data_t* global_data)
    {
        global_data->enclave_size = (sys_word_t)create_param->enclave_size;
        global_data->heap_offset = (sys_word_t)create_param->heap_offset;
        global_data->heap_size = (sys_word_t)create_param->heap_max_size;
        global_data->thread_policy = (uint32_t)create_param->tcs_policy;
        thread_data_t *thread_data = &global_data->td_template;
        thread_data->stack_base_addr = (sys_word_t)create_param->stack_base_addr;
        thread_data->stack_limit_addr = (sys_word_t)create_param->stack_limit_addr;
        thread_data->last_sp = thread_data->stack_base_addr;
        thread_data->ssa_frame_size = create_param->ssa_frame_size;
        thread_data->first_ssa_gpr = (sys_word_t)create_param->first_ssa_gpr;
        // TD address relative to TCS
        thread_data->self_addr = (sys_word_t)create_param->td_addr;
        thread_data->tls_addr = (sys_word_t)create_param->tls_addr;
        thread_data->tls_array = thread_data->self_addr + (sys_word_t)offsetof(thread_data_t, tls_addr);
    }
}
