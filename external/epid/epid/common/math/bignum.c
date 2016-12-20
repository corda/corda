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
 * \brief Big number implementation.
 */
#include "epid/common/math/bignum.h"
#include "epid/common/math/bignum-internal.h"
#include "epid/common/memory.h"
#include "ext/ipp/include/ippcp.h"

EpidStatus NewBigNum(size_t data_size_bytes, BigNum** bignum) {
  EpidStatus result = kEpidErr;
  IppsBigNumState* ipp_bn_ctx = NULL;
  BigNum* bn = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    unsigned int ctxsize;
    unsigned int wordsize =
        (unsigned int)((data_size_bytes + sizeof(Ipp32u) - 1) / sizeof(Ipp32u));

    if (!bignum) {
      result = kEpidBadArgErr;
      break;
    }
    // Determine the memory requirement for bignum context
    sts = ippsBigNumGetSize(wordsize, (int*)&ctxsize);
    if (ippStsNoErr != sts) {
      if (ippStsLengthErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }
    // Allocate space for ipp bignum context
    ipp_bn_ctx = (IppsBigNumState*)SAFE_ALLOC(ctxsize);
    if (!ipp_bn_ctx) {
      result = kEpidMemAllocErr;
      break;
    }
    // Initialize ipp bignum context
    sts = ippsBigNumInit(wordsize, ipp_bn_ctx);
    if (ippStsNoErr != sts) {
      if (ippStsLengthErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }

    bn = (BigNum*)SAFE_ALLOC(sizeof(BigNum));
    if (!bn) {
      result = kEpidMemAllocErr;
      break;
    }

    bn->ipp_bn = ipp_bn_ctx;

    *bignum = bn;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    SAFE_FREE(ipp_bn_ctx);
    SAFE_FREE(bn);
  }
  return result;
}

void DeleteBigNum(BigNum** bignum) {
  if (bignum) {
    if (*bignum) {
      SAFE_FREE((*bignum)->ipp_bn);
    }
    SAFE_FREE(*bignum);
  }
}

EpidStatus ReadBigNum(void const* bn_str, size_t strlen, BigNum* bn) {
  IppStatus sts;
  size_t i;
  bool is_zero = true;
  Ipp8u const* byte_str = (Ipp8u const*)bn_str;
  int ipp_strlen = (int)strlen;

  if (!bn || !bn_str) return kEpidBadArgErr;

  if (!bn->ipp_bn) return kEpidBadArgErr;

  if (INT_MAX < strlen || strlen <= 0) return kEpidBadArgErr;

  /*
  Some versions of ippsSetOctString_BN have bug:
  When called for octet string with all bits set to zero the resulted BigNumber
  state initialize incorrectly which leads to unpredictable behaviour
  if used.

  Workaround:
  Test the input string before ippsSetOctStringSet_BN() call.
  If length of the string is zero or it does not contain any significant
  bits, then set BN to zero.  Keep in mind that ippsBigNumInit() set BN
  value to zero.
  */
  for (i = 0; i < strlen; ++i)
    if (0 != byte_str[i]) {
      is_zero = false;
      break;
    }
  if (is_zero) {
    Ipp32u zero32 = 0;
    sts = ippsSet_BN(IppsBigNumPOS, 1, &zero32, bn->ipp_bn);
  } else {
    sts = ippsSetOctString_BN(bn_str, ipp_strlen, bn->ipp_bn);
  }
  if (sts != ippStsNoErr) {
    if (ippStsContextMatchErr == sts || ippStsSizeErr == sts ||
        ippStsLengthErr == sts || ippStsOutOfRangeErr == sts)
      return kEpidBadArgErr;
    else
      return kEpidMathErr;
  }

  return kEpidNoErr;
}

EpidStatus WriteBigNum(BigNum const* bn, size_t strlen, void const* bn_str) {
  IppStatus sts;
  int ipp_strlen = (int)strlen;
  if (!bn || !bn_str) return kEpidBadArgErr;

  if (!bn->ipp_bn) return kEpidBadArgErr;

  sts = ippsGetOctString_BN((Ipp8u*)bn_str, ipp_strlen, bn->ipp_bn);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
        ippStsLengthErr == sts)
      return kEpidBadArgErr;
    else
      return kEpidMathErr;
  }

  return kEpidNoErr;
}

/// convert octet string into "big number unsigned" representation
int OctStr2Bnu(uint32_t* bnu_ptr, void const* octstr_ptr, int octstr_len) {
  int bnusize = 0;
  uint8_t const* byte_str = (uint8_t const*)octstr_ptr;
  if (!bnu_ptr || !octstr_ptr) {
    return -1;
  }
  if (octstr_len < 4 || octstr_len % 4 != 0) return -1;

  *bnu_ptr = 0;
  /* start from the end of string */
  for (; octstr_len >= 4; bnusize++, octstr_len -= 4) {
    /* pack 4 bytes into single Ipp32u value*/
    *bnu_ptr++ = (byte_str[octstr_len - 4] << (8 * 3)) +
                 (byte_str[octstr_len - 3] << (8 * 2)) +
                 (byte_str[octstr_len - 2] << (8 * 1)) +
                 byte_str[octstr_len - 1];
  }
  return bnusize ? bnusize : -1;
}

EpidStatus BigNumAdd(BigNum const* a, BigNum const* b, BigNum* r) {
  IppStatus sts;

  if (!r || !a || !b) return kEpidBadArgErr;

  if (!r->ipp_bn || !a->ipp_bn || !b->ipp_bn) return kEpidBadArgErr;

  sts = ippsAdd_BN(a->ipp_bn, b->ipp_bn, r->ipp_bn);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
        ippStsLengthErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }

  return kEpidNoErr;
}

EpidStatus BigNumSub(BigNum const* a, BigNum const* b, BigNum* r) {
  IppStatus sts;
  Ipp32u sign = IS_ZERO;
  if (!r || !a || !b) return kEpidBadArgErr;

  if (!r->ipp_bn || !a->ipp_bn || !b->ipp_bn) return kEpidBadArgErr;

  sts = ippsSub_BN(a->ipp_bn, b->ipp_bn, r->ipp_bn);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
        ippStsLengthErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }
  sts = ippsCmpZero_BN(r->ipp_bn, &sign);
  if (ippStsNoErr != sts) {
    return kEpidMathErr;
  }
  if (sign == LESS_THAN_ZERO) {
    return kEpidUnderflowErr;
  }
  return kEpidNoErr;
}

EpidStatus BigNumMul(BigNum const* a, BigNum const* b, BigNum* r) {
  IppStatus sts;

  if (!r || !a || !b) return kEpidBadArgErr;

  if (!r->ipp_bn || !a->ipp_bn || !b->ipp_bn) return kEpidBadArgErr;

  sts = ippsMul_BN(a->ipp_bn, b->ipp_bn, r->ipp_bn);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
        ippStsLengthErr == sts || ippStsOutOfRangeErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }

  return kEpidNoErr;
}

EpidStatus BigNumMod(BigNum const* a, BigNum const* b, BigNum* r) {
  IppStatus sts;

  if (!r || !a || !b) return kEpidBadArgErr;

  if (!r->ipp_bn || !a->ipp_bn || !b->ipp_bn) return kEpidBadArgErr;

  sts = ippsMod_BN(a->ipp_bn, b->ipp_bn, r->ipp_bn);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
        ippStsLengthErr == sts || ippStsOutOfRangeErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }

  return kEpidNoErr;
}
