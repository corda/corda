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



#ifndef _PARSE_KEY_FILE_H_
#define _PARSE_KEY_FILE_H_


#define N_SIZE_IN_BYTES    384
#define E_SIZE_IN_BYTES    4
#define D_SIZE_IN_BYTES    384
#define P_SIZE_IN_BYTES    192
#define Q_SIZE_IN_BYTES    192
#define DMP1_SIZE_IN_BYTES 192
#define DMQ1_SIZE_IN_BYTES 192
#define IQMP_SIZE_IN_BYTES 192

#define N_SIZE_IN_UINT     N_SIZE_IN_BYTES/sizeof(unsigned int)
#define E_SIZE_IN_UINT     E_SIZE_IN_BYTES/sizeof(unsigned int)
#define D_SIZE_IN_UINT     D_SIZE_IN_BYTES/sizeof(unsigned int)
#define P_SIZE_IN_UINT     P_SIZE_IN_BYTES/sizeof(unsigned int)
#define Q_SIZE_IN_UINT     Q_SIZE_IN_BYTES/sizeof(unsigned int)
#define DMP1_SIZE_IN_UINT  DMP1_SIZE_IN_BYTES/sizeof(unsigned int)
#define DMQ1_SIZE_IN_UINT  DMQ1_SIZE_IN_BYTES/sizeof(unsigned int)
#define IQMP_SIZE_IN_UINT  IQMP_SIZE_IN_BYTES/sizeof(unsigned int)

typedef enum _key_type_t
{
    UNIDENTIFIABLE_KEY = -1,
    NO_KEY = 0,
    PRIVATE_KEY,
    PUBLIC_KEY 
} key_type_t;

typedef struct _rsa_params_t
{
    unsigned int n[N_SIZE_IN_UINT];
    unsigned int e[E_SIZE_IN_UINT];
    unsigned int d[D_SIZE_IN_UINT];
    unsigned int p[P_SIZE_IN_UINT];
    unsigned int q[Q_SIZE_IN_UINT];
    unsigned int dmp1[DMP1_SIZE_IN_UINT];
    unsigned int dmq1[DMQ1_SIZE_IN_UINT];
    unsigned int iqmp[IQMP_SIZE_IN_UINT];
}rsa_params_t;


bool parse_key_file(const char *key_path, rsa_params_t *prsa, int *pkey_type);

#endif
