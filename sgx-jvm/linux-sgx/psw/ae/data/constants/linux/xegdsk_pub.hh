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

/*ECDSA key for Extended EPID Blob signing key in little endian format*/
const sgx_ec256_public_t g_sdsk_pub_key_little_endian={
    {
        0X21, 0X6D, 0X79, 0X72, 0X46, 0X45, 0XF9, 0X3A,
        0XE3, 0X74, 0XD9, 0X39, 0X6D, 0XDA, 0XFB, 0X61,
        0XDD, 0X87, 0X57, 0X72, 0X55, 0X2C, 0XCF, 0XBF,
        0X58, 0X0D, 0X51, 0X36, 0XC4, 0X27, 0XF0, 0X63
    },
    {
        0X18, 0X68, 0X1C, 0X77, 0X27, 0X2E, 0X9B, 0XE6,
        0X25, 0X7B, 0XAC, 0XA1, 0XB9, 0X2C, 0XBF, 0X2C,
        0X84, 0X95, 0X16, 0XD6, 0XDD, 0X7F, 0XA1, 0X61,
        0XC5, 0X33, 0XBE, 0X9B, 0XFF, 0XED, 0X06, 0XAC
    }
};
