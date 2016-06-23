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
#include "pcphash.h"
#include "pcptool.h"


/*F*
//    Name: ippsMGF_SHA1
//          ippsMGF_SHA224
//          ippsMGF_SHA256
//          ippsMGF_SHA384
//          ippsMGF_SHA512
//          ippsMGF_MD5
//
// Purpose: Mask Generation Functios.
//
// Returns:                Reason:
//    ippStsNullPtrErr           pMask == NULL
//    ippStsLengthErr            seedLen <0
//                               maskLen <0
//    ippStsNotSupportedModeErr  if algID is not match to supported hash alg
//    ippStsNoErr                no errors
//
// Parameters:
//    pSeed       pointer to the input stream
//    seedLen     input stream length (bytes)
//    pMaske      pointer to the ouput mask
//    maskLen     desired length of mask (bytes)
//
*F*/
IPPFUN(IppStatus, ippsMGF,(const Ipp8u* pSeed, int seedLen, Ipp8u* pMask, int maskLen, IppHashAlgId hashAlg))
{
   /* get algorithm id */
   hashAlg = cpValidHashAlg(hashAlg);
   /* test hash alg */
   IPP_BADARG_RET(ippHashAlg_Unknown==hashAlg, ippStsNotSupportedModeErr);

   IPP_BAD_PTR1_RET(pMask);
   IPP_BADARG_RET((seedLen<0)||(maskLen<0), ippStsLengthErr);

   {
      /* hash specific */
      int hashSize = cpHashSize(hashAlg);

      int i, outLen;

      IppsHashState hashCtx;
      ippsHashInit(&hashCtx, hashAlg);

      if(!pSeed)
         seedLen = 0;

      for(i=0,outLen=0; outLen<maskLen; i++) {
         Ipp8u cnt[4];
         cnt[0] = (Ipp8u)((i>>24) & 0xFF);
         cnt[1] = (Ipp8u)((i>>16) & 0xFF);
         cnt[2] = (Ipp8u)((i>>8)  & 0xFF);
         cnt[3] = (Ipp8u)(i & 0xFF);

         cpReInitHash(&hashCtx, hashAlg);
         ippsHashUpdate(pSeed, seedLen, &hashCtx);
         ippsHashUpdate(cnt,   4,       &hashCtx);

         if((outLen + hashSize) <= maskLen) {
            ippsHashFinal(pMask+outLen, &hashCtx);
            outLen += hashSize;
         }
         else {
            Ipp8u md[BITS2WORD8_SIZE(IPP_SHA512_DIGEST_BITSIZE)];
            ippsHashFinal(md, &hashCtx);
            CopyBlock(md, pMask+outLen, maskLen-outLen);
            outLen = maskLen;
         }
      }

      return ippStsNoErr;
   }
}
