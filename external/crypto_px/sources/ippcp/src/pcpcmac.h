/*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
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

#if !defined(_PCP_CMAC_H)
#define _PCP_CMAC_H

#include "pcprij.h"


/*
// Rijndael128 based CMAC context
*/
struct _cpAES_CMAC {
   IppCtxId idCtx;              /* CMAC  identifier              */
   int      index;              /* internal buffer entry (free)  */
   int      dummy[2];           /* align-16                      */
   Ipp8u    k1[MBS_RIJ128];     /* k1 subkey                     */
   Ipp8u    k2[MBS_RIJ128];     /* k2 subkey                     */
   Ipp8u    mBuffer[MBS_RIJ128];/* buffer                        */
   Ipp8u    mMAC[MBS_RIJ128];   /* intermediate digest           */
   __ALIGN16                    /* aligned AES context           */
   IppsAESSpec mCipherCtx;
};

/* alignment */
#define AESCMAC_ALIGNMENT  (RIJ_ALIGNMENT)

/*
// Useful macros
*/
#define CMAC_ID(stt)      ((stt)->idCtx)
#define CMAC_INDX(stt)    ((stt)->index)
#define CMAC_K1(stt)      ((stt)->k1)
#define CMAC_K2(stt)      ((stt)->k2)
#define CMAC_BUFF(stt)    ((stt)->mBuffer)
#define CMAC_MAC(stt)     ((stt)->mMAC)
#define CMAC_CIPHER(stt)  ((stt)->mCipherCtx)

/* valid context ID */
#define VALID_AESCMAC_ID(ctx) (CMAC_ID((ctx))==idCtxCMAC)

#endif /* _PCP_CMAC_H */
