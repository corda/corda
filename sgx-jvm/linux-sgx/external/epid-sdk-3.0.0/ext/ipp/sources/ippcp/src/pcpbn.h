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
//               Intel(R) Integrated Performance Primitives
//                   Cryptographic Primitives (ippcp)
// 
// 
// 
*/

#if !defined(_CP_BN_H)
#define _CP_BN_H

#include "pcpbnuimpl.h"
#include "pcpbnuarith.h"
#include "pcpbnumisc.h"
#include "pcpbnu32arith.h"
#include "pcpbnu32misc.h"

/*
// Big Number context
*/
struct _cpBigNum
{
   IppCtxId      idCtx;    /* BigNum ctx id                 */
   IppsBigNumSGN sgn;      /* sign                          */
   cpSize        size;     /* BigNum size (BNU_CHUNK_T)     */
   cpSize        room;     /* BigNum max size (BNU_CHUNK_T) */
   BNU_CHUNK_T*  number;   /* BigNum value                  */
   BNU_CHUNK_T*  buffer;   /* temporary buffer              */
};

/* BN accessory macros */
#define BN_ID(pBN)         ((pBN)->idCtx)
#define BN_SIGN(pBN)       ((pBN)->sgn)
#define BN_POSITIVE(pBN)   (BN_SIGN(pBN)==ippBigNumPOS)
#define BN_NEGATIVE(pBN)   (BN_SIGN(pBN)==ippBigNumNEG)
#define BN_NUMBER(pBN)     ((pBN)->number)
#define BN_BUFFER(pBN)     ((pBN)->buffer)
#define BN_ROOM(pBN)       ((pBN)->room)
#define BN_SIZE(pBN)       ((pBN)->size)
#define BN_SIZE32(pBN)     ((pBN)->size*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u))
//#define BN_SIZE32(pBN)     (BITS2WORD32_SIZE( BITSIZE_BNU(BN_NUMBER((pBN)),BN_SIZE((pBN)))))

#define BN_VALID_ID(pBN)   (BN_ID((pBN))==idCtxBigNum)

#define INVERSE_SIGN(s)    (((s)==ippBigNumPOS)? ippBigNumNEG : ippBigNumPOS)

#define BN_ALIGNMENT       ((int)sizeof(void*))


/* pack-unpack context */
void cpPackBigNumCtx(const IppsBigNumState* pBN, Ipp8u* pBuffer);
void cpUnpackBigNumCtx(const Ipp8u* pBuffer, IppsBigNumState* pBN);

/* copy BN */
__INLINE IppsBigNumState* cpBN_copy(IppsBigNumState* pDst, const IppsBigNumState* pSrc)
{
   BN_SIGN(pDst) = BN_SIGN(pSrc);
   BN_SIZE(pDst) = BN_SIZE(pSrc);
   ZEXPAND_COPY_BNU(BN_NUMBER(pDst), BN_ROOM(pDst), BN_NUMBER(pSrc), BN_SIZE(pSrc));
   return pDst;
}
/* set BN to zero */
__INLINE IppsBigNumState* cpBN_zero(IppsBigNumState* pBN)
{
   BN_SIGN(pBN)   = ippBigNumPOS;
   BN_SIZE(pBN)   = 1;
   ZEXPAND_BNU(BN_NUMBER(pBN),0, (int)BN_ROOM(pBN));
   return pBN;
}
/* fixup BN */
__INLINE IppsBigNumState* cpBN_fix(IppsBigNumState* pBN)
{
   cpSize len = BN_SIZE(pBN);
   FIX_BNU(BN_NUMBER(pBN), len);
   BN_SIZE(pBN) = len;
   return pBN;
}
/* set BN to chunk */
__INLINE IppsBigNumState* cpBN_chunk(IppsBigNumState* pBN, BNU_CHUNK_T a)
{
   BN_SIGN(pBN)   = ippBigNumPOS;
   BN_SIZE(pBN)   = 1;
   ZEXPAND_BNU(BN_NUMBER(pBN),0, (int)BN_ROOM(pBN));
   BN_NUMBER(pBN)[0] = a;
   return pBN;
}
/* set BN to 2^m */
__INLINE IppsBigNumState* cpBN_power2(IppsBigNumState* pBN, int power)
{
   cpSize size = BITS_BNU_CHUNK(power+1);
   if(BN_ROOM(pBN) >= size) {
      BN_SIGN(pBN) = ippBigNumPOS;
      BN_SIZE(pBN) = size;
      ZEXPAND_BNU(BN_NUMBER(pBN),0, BN_ROOM(pBN));
      SET_BIT(BN_NUMBER(pBN), power);
      return pBN;
   }
   else return NULL;
}

/* bitsize of BN */
__INLINE int cpBN_bitsize(const IppsBigNumState* pA)
{
   int bitsize =  BITSIZE_BNU(BN_NUMBER(pA), BN_SIZE(pA));
   return bitsize;
}

/* returns -1/0/+1 depemding on A~B comparison */
__INLINE int cpBN_cmp(const IppsBigNumState* pA, const IppsBigNumState* pB)
{
   IppsBigNumSGN signA = BN_SIGN(pA);
   IppsBigNumSGN signB = BN_SIGN(pB);

   if(signA==signB) {
      int result = cpCmp_BNU(BN_NUMBER(pA), BN_SIZE(pA), BN_NUMBER(pB), BN_SIZE(pB));
      return (ippBigNumPOS==signA)? result : -result;
   }
   return (ippBigNumPOS==signA)? 1 : -1;
}

/* returns -1/0/+1 depemding on A comparison  0</==0/>0 */
__INLINE int cpBN_tst(const IppsBigNumState* pA)
{
   if(1==BN_SIZE(pA) && 0==BN_NUMBER(pA)[0])
      return 0;
   else
      return BN_POSITIVE(pA)? 1 : -1;
}


// some addtition functions
__INLINE int IsZero_BN(const IppsBigNumState* pA)
{
   return ( BN_SIZE(pA)==1 ) && ( BN_NUMBER(pA)[0]==0 );
}
__INLINE int IsOdd_BN(const IppsBigNumState* pA)
{
   return BN_NUMBER(pA)[0] & 1;
}

__INLINE IppsBigNumState* BN_Word(IppsBigNumState* pBN, BNU_CHUNK_T w)
{
   BN_SIGN(pBN)   = ippBigNumPOS;
   BN_SIZE(pBN)   = 1;
   ZEXPAND_BNU(BN_NUMBER(pBN),0, BN_ROOM(pBN));
   BN_NUMBER(pBN)[0] = w;
   return pBN;
}
__INLINE IppsBigNumState* BN_Set(const BNU_CHUNK_T* pData, cpSize len, IppsBigNumState* pBN)
{
   BN_SIGN(pBN)   = ippBigNumPOS;
   BN_SIZE(pBN)   = len;
   ZEXPAND_COPY_BNU(BN_NUMBER(pBN), BN_ROOM(pBN), pData, len);
   return pBN;
}
__INLINE IppsBigNumState* BN_Make(BNU_CHUNK_T* pData, BNU_CHUNK_T* pBuffer, cpSize len, IppsBigNumState* pBN)
{
   BN_ID(pBN)   = idCtxBigNum;
   BN_SIGN(pBN) = ippBigNumPOS;
   BN_SIZE(pBN) = 1;
   BN_ROOM(pBN) = len;
   BN_NUMBER(pBN) = pData;
   BN_BUFFER(pBN) = pBuffer;
   return pBN;
}



/*
// fixed single chunk BN
*/
typedef struct _ippcpBigNumChunk {
   IppsBigNumState   bn;
   BNU_CHUNK_T       value;
   BNU_CHUNK_T       temporary;
} IppsBigNumStateChunk;

/* reference to BN(1) and BN(2) */
IppsBigNumState* cpBN_OneRef(void);
IppsBigNumState* cpBN_TwoRef(void);
IppsBigNumState* cpBN_ThreeRef(void);

#define BN_ONE_REF()  cpBN_OneRef()
#define BN_TWO_REF()  cpBN_TwoRef()
#define BN_THREE_REF()  cpBN_ThreeRef()

#endif /* _CP_BN_H */
