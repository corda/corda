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

#include "owndefs.h"
#include "owncp.h"
#include "pcpaesm.h"
#include "pcptool.h"

#include "pcprijtables.h"

/*F*
//    Name: ippsAESGetSize
//
// Purpose: Returns size of AES context (in bytes).
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSzie == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize       pointer to AES size of context(in bytes)
//
*F*/
IPPFUN(IppStatus, ippsAESGetSize,(int* pSize))
{
   /* test size's pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = cpSizeofCtx_AES();

   return ippStsNoErr;
}

/* number of rounds (use [NK] for access) */
static int rij128nRounds[3] = {NR128_128, NR128_192, NR128_256};

/*
// number of keys (estimation only!)  (use [NK] for access)
//
// accurate number of keys necassary for encrypt/decrypt are:
//    nKeys = NB * (NR+1)
//       where NB - data block size (32-bit words)
//             NR - number of rounds (depend on NB and keyLen)
//
// but the estimation
//    estnKeys = (NK*n) >= nKeys
// or
//    estnKeys = ( (NB*(NR+1) + (NK-1)) / NK) * NK
//       where NK - key length (words)
//             NB - data block size (word)
//             NR - number of rounds (depend on NB and keyLen)
//             nKeys - accurate numner of keys
// is more convinient when calculates key extension
*/
static int rij128nKeys[3] = {44,  54,  64 };

/*
// helper for nRounds[] and estnKeys[] access
// note: x is length in 32-bits words
*/
__INLINE int rij_index(int x)
{ return (x-NB(128))>>1; }

/*F*
//    Name: ippsAESInit
//
// Purpose: Init AES context for future usage
//          and setup secret key.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pCtx == NULL
//    ippStsMemAllocErr       size of buffer is not match fro operation
//    ippStsLengthErr         keyLen != 16
//                            keyLen != 24
//                            keyLen != 32
//
// Parameters:
//    pKey        secret key
//    keyLen      length of the secret key (in bytes)
//    pCtx        pointer to buffer initialized as AES context
//    ctxSize     available size (in bytes) of buffer above
//
// Note:
//    if pKey==NULL, then AES initialized by zero value key
//
*F*/
IPPFUN(IppStatus, ippsAESInit,(const Ipp8u* pKey, int keyLen,
                               IppsAESSpec* pCtxRaw, int rawCtxSize))
{
   /* test context pointer */
   IPP_BAD_PTR1_RET(pCtxRaw);

   /* make sure in legal keyLen */
   IPP_BADARG_RET(keyLen!=16 && keyLen!=24 && keyLen!=32, ippStsLengthErr);

   {
      /* use aligned Rijndael context */
      IppsAESSpec* pCtx = (IppsAESSpec*)( IPP_ALIGNED_PTR(pCtxRaw, AES_ALIGNMENT) );

      /* test available size of context buffer */
      if(((Ipp8u*)pCtx+sizeof(IppsAESSpec)) > ((Ipp8u*)pCtxRaw+rawCtxSize))
         IPP_ERROR_RET(ippStsMemAllocErr);

      else {
         int keyWords = NK(keyLen*BITSIZE(Ipp8u));
         int nExpKeys = rij128nKeys  [ rij_index(keyWords) ];
         int nRounds  = rij128nRounds[ rij_index(keyWords) ];

         Ipp8u zeroKey[32] = {0};
         const Ipp8u* pActualKey = pKey? pKey : zeroKey;

         /* clear context */
         PaddBlock(0, pCtx, sizeof(IppsAESSpec));

         /* init spec */
         RIJ_ID(pCtx) = idCtxRijndael;
         RIJ_NB(pCtx) = NB(128);
         RIJ_NK(pCtx) = keyWords;
         RIJ_NR(pCtx) = nRounds;
         RIJ_SAFE_INIT(pCtx) = 1;

         /* set key expansion */
         ExpandRijndaelKey(pActualKey, keyWords, NB(128), nRounds, nExpKeys,
                           RIJ_EKEYS(pCtx),
                           RIJ_DKEYS(pCtx));
         {
            int nr;
            Ipp8u* pEnc_key = (Ipp8u*)(RIJ_EKEYS(pCtx));
            /* update key material: transpose inplace */
            for(nr=0; nr<(1+nRounds); nr++, pEnc_key+=16) {
               SWAP(pEnc_key[ 1], pEnc_key[ 4]);
               SWAP(pEnc_key[ 2], pEnc_key[ 8]);
               SWAP(pEnc_key[ 3], pEnc_key[12]);
               SWAP(pEnc_key[ 6], pEnc_key[ 9]);
               SWAP(pEnc_key[ 7], pEnc_key[13]);
               SWAP(pEnc_key[11], pEnc_key[14]);
            }
         }
         RIJ_ENCODER(pCtx) = Safe2Encrypt_RIJ128; /* safe encoder (compact Sbox)) */
         RIJ_DECODER(pCtx) = Safe2Decrypt_RIJ128; /* safe decoder (compact Sbox)) */

         return ippStsNoErr;
      }
   }
}
