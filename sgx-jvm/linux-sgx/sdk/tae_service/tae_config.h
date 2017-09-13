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

#ifndef _TAE_CONFIG_H_
#define _TAE_CONFIG_H_

#include "sgx.h"

#ifdef _DEBUG
//wait for 10min at most for debug
#define SGX_PSE_LATENCY 600000
#else
//long enough to do long-term pairing,  PSE Provisioning, update RPDATA and commit the VMC database
#define SGX_PSE_LATENCY 20000
#endif


#define SE_CREATE_SESSION_TIMEOUT_MSEC (SGX_PSE_LATENCY)
#define SE_EXCHANGE_REPORT_TIMEOUT_MSEC (SGX_PSE_LATENCY)
#define SE_CLOSE_SESSION_TIMEOUT_MSEC (SGX_PSE_LATENCY)
#define SE_GET_TRUSTED_TIME_TIMEOUT_MSEC (SGX_PSE_LATENCY)
#define SE_CREATE_MONOTONIC_COUNTER_TIMEOUT_MSEC (SGX_PSE_LATENCY)
#define SE_READ_MONOTONIC_COUNTER_TIMEOUT_MSEC (SGX_PSE_LATENCY)
#define SE_INCREMENT_MONOTONIC_COUNTER_TIMEOUT_MSEC (SGX_PSE_LATENCY)
#define SE_DESTROY_MONOTONIC_COUNTER_TIMEOUT_MSEC (SGX_PSE_LATENCY)

#define RETRY_TIMES 2
#define DEFAULT_VMC_ATTRIBUTE_MASK  0xFF0000000000000BULL
#define DEFAULT_VMC_XFRM_MASK  0x0

#endif
