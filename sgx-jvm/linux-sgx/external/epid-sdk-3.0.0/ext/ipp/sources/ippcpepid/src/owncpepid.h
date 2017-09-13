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
//                   Internal Cryptographic Primitives (Intel(R) EPID v2.0)
// 
// 
*/

#ifndef __OWNCP_EPID_H__
#define __OWNCP_EPID_H__

#if defined (USE_P8_HEADER)
    #include "ippcpepid_p8.h"
#elif defined (USE_Y8_HEADER)
    #include "ippcpepid_y8.h"
#endif

#ifndef __OWNDEFS_H__
  #include "owndefs.h"
#endif

#ifndef __OWNCP_H__
  #include "owncp.h"
#endif

#ifndef __IPPCP_EPID_H__
  #include "ippcpepid.h"
#endif

#define LOG2_CACHE_LINE_SIZE  (6)   /* LOG2(CACHE_LINE_SIZE) */

/* convert bitsize nbits into  the number of BNU_CHUNK_T */
#define BITS_CHUNKSIZE(nbits) (((nbits)+BITSIZE(BNU_CHUNK_T)-1)/BITSIZE(BNU_CHUNK_T))

/*
// dst = (src1 & mask) | (src2 & ~mask)
*/
#define MASKED_COPY(dst, mask, src1, src2, len) { \
   /*cpSize*/ int i; \
   for(i=0; i<(len); i++) (dst)[i] = ((mask) & (src1)[i]) | (~(mask) & (src2)[i]); \
}

#endif /* __OWNCP_EPID_H__ */
