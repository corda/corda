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

#ifndef UAE_SERVICE_SIM_H
#define UAE_SERVICE_SIM_H

#include <stdio.h>
#include <time.h>
#include <string.h>
#include <errno.h>

#include "arch.h"
#include "sgx_uae_service.h"
#include "uae_service_internal.h"
#include "pse_types.h"
#include "sgx_tseal.h"
#include "util.h"
#include "se_memcpy.h"
#include "sgx_dh.h"
#include "sgx_read_rand.h"
#include "se_lock.hpp"

#ifdef  __cplusplus
extern "C" {
#endif

typedef struct _vmc_sim_t {
    uint8_t counter_id[3];
    uint8_t nonce[13];
    uint32_t counter_value;
} vmc_sim_t;

sgx_status_t get_counter_id(vmc_sim_t *p_vmc_sim);
sgx_status_t del_vmc_sim(const vmc_sim_t *p_vmc_sim);

sgx_status_t store_vmc_sim(const vmc_sim_t *p_vmc_sim);

sgx_status_t load_vmc_sim(vmc_sim_t *p_vmc_sim);

#ifdef  __cplusplus
}
#endif
#endif
