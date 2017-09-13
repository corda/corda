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

#ifndef ECDSA_PUBLIC_KEY_SIZE
#define ECDSA_PUBLIC_KEY_SIZE   64
#endif

const uint8_t INTEL_ECDSA_PUBKEY_PROD_IVK_LE[ECDSA_PUBLIC_KEY_SIZE] = {
    0x27, 0xBD, 0x65, 0x2E, 0xA4, 0xB0, 0x4C, 0x00, 0x06, 0x6E, 0x81, 0x4A, 0x4E, 0x1A, 0x2D, 0x35,
    0xC2, 0x80, 0x1E, 0xD0, 0xCC, 0x67, 0x8B, 0x02, 0x3C, 0xB4, 0xC4, 0xE6, 0xC7, 0xC7, 0xE2, 0x7B,
    0xAE, 0xC8, 0x3E, 0x16, 0x44, 0x1B, 0xC6, 0xD5, 0x38, 0x3F, 0xBD, 0x9F, 0x6C, 0x9F, 0xC7, 0x0F,
    0x12, 0xCA, 0xA6, 0x84, 0xC7, 0x2A, 0xB2, 0x42, 0x48, 0x3C, 0xB9, 0xCB, 0x62, 0x71, 0x07, 0x52
};

const uint8_t* pEpidVerifyKeys[] = { INTEL_ECDSA_PUBKEY_PROD_IVK_LE };

