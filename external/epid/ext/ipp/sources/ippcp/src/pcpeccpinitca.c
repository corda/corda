/*############################################################################
  # Copyright 2016 Intel Corporation
  #
  # Licensed under the Apache License, Version 2.0 (the "License");
  # you may not use this file except in compliance with the License.
  # You may obtain a copy of the License at
  #
  #     http://www.apache.org/licenses/LICENSE-2.0
  #
  # Unless required by applicable law or agreed to in writing, software
  # distributed under the License is distributed on an "AS IS" BASIS,
  # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  # See the License for the specific language governing permissions and
  # limitations under the License.
  ############################################################################*/

/* 
// 
//  Purpose:
//     Cryptography Primitive.
//     EC over Prime Finite Field (initialization)
// 
//  Contents:
//     ippsECCPGetSize()
//     ippsECCPGetSizeStd128r1()
//     ippsECCPGetSizeStd128r2()
//     ippsECCPGetSizeStd192r1()
//     ippsECCPGetSizeStd224r1()
//     ippsECCPGetSizeStd256r1()
//     ippsECCPGetSizeStd384r1()
//     ippsECCPGetSizeStd521r1()
//     ippsECCPGetSizeStdSM2()
// 
//     ippsECCPInit()
//     ippsECCPInitStd128r1()
//     ippsECCPInitStd128r2()
//     ippsECCPInitStd192r1()
//     ippsECCPInitStd224r1()
//     ippsECCPInitStd256r1()
//     ippsECCPInitStd384r1()
//     ippsECCPInitStd521r1()
//     ippsECCPInitStdSM2()
// 
// 
*/

#include "precomp.h"
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
   IPP_BADARG_RET((2>feBitSize), ippStsSizeErr);

   {
      int bn1Size;
      int bn2Size;
      int pointSize;
      int mont1Size;
      int mont2Size;
      #if defined(_USE_NN_VERSION_)
      int randSize;
      int randCntSize;
      #endif
      int primeSize;
      int listSize;

      /* size of field element */
      int gfeSize = BITS2WORD32_SIZE(feBitSize);
      /* size of order */
      int ordSize = BITS2WORD32_SIZE(feBitSize+1);

      #if defined (_USE_ECCP_SSCM_)
      /* size of sscm buffer */
      int w = cpECCP_OptimalWinSize(feBitSize+1);
      int nPrecomputed = 1<<w;
      int sscmBuffSize = nPrecomputed*(BITS_BNU_CHUNK(feBitSize)*3*sizeof(BNU_CHUNK_T)) +(CACHE_LINE_SIZE-1);
      #endif

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

              #if defined(_USE_NN_VERSION_)
              +randSize             /* randomizer eng*/
              +randCntSize          /* randomizer bit*/
              #endif

              +primeSize            /* prime engine  */
              #if defined (_USE_ECCP_SSCM_)
              +sscmBuffSize         /* sscm buffer   */
              #endif
              +listSize             /* temp big num  */
              +(ALIGN_VAL-1);
   }

   return ippStsNoErr;
}

/*F*
//    Name: ippsECCPGetSizeStd128r1
//          ippsECCPGetSizeStd128r2
//          ippsECCPGetSizeStd192r1
//          ippsECCPGetSizeStd224r1
//          ippsECCPGetSizeStd256r1
//          ippsECCPGetSizeStd384r1
//          ippsECCPGetSizeStd521r1
//          ippsECCPGetSizeStdSM2
*F*/
IPPFUN(IppStatus, ippsECCPGetSizeStd128r1, (int *pSize))
{
   return ippsECCPGetSize(128, pSize);
}

IPPFUN(IppStatus, ippsECCPGetSizeStd128r2, (int *pSize))
{
   return ippsECCPGetSize(128, pSize);
}

IPPFUN(IppStatus, ippsECCPGetSizeStd192r1, (int *pSize))
{
   return ippsECCPGetSize(192, pSize);
}

IPPFUN(IppStatus, ippsECCPGetSizeStd224r1, (int *pSize))
{
   return ippsECCPGetSize(224, pSize);
}

IPPFUN(IppStatus, ippsECCPGetSizeStd256r1, (int *pSize))
{
   return ippsECCPGetSize(256, pSize);
}

IPPFUN(IppStatus, ippsECCPGetSizeStd384r1, (int *pSize))
{
   return ippsECCPGetSize(384, pSize);
}

IPPFUN(IppStatus, ippsECCPGetSizeStd521r1, (int *pSize))
{
   return ippsECCPGetSize(521, pSize);
}

IPPFUN(IppStatus, ippsECCPGetSizeStdSM2, (int *pSize))
{
   return ippsECCPGetSize(256, pSize);
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
   IPP_BADARG_RET((2>feBitSize), ippStsSizeErr);

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
      #if defined(_USE_NN_VERSION_)
      int randSize;
      int randCntSize;
      #endif
      int primeSize;
    //int listSize;

      /* size of field element */
      int gfeSize = BITS2WORD32_SIZE(feBitSize);
      /* size of order */
      int ordSize = BITS2WORD32_SIZE(feBitSize+1);

      #if defined (_USE_ECCP_SSCM_)
      /* size of sscm buffer */
      int w = cpECCP_OptimalWinSize(feBitSize+1);
      int nPrecomputed = 1<<w;
      int sscmBuffSize = nPrecomputed*(BITS_BNU_CHUNK(feBitSize)*3*sizeof(BNU_CHUNK_T)) +(CACHE_LINE_SIZE-1);
      #endif

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
    //listSize = cpBigNumListGetSize(feBitSize+1+32, BNLISTSIZE);

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
      #if defined(_USE_NN_VERSION_)
      ECP_RAND(pECC)    = (IppsPRNGState*)     ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += randSize;
      ECP_RANDCNT(pECC) = (IppsBigNumState*)   ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      ptr += randCntSize;
      #endif
      ECP_PRIMARY(pECC) = (IppsPrimeState*)    ( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += primeSize;

      #if defined (_USE_ECCP_SSCM_)
      ECP_SCCMBUFF(pECC) = (Ipp8u*)            ( IPP_ALIGNED_PTR(ptr,CACHE_LINE_SIZE) );
      ptr += sscmBuffSize;
      #endif

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

      #if defined(_USE_NN_VERSION_)
      ippsPRNGInit(feBitSize+1, ECP_RAND(pECC));
      ippsBigNumInit(RAND_CONTENT_LEN, ECP_RANDCNT(pECC));
      #endif

      cpBigNumListInit(feBitSize+1, BNLISTSIZE, ECP_BNCTX(pECC));
   }

   return ippStsNoErr;
}

/*F*
//    Name: ippsECCPInitStd128r1
//          ippsECCPInitStd128r2
//          ippsECCPInitStd192r1
//          ippsECCPInitStd224r1
//          ippsECCPInitStd256r1
//          ippsECCPInitStd384r1
//          ippsECCPInitStd521r1
//          ippsECCPInitStdSM2
*F*/
IPPFUN(IppStatus, ippsECCPInitStd128r1, (IppsECCPState* pEC))
{
   return ippsECCPInit(128, pEC);
}

IPPFUN(IppStatus, ippsECCPInitStd128r2, (IppsECCPState* pEC))
{
   return ippsECCPInit(128, pEC);
}

IPPFUN(IppStatus, ippsECCPInitStd192r1, (IppsECCPState* pEC))
{
   return ippsECCPInit(192, pEC);
}

IPPFUN(IppStatus, ippsECCPInitStd224r1, (IppsECCPState* pEC))
{
   return ippsECCPInit(224, pEC);
}

IPPFUN(IppStatus, ippsECCPInitStd256r1, (IppsECCPState* pEC))
{
   return ippsECCPInit(256, pEC);
}

IPPFUN(IppStatus, ippsECCPInitStd384r1, (IppsECCPState* pEC))
{
   return ippsECCPInit(384, pEC);
}

IPPFUN(IppStatus, ippsECCPInitStd521r1, (IppsECCPState* pEC))
{
   return ippsECCPInit(521, pEC);
}

IPPFUN(IppStatus, ippsECCPInitStdSM2, (IppsECCPState* pEC))
{
   return ippsECCPInit(256, pEC);
}
