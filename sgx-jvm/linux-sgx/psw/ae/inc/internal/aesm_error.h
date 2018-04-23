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

#ifndef _AESM_ERROR_H_
#define _AESM_ERROR_H_

/*File to define aesm error code*/

typedef enum _aesm_error_t
{
    AESM_SUCCESS                                  =  0,
    AESM_UNEXPECTED_ERROR                         =  1,
    AESM_NO_DEVICE_ERROR                          =  2,
    AESM_PARAMETER_ERROR                          =  3,
    AESM_EPIDBLOB_ERROR                           =  4,
    AESM_EPID_REVOKED_ERROR                       =  5,
    AESM_GET_LICENSETOKEN_ERROR                   =  6,
    AESM_SESSION_INVALID                          =  7,
    AESM_MAX_NUM_SESSION_REACHED                  =  8,
    AESM_PSDA_UNAVAILABLE                         =  9,
    AESM_EPH_SESSION_FAILED                       = 10,
    AESM_LONG_TERM_PAIRING_FAILED                 = 11,
    AESM_NETWORK_ERROR                            = 12,
    AESM_NETWORK_BUSY_ERROR                       = 13,
    AESM_PROXY_SETTING_ASSIST                     = 14,
    AESM_FILE_ACCESS_ERROR                        = 15,
    AESM_SGX_PROVISION_FAILED                     = 16,
    AESM_SERVICE_STOPPED                          = 17,
    AESM_BUSY                                     = 18,
    AESM_BACKEND_SERVER_BUSY                      = 19,
    AESM_UPDATE_AVAILABLE                         = 20,
    AESM_OUT_OF_MEMORY_ERROR                      = 21,
    AESM_MSG_ERROR                                = 22,
    AESM_THREAD_ERROR                             = 23,
    AESM_SGX_DEVICE_NOT_AVAILABLE                 = 24,
    AESM_ENABLE_SGX_DEVICE_FAILED                 = 25,
    AESM_PLATFORM_INFO_BLOB_INVALID_SIG           = 26,
    AESM_SERVICE_NOT_AVAILABLE                    = 27,
    AESM_KDF_MISMATCH                             = 28,
    AESM_OUT_OF_EPC                               = 29,
    AESM_SERVICE_UNAVAILABLE                      = 30,
    AESM_UNRECOGNIZED_PLATFORM                    = 31,
} aesm_error_t;
#endif

