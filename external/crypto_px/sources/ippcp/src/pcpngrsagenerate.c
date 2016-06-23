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
#include "pcpprimeg.h"
#include "pcpngrsa.h"
#include "pcpngrsamontstuff.h"

/*F*
// Name: ippsRSA_ValidateKeys
//
// Purpose: Validate RSA keys
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pPublicKey
//                               NULL == pPrivateKeyType2
//                               NULL == pPrivateKeyType1
//                               NULL == pBuffer
//                               NULL == pPrimeGen
//                               NULL == rndFunc
//                               NULL == pResult
//
//    ippStsContextMatchErr     !RSA_PUB_KEY_VALID_ID(pPublicKey)
//                              !RSA_PRV_KEY2_VALID_ID(pPrivateKeyType2)
//                              !RSA_PRV_KEY1_VALID_ID(pPrivateKeyType1)
//                              !PRIME_VALID_ID(pPrimeGen)
//
//    ippStsIncompleteContextErr public and.or private key is not set up
//
//    ippStsSizeErr              PRIME_MAXBITSIZE(pPrimeGen) < factorPbitSize
//
//    ippStsBadArgErr            nTrials < 1
//
//    ippStsNoErr                no error
//
// Parameters:
//    pResult           pointer to the validation result
//    pPublicKey        pointer to the public key context
//    pPrivateKeyType2  pointer to the private key type2 context
//    pPrivateKeyType1  (optional) pointer to the private key type1 context
//    pBuffer           pointer to the temporary buffer
//    nTrials           parameter of Miller-Rabin Test
//    pPrimeGen         pointer to the Prime generator context
//    rndFunc           external PRNG
//    pRndParam         pointer to the external PRNG parameters
*F*/
/*
// make sure D*E = 1 mod(phi(P,Q))
// where phi(P,Q) = (P-1)*(Q-1)
*/
static
int isValidPriv1_classic(const BNU_CHUNK_T* pN, int nsN,
                         const BNU_CHUNK_T* pE, int nsE,
                         const BNU_CHUNK_T* pD, int nsD,
                         const BNU_CHUNK_T* pFactorP, int nsP,
                         const BNU_CHUNK_T* pFactorQ, int nsQ,
                         BNU_CHUNK_T* pBuffer)
{
   BNU_CHUNK_T* pPhi = pBuffer;
   BNU_CHUNK_T* pProduct = pPhi + nsN;
   BNU_CHUNK_T c = cpSub_BNU(pPhi, pN, pFactorP, nsP);
   int prodLen;
   if(nsN>1) cpDec_BNU(pPhi+nsP, pN+nsP, nsQ, c);
   c = cpSub_BNU(pPhi,pPhi, pFactorQ, nsQ);
   if(nsN>1) cpDec_BNU(pPhi+nsQ, pPhi+nsQ, nsP, c);
   cpInc_BNU(pPhi, pPhi, nsP+nsQ, 1);

   cpMul_BNU_school(pProduct, pE, nsE, pD, nsD);
   prodLen = cpMod_BNU(pProduct, nsE+nsD, pPhi, nsN);

   return 1==cpEqu_BNU_CHUNK(pProduct, prodLen, 1)? IPP_IS_VALID : IPP_IS_INVALID;
}

/*
// make sure D*E = 1 mod(lcm(P-1,Q-1))
// where lcm(P-1,Q-1) = (P-1)*(Q-1)/gcd(P-1,Q-1)
*/
static
int isValidPriv1_rsa(const BNU_CHUNK_T* pN, int nsN,
                     const BNU_CHUNK_T* pE, int nsE,
                     const BNU_CHUNK_T* pD, int nsD,
                           BNU_CHUNK_T* pFactorP, int nsP,
                           BNU_CHUNK_T* pFactorQ, int nsQ,
                     BNU_CHUNK_T* pBuffer)
{
   __ALIGN8 IppsBigNumState tmpBN1;
   __ALIGN8 IppsBigNumState tmpBN2;
   __ALIGN8 IppsBigNumState tmpBN3;

   BNU_CHUNK_T* pProduct = pBuffer;
   BNU_CHUNK_T* pGcd = pProduct+(nsN+1);
   BNU_CHUNK_T* pLcm;
   int nsLcm;
   int prodLen;
   pBuffer = pGcd + (nsP+1)*2;

   /* P = P-1 and Q = Q-1 */
   pFactorP[0]--;
   pFactorQ[0]--;

   /* compute product (P-1)*(Q-1) = P*Q -P -Q +1 = N -(P-1) -(Q-1) -1 */
   {
      BNU_CHUNK_T c = cpSub_BNU(pProduct, pN, pFactorP, nsP);
      if(nsN>1) cpDec_BNU(pProduct+nsP, pN+nsP, nsQ, c);
      c = cpSub_BNU(pProduct, pProduct, pFactorQ, nsQ);
      if(nsN>1) cpDec_BNU(pProduct+nsQ, pProduct+nsQ, nsP, c);
      cpDec_BNU(pProduct, pProduct, nsN, 1);
   }

   /* compute gcd(p-1, q-1) */
   BN_Make(pGcd,     pGcd+nsP+1,    nsP, &tmpBN1); /* BN(gcd) */
   BN_SIZE(&tmpBN1) = nsP;
   BN_Make(pFactorP, pBuffer,       nsP, &tmpBN2); /* BN(P-1) */
   BN_SIZE(&tmpBN2) = nsP;
   BN_Make(pFactorQ, pBuffer+nsP+1, nsQ, &tmpBN3); /* BN(Q-1) */
   BN_SIZE(&tmpBN3) = nsQ;
   ippsGcd_BN(&tmpBN2, &tmpBN3, &tmpBN1);

   /* compute lcm(p-1, q-1) = (p-1)(q-1)/gcd(p-1, q-1) */
   pLcm = pBuffer;
   cpDiv_BNU(pLcm, &nsLcm, pProduct, nsN, pGcd, BN_SIZE(&tmpBN1));

   /* test E*D = 1 mod lcm */
   cpMul_BNU_school(pProduct, pE, nsE, pD, nsD);
   prodLen = cpMod_BNU(pProduct, nsE+nsD, pLcm, nsLcm);

   /* restore P and Q */
   pFactorP[0]++;
   pFactorQ[0]++;

   return 1==cpEqu_BNU_CHUNK(pProduct, prodLen, 1)? IPP_IS_VALID : IPP_IS_INVALID;
}

IPPFUN(IppStatus, ippsRSA_ValidateKeys,(int* pResult,
                                 const IppsRSAPublicKeyState* pPublicKey,
                                 const IppsRSAPrivateKeyState* pPrivateKeyType2,
                                 const IppsRSAPrivateKeyState* pPrivateKeyType1, /*optional */
                                       Ipp8u* pBuffer,
                                       int nTrials,
                                       IppsPrimeState* pPrimeGen,
                                       IppBitSupplier rndFunc, void* pRndParam))
{
   IPP_BAD_PTR1_RET(pPublicKey);
   pPublicKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pPublicKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pPublicKey), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pPublicKey), ippStsIncompleteContextErr);

   IPP_BAD_PTR1_RET(pPrivateKeyType2);
   pPrivateKeyType2 = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pPrivateKeyType2, RSA_PRIVATE_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PRV_KEY2_VALID_ID(pPrivateKeyType2), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PRV_KEY_IS_SET(pPrivateKeyType2), ippStsIncompleteContextErr);

   if(pPrivateKeyType1) { /* pPrivateKeyType1 is optional */
      pPrivateKeyType1 = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pPrivateKeyType1, RSA_PRIVATE_KEY_ALIGNMENT) );
      IPP_BADARG_RET(!RSA_PRV_KEY1_VALID_ID(pPrivateKeyType1), ippStsContextMatchErr);
      IPP_BADARG_RET(!RSA_PRV_KEY_IS_SET(pPrivateKeyType1), ippStsIncompleteContextErr);
   }

   IPP_BAD_PTR1_RET(pPrimeGen);
   pPrimeGen = (IppsPrimeState*)( IPP_ALIGNED_PTR(pPrimeGen, PRIME_ALIGNMENT) );
   IPP_BADARG_RET(!PRIME_VALID_ID(pPrimeGen), ippStsContextMatchErr);
   IPP_BADARG_RET(PRIME_MAXBITSIZE(pPrimeGen) < RSA_PRV_KEY_BITSIZE_P(pPrivateKeyType2), ippStsSizeErr);

   IPP_BAD_PTR3_RET(pResult, pBuffer, rndFunc);

   /* test security parameter parameter */
   IPP_BADARG_RET((1>nTrials), ippStsBadArgErr);

   {
      BNU_CHUNK_T* pScratchBuffer = (BNU_CHUNK_T*)(IPP_ALIGNED_PTR(pBuffer, (int)sizeof(BNU_CHUNK_T)));

      /* E key component */
      BNU_CHUNK_T* pExpE = RSA_PUB_KEY_E(pPublicKey);
      cpSize nsE = BITS_BNU_CHUNK(RSA_PUB_KEY_BITSIZE_E(pPublicKey));
      /* P, dP, invQ key components */
      BNU_CHUNK_T* pFactorP= MNT_MODULUS(RSA_PRV_KEY_PMONT(pPrivateKeyType2));
      BNU_CHUNK_T* pExpDp = RSA_PRV_KEY_DP(pPrivateKeyType2);
      BNU_CHUNK_T* pInvQ  = RSA_PRV_KEY_INVQ(pPrivateKeyType2);
      cpSize nsP = MNT_SIZE(RSA_PRV_KEY_PMONT(pPrivateKeyType2));
      /* Q, dQ key components */
      BNU_CHUNK_T* pFactorQ= MNT_MODULUS(RSA_PRV_KEY_QMONT(pPrivateKeyType2));
      BNU_CHUNK_T* pExpDq = RSA_PRV_KEY_DQ(pPrivateKeyType2);
      cpSize nsQ = MNT_SIZE(RSA_PRV_KEY_QMONT(pPrivateKeyType2));

      /*const*/ BNU_CHUNK_T* pN0 = MNT_MODULUS(RSA_PUB_KEY_NMONT(pPublicKey));
      cpSize nsN = MNT_SIZE(RSA_PUB_KEY_NMONT(pPublicKey));

      *pResult = IPP_IS_VALID;

      /* make sure P is prime */
      if(!cpPrimeTest(pFactorP, nsP, nTrials, pPrimeGen, rndFunc, pRndParam)) {
         *pResult = IPP_IS_COMPOSITE;
         return ippStsNoErr;
      }

      /* make sure Q is prime */
      if(!cpPrimeTest(pFactorQ, nsQ, nTrials, pPrimeGen, rndFunc, pRndParam)) {
         *pResult = IPP_IS_COMPOSITE;
         return ippStsNoErr;
      }

      /* make sure PubKey(N)==PrivKeytype2(N) and PubKey(N)==PrivKeytype1(N) */
      if(cpCmp_BNU(pN0, nsN,
                   MNT_MODULUS(RSA_PRV_KEY_NMONT(pPrivateKeyType2)), MNT_SIZE(RSA_PRV_KEY_NMONT(pPrivateKeyType2)))) {
         *pResult = IPP_IS_INVALID;
         return ippStsNoErr;
      }
      if(pPrivateKeyType1) {
         if(cpCmp_BNU(pN0, nsN,
                      MNT_MODULUS(RSA_PRV_KEY_NMONT(pPrivateKeyType1)), MNT_SIZE(RSA_PRV_KEY_NMONT(pPrivateKeyType1)))) {
            *pResult = IPP_IS_INVALID;
            return ippStsNoErr;
         }
      }

      /* make sure 3 <= E < N */
      if(1==nsE && pExpE[0]<3) {
         *pResult = IPP_IS_INVALID;
         return ippStsNoErr;
      }
      if(0 <= cpCmp_BNU(pExpE, nsE, pN0, nsN)) {
         *pResult = IPP_IS_INVALID;
         return ippStsNoErr;
      }

      {
         BNU_CHUNK_T* pFactor1 = pScratchBuffer;
         BNU_CHUNK_T* pInv     = pFactor1 +nsP+1;
         BNU_CHUNK_T* pBufInv  = pInv     +nsP+1;
         BNU_CHUNK_T* pBufE    = pBufInv  +nsP+1;
         BNU_CHUNK_T* pBufFact = pBufE    +nsP+1;
         BNU_CHUNK_T* pProduct = pBufInv;

         /* make sure E*dP = 1 mod (P-1) */
         cpDec_BNU(pFactor1, pFactorP, nsP, 1);
         cpMul_BNU_school(pProduct, pExpDp, nsP, pExpE, nsE);
         cpMod_BNU(pProduct, nsP+nsE, pFactor1, nsP);
         if(!cpEqu_BNU_CHUNK(pProduct, nsP, 1)) {
            *pResult = IPP_IS_INVALID;
            return ippStsNoErr;
         }
         /* make sure 1==GCD(E,P-1) => exist Inv(E,P-1) */
         if(!cpModInv_BNU(pInv, pExpE, nsE, pFactor1, nsP, pBufInv, pBufE, pBufFact)) {
            *pResult = IPP_IS_INVALID;
            return ippStsNoErr;
         }

         /* make sure E*dQ = 1 mod (Q-1) */
         cpDec_BNU(pFactor1, pFactorQ, nsQ, 1);
         cpMul_BNU_school(pProduct, pExpDq, nsQ, pExpE, nsE);
         cpMod_BNU(pProduct, nsQ+nsE, pFactor1, nsQ);
         if(!cpEqu_BNU_CHUNK(pProduct, nsQ, 1)) {
            *pResult = IPP_IS_INVALID;
            return ippStsNoErr;
         }
         /* make sure 1==GCD(E,Q-1) => exist Inv(E,Q-1) */
         if(!cpModInv_BNU(pInv, pExpE, nsE, pFactor1, nsQ, pBufInv, pBufE, pBufFact)) {
            *pResult = IPP_IS_INVALID;
            return ippStsNoErr;
         }
      }

      /* make sure Q*Qinv = 1 mod P */
      cpMontMul_BNU(pScratchBuffer,
                 pFactorQ, nsQ,
                 pInvQ, nsP,
                 pFactorP, nsP, MNT_HELPER(RSA_PRV_KEY_PMONT(pPrivateKeyType2)),
                 pScratchBuffer+nsP, NULL);
      if(!cpEqu_BNU_CHUNK(pScratchBuffer, nsP, 1)) {
         *pResult = IPP_IS_INVALID;
         return ippStsNoErr;
      }

      /* test priva exponent (optiobal) */
      if(pPrivateKeyType1) {
         const BNU_CHUNK_T* pExpD = RSA_PRV_KEY_D(pPrivateKeyType1);
         cpSize nsD = nsN;

         int resilt1 = isValidPriv1_classic(pN0,nsN, pExpE,nsE, pExpD,nsD,
                                            pFactorP,nsP, pFactorQ,nsQ,
                                            (BNU_CHUNK_T*)pScratchBuffer);
         int resilt2 = isValidPriv1_rsa(pN0,nsN, pExpE,nsE, pExpD,nsD,
                                        pFactorP,nsP, pFactorQ,nsQ,
                                        (BNU_CHUNK_T*)pScratchBuffer);
         if(IPP_IS_VALID!=resilt1 && IPP_IS_VALID!=resilt2) {
            *pResult = IPP_IS_INVALID;
            return ippStsNoErr;
         }
      }

      return ippStsNoErr;
   }
}
