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

 
#ifndef PSDA_SERVICE_H_
#define PSDA_SERVICE_H_

#include "pse_inc.h"
#include "pse_types.h"
#include "session_mgr.h"

////////////////////////////////////////
//message handling data
////////////////////////////////////////
#define CSE_TRUSTED_TIME_SERVICE       0
#define CSE_MC_SERVICE                 1
#define CSE_PROTECTED_OUTPUT_SERVICE   2
#define CSE_RPDATA_SERVICE             3
//trusted time
#define CSE_TIMER_READ            0

//RPDATA
#define CSE_RPDATA_READ           0
#define CSE_RPDATA_UPDATE         1
#define CSE_RPDATA_RESET          2

// CSE ERROR CODES
#define CSE_SERVICE_SUCCESS                    0
#define CSE_ERROR_UNKNOWN_REQ                  1
#define CSE_ERROR_CAP_NOT_AVAILABLE            2
#define CSE_ERROR_INVALID_PARAM                3
#define CSE_ERROR_INTERNAL                     4
#define CSE_ERROR_PERSISTENT_DATA_WRITE_THROTTLED 7

pse_op_error_t psda_read_mc(
    uint8_t counter_id, 
    uint32_t* counter_value, 
    uint32_t* mc_epoch);

pse_op_error_t psda_inc_mc(
    uint8_t counter_id, 
    uint8_t inc_amount,
    uint32_t* counter_value, 
    uint32_t* mc_epoch);

pse_op_error_t psda_read_timer(
    const isv_attributes_t &owner_attributes,
    uint64_t* timestamp, 
    uint8_t* time_source_nonce);

pse_op_error_t psda_read_rpdata(
    uint8_t* rpdata, 
    uint32_t* rp_epoch);

pse_op_error_t psda_update_rpdata(
    uint8_t* rpdata_cur, 
    uint8_t* rpdata_new, 
    uint32_t* rp_epoch);

pse_op_error_t psda_reset_rpdata(
    uint8_t* rpdata_cur, 
    uint8_t* rpdata_new,
    uint32_t* rp_epoch);

#endif // PSDA_SERVICE_H_
