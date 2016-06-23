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
#include "pcpeccppoint.h"


/*F*
//    Name: ippsECCPPointGetSize
//
// Purpose: Returns size of EC Point context (bytes).
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pSzie
//    ippStsSizeErr           2>feBitSize
//    ippStsNoErr             no errors
//
// Parameters:
//    feBitSize   size of field element (bits)
//    pSize       pointer to the size of EC Point context
//
*F*/
IPPFUN(IppStatus, ippsECCPPointGetSize, (int feBitSize, int* pSize))
{
   /* test size's pointer */
   IPP_BAD_PTR1_RET(pSize);

   /* test size of field element */
   IPP_BADARG_RET((2>feBitSize), ippStsSizeErr);

   {
      int bnSize;
      ippsBigNumGetSize(BITS2WORD32_SIZE(feBitSize), &bnSize);
      *pSize = sizeof(IppsECCPPointState)
              + bnSize              /* X coodinate */
              + bnSize              /* Y coodinate */
              + bnSize              /* Z coodinate */
              +(ALIGN_VAL-1);
   }
   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPPointInit
//
// Purpose: Init EC Point context.
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pPoint
//    ippStsSizeErr           2>feBitSize
//    ippStsNoErr             no errors
//
// Parameters:
//    feBitSize   size of field element (bits)
//    pECC        pointer to ECC context
//
*F*/
IPPFUN(IppStatus, ippsECCPPointInit, (int feBitSize, IppsECCPPointState* pPoint))
{
   /* test pEC pointer */
   IPP_BAD_PTR1_RET(pPoint);

   /* use aligned context */
   pPoint = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPoint, ALIGN_VAL) );

   /* test size of field element */
   IPP_BADARG_RET((2>feBitSize), ippStsSizeErr);

   /* context ID */
   ECP_POINT_ID(pPoint) = idCtxECCPPoint;

   /* meaning: point was not set */
   ECP_POINT_AFFINE(pPoint) =-1;

   /*
   // init other context fields
   */
   {
      Ipp8u* ptr = (Ipp8u*)pPoint;
      int bnLen  = BITS2WORD32_SIZE(feBitSize);
      int bnSize;
      ippsBigNumGetSize(bnLen, &bnSize);

      /* allocate coordinate buffers */
      ptr += sizeof(IppsECCPPointState);
      ECP_POINT_X(pPoint) = (IppsBigNumState*)( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bnSize;
      ECP_POINT_Y(pPoint) = (IppsBigNumState*)( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );
      ptr += bnSize;
      ECP_POINT_Z(pPoint) = (IppsBigNumState*)( IPP_ALIGNED_PTR(ptr,ALIGN_VAL) );

      /* init coordinate buffers */
      ippsBigNumInit(bnLen, ECP_POINT_X(pPoint));
      ippsBigNumInit(bnLen, ECP_POINT_Y(pPoint));
      ippsBigNumInit(bnLen, ECP_POINT_Z(pPoint));
   }
   return ippStsNoErr;
}
