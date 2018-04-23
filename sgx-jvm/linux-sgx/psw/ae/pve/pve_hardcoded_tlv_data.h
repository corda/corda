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
#include "epid_pve_type.h"
#define BLOCK_CIPHER_INFO_TLV_TOTAL_SIZE 20
#define BLOCK_CIPHER_INFO_TLV_HEADER_SIZE 4
const uint8_t BLOCK_CIPHER_INFO_TLV_HEADER[]={2,1,0,16};
#define PSID_TLV_TOTAL_SIZE 36
#define PSID_TLV_HEADER_SIZE 4
const uint8_t PSID_TLV_HEADER[]={9,1,0,32,};
#define PWK2_TLV_TOTAL_SIZE 20
#define PWK2_TLV_HEADER_SIZE 4
const uint8_t PWK2_TLV_HEADER[]={25,1,0,16,};
#define DEVICE_ID_TLV_TOTAL_SIZE 42
#define DEVICE_ID_TLV_HEADER_SIZE 4
const uint8_t DEVICE_ID_TLV_HEADER[]={8,1,0,38};
#define MEMBERSHIP_CREDENTIAL_TLV_TOTAL_SIZE 164
#define MEMBERSHIP_CREDENTIAL_TLV_HEADER_SIZE 4
const uint8_t MEMBERSHIP_CREDENTIAL_TLV_HEADER[]={12,1,0,160};
#define JOIN_PROOF_TLV_TOTAL_SIZE 196
#define JOIN_PROOF_TLV_HEADER_SIZE 4
const uint8_t JOIN_PROOF_TLV_HEADER[]={10,1,0,192};
#define EPID_SIGNATURE_TLV_HEADER_SIZE 6
#define EPID_SIGNATURE_TLV_SIZE_OFFSET 2
const uint8_t EPID_SIGNATURE_TLV_HEADER[]={139,1,0,0,2,174};
