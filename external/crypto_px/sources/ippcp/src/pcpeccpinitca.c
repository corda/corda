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
#include "pcpeccp.h"
#include "pcpeccppoint.h"
#include "pcpbnresource.h"
#include "pcpeccpmethod.h"
#include "pcpeccpsscm.h"
#include "pcptool.h"


/*F*
//    Name: ippsECCPGetSize
//
// Purpose: Returns size of ECC context (bytes).
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pSize
//
//    ippStsSizeErr              2>feBitSize
//
//    ippStsNoErr                no errors
//
// Parameters:
//    feBitSize   size of field element (bits)
//    pSize       pointer to the size of internal ECC context
//
*F*/
IPPFUN(IppStatus, ippsECCPGetSize, (int feBitSize, int *pSize))
{
   /* test size's pointer */
   IPP_BAD_PTR1_RET(pSize);

   /* test size of field element */
   IPP_BADARG_RET((2>feBitSize || feBitSize>EC_GFP_MAXBITSIZE), ippStsSizeErr);

   {
      int bn1Size;
      int bn2Size;
      int pointSize;
      int mont1Size;
      int mont2Size;
      int primeSize;
      int listSize;

      /* size of field element */
      int gfeSize = BITS2WORD32_SIZE(feBitSize);
      /* size of order */
      int ordSize = BITS2WORD32_SIZE(feBitSize+1);

      /* size of sscm buffer */
      int w = cpECCP_OptimalWinSize(feBitSize+1);
      int nPrecomputed = 1<<w;
      int sscmBuffSize = nPrecomputed*(BITS_BNU_CHUNK(feBitSize)*3*sizeof(BNU_CHUNK_T)) +(CACHE_LINE_SIZE-1);

      /* size of BigNum over GF(p) */
      ippsBigNumGetSize(gfeSize, &bn1Size);

      /* size of BigNum over GF(r) */
      ippsBigNumGetSize(ordSize, &bn2Size);

      /* size of EC point over GF(p) */
      ippsECCPPointGetSize(feBitSize, &pointSize);

      /* size of montgomery engine over GF(p) */
      ippsMontGetSize(ippBinaryMethod, BITS2WORD32_SIZE(feBitSize), &mont1Size);

      /* size of montgomery engine over GF(r) */
      ippsMontGetSize(ippBinaryMethod, BITS2WORD32_SIZE(feBitSize+1), &mont2Size);

      /* size of prime engine */
      ippsPrimeGetSize(feBitSize+1, &primeSize);

      /* size of big num list (big num in the list preserve 32 bit word) */
      listSize = cpBigNumListGetSize(feBitSize+1, BNLISTSIZE);

      *pSize = sizeof(IppsECCPState)
              +sizeof(ECCP_METHOD)  /* methods       */

              +bn1Size              /* prime         */
              +bn1Size              /* A             */
              +bn1Size              /* B             */

              +bn1Size              /* GX            */
              +bn1Size              /* GY            */
              +bn2Size              /* order         */

              +bn1Size              /* Aenc          */
              +bn1Size              /* Benc          */
              +mont1Size            /* montgomery(p) */

              +pointSize            /* Genc          */
              +bn2Size              /* cofactor      */
              +mont2Size            /* montgomery(r) */

              +bn2Size              /* private       */
              +pointSize            /* public        */

              +bn2Size              /* eph private   */
              +pointSize            /* eph public    */

              +primeSize            /* prime engine  */
              +sscmBuffSize         /* sscm buffer   */
              +listSize             /* temp big num  */
              +(ALIGN_VAL-1);
   }

   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPInit
//
// Purpose: Init ECC context.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pECC
//
//    ippStsSizeErr              2>feBitSize
//
//    ippStsNoErr                no errors
//
// Parameters:
//    feBitSize   size of field element (bits)
//    pECC        pointer to the ECC context
//
*F*/
IPPFUN(IppStatus, ippsECCPInit, (int feBitSize, IppsECCPState* pECC))
{
   /* test pECC pointer */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );

   /* test size of field element */
   IPP_BADARG_RET((2>feBitSize || feBitSize>EC_GFP_MAXBITSIZE), ippStsSizeErr);

   /* clear context */
   PaddBlock(0, pECC, sizeof(IppsECCPState));

   /* context ID */
   ECP_ID(pECC) = idCtxECCP;

   /* generic EC */
   ECP_TYPE(pECC) = IppECCArbitrary;

   /* size of field element & BP order */
   ECP_GFEBITS(pECC) = feBitSize;
   ECP_ORDBITS(pECC) = feBitSize+1;

   /*
   // init other context fields
   */
   {
      int bn1Size;
      int bn2Size;
      int pointSize;
      int mont1Size;
      int mont2Size;
      int primeSize;

      /* size of field element */
      int gfeSize = BITS2WORD32_SIZE(feBitSize);
      /* size of order */
      int ordSize = BITS2WORD32_SIZE(feBitSize+1);

      /* size of sscm buffer */
      int w = cpECCP_OptimalWinSize(feBitSize+1);
      int nPrecomputed = 1<<w;
      int sscmBuffSize = nPrecomputed*(BITS_BNU_CHUNK(feBitSize)*3*sizeof(BNU_CHUNK_T)) +(CACHE_LINE_SIZE-1);

      Ipp8u* ptr = (Ipp8u*)pECC;

      /* size of BigNum over GF(p) */
      ippsBigNumGetSize(gfeSize, &bn1Size);

      /* size of BigNum over GF(r) */
      ippsBigNumGetSize(ordSize, &bn2Size);

      /* size of EC point over GF(p) */
      ippsECCPPointGetSize(feBitSize, &pointSize);

      /* size of montgomery engine over GF(p) */
      ippsMontGetSize(ippBinaryMethod, BITS2WORD32_SIZE(feBitSize), &mont1Size);

      /* size of montgomery engine over GF(r) */
      ippsMontGetSize(ippBinaryMethod, BITS2WORD32_SIZE(feBitSize+1), &mont2Size);

      /* size of prime engine */
      ippsPrimeGetSize(feBitSize+1, &primeSize);

      /* size of big num list */
      /* listSize = cpBigNumListGetSize(feBitSize+1+32, BNLISTSIZE); */

      /* allocate buffers */
      ptr += sizeof(IppsECCPState);

      ECP_METHOD(pECC)  = (ECCP_METHOD*)  (ptr);
      ptr += sizeof(ECCP_METHOD);

      ECP_PRIME(pECC)   = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn1Size;
      ECP_A(pECC)       = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn1Size;
      ECP_B(pECC)       = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      ptr += bn1Size;
      ECP_GX(pECC)      = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn1Size;
      ECP_GY(pECC)      = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn1Size;
      ECP_ORDER(pECC)   = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      ptr += bn2Size;
      ECP_AENC(pECC)    = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn1Size;
      ECP_BENC(pECC)    = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn1Size;
      ECP_PMONT(pECC)   = (IppsMontState*)     ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      ptr += mont1Size;
      ECP_GENC(pECC)    = (IppsECCPPointState*)( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += pointSize;
      ECP_COFACTOR(pECC)= (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn2Size;
      ECP_RMONT(pECC)   = (IppsMontState*)     ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      ptr += mont2Size;
      ECP_PRIVATE(pECC) = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn2Size;
      ECP_PUBLIC(pECC)  = (IppsECCPPointState*)( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      ptr += pointSize;
      ECP_PRIVATE_E(pECC) = (IppsBigNumState*) ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bn2Size;
      ECP_PUBLIC_E(pECC) =(IppsECCPPointState*)( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      ptr += pointSize;
      ECP_PRIMARY(pECC) = (IppsPrimeState*)    ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += primeSize;

      ECP_SCCMBUFF(pECC) = (Ipp8u*)            ( IPP_ALIGNED_PTR(ptr,CACHE_LINE_SIZE) );
      ptr += sscmBuffSize;

      ECP_BNCTX(pECC)   = (BigNumNode*)        ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      /* init buffers */
      ippsBigNumInit(gfeSize,  ECP_PRIME(pECC));
      ippsBigNumInit(gfeSize,  ECP_A(pECC));
      ippsBigNumInit(gfeSize,  ECP_B(pECC));

      ippsBigNumInit(gfeSize,  ECP_GX(pECC));
      ippsBigNumInit(gfeSize,  ECP_GY(pECC));
      ippsBigNumInit(ordSize,  ECP_ORDER(pECC));

      ippsBigNumInit(gfeSize,  ECP_AENC(pECC));
      ippsBigNumInit(gfeSize,  ECP_BENC(pECC));
      ippsMontInit(ippBinaryMethod, BITS2WORD32_SIZE(feBitSize), ECP_PMONT(pECC));

      ippsECCPPointInit(feBitSize, ECP_GENC(pECC));
      ippsBigNumInit(ordSize,    ECP_COFACTOR(pECC));
      ippsMontInit(ippBinaryMethod, BITS2WORD32_SIZE(feBitSize+1), ECP_RMONT(pECC));

      ippsBigNumInit(ordSize,   ECP_PRIVATE(pECC));
      ippsECCPPointInit(feBitSize,ECP_PUBLIC(pECC));

      ippsBigNumInit(ordSize,   ECP_PRIVATE_E(pECC));
      ippsECCPPointInit(feBitSize,ECP_PUBLIC_E(pECC));

      cpBigNumListInit(feBitSize+1, BNLISTSIZE, ECP_BNCTX(pECC));
   }

   return ippStsNoErr;
}
