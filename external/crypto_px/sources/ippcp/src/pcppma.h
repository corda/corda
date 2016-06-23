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

#if !defined(_PCP_PMA_H)
#define _PCP_PMA_H

#include "pcpbn.h"
#include "pcpmontgomery.h"


/*
// Prime Modulo Arithmetic
*/
#define PMA_set(r,a) \
   BN_SIGN((r)) = BN_SIGN((a)); \
   BN_SIZE((r)) = BN_SIZE((a)); \
   ZEXPAND_COPY_BNU(BN_NUMBER((r)),BN_ROOM((r)), BN_NUMBER((a)),BN_SIZE((a))) \

#define PMA_mod(r,a,modulo) \
   ippsMod_BN((a),(modulo),(r))

#define PMA_inv(r,a,modulo) \
   ippsModInv_BN((a),(modulo),(r))

#define PMA_div2(r,a,modulo) { \
   if( IsOdd_BN((a)) ) { \
      ippsAdd_BN((a), (modulo), (a)); \
   } \
   BN_SIZE((r)) = cpLSR_BNU(BN_NUMBER((r)), BN_NUMBER((a)), (int)BN_SIZE((a)), 1); \
   cpBN_fix((r)); \
}

#define PMA_add(r,a,b,modulo) \
   ippsAdd_BN((a),(b),(r));   \
   if( cpCmp_BNU(BN_NUMBER((r)),BN_SIZE((r)),BN_NUMBER((modulo)),BN_SIZE(modulo)) >= 0 ) \
      ippsSub_BN((r),(modulo),(r))

#define PMA_sub(r,a,b,modulo) \
   ippsSub_BN((a),(b),(r));   \
   if( BN_NEGATIVE((r)) )     \
      ippsAdd_BN((r),(modulo),(r))

#define PMA_enc(r,a,mont) \
   cpMontEnc_BN((r), (a), (mont))

#define PMA_dec(r,a,mont) \
   cpMontDec_BN((r), (a), (mont))

#define PMA_sqre(r,a,mont) \
   ippsMontMul((a),(a), (mont),(r))

#define PMA_mule(r,a,b,mont) \
   ippsMontMul((a),(b), (mont),(r))

#endif /* _PCP_PMA_H */
