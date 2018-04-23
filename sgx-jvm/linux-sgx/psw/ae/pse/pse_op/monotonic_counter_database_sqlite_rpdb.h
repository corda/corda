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


#ifndef _VMC_SQLITE_RPDB_H
#define _VMC_SQLITE_RPDB_H

#include "pse_inc.h"
#include "pse_types.h"
#include "stdlib.h"
#include "sgx_utils.h"
#include "ae_ipp.h"
#include "math.h"
#include "monotonic_counter_database_types.h"
#include "session_mgr.h"

#define INVALID_VMC_ID 0xFFFFFF

pse_op_error_t create_vmc(
    const isv_attributes_t &owner_attributes,
    vmc_data_blob_t &vmc_data_blob, 
    mc_rpdb_uuid_t &mc_rpdb_uuid);

pse_op_error_t read_vmc(
    const isv_attributes_t &owner_attributes,  // [IN] ISV's attributes that 
    const mc_rpdb_uuid_t &mc_rpdb_uuid,      // [IN] UUID of VMC
    vmc_data_blob_t &rpdb);             // [IN,OUT] Pointer that points to VMC data blob

pse_op_error_t inc_vmc(
    const isv_attributes_t &owner_attributes,  // [IN] ISV's attributes that 
    const mc_rpdb_uuid_t &mc_rpdb_uuid,      // [IN] UUID of VMC
    vmc_data_blob_t &rpdb);             // [IN,OUT] Pointer that points to VMC data blob

pse_op_error_t delete_vmc(
    const isv_attributes_t &owner_attributes,
    const mc_rpdb_uuid_t &mc_rpdb_uuid);

#endif

