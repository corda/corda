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

#if !defined(_PCP_ECCP_METHOD_H)
#define _PCP_ECCP_METHOD_H

/*
// Point Operation Prototypes
*/
struct eccp_method_st {
    void (*SetPointProjective)(const IppsBigNumState* pX,
                              const IppsBigNumState* pY,
                              const IppsBigNumState* pZ,
                              IppsECCPPointState* pPoint,
                              const IppsECCPState* pECC);
   void (*SetPointAffine)(const IppsBigNumState* pX,
                          const IppsBigNumState* pY,
                          IppsECCPPointState* pPoint,
                          const IppsECCPState* pECC);

   void (*GetPointAffine)(IppsBigNumState* pX,
                          IppsBigNumState* pY,
                          const IppsECCPPointState* pPoint,
                          const IppsECCPState* pECC,
                          BigNumNode* pList);

   int (*IsPointOnCurve)(const IppsECCPPointState* pPoint,
                         const IppsECCPState* pECC,
                         BigNumNode* pList);

   int (*ComparePoint)(const IppsECCPPointState* pP,
                       const IppsECCPPointState* pQ,
                       const IppsECCPState* pECC,
                       BigNumNode* pList);
   void (*NegPoint)(const IppsECCPPointState* pP,
                    IppsECCPPointState* pR,
                    const IppsECCPState* pECC);
   void (*DblPoint)(const IppsECCPPointState* pP,
                    IppsECCPPointState* pR,
                    const IppsECCPState* pECC,
                    BigNumNode* pList);
   void (*AddPoint)(const IppsECCPPointState* pP,
                    const IppsECCPPointState* pQ,
                    IppsECCPPointState* pR,
                    const IppsECCPState* pECC,
                    BigNumNode* pList);
   void (*MulPoint)(const IppsECCPPointState* pP,
                    const IppsBigNumState* pK,
                    IppsECCPPointState* pR,
                    const IppsECCPState* pECC,
                    BigNumNode* pList);
   void (*MulBasePoint)(const IppsBigNumState* pK,
                    IppsECCPPointState* pR,
                    const IppsECCPState* pECC,
                    BigNumNode* pList);
   void (*ProdPoint)(const IppsECCPPointState* pP,
                     const IppsBigNumState*    bnPscalar,
                     const IppsECCPPointState* pQ,
                     const IppsBigNumState*    bnQscalar,
                     IppsECCPPointState* pR,
                     const IppsECCPState* pECC,
                     BigNumNode* pList);
};

#endif /* _PCP_ECCP_METHOD_H */
