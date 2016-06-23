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
#include "pcpbnresource.h"
#include "pcpbn.h"

/*
// Size of BigNum List Buffer
*/
int cpBigNumListGetSize(int feBitSize, int nodes)
{
   /* size of buffer per single big number */
   int bnSize;
   ippsBigNumGetSize(BITS2WORD32_SIZE(feBitSize), &bnSize);

   /* size of buffer for whole list */
   return (ALIGN_VAL-1) + (sizeof(BigNumNode) + bnSize) * nodes;
}

/*
// Init list
//
// Note: buffer for BN list must have appropriate alignment
*/
void cpBigNumListInit(int feBitSize, int nodes, BigNumNode* pList)
{
   int itemSize;
   /* length of Big Num */
   int bnLen = BITS2WORD32_SIZE(feBitSize);
   /* size of buffer per single big number */
   ippsBigNumGetSize(bnLen, &itemSize);
   /* size of list item */
   itemSize += sizeof(BigNumNode);

   {
      int n;
      /* init all nodes */
      BigNumNode* pNode = (BigNumNode*)( (Ipp8u*)pList + (nodes-1)*itemSize );
      BigNumNode* pNext = NULL;
      for(n=0; n<nodes; n++) {
         Ipp8u* tbnPtr = (Ipp8u*)pNode + sizeof(BigNumNode);
         pNode->pNext = pNext;
         pNode->pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(tbnPtr, ALIGN_VAL) );
         ippsBigNumInit(bnLen, pNode->pBN);
         pNext = pNode;
         pNode = (BigNumNode*)( (Ipp8u*)pNode - itemSize);
      }
   }
}

/*
// Get BigNum reference
*/
IppsBigNumState* cpBigNumListGet(BigNumNode** ppList)
{
   if(*ppList) {
      IppsBigNumState* ret = (*ppList)->pBN;
      *ppList = (BigNumNode*)((*ppList)->pNext);
      return ret;
   }
   else
      return NULL;
}
