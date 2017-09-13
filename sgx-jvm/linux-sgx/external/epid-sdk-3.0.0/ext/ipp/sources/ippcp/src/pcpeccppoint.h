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
//     Internal EC Point Definitions & Function Prototypes
// 
// 
*/

#if !defined(_PCP_ECCPPOINT_H)
#define _PCP_ECCPPOINT_H

#include "pcpeccp.h"


/*
// EC Point context
*/
struct _cpECCPPoint {
   IppCtxId         idCtx;   /* EC Point identifier      */

   IppsBigNumState* pX;      /* projective X             */
   IppsBigNumState* pY;      /*            Y             */
   IppsBigNumState* pZ;      /*            Z coordinates */
   int              affine;  /* impotrant case Z=1       */
};

/*
// Contetx Access Macros
*/
#define ECP_POINT_ID(ctx)       ((ctx)->idCtx)
#define ECP_POINT_X(ctx)        ((ctx)->pX)
#define ECP_POINT_Y(ctx)        ((ctx)->pY)
#define ECP_POINT_Z(ctx)        ((ctx)->pZ)
#define ECP_POINT_AFFINE(ctx)   ((ctx)->affine)
#define ECP_POINT_VALID_ID(ctx) (ECP_POINT_ID((ctx))==idCtxECCPPoint)

#endif /* _PCP_ECCPPOINT_H */
