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
//     Intel(R) Performance Primitives. Cryptography Primitives.
//     Internal EC over GF(p^m) basic Definitions & Function Prototypes
// 
// 
*/

#if !defined(_CP_ECGFP_H_)
#define _CP_ECGFP_H_

#include "pcpgfpstuff.h"
#include "pcpgfpxstuff.h"


/*
// EC over GF(p) Point context
*/
typedef struct _cpGFpECPoint {
   IppCtxId     idCtx;  /* EC Point identifier     */
   int          flags;  /* flags: affine           */
   int    elementSize;  /* size of each coordinate */
   BNU_CHUNK_T*   pData;  /* coordinatex X, Y, Z     */
} cpGFPECPoint;

/*
// Contetx Access Macros
*/
#define ECP_POINT_ID(ctx)       ((ctx)->idCtx)
#define ECP_POINT_FLAGS(ctx)    ((ctx)->flags)
#define ECP_POINT_FELEN(ctx)    ((ctx)->elementSize)
#define ECP_POINT_DATA(ctx)     ((ctx)->pData)
#define ECP_POINT_X(ctx)        ((ctx)->pData)
#define ECP_POINT_Y(ctx)        ((ctx)->pData+(ctx)->elementSize)
#define ECP_POINT_Z(ctx)        ((ctx)->pData+(ctx)->elementSize*2)
#define ECP_POINT_TEST_ID(ctx)  (ECP_POINT_ID((ctx))==idCtxGFPPoint)

/* point flags */
#define ECP_AFFINE_POINT   (1)
#define ECP_FINITE_POINT   (2)

#define  IS_ECP_AFFINE_POINT(ctx)      (ECP_POINT_FLAGS((ctx))&ECP_AFFINE_POINT)
#define SET_ECP_AFFINE_POINT(ctx)      (ECP_POINT_FLAGS((ctx))|ECP_AFFINE_POINT)
#define SET_ECP_PROJECTIVE_POINT(ctx)  (ECP_POINT_FLAGS((ctx))&~ECP_AFFINE_POINT)

#define  IS_ECP_FINITE_POINT(ctx)      (ECP_POINT_FLAGS((ctx))&ECP_FINITE_POINT)
#define SET_ECP_FINITE_POINT(ctx)      (ECP_POINT_FLAGS((ctx))|ECP_FINITE_POINT)
#define SET_ECP_INFINITE_POINT(ctx)    (ECP_POINT_FLAGS((ctx))&~ECP_FINITE_POINT)

/*
// define using projective coordinates
*/
#define JACOBIAN        (0)
#define HOMOGENEOUS     (1)
#define ECP_PROJECTIVE_COORD  JACOBIAN
//#define ECP_PROJECTIVE_COORD  HOMOGENEOUS

#if (ECP_PROJECTIVE_COORD== JACOBIAN)
   #pragma message ("ECP_PROJECTIVE_COORD = JACOBIAN")
#elif (ECP_PROJECTIVE_COORD== HOMOGENEOUS)
   #pragma message ("ECP_PROJECTIVE_COORD = HOMOGENEOUS")
#else
   #error ECP_PROJECTIVE_COORD should be either JACOBIAN or HOMOGENEOUS type
#endif

#define _EPID20_EC_PARAM_SPECIFIC_

#if defined(_EPID20_EC_PARAM_SPECIFIC_)
#pragma message ("_EPID20_EC_PARAM_SPECIFIC_")
#endif


/* EC over GF(p) context */
typedef struct _cpGFpEC {
   IppCtxId     idCtx;  /* EC identifier */

   IppsGFpState*  pGF;  /* arbitrary GF(p^d)*/

   int     elementSize;  /* size of point's coordinate */
   int    orderBitSize;  /* base_point order bitsize */
// int        cofactor;  /* cofactor = #E/base_point order */
   int      epidParams;  /* Intel(R) EPID 2.0 specific parameters */
   BNU_CHUNK_T*       pA;  /*   EC parameter A */
   BNU_CHUNK_T*       pB;  /*                B */
   BNU_CHUNK_T*       pG;  /*       base_point */
   BNU_CHUNK_T*       pR;  /* base_point order */
   BNU_CHUNK_T* cofactor;  /* cofactor = #E/base_point order */
   BNU_CHUNK_T*    pPool;  /* pool of points   */
} cpGFPEC;

#define ECGFP_ALIGNMENT   ((int)(sizeof(void*)))

/* Local definitions */
#define EC_POOL_SIZE       (8)  /* num of points into the pool */

#define ECP_ID(pCtx)          ((pCtx)->idCtx)
#define ECP_GFP(pCtx)         ((pCtx)->pGF)
#define ECP_FELEN(pCtx)       ((pCtx)->elementSize)
#define ECP_ORDBITSIZE(pCtx)  ((pCtx)->orderBitSize)
#define ECP_COFACTOR(pCtx)    ((pCtx)->cofactor)
#define EPID_PARAMS(pCtx)     ((pCtx)->epidParams)
#define ECP_A(pCtx)           ((pCtx)->pA)
#define ECP_B(pCtx)           ((pCtx)->pB)
#define ECP_G(pCtx)           ((pCtx)->pG)
#define ECP_R(pCtx)           ((pCtx)->pR)
#define ECP_POOL(pCtx)        ((pCtx)->pPool)

#define ECP_TEST_ID(pCtx)     (ECP_ID((pCtx))==idCtxGFPEC)

/*
// get/release n points from/to the pool
*/
__INLINE BNU_CHUNK_T* cpEcGFpGetPool(int n, IppsGFpECState* pEC)
{
   BNU_CHUNK_T* pPool = ECP_POOL(pEC);
   ECP_POOL(pEC) += n*GFP_FELEN(ECP_GFP(pEC))*3;
   return pPool;
}
__INLINE void cpEcGFpReleasePool(int n, IppsGFpECState* pEC)
{
   ECP_POOL(pEC) -= n*GFP_FELEN(ECP_GFP(pEC))*3;
}

__INLINE IppsGFpECPoint* cpEcGFpInitPoint(IppsGFpECPoint* pPoint, BNU_CHUNK_T* pData, int flags, const IppsGFpECState* pEC)
{
   ECP_POINT_ID(pPoint) = idCtxGFPPoint;
   ECP_POINT_FLAGS(pPoint) = flags;
   ECP_POINT_FELEN(pPoint) = GFP_FELEN(ECP_GFP(pEC));
   ECP_POINT_DATA(pPoint) = pData;
   return pPoint;
}

/*
// copy one point into another
*/
__INLINE IppsGFpECPoint* cpEcGFpCopyPoint(IppsGFpECPoint* pPointR, const IppsGFpECPoint* pPointA, int elemLen)
{
   cpGFpElementCopy(ECP_POINT_DATA(pPointR), ECP_POINT_DATA(pPointA), 3*elemLen);
   ECP_POINT_FLAGS(pPointR) = ECP_POINT_FLAGS(pPointA);
   return pPointR;
}

/*
// set point (convert into inside representation)
//    SetProjectivePoint
//    SetProjectivePointAtInfinity
//    SetAffinePoint
*/
__INLINE IppsGFpECPoint* cpEcGFpSetProjectivePoint(IppsGFpECPoint* pPoint,
                                               const BNU_CHUNK_T* pX, const BNU_CHUNK_T* pY, const BNU_CHUNK_T* pZ,
                                               IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);
   int pointFlag = 0;

   cpGFpxSet(ECP_POINT_X(pPoint), pX, elemLen, pGF, USE_MONT_SPACE_REPRESENTATION);
   cpGFpxSet(ECP_POINT_Y(pPoint), pY, elemLen, pGF, USE_MONT_SPACE_REPRESENTATION);
   cpGFpxSet(ECP_POINT_Z(pPoint), pZ, elemLen, pGF, USE_MONT_SPACE_REPRESENTATION);

   if(!GFP_IS_ZERO(pZ, elemLen)) pointFlag |= ECP_FINITE_POINT;
   if(GFP_IS_ONE(pZ, elemLen))  pointFlag |= ECP_AFFINE_POINT;
   ECP_POINT_FLAGS(pPoint) = pointFlag;
   return pPoint;
}
__INLINE IppsGFpECPoint* cpEcGFpSetProjectivePointAtInfinity(IppsGFpECPoint* pPoint, int elemLen)
{
   cpGFpElementPadd(ECP_POINT_X(pPoint), elemLen, 0);
   cpGFpElementPadd(ECP_POINT_Y(pPoint), elemLen, 0);
   cpGFpElementPadd(ECP_POINT_Z(pPoint), elemLen, 0);
   ECP_POINT_FLAGS(pPoint) = 0;
   return pPoint;
}
__INLINE IppsGFpECPoint* cpEcGFpSetAffinePoint(IppsGFpECPoint* pPoint,
                                           const BNU_CHUNK_T* pX, const BNU_CHUNK_T* pY,
                                           IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   IppsGFpState* pBasicGF = cpGFpBasic(pGF);

   cpGFpElementCopy(ECP_POINT_X(pPoint), pX, GFP_FELEN(pGF));
   cpGFpElementCopy(ECP_POINT_Y(pPoint), pY, GFP_FELEN(pGF));
   cpGFpElementCopyPadd(ECP_POINT_Z(pPoint), GFP_FELEN(pGF), MNT_1(GFP_MONT(pBasicGF)), GFP_FELEN(pBasicGF));
   ECP_POINT_FLAGS(pPoint) = ECP_AFFINE_POINT | ECP_FINITE_POINT;
   return pPoint;
}

/*
// test infinity:
//    IsProjectivePointAtInfinity
*/
__INLINE int cpEcGFpIsProjectivePointAtInfinity(const IppsGFpECPoint* pPoint, Ipp32u elemLen)
{
   return GFP_IS_ZERO( ECP_POINT_Z(pPoint), elemLen );
}

/*
// get point (convert from inside representation)
//    GetProjectivePoint
//    GetAffinePointAtInfinity0 (B==0)
//    GetAffinePointAtInfinity1 (B!=0)
//    GetAffinePoint
*/
__INLINE void cpEcGFpGetProjectivePoint(BNU_CHUNK_T* pX, BNU_CHUNK_T* pY, BNU_CHUNK_T* pZ,
                                const IppsGFpECPoint* pPoint,
                                      IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   cpGFpxGet(pX, GFP_FELEN(pGF), ECP_POINT_X(pPoint), pGF, USE_MONT_SPACE_REPRESENTATION);
   cpGFpxGet(pY, GFP_FELEN(pGF), ECP_POINT_Y(pPoint), pGF, USE_MONT_SPACE_REPRESENTATION);
   cpGFpxGet(pZ, GFP_FELEN(pGF), ECP_POINT_Z(pPoint), pGF, USE_MONT_SPACE_REPRESENTATION);
}
#if 0
__INLINE void cpEcGFpGetAffinePointAtInfinity0(BNU_CHUNK_T* pX, BNU_CHUNK_T* pY, int elemLen)
{
   GFP_ZERO(pX, elemLen);
   GFP_ONE(pY, elemLen);
}
__INLINE void cpEcGFpGetAffinePointAtInfinity1(BNU_CHUNK_T* pX, BNU_CHUNK_T* pY, int elemLen)
{
   GFP_ZERO(pX, elemLen);
   GFP_ZERO(pY, elemLen);
}
#endif


/* signed encode */
__INLINE void booth_recode(Ipp8u* sign, Ipp8u* digit, Ipp8u in, int w)
{
   Ipp8u s = ~((in >> w) - 1);
   int d = (1 << (w+1)) - in - 1;
   d = (d & s) | (in & ~s);
   d = (d >> 1) + (d & 1);
   *sign = s & 1;
   *digit = (Ipp8u)d;
}

/* mask of the argument:
   if x==0  returns 0
   if x!=0  returns BNU_CHUNK_T(-1)
*/
__INLINE BNU_CHUNK_T cpIsNonZeroMask(BNU_CHUNK_T x)
{
   #if(_IPP_ARCH==_IPP_ARCH_EM64T)
   x |= x>>32;
   #endif
   x |= x>>16;
   x |= x>>8;
   x |= x>>4;
   x |= x>>2;
   x |= x>>1;
   return 0-(x&1);
}

/* dst[] = src[], iif moveFlag!=0 */
__INLINE void cpMaskMove(BNU_CHUNK_T* dst, const BNU_CHUNK_T* src, int len, int moveFlag)
{
   BNU_CHUNK_T mask1 = cpIsNonZeroMask(moveFlag);
   BNU_CHUNK_T mask2 = ~mask1;
   int n;
   for(n=0; n<len; n++)
      dst[n] = (src[n] & mask1) ^  (dst[n] & mask2);
}

__INLINE void cpScatter32(Ipp32u* pTbl, int scale, int idx, const Ipp32u* pData, int len)
{
   int i;
   pTbl += idx;
   for(i=0; i<len; i++, pTbl+=scale, pData++)
      pTbl[0] = pData[0];
}

__INLINE void cpGather32(Ipp32u* pData, int len, const Ipp32u* pTbl, int scale, int idx)
{
   Ipp32u mask = (Ipp32u)cpIsNonZeroMask(idx);
   int i;
   idx = (idx & mask) | (1 & (~mask)); /* set idx=1 if input idx==0 */
   pTbl += (idx-1);
   for(i=0; i<len; i++, pTbl+=scale, pData++) pData[0] = pTbl[0] & mask;
}

/*
// other point operations
*/
int cpEcGFpGetAffinePoint(BNU_CHUNK_T* pX, BNU_CHUNK_T* pY, const IppsGFpECPoint* pPoint, IppsGFpECState* pEC);

int cpEcGFpMakePoint(IppsGFpECPoint* pPoint, const BNU_CHUNK_T* pElm, IppsGFpECState* pEC);

int cpEcGFpIsPointEquial(const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ, IppsGFpECState* pEC);
int cpEcGFpIsPointOnCurve(const IppsGFpECPoint* pP, IppsGFpECState* pEC);
int cpEcGFpIsPointInGroup(const IppsGFpECPoint* pP, IppsGFpECState* pEC);

IppsGFpECPoint* cpEcGFpNegPoint(IppsGFpECPoint* pR, const IppsGFpECPoint* pP, IppsGFpECState* pEC);
IppsGFpECPoint* cpEcGFpDblPoint(IppsGFpECPoint* pR, const IppsGFpECPoint* pP, IppsGFpECState* pEC);
IppsGFpECPoint* cpEcGFpAddPoint(IppsGFpECPoint* pR, const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ, IppsGFpECState* pEC);

int  cpEcGFpGetOptimalWinSize(int scalarBitsize);

IppsGFpECPoint* cpEcGFpMulPoint(IppsGFpECPoint* pR,
                          const IppsGFpECPoint* pP, const BNU_CHUNK_T* pN, int nsN,
                          IppsGFpECState* pEC, Ipp8u* pScratchBuffer);

#endif /* _CP_ECGFP_H_ */
