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
//     ECCP SSCM stuff
// 
// 
*/

#if defined (_USE_ECCP_SSCM_)
#if !defined(_PCP_ECCP_SSCM_H)
#define _PCP_ECCP_SSCM_H

#include "pcpeccppoint.h"

int cpECCP_OptimalWinSize(int bitSize);

int cpECCP_ConvertRepresentation(BNU_CHUNK_T* pR, int inpBits, int w);

/*
// cpsScramblePut/cpsScrambleGet
// stores to/retrieves from pScrambleEntry position
// pre-computed data if fixed window method is used
*/
void cpECCP_ScramblePut(Ipp8u* pScrambleEntry, int proposity, const IppsECCPPointState* pPoint, int coordLen);
void cpECCP_ScrambleGet(IppsECCPPointState* pPoint, int coordLen, const Ipp8u* pScrambleEntry, int proposity);

#endif /* _PCP_ECCP_SSCM_H */
#endif /* _USE_ECCP_SSCM_ */
