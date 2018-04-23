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



#pragma once
#ifndef _SEALED_DATA_DEFINES_H_
#define _SEALED_DATA_DEFINES_H_

#include "sgx_error.h"

#define PLATFORM_SERVICE_DOWNGRADED 0xF001

#define REPLAY_DETECTED             0xF002
#define MAX_RELEASE_REACHED         0xF003

/* equal to sgx_calc_sealed_data_size(0,sizeof(replay_protected_pay_load))) */ 
#define SEALED_REPLAY_PROTECTED_PAY_LOAD_SIZE 620
#define REPLAY_PROTECTED_PAY_LOAD_MAX_RELEASE_VERSION 5

#define TIMESOURCE_CHANGED          0xF004
#define TIMESTAMP_UNEXPECTED        0xF005
#define LEASE_EXPIRED               0xF006

/* equal tosgx_calc_sealed_data_size(0,sizeof(time_based_pay_load))) */ 
#define TIME_BASED_PAY_LOAD_SIZE 624
#define TIME_BASED_LEASE_DURATION_SECOND 3

#endif
