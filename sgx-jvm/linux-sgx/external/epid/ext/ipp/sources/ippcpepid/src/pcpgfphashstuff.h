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
//     Internal hash wrappers
// 
// 
*/

#if !defined(_CP_GFP_HASH_H_)
#define _CP_GFP_HASH_H_

#include "owncpepid.h"

#include "pcphash.h"

/* init context */
__INLINE int cpTestHashID(IppHashID id)
{
   switch (id) {
   case ippMD5:
   case ippSHA1:
   case ippSHA256:
   case ippSHA224:
   case ippSHA512:
   case ippSHA384:return 1;
   default: return 0;
   }
}

/* init context */
__INLINE IppStatus cpHashInit(void* pCtx, IppHashID id)
{
   switch (id) {
   case ippMD5:   return ippsMD5Init((IppsMD5State*)pCtx);
   case ippSHA1:  return ippsSHA1Init((IppsSHA1State*)pCtx);
   case ippSHA256:return ippsSHA256Init((IppsSHA256State*)pCtx);
   case ippSHA224:return ippsSHA224Init((IppsSHA224State*)pCtx);
   case ippSHA512:return ippsSHA512Init((IppsSHA512State*)pCtx);
   case ippSHA384:return ippsSHA384Init((IppsSHA384State*)pCtx);
   default: return ippStsBadArgErr;
   }
}

/* update hash */
__INLINE IppStatus cpHashUpdate(const Ipp8u* pMsg, int msgLen, void* pCtx, IppHashID id)
{
   switch (id) {
   case ippMD5:   return ippsMD5Update(pMsg, msgLen, (IppsMD5State*)pCtx);
   case ippSHA1:  return ippsSHA1Update(pMsg, msgLen, (IppsSHA1State*)pCtx);
   case ippSHA256:return ippsSHA256Update(pMsg, msgLen, (IppsSHA256State*)pCtx);
   case ippSHA224:return ippsSHA224Update(pMsg, msgLen, (IppsSHA224State*)pCtx);
   case ippSHA512:return ippsSHA512Update(pMsg, msgLen, (IppsSHA512State*)pCtx);
   case ippSHA384:return ippsSHA384Update(pMsg, msgLen, (IppsSHA384State*)pCtx);
   default: return ippStsBadArgErr;
   }
}

/* hash length */
__INLINE int cpHashLength(IppHashID id)
{
   switch (id) {
   case ippMD5:   return IPP_MD5_DIGEST_BITSIZE/BYTESIZE;
   case ippSHA1:  return IPP_SHA1_DIGEST_BITSIZE/BYTESIZE;
   case ippSHA256:return IPP_SHA256_DIGEST_BITSIZE/BYTESIZE;
   case ippSHA224:return IPP_SHA224_DIGEST_BITSIZE/BYTESIZE;
   case ippSHA512:return IPP_SHA512_DIGEST_BITSIZE/BYTESIZE;
   case ippSHA384:return IPP_SHA384_DIGEST_BITSIZE/BYTESIZE;
   default: return 0;
   }
}

/* final hash */
__INLINE IppStatus cpHashFinal(Ipp8u* pMd, void* pCtx, IppHashID id)
{
   switch (id) {
   case ippMD5:   return ippsMD5Final(pMd, (IppsMD5State*)pCtx);
   case ippSHA1:  return ippsSHA1Final(pMd, (IppsSHA1State*)pCtx);
   case ippSHA256:return ippsSHA256Final(pMd, (IppsSHA256State*)pCtx);
   case ippSHA224:return ippsSHA224Final(pMd, (IppsSHA224State*)pCtx);
   case ippSHA512:return ippsSHA512Final(pMd, (IppsSHA512State*)pCtx);
   case ippSHA384:return ippsSHA384Final(pMd, (IppsSHA384State*)pCtx);
   default: return ippStsBadArgErr;
   }
}

/* whole message hash */
__INLINE IppStatus cpHashMessage(const Ipp8u* pMsg, int msgLen, Ipp8u* pMd, IppHashID id)
{
   switch (id) {
   case ippMD5:   return ippsMD5MessageDigest(pMsg, msgLen, pMd);
   case ippSHA1:  return ippsSHA1MessageDigest(pMsg, msgLen, pMd);
   case ippSHA256:return ippsSHA256MessageDigest(pMsg, msgLen, pMd);
   case ippSHA224:return ippsSHA224MessageDigest(pMsg, msgLen, pMd);
   case ippSHA512:return ippsSHA512MessageDigest(pMsg, msgLen, pMd);
   case ippSHA384:return ippsSHA384MessageDigest(pMsg, msgLen, pMd);
   default: return ippStsBadArgErr;
   }
}

#endif /* _CP_GFP_HASH_H_ */
