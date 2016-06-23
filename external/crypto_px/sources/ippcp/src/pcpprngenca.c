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
#include "pcpbn.h"
#include "pcphash.h"
#include "pcpprng.h"
#include "pcptool.h"

/*
// G() function based on SHA1
//
// Parameters:
//    T           160 bit parameter
//    pHexStr     input hex string
//    hexStrLen   size of hex string (Ipp8u segnments)
//    xBNU        160 bit BNU result
//
// Note 1:
//    must to be hexStrLen <= 64 (512 bits)
*/
static
void SHA1_G(Ipp32u* xBNU, const Ipp32u* T, Ipp8u* pHexStr, int hexStrLen)
{
   /* select processing function */
   cpHashProc updateFunc = UpdateSHA1;

   /* pad HexString zeros */
   PaddBlock(0, pHexStr+hexStrLen, BITS2WORD8_SIZE(MAX_XKEY_SIZE)-hexStrLen);

   /* reset initial HASH value */
   xBNU[0] = T[0];
   xBNU[1] = T[1];
   xBNU[2] = T[2];
   xBNU[3] = T[3];
   xBNU[4] = T[4];

   /* SHA1 */
   //UpdateSHA1(xBNU, pHexStr, BITS2WORD8_SIZE(MAX_XKEY_SIZE), SHA1_cnt);
   updateFunc(xBNU, pHexStr, BITS2WORD8_SIZE(MAX_XKEY_SIZE), SHA1_cnt);

   /* swap back */
   SWAP(xBNU[0],xBNU[4]);
   SWAP(xBNU[1],xBNU[3]);
}

/*
// Returns bitsize of the bitstring has beed added
*/
int cpPRNGen(Ipp32u* pRand, cpSize nBits, IppsPRNGState* pRnd)
{
   BNU_CHUNK_T Xj  [BITS_BNU_CHUNK(MAX_XKEY_SIZE)];
   BNU_CHUNK_T XVAL[BITS_BNU_CHUNK(MAX_XKEY_SIZE)];

   Ipp8u  TXVAL[BITS2WORD8_SIZE(MAX_XKEY_SIZE)];

   /* XKEY length in BNU_CHUNK_T */
   cpSize xKeyLen = BITS_BNU_CHUNK(RAND_SEEDBITS(pRnd));
   /* XKEY length in bytes */
   cpSize xKeySize= BITS2WORD8_SIZE(RAND_SEEDBITS(pRnd));
   /* XKEY word's mask */
   BNU_CHUNK_T xKeyMsk = MASK_BNU_CHUNK(RAND_SEEDBITS(pRnd));

   /* number of Ipp32u chunks to be generated */
   cpSize genlen = BITS2WORD32_SIZE(nBits);

   ZEXPAND_BNU(Xj, 0, BITS_BNU_CHUNK(MAX_XKEY_SIZE));
   ZEXPAND_BNU(XVAL, 0, BITS_BNU_CHUNK(MAX_XKEY_SIZE));

   while(genlen) {
      cpSize len;

      /* Step 1: XVAL=(Xkey+Xseed) mod 2^b */
      BNU_CHUNK_T carry = cpAdd_BNU(XVAL, RAND_XKEY(pRnd), RAND_XAUGMENT(pRnd), xKeyLen);
      XVAL[xKeyLen-1] &= xKeyMsk;

      /* Step 2: xj=G(t, XVAL) mod Q */
      cpToOctStr_BNU(TXVAL, xKeySize, XVAL, xKeyLen);
      SHA1_G((Ipp32u*)Xj, (Ipp32u*)RAND_T(pRnd), TXVAL, xKeySize);

      {
         cpSize sizeXj = BITS_BNU_CHUNK(160);
         if(0 <= cpCmp_BNU(Xj, BITS_BNU_CHUNK(IPP_SHA1_DIGEST_BITSIZE), RAND_Q(pRnd),BITS_BNU_CHUNK(IPP_SHA1_DIGEST_BITSIZE)) )
            sizeXj = cpMod_BNU(Xj, BITS_BNU_CHUNK(IPP_SHA1_DIGEST_BITSIZE), RAND_Q(pRnd), BITS_BNU_CHUNK(IPP_SHA1_DIGEST_BITSIZE));
         FIX_BNU(Xj, sizeXj);
         ZEXPAND_BNU(Xj, sizeXj, BITS_BNU_CHUNK(MAX_XKEY_SIZE));
      }

      /* Step 3: Xkey=(1+Xkey+Xj) mod 2^b */
      cpInc_BNU(RAND_XKEY(pRnd), RAND_XKEY(pRnd), xKeyLen, 1);
      carry = cpAdd_BNU(RAND_XKEY(pRnd), RAND_XKEY(pRnd), Xj, xKeyLen);
      RAND_XKEY(pRnd)[xKeyLen-1] &= xKeyMsk;

      /* fill out result */
      len = genlen<BITS2WORD32_SIZE(IPP_SHA1_DIGEST_BITSIZE)? genlen : BITS2WORD32_SIZE(IPP_SHA1_DIGEST_BITSIZE);
      COPY_BNU(pRand, (Ipp32u*)Xj, len);

      pRand  += len;
      genlen -= len;
   }

   return nBits;
}


/*F*
// Name: ippsPRNGen
//
// Purpose: Generates a pseudorandom bit sequence of the specified nBits length.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pRnd
//                               NULL == pBuffer
//
//    ippStsContextMatchErr      illegal pRnd->idCtx
//
//    ippStsLengthErr            1 > nBits
//
//    ippStsNoErr                no error
//
// Parameters:
//    pBuffer  pointer to the buffer
//    nBits    number of bits be requested
//    pRndCtx  pointer to the context
*F*/
IPPFUN(IppStatus, ippsPRNGen,(Ipp32u* pBuffer, cpSize nBits, void* pRnd))
{
   IppsPRNGState* pRndCtx = (IppsPRNGState*)pRnd;

   /* test PRNG context */
   IPP_BAD_PTR2_RET(pBuffer, pRnd);

   pRndCtx = (IppsPRNGState*)( IPP_ALIGNED_PTR(pRndCtx, PRNG_ALIGNMENT) );
   IPP_BADARG_RET(!RAND_VALID_ID(pRndCtx), ippStsContextMatchErr);

   /* test sizes */
   IPP_BADARG_RET(nBits< 1, ippStsLengthErr);

   {
      cpSize rndSize = BITS2WORD32_SIZE(nBits);
      Ipp32u rndMask = MAKEMASK32(nBits);

      cpPRNGen(pBuffer, nBits, pRndCtx);
      pBuffer[rndSize-1] &= rndMask;

      return ippStsNoErr;
   }
}
