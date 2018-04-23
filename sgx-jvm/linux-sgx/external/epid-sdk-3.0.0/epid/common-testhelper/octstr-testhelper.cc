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

/*!
 * \file
 * \brief OctString handling utility implementation.
 */
#include "epid/common-testhelper/octstr-testhelper.h"
extern "C" {
#include "epid/common/src/memory.h"
}
#include "ext/ipp/include/ippcp.h"

/// Internal function to delete BigNum
void delete_BigNum(IppsBigNumState** bn) {
  if (*bn) {
    SAFE_FREE(*bn);
  }
}
/// Internal function to create BigNum from an OctStr256
EpidStatus create_BigNum(IppsBigNumState** bn, const OctStr256* str) {
  EpidStatus result = kEpidErr;
  IppsBigNumState* ipp_bn_ctx = nullptr;
  do {
    IppStatus sts = ippStsNoErr;
    unsigned int byte_size = sizeof(OctStr256);
    unsigned int word_size =
        (unsigned int)((byte_size + sizeof(Ipp32u) - 1) / sizeof(Ipp32u));
    int bignum_ctx_size = 0;

    if (!bn || !str) {
      return kEpidBadArgErr;
    }

    sts = ippsBigNumGetSize(word_size, &bignum_ctx_size);
    if (ippStsNoErr != sts) {
      if (ippStsLengthErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }
    // Allocate space for ipp bignum context
    ipp_bn_ctx = (IppsBigNumState*)SAFE_ALLOC(bignum_ctx_size);
    if (!ipp_bn_ctx) {
      result = kEpidMemAllocErr;
      break;
    }
    // Initialize ipp bignum context
    sts = ippsBigNumInit(word_size, ipp_bn_ctx);
    if (sts != ippStsNoErr) {
      if (sts == ippStsLengthErr) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }

    sts = ippsSetOctString_BN((Ipp8u*)str, byte_size, ipp_bn_ctx);
    if (sts != ippStsNoErr) {
      if (sts == ippStsLengthErr) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }
    *bn = ipp_bn_ctx;
    result = kEpidNoErr;
  } while (0);

  if (result != kEpidNoErr) {
    SAFE_FREE(ipp_bn_ctx);
  }
  return result;
}

EpidStatus Cmp_OctStr256(const OctStr256* pA, const OctStr256* pB,
                         unsigned int* pResult) {
  EpidStatus result = kEpidErr;
  IppsBigNumState* ipp_a_ctx = nullptr;
  IppsBigNumState* ipp_b_ctx = nullptr;

  do {
    IppStatus sts = ippStsNoErr;
    if (!pA || !pB || !pResult) {
      return kEpidBadArgErr;
    }
    result = create_BigNum(&ipp_a_ctx, pA);
    if (kEpidNoErr != result) {
      break;
    }
    result = create_BigNum(&ipp_b_ctx, pB);
    if (kEpidNoErr != result) {
      break;
    }
    sts = ippsCmp_BN(ipp_a_ctx, ipp_b_ctx, pResult);
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
          ippStsLengthErr == sts || ippStsOutOfRangeErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
    }
  } while (0);

  delete_BigNum(&ipp_a_ctx);
  delete_BigNum(&ipp_b_ctx);
  return result;
}
