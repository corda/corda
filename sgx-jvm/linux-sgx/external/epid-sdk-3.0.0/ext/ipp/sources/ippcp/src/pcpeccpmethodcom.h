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
//     Definitions of EC methods over common GF(p)
// 
// 
*/

#if !defined(_PCP_ECCPMETHODCOM_H)
#define _PCP_ECCPMETHODCOM_H

#include "pcpeccp.h"


/*
// Returns reference
*/
ECCP_METHOD* ECCPcom_Methods(void);

/*
// Copy
*/
void ECCP_CopyPoint(const IppsECCPPointState* pSrc, IppsECCPPointState* pDst);

/*
// Point Set. These operations implies
// transformation of regular coordinates into internal format
*/
void ECCP_SetPointProjective(const IppsBigNumState* pX,
                             const IppsBigNumState* pY,
                             const IppsBigNumState* pZ,
                             IppsECCPPointState* pPoint,
                             const IppsECCPState* pECC);

void ECCP_SetPointAffine(const IppsBigNumState* pX,
                         const IppsBigNumState* pY,
                         IppsECCPPointState* pPoint,
                         const IppsECCPState* pECC);

/*
// Get Point. These operations implies
// transformation of internal format coordinates into regular
*/
//void ECCP_GetPointProjective(IppsBigNumState* pX,
//                             IppsBigNumState* pY,
//                             IppsBigNumState* pZ,
//                             const IppsECCPPointState* pPoint,
//                             const IppsECCPState* pECC);

void ECCP_GetPointAffine(IppsBigNumState* pX,
                         IppsBigNumState* pY,
                         const IppsECCPPointState* pPoint,
                         const IppsECCPState* pECC,
                         BigNumNode* pList);

/*
// Set To Infinity
*/
void ECCP_SetPointToInfinity(IppsECCPPointState* pPoint);
void ECCP_SetPointToAffineInfinity0(IppsBigNumState* pX, IppsBigNumState* pY);
void ECCP_SetPointToAffineInfinity1(IppsBigNumState* pX, IppsBigNumState* pY);

/*
// Test Is At Infinity
// Test is On EC
*/
int ECCP_IsPointAtInfinity(const IppsECCPPointState* pPoint);
int ECCP_IsPointAtAffineInfinity0(const IppsBigNumState* pX, const IppsBigNumState* pY);
int ECCP_IsPointAtAffineInfinity1(const IppsBigNumState* pX, const IppsBigNumState* pY);
int ECCP_IsPointOnCurve(const IppsECCPPointState* pPoint,
                        const IppsECCPState* pECC,
                        BigNumNode* pList);

/*
// Operations
*/
int ECCP_ComparePoint(const IppsECCPPointState* pP,
                      const IppsECCPPointState* pQ,
                      const IppsECCPState* pECC,
                      BigNumNode* pList);

void ECCP_NegPoint(const IppsECCPPointState* pP,
                   IppsECCPPointState* pR,
                   const IppsECCPState* pECC);

void ECCP_DblPoint(const IppsECCPPointState* pP,
                   IppsECCPPointState* pR,
                   const IppsECCPState* pECC,
                   BigNumNode* pList);

void ECCP_AddPoint(const IppsECCPPointState* pP,
                   const IppsECCPPointState* pQ,
                   IppsECCPPointState* pR,
                   const IppsECCPState* pECC,
                   BigNumNode* pList);

void ECCP_MulPoint(const IppsECCPPointState* pP,
                   const IppsBigNumState* pK,
                   IppsECCPPointState* pR,
                   const IppsECCPState* pECC,
                   BigNumNode* pList);

void ECCP_MulBasePoint(const IppsBigNumState* pK,
                    IppsECCPPointState* pR,
                    const IppsECCPState* pECC,
                    BigNumNode* pList);

void ECCP_ProdPoint(const IppsECCPPointState* pP,
                    const IppsBigNumState*    bnPscalar,
                    const IppsECCPPointState* pQ,
                    const IppsBigNumState*    bnQscalar,
                    IppsECCPPointState* pR,
                    const IppsECCPState* pECC,
                    BigNumNode* pList);

#endif /* _PCP_ECCPMETHODCOM_H */
