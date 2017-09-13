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


#include "../App.h"
#include "Enclave_u.h"

/* edger8r_type_attributes:
 *   Invokes ECALLs declared with basic types.
 */
void edger8r_type_attributes(void)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;

    ret = ecall_type_char(global_eid, (char)0x12);
    if (ret != SGX_SUCCESS)
        abort();

    ret = ecall_type_int(global_eid, (int)1234);
    if (ret != SGX_SUCCESS)
        abort();

    ret = ecall_type_float(global_eid, (float)1234.0);
    if (ret != SGX_SUCCESS)
        abort();

    ret = ecall_type_double(global_eid, (double)1234.5678);
    if (ret != SGX_SUCCESS)
        abort();

    ret = ecall_type_size_t(global_eid, (size_t)12345678);
    if (ret != SGX_SUCCESS)
        abort();

    ret = ecall_type_wchar_t(global_eid, (wchar_t)0x1234);
    if (ret != SGX_SUCCESS)
        abort();

    struct struct_foo_t g = {1234, 5678};
    ret = ecall_type_struct(global_eid, g);
    if (ret != SGX_SUCCESS)
        abort();
    
    union union_foo_t val = {0};
    ret = ecall_type_enum_union(global_eid, ENUM_FOO_0, &val);
    if (ret != SGX_SUCCESS)
        abort();
    assert(val.union_foo_0 == 2);
}
