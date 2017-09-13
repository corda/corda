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

#ifndef ERROR_CODES_H_
#define ERROR_CODES_H_

typedef uint32_t ATTESTATION_STATUS;

#define SUCCESS                          0x00
#define INVALID_PARAMETER                0xE1
#define VALID_SESSION                    0xE2
#define INVALID_SESSION                  0xE3
#define ATTESTATION_ERROR                0xE4
#define ATTESTATION_SE_ERROR             0xE5
#define IPP_ERROR                        0xE6
#define NO_AVAILABLE_SESSION_ERROR       0xE7
#define MALLOC_ERROR                     0xE8
#define ERROR_TAG_MISMATCH               0xE9
#define OUT_BUFFER_LENGTH_ERROR          0xEA
#define INVALID_REQUEST_TYPE_ERROR       0xEB
#define INVALID_PARAMETER_ERROR          0xEC
#define ENCLAVE_TRUST_ERROR              0xED
#define ENCRYPT_DECRYPT_ERROR            0xEE
#define DUPLICATE_SESSION                0xEF
#endif
