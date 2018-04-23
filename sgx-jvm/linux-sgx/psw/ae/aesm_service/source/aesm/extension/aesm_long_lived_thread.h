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

#ifndef _AESM_LONG_LIVED_THREAD_H_
#define _AESM_LONG_LIVED_THREAD_H_
#include "aesm_logic.h"

#define AESM_STOP_TIMEOUT (60*1000) /*waiting for 1 minute at most*/
extern ae_error_t start_epid_provision_thread(bool performance_rekey, unsigned long timeout=THREAD_TIMEOUT);
extern ae_error_t start_check_ltp_thread(bool& is_new_pairing, unsigned long timeout=THREAD_TIMEOUT);
extern ae_error_t start_white_list_thread(unsigned long timeout=THREAD_TIMEOUT);
extern ae_error_t start_update_pse_thread(const platform_info_blob_wrapper_t* update_blob, uint32_t attestation_status, unsigned long timeout=THREAD_TIMEOUT);
extern ae_error_t start_long_term_pairing_thread(bool& is_new_paring, unsigned long timeout=THREAD_TIMEOUT);
extern bool query_pve_thread_status(void);/*return true if idle and reset clock for thread*/
extern bool query_pse_thread_status(void);/*return true if idel and reset clock for thread*/
extern ae_error_t wait_pve_thread(uint64_t time_out_milliseconds=AESM_THREAD_INFINITE);
extern void stop_all_long_lived_threads(uint64_t time_out_milliseconds=AESM_STOP_TIMEOUT);
#endif

