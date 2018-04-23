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
//     EC (prime) Point
// 
//  Contents:
//     ippsECCPPointGetSize()
//     ippsECCPPointInit()
// 
// 
*/

#include "precomp.h"
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
