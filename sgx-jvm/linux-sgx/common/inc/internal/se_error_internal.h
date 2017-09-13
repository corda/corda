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

#ifndef _SE_ERROR_INTERNAL_H_
#define _SE_ERROR_INTERNAL_H_

#include "sgx_error.h"

/*
bit[31:30] - main module id
    00 - external error
    11---internal error
bit[29:16] - reserved
bit[15:12] - sub-module id
bit[11:0] - specific error
*/
    
#define MAIN_MOD_SHIFT 30
#define SUB_MOD_SHIFT 12

#define INTERNAL_ERROR 3
#define EXTERNAL_ERROR 0

#define SE_INTERNAL_ERROR(x)      (0xC0000000|(x))

typedef enum _se_status_internal_t
{
    SE_ERROR_SUCCESS = 0,      /*same value as SGX_SUCCESS*/
    /*error code for driver return to uRTS*/
    SE_ERROR_DRIVER_UNEXPECTED          = SE_INTERNAL_ERROR(0X2001),
    SE_ERROR_DRIVER_INVALID_ID          = SE_INTERNAL_ERROR(0X2002),
    SE_ERROR_DRIVER_INVALID_PARAMETER   = SE_INTERNAL_ERROR(0X2003),
    SE_ERROR_DRIVER_INVALID_REQUEST     = SE_INTERNAL_ERROR(0X2004),
    SE_ERROR_DRIVER_OUTOF_MEMORY_R0     = SE_INTERNAL_ERROR(0X2005),
    SE_ERROR_DRIVER_OUTOF_MEMORY_R3     = SE_INTERNAL_ERROR(0X2006),
    SE_ERROR_DRIVER_OUTOF_EPC           = SE_INTERNAL_ERROR(0X2007),
    SE_ERROR_DRIVER_HW_CAPABILITY       = SE_INTERNAL_ERROR(0X2008),
    SE_ERROR_DRIVER_MEMORY_MAP_CONFLICT = SE_INTERNAL_ERROR(0X2009),
    SE_ERROR_DRIVER_POWER               = SE_INTERNAL_ERROR(0X200a),
    SE_ERROR_DRIVER_INVALID_PRIVILEGE   = SE_INTERNAL_ERROR(0X200b),
    SE_ERROR_DRIVER_INVALID_ISVSVNLE    = SE_INTERNAL_ERROR(0X200c),
        
    SE_ERROR_DRIVER_INVALID_SIG_STRUCT  = SE_INTERNAL_ERROR(0X2100),
    SE_ERROR_DRIVER_INVALID_ATTRIBUTE   = SE_INTERNAL_ERROR(0X2101),
    SE_ERROR_DRIVER_INVALID_MEASUREMENT = SE_INTERNAL_ERROR(0X2102),
    SE_ERROR_DRIVER_INVALID_SIGNATURE   = SE_INTERNAL_ERROR(0X2103),
    SE_ERROR_DRIVER_INVALID_LAUNCH_TOKEN= SE_INTERNAL_ERROR(0X2104),
    SE_ERROR_DRIVER_INVALID_CPUSVN      = SE_INTERNAL_ERROR(0X2105),
    SE_ERROR_DRIVER_UNMASKED_EVENT      = SE_INTERNAL_ERROR(0X2106),

    SE_ERROR_INVALID_LAUNCH_TOKEN       = SE_INTERNAL_ERROR(0x2200),      /* the license is invalid*/
    SE_ERROR_INVALID_MEASUREMENT        = SE_INTERNAL_ERROR(0x2201),      /* The measurement of the enclave is invalid. May caused by signature or launch token*/
    SE_ERROR_READ_LOCK_FAIL             = SE_INTERNAL_ERROR(0x2202),
    SE_ERROR_INVALID_ISVSVNLE           = SE_INTERNAL_ERROR(0X2203),

    /*error code for untrusted event of SE mutex*/
    SE_ERROR_MUTEX_GET_EVENT            = SE_INTERNAL_ERROR(0x3001),
    SE_ERROR_MUTEX_WAIT_EVENT           = SE_INTERNAL_ERROR(0x3002),
    SE_ERROR_MUTEX_WAKE_EVENT           = SE_INTERNAL_ERROR(0x3003),
} se_status_internal_t;

#endif
