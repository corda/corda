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
//     Internal Prime Modulo Arithmetic Function
// 
// 
*/

#include "precomp.h"
#include "owncp.h"

#if (_ECP_SM2_==_ECP_IMPL_SPECIFIC_)
#include "pcpeccp.h"
#include "pcppmasm2.h"


/*
// Specific Modulo Arithmetic
//    P256 = 2^256 -2^224 -2^96 +2^64 -1
//    (reference tpmSM2_p256_p)
*/

/*
// Reduce modulo:
//
//  x = c15|c14|c13|c12|c11|c10|c09|c08|c07|c06|c05|c04|c03|c02|c01|c00 - 32-bits values
//
//                 r7    r6    r5    r4    r3    r2    r1    r0
//  c08 deposit: | c08 | 000 | 000 | 000 | c08 |-c08 | 000 | c08 |
//  c09 deposit: | c09 | 000 | 000 | c09 | 000 |-c09 | c09 | c09 |
//  c10 deposit: | c10 | 000 | c10 | 000 | 000 | 000 | c10 | c10 |
//  c11 deposit: | c11 | c11 | 000 | 000 | c11 | 000 | c11 | c11 |
//  c12 deposit: |2*c12| 000 | 000 | c12 | c12 | 000 | c12 | c12 |
//  c13 deposit: |2*c13| 000 | c13 | c13 |2*c13|-c13 | c13 |2*c13|
//  c14 deposit: |2*c14| c14 | c14 |2*c14| c14 |-c14 |2*c14|2*c14|
//  c15 deposit: |3*c15| c15 |2*c15| c15 | c15 | 000 |2*c15|2*c15|
//
*/
//#if !((_IPP==_IPP_W7) || (_IPP==_IPP_T7) || \
//      (_IPP==_IPP_V8) || (_IPP==_IPP_P8) || \
//      (_IPPLP32==_IPPLP32_S8) || (_IPP>=_IPP_G9) )
#if (_IPP < _IPP_W7)
void Reduce_SM2(BNU_CHUNK_T* pProduct)
{
   Ipp32u* pR = (Ipp32u*)pProduct;

   Ipp64u t0 = (Ipp64u)pR[ 8] + pR[ 9] + pR[10] + pR[11] + pR[12];
   Ipp64u w0 = (Ipp64u)pR[13] +pR[14] + pR[15];
   Ipp64u u0 = w0<<1;

   Ipp64s
   sum = (Ipp64u)pR[ 0] +t0 + u0;
   pR[0] = LODWORD(sum);
   sum >>= 32;

   sum += (Ipp64u)pR[ 1] +(t0-pR[8]) +(u0-pR[13]);
   pR[1] = LODWORD(sum);
   sum >>= 32;

   //sum += (Ipp64u)pR[ 2] - (pR[8]+pR[9]) - (w0-pR[15]);
   sum += (Ipp64u)pR[ 2] - pR[8] -pR[9] - (w0-pR[15]);
   pR[2] = LODWORD(sum);
   sum >>= 32;

   sum += (Ipp64u)pR[ 3] +pR[ 8] +pR[11] +pR[12] +(w0+pR[13]);
   pR[3] = LODWORD(sum);
   sum >>= 32;

   sum += (Ipp64u)pR[ 4] +pR[ 9] + pR[12] + (w0+pR[14]);
   pR[4] = LODWORD(sum);
   sum >>= 32;

   sum += (Ipp64u)pR[ 5] +pR[10] +(w0+pR[15]);
   pR[5] = LODWORD(sum);
   sum >>= 32;

   sum += (Ipp64u)pR[ 6] +pR[11] + (w0-pR[13]);
   pR[6] = LODWORD(sum);
   sum >>= 32;

   sum += (Ipp64u)pR[ 7] + (t0+pR[12]) + (u0+pR[15]);
   pR[7] = LODWORD(sum);
   sum >>= 32;
   pProduct[LEN_P256] = (BNU_CHUNK_T)(sum);

   while(((BNS_CHUNK_T)pProduct[LEN_P256]) <0)
      cpAdd_BNU(pProduct, pProduct, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256+1);

   while(0 <= cpCmp_BNU(pProduct, LEN_P256+1, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256+1))
      cpSub_BNU(pProduct, pProduct, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256+1);
}

#else
#if 0
void Reduce_SM2(BNU_CHUNK_T* pProduct)
{
   Ipp32u* pR = (Ipp32u*)pProduct;

   __m64 s8  = _mm_cvtsi32_si64((Ipp32s)pR[8]);
   __m64 s9  = _mm_cvtsi32_si64((Ipp32s)pR[9]);
   __m64 s10 = _mm_cvtsi32_si64((Ipp32s)pR[10]);
   __m64 s11 = _mm_cvtsi32_si64((Ipp32s)pR[11]);
   __m64 s12 = _mm_cvtsi32_si64((Ipp32s)pR[12]);
   __m64 s13 = _mm_cvtsi32_si64((Ipp32s)pR[13]);
   __m64 s14 = _mm_cvtsi32_si64((Ipp32s)pR[14]);
   __m64 s15 = _mm_cvtsi32_si64((Ipp32s)pR[15]);

   __m64 w0 = _mm_add_si64(s13,
              _mm_add_si64(s14, s15));
   __m64 t0 = _mm_add_si64(s8,
              _mm_add_si64(s9,
              _mm_add_si64(s10,
              _mm_add_si64(s11,
              _mm_add_si64(s12,
              _mm_add_si64(w0, w0))))));

   __m64
   // sum = pR[ 0] +t0 + u0
   sum = _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[0]), t0);
   pR[0] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);

   // sum += pR[ 1] +(t0-pR[8]) +(u0-pR[13])
   sum = _mm_sub_si64(
            _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[1]),
            _mm_add_si64(sum, t0)),
            _mm_add_si64(s8, s13));
   pR[1] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);

   // sum += pR[ 2] - pR[8] -pR[9] - (w0-pR[15])
   sum = _mm_sub_si64(
            _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[2]),
            _mm_add_si64(sum, s15)),
            _mm_add_si64(s8,
            _mm_add_si64(s9, w0)));
   pR[2] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);

   // sum += pR[ 3] +pR[ 8] +pR[11] +pR[12] +(w0+pR[13]);
   sum = _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[3]),
         _mm_add_si64(sum,
         _mm_add_si64(s8,
         _mm_add_si64(s11,
         _mm_add_si64(s12,
         _mm_add_si64(w0, s13))))));
   pR[3] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);

   // sum += pR[ 4] +pR[ 9] + pR[12] + (w0+pR[14]);
   sum = _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[4]),
         _mm_add_si64(sum,
         _mm_add_si64(s9,
         _mm_add_si64(s12,
         _mm_add_si64(w0, s14)))));
   pR[4] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);

   // sum += pR[ 5] +pR[10] +(w0+pR[15]);
   sum = _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[5]),
         _mm_add_si64(sum,
         _mm_add_si64(s10,
         _mm_add_si64(w0, s15))));
   pR[5] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);

   // sum += pR[ 6] +pR[11] + (w0-pR[13]);
   sum = _mm_sub_si64(
            _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[6]),
            _mm_add_si64(sum,
            _mm_add_si64(s11, w0))),
            s13);
   pR[6] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);

   // sum += pR[ 7] + (t0+pR[12]) + (u0+pR[15]);
   sum = _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pR[7]),
         _mm_add_si64(sum,
         _mm_add_si64(t0,
         _mm_add_si64(s12, s15))));
   pR[7] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
   sum = _mm_shuffle_pi16(sum, 0xfe);
   pProduct[LEN_P256] = (BNS_CHUNK_T)( _mm_cvtsi64_si32(sum) );

   {
      int n;
      const Ipp32u* pMx;

      // reduce multiple modulus
      if( pProduct[LEN_P256] ) {
         pMx = tpmSM2_p256_p_mx[ pProduct[LEN_P256] ];
         sum = _mm_setzero_si64();
         for(n=0; n<LEN_P256+1; n++) {
            sum  = _mm_add_si64(sum,
                   _mm_sub_si64(_mm_cvtsi32_si64((Ipp32s)pProduct[n]),
                                _mm_cvtsi32_si64((Ipp32s)pMx[n])));
            pProduct[n] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
            sum = _mm_shuffle_pi16(sum, 0xfe);
         }
      }

      // increase temporary result
      while(((BNS_CHUNK_T)pProduct[LEN_P256]) <0) {
         sum = _mm_setzero_si64();
         for(n=0; n<LEN_P256+1; n++) {
            sum  = _mm_add_si64(sum,
                   _mm_add_si64(_mm_cvtsi32_si64((Ipp32s)pProduct[n]),
                                _mm_cvtsi32_si64((Ipp32s)tpmSM2_p256_p[n])));
            pProduct[n] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
            sum = _mm_shuffle_pi16(sum, 0xfe);
         }
      }

      // reduce temporary result
      if(0 <= cpCmp_BNU(pProduct, LEN_P256+1, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256+1)) {
         sum = _mm_setzero_si64();
         for(n=0; n<LEN_P256+1; n++) {
            sum  = _mm_add_si64(sum,
                   _mm_sub_si64(_mm_cvtsi32_si64((Ipp32s)pProduct[n]),
                                _mm_cvtsi32_si64((Ipp32s)tpmSM2_p256_p[n])));
            pProduct[n] = (Ipp32u)( _mm_cvtsi64_si32(sum) );
            sum = _mm_shuffle_pi16(sum, 0xfe);
         }
      }
   }

   _mm_empty();
}
#endif
#endif


void cpAdde_SM2(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T carry = cpAdd_BNU(rPtr, aPtr, bPtr, LEN_P256);
   if(carry || (0<=cpCmp_BNU(rPtr, LEN_P256, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256)))
      cpSub_BNU(rPtr, rPtr, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

void cpSube_SM2(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T borrow = cpSub_BNU(rPtr, aPtr, bPtr, LEN_P256);
   if(borrow)
      cpAdd_BNU(rPtr, rPtr, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

void cpSqre_SM2(IppsBigNumState* pA, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P256];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpSqr_BNU_school(tmpR, aPtr, LEN_P256);

   Reduce_SM2(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

void cpMule_SM2(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P256];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpMul_BNU_school(tmpR, aPtr, LEN_P256, bPtr, LEN_P256);

   Reduce_SM2(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

#endif
