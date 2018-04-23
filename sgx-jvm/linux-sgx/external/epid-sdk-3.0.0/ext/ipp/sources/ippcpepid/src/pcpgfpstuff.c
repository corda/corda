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
//     Intel(R) Performance Primitives. Cryptography Primitives.
//     Internal operations over GF(p).
// 
//     Context:
//        cpGFpCmpare
// 
//        cpGFpRand
//        cpGFpSet
//        cpGFpGet
// 
//        cpGFpNeg
//        cpGFpInv
//        cpGFpHalve
//        cpGFpAdd
//        cpGFpSub
//        cpGFpMul
//        cpGFpExp, cpGFpExp2
//        cpGFpSqrt
// 
// 
*/

#include "owncpepid.h"

#include "pcpgfpstuff.h"

IppsBigNumState* cpGFpInitBigNum(IppsBigNumState* pBN, int len, BNU_CHUNK_T* pNumBuffer, BNU_CHUNK_T* pTmpBuffer)
{
   BN_ID(pBN)     = idCtxBigNum;
   BN_SIGN(pBN)   = ippBigNumPOS;
   BN_NUMBER(pBN) = pNumBuffer;
   BN_BUFFER(pBN) = pTmpBuffer;
   BN_ROOM(pBN)   = len;
   BN_SIZE(pBN)   = 0;
   return pBN;
}

IppsBigNumState* cpGFpSetBigNum(IppsBigNumState* pBN, int len, const BNU_CHUNK_T* pBNU, BNU_CHUNK_T* pTmpBuffer)
{
   cpGFpInitBigNum(pBN, len, (BNU_CHUNK_T*)pBNU, pTmpBuffer);
   FIX_BNU(pBNU, len);
   BN_SIZE(pBN) = len;
   return pBN;
}

static void cpGFpMontEncode(BNU_CHUNK_T* pR, BNU_CHUNK_T* pA, int elemLen, IppsMontState* pMont)
{
   cpMontEnc_BNU(pR, pA, elemLen, pMont);
}

static void cpGFpMontDecode(BNU_CHUNK_T* pR, BNU_CHUNK_T* pA, int elemLen, IppsMontState* pMont)
{
   cpMontDec_BNU(pR, pA, elemLen, pMont);
}

/*
// compare GF.
// returns:
//    0 - are equial
//    1 - are different
//    2 - different structure
*/
int cpGFpCompare(const IppsGFpState* pGFp1, const IppsGFpState* pGFp2)
{
   if( GFP_DEGREE(pGFp1) != GFP_DEGREE(pGFp2) )
      return 2;
   if( GFP_FELEN(pGFp1) != GFP_FELEN(pGFp2) )
      return 1;
   if(0 != cpGFpElementCmp(GFP_MODULUS(pGFp1), GFP_MODULUS(pGFp1), GFP_FELEN(pGFp1)) )
      return 1;
   return 0;
}

BNU_CHUNK_T* cpGFpSet(BNU_CHUNK_T* pElm, const BNU_CHUNK_T* pDataA, int nsA, IppsGFpState* pGF, int montSpace)
{
   const BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen = GFP_FELEN(pGF);

   if(0 <= cpCmp_BNU(pDataA, nsA, pModulus, elemLen))
      return NULL;

   else {
      ZEXPAND_COPY_BNU(pElm, elemLen, pDataA, nsA);

      if(montSpace)
         cpGFpMontEncode(pElm, pElm, elemLen, GFP_MONT(pGF));

      return pElm;
   }
}

BNU_CHUNK_T* cpGFpSetOctString(BNU_CHUNK_T* pElm, const Ipp8u* pStr, int strSize, IppsGFpState* pGF, int montSpace)
{
   int elemLen = GFP_FELEN(pGF);

   if((int)(elemLen*sizeof(BNU_CHUNK_T)) < strSize)
      return NULL;

   else {
      BNU_CHUNK_T* pTmp = cpGFpGetPool(1, pGF);

      int len = cpFromOctStr_BNU(pTmp, pStr, strSize);
      pElm = cpGFpSet(pElm, pTmp, len, pGF, montSpace);

      cpGFpReleasePool(1, pGF);
      return pElm;
   }
}

BNU_CHUNK_T* cpGFpGet(BNU_CHUNK_T* pDataA, int nsA, const BNU_CHUNK_T* pElm, IppsGFpState* pGFp, int montSpace)
{
   int elemLen = GFP_FELEN(pGFp);
   BNU_CHUNK_T* pTmp = GFP_POOL(pGFp);

   cpGFpElementCopy(pTmp, pElm, elemLen);

   if(montSpace)
      cpGFpMontDecode(pTmp, pTmp, elemLen, GFP_MONT(pGFp));

   ZEXPAND_COPY_BNU(pDataA, nsA, pTmp, elemLen);
   return pDataA;
}

Ipp8u* cpGFpGetOctString(Ipp8u* pStr, int strSize, const BNU_CHUNK_T* pA, IppsGFpState* pGF, int montSpace)
{
   BNU_CHUNK_T* pTmp = cpGFpGetPool(1, pGF);
   int elemLen = GFP_FELEN(pGF);

   if(montSpace)
      cpGFpMontDecode(pTmp, (BNU_CHUNK_T*)pA, elemLen, GFP_MONT(pGF));
   else
      cpGFpElementCopy(pTmp, pA, elemLen);

   cpToOctStr_BNU(pStr, strSize, pTmp, elemLen);

   cpGFpReleasePool(1, pGF);

   return pStr;
}

/* sscm version */
BNU_CHUNK_T* cpGFpNeg(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pTmpR = cpGFpGetPool(1, pGF);

   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen = GFP_FELEN(pGF);

   BNU_CHUNK_T e = cpSub_BNU(pR, pModulus, pA, elemLen);
   e -= cpSub_BNU(pTmpR, pR, pModulus, elemLen);
   MASKED_COPY(pR, e, pR, pTmpR, elemLen);

   cpGFpReleasePool(1, pGF);

   return pR;
}

#if(_IPP32E >= _IPP32E_M7)
BNU_CHUNK_T* cp256pNeg(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   return gf256_neg(pR, pA, GFP_MODULUS(pGF));
}
#endif


BNU_CHUNK_T* cpGFpInv(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen   = GFP_FELEN(pGF);
   int poolelementLen= GFP_PELEN(pGF);

   BNU_CHUNK_T* tmpM = cpGFpGetPool(4, pGF);
   BNU_CHUNK_T* tmpX1= tmpM +poolelementLen;
   BNU_CHUNK_T* tmpX2= tmpX1+poolelementLen;
   BNU_CHUNK_T* tmpX3= tmpX2+poolelementLen;
   int nsR;

   cpGFpElementCopy(tmpM, pModulus, elemLen);
   nsR = cpModInv_BNU(pR, pA,elemLen, tmpM, elemLen, tmpX1,tmpX2,tmpX3);
   cpGFpReleasePool(4, pGF);

   cpGFpElementPadd(pR+nsR, elemLen-nsR, 0);
   return cpGFpMul(pR, pR, MNT_CUBE_R(GFP_MONT(pGF)), pGF);
}


/* sscm version */
BNU_CHUNK_T* cpGFpHalve(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen = GFP_FELEN(pGF);

   BNU_CHUNK_T mask = 0 - (pA[0]&1); /* set mask iif A is odd */
   /* t = if(isOdd(A))? modulus : 0 */
   int i;
   BNU_CHUNK_T* t = cpGFpGetPool(1, pGF);
   for(i=0; i<elemLen; i++) t[i] = pModulus[i] & mask;

   t[elemLen] = cpAdd_BNU(t, t, pA, elemLen);
   cpLSR_BNU(t, t, elemLen+1, 1);
   cpGFpElementCopy(pR, t, elemLen);

   cpGFpReleasePool(1, pGF);

   return pR;
}

#if(_IPP32E >= _IPP32E_M7)
BNU_CHUNK_T* cp256pHalve(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   return gf256_div2(pR, pA, GFP_MODULUS(pGF));
}
#endif


/* sscm version */
BNU_CHUNK_T* cpGFpAdd(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pTmpR = cpGFpGetPool(1, pGF);

   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen = GFP_FELEN(pGF);

   BNU_CHUNK_T e = cpAdd_BNU(pR, pA, pB, elemLen);
   e -= cpSub_BNU(pTmpR, pR, pModulus, elemLen);
   MASKED_COPY(pR, e, pR, pTmpR, elemLen);

   cpGFpReleasePool(1, pGF);

   return pR;
}

#if(_IPP32E >= _IPP32E_M7)
BNU_CHUNK_T* cp256pAdd(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGF)
{
   return gf256_add(pR, pA, pB, GFP_MODULUS(pGF));
}
#endif


/* sscm version */
BNU_CHUNK_T* cpGFpSub(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pTmpR = cpGFpGetPool(1, pGF);

   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen = GFP_FELEN(pGF);

   BNU_CHUNK_T e = cpSub_BNU(pR, pA, pB, elemLen);
   cpAdd_BNU(pTmpR, pR, pModulus, elemLen);
   MASKED_COPY(pR, (0-e), pTmpR, pR, elemLen);

   cpGFpReleasePool(1, pGF);

   return pR;
}

#if(_IPP32E >= _IPP32E_M7)
BNU_CHUNK_T* cp256pSub(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGF)
{
   return gf256_sub(pR, pA, pB, GFP_MODULUS(pGF));
}
#endif


BNU_CHUNK_T* cpGFpMul(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen = GFP_FELEN(pGF);

   IppsMontState* pMont = GFP_MONT(pGF);
   BNU_CHUNK_T* pBuffer = MNT_PRODUCT(pMont);
   BNU_CHUNK_T  m0 = MNT_HELPER(pMont);

   cpMontMul_BNU(pR, pA,elemLen, pB,elemLen, pModulus,elemLen, m0, pBuffer, NULL);
   return pR;
}

#if(_IPP32E >= _IPP32E_M7)
BNU_CHUNK_T* cp256pMul(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   IppsMontState* pMont = GFP_MONT(pGF);
   BNU_CHUNK_T  m0 = MNT_HELPER(pMont);
   return gf256_mulm(pR, pA, pB, pModulus, m0);
}
#endif

BNU_CHUNK_T* cpGFpSqr(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   int elemLen = GFP_FELEN(pGF);

   IppsMontState* pMont = GFP_MONT(pGF);
   BNU_CHUNK_T* pBuffer = MNT_PRODUCT(pMont);
   BNU_CHUNK_T  m0 = MNT_HELPER(pMont);

   cpMontSqr_BNU(pR, pA,elemLen, pModulus,elemLen, m0, pBuffer, NULL);
   return pR;
}

#if(_IPP32E >= _IPP32E_M7)
BNU_CHUNK_T* cp256pSqr(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);
   IppsMontState* pMont = GFP_MONT(pGF);
   BNU_CHUNK_T  m0 = MNT_HELPER(pMont);
   return gf256_sqrm(pR, pA, pModulus, m0);
}
#endif


BNU_CHUNK_T* cpGFpExp(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pE, int nsE, IppsGFpState* pGF)
{
   IppsBigNumState A;
   IppsBigNumState E;
   IppsBigNumState R;

   BNU_CHUNK_T* pPool = cpGFpGetPool(3, pGF);
   int poolElemLen = GFP_PELEN(pGF);
   int elemLen = GFP_FELEN(pGF);

   cpGFpSetBigNum(&A, elemLen, pA, pPool+0*poolElemLen);
   cpGFpSetBigNum(&E, nsE, pE, pPool+1*poolElemLen);
   cpGFpInitBigNum(&R,elemLen, pR, pPool+2*poolElemLen);

   cpMontExpBin_BN(&R, &A, &E, GFP_MONT(pGF));

   cpGFpReleasePool(3, pGF);
   return pR;
}


static int factor2(BNU_CHUNK_T* pA, int nsA)
{
   int factor = 0;
   int bits;

   int i;
   for(i=0; i<nsA; i++) {
      int ntz = cpNTZ_BNU(pA[i]);
      factor += ntz;
      if(ntz<BITSIZE(BNU_CHUNK_T))
         break;
   }

   bits = factor;
   if(bits >= BITSIZE(BNU_CHUNK_T)) {
      int nchunk = bits/BITSIZE(BNU_CHUNK_T);
      cpGFpElementCopyPadd(pA, nsA, pA+nchunk, nsA-nchunk);
      bits %= BITSIZE(BNU_CHUNK_T);
   }
   if(bits)
      cpLSR_BNU(pA, pA, nsA, bits);

   return factor;
}
static BNU_CHUNK_T* cpGFpExp2(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, int e, IppsGFpState* pGF)
{
   cpGFpElementCopy(pR, pA, GFP_FELEN(pGF));
   while(e--) {
      pGF->sqr(pR, pR, pGF);
   }
   return pR;
}

/* returns:
   0, if a - qnr
   1, if sqrt is found
*/
int cpGFpSqrt(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGF)
{
   int elemLen = GFP_FELEN(pGF);
   int poolelementLen = GFP_PELEN(pGF);
   int resultFlag = 1;

   /* case A==0 */
   if( GFP_IS_ZERO(pA, elemLen) )
      cpGFpElementPadd(pR, elemLen, 0);

   /* general case */
   else {
      BNU_CHUNK_T* q = cpGFpGetPool(4, pGF);
      BNU_CHUNK_T* x = q + poolelementLen;
      BNU_CHUNK_T* y = x + poolelementLen;
      BNU_CHUNK_T* z = y + poolelementLen;

      int s;

      /* z=1 */
      GFP_ONE(z, elemLen);

      /* (modulus-1) = 2^s*q */
      cpSub_BNU(q, GFP_MODULUS(pGF), z, elemLen);
      s = factor2(q, elemLen);

      /*
      // initialization
      */

      /* y = qnr^q */
      cpGFpExp(y, GFP_QNR(pGF), q,elemLen, pGF);
      /* x = a^((q-1)/2) */
      cpSub_BNU(q, q, z, elemLen);
      cpLSR_BNU(q, q, elemLen, 1);
      cpGFpExp(x, pA, q, elemLen, pGF);
      /* z = a*x^2 */
      pGF->mul(z, x, x, pGF);
      pGF->mul(z, pA, z, pGF);
      /* R = a*x */
      pGF->mul(pR, pA, x, pGF);

      while( !GFP_EQ(z, MNT_1(GFP_MONT(pGF)), elemLen) ) {
         int m = 0;
         cpGFpElementCopy(q, z, elemLen);

         for(m=1; m<s; m++) {
            pGF->mul(q, q, q, pGF);
            if( GFP_EQ(q, MNT_1(GFP_MONT(pGF)), elemLen) )
               break;
         }

         if(m==s) {
            /* A is quadratic non-residue */
            resultFlag = 0;
            break;
         }
         else {
            /* exponent reduction */
            cpGFpExp2(q, y, (s-m-1), pGF);   /* q = y^(2^(s-m-1)) */
            pGF->mul(y, q, q, pGF);          /* y = q^2 */
            pGF->mul(pR, q, pR, pGF);        /* R = q*R */
            pGF->mul(z, y, z, pGF);          /* z = z*y */
            s = m;
         }
      }

      /* choose smallest between R and (modulus-R) */
      cpGFpMontDecode(q, pR, elemLen, GFP_MONT(pGF));
      if(GFP_GT(q, GFP_HMODULUS(pGF), elemLen))
         pGF->neg(pR, pR, pGF);

      cpGFpReleasePool(4, pGF);
   }

   return resultFlag;
}


BNU_CHUNK_T* cpGFpRand(BNU_CHUNK_T* pR, IppsGFpState* pGF, IppBitSupplier rndFunc, void* pRndParam, int montSpace)
{
   int elemLen = GFP_FELEN(pGF);
   int reqBitSize = GFP_FEBITSIZE(pGF)+GF_RAND_ADD_BITS;
   int nsR = (reqBitSize +BITSIZE(BNU_CHUNK_T)-1)/BITSIZE(BNU_CHUNK_T);

   BNU_CHUNK_T* pPool = cpGFpGetPool(2, pGF);
   cpGFpElementPadd(pPool, nsR, 0);
   rndFunc((Ipp32u*)pPool, reqBitSize, pRndParam);

   nsR = cpMod_BNU(pPool, nsR, GFP_MODULUS(pGF), elemLen);
   cpGFpElementPadd(pPool+nsR, elemLen-nsR, 0);
   if(montSpace)
      cpGFpMontEncode(pR, pPool, elemLen, GFP_MONT(pGF));
   else
      cpGFpElementCopy(pR, pPool, elemLen);

   cpGFpReleasePool(2, pGF);
   return pR;
}
