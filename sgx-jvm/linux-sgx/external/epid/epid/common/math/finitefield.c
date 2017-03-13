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
 * \brief Finite field implementation.
 */

#include <limits.h>
#include <string.h>
#include "epid/common/math/finitefield.h"
#include "epid/common/math/bignum-internal.h"
#include "epid/common/math/finitefield-internal.h"
#include "epid/common/memory.h"
#include "ext/ipp/include/ippcp.h"
#include "ext/ipp/include/ippcpepid.h"

/// Initializes a FiniteField structure
EpidStatus InitFiniteFieldFromIpp(IppsGFpState* ipp_ff, FiniteField* ff) {
  IppStatus sts = ippStsNoErr;

  if (!ipp_ff || !ff) return kEpidBadArgErr;

  memset(ff, 0, sizeof(*ff));

  sts = ippsGFpGetInfo(ipp_ff, &(ff->info));
  if (ippStsNoErr != sts) return kEpidMathErr;

  ff->ipp_ff = ipp_ff;

  return kEpidNoErr;
}

EpidStatus NewFiniteField(BigNumStr const* prime, FiniteField** ff) {
  EpidStatus result = kEpidErr;
  IppsGFpState* ipp_finitefield_ctx = NULL;
  FiniteField* finitefield_ptr = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    Ipp32u bnu[sizeof(BigNumStr) / sizeof(Ipp32u)];
    int bnu_size;
    int bit_size = CHAR_BIT * sizeof(BigNumStr);
    int state_size_in_bytes = 0;

    if (!prime || !ff) {
      result = kEpidBadArgErr;
      break;
    }
    bnu_size = OctStr2Bnu(bnu, prime, sizeof(*prime));
    if (bnu_size < 0) {
      result = kEpidMathErr;
      break;
    }

    // Determine the memory requirement for finite field context
    sts = ippsGFpGetSize(bit_size, &state_size_in_bytes);
    if (ippStsNoErr != sts) {
      if (ippStsSizeErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }
    // Allocate space for ipp bignum context
    ipp_finitefield_ctx = (IppsGFpState*)SAFE_ALLOC(state_size_in_bytes);
    if (!ipp_finitefield_ctx) {
      result = kEpidMemAllocErr;
      break;
    }
    // Initialize ipp finite field context
    sts = ippsGFpInit(bnu, bit_size, ipp_finitefield_ctx);
    if (ippStsNoErr != sts) {
      if (ippStsSizeErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }
    finitefield_ptr = (FiniteField*)SAFE_ALLOC(sizeof(FiniteField));
    if (!finitefield_ptr) {
      result = kEpidMemAllocErr;
      break;
    }
    result = InitFiniteFieldFromIpp(ipp_finitefield_ctx, finitefield_ptr);
    if (kEpidNoErr != result) break;

    *ff = finitefield_ptr;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    SAFE_FREE(finitefield_ptr);
    SAFE_FREE(ipp_finitefield_ctx);
  }
  return result;
}

EpidStatus NewFiniteFieldViaBinomalExtension(FiniteField const* ground_field,
                                             FfElement const* ground_element,
                                             int degree, FiniteField** ff) {
  EpidStatus result = kEpidErr;
  IppsGFpState* ipp_finitefield_ctx = NULL;
  FiniteField* finitefield_ptr = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    int state_size_in_bytes = 0;
    if (!ground_field || !ground_element || !ff) {
      result = kEpidBadArgErr;
      break;
    } else if (degree < 2 || !ground_field->ipp_ff ||
               !ground_element->ipp_ff_elem) {
      result = kEpidBadArgErr;
      break;
    }

    // Determine the memory requirement for finite field context
    sts = ippsGFpxGetSize(ground_field->ipp_ff, degree, &state_size_in_bytes);
    if (ippStsNoErr != sts) {
      if (ippStsSizeErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }

    // Allocate space for ipp finite field context
    ipp_finitefield_ctx = (IppsGFpState*)SAFE_ALLOC(state_size_in_bytes);
    if (!ipp_finitefield_ctx) {
      result = kEpidMemAllocErr;
      break;
    }

    // Initialize ipp binomial extension finite field context
    sts =
        ippsGFpxInitBinomial(ground_field->ipp_ff, ground_element->ipp_ff_elem,
                             degree, ipp_finitefield_ctx);
    if (ippStsNoErr != sts) {
      if (ippStsSizeErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }
    finitefield_ptr = (FiniteField*)SAFE_ALLOC(sizeof(FiniteField));
    if (!finitefield_ptr) {
      result = kEpidMemAllocErr;
      break;
    }
    result = InitFiniteFieldFromIpp(ipp_finitefield_ctx, finitefield_ptr);
    if (kEpidNoErr != result) break;

    *ff = finitefield_ptr;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    SAFE_FREE(finitefield_ptr);
    SAFE_FREE(ipp_finitefield_ctx);
  }
  return result;
}

void DeleteFiniteField(FiniteField** ff) {
  if (ff) {
    if (*ff) {
      SAFE_FREE((*ff)->ipp_ff);
    }
    SAFE_FREE((*ff));
  }
}

EpidStatus NewFfElement(FiniteField const* ff, FfElement** new_ff_elem) {
  EpidStatus result = kEpidErr;
  IppsGFpElement* ipp_ff_elem = NULL;
  FfElement* ff_elem = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    unsigned int ctxsize = 0;
    Ipp32u zero = 0;
    // check parameters
    if (!ff || !new_ff_elem) {
      result = kEpidBadArgErr;
      break;
    } else if (!ff->ipp_ff) {
      result = kEpidBadArgErr;
      break;
    }
    // Determine the memory requirement for finite field element context
    sts = ippsGFpElementGetSize(ff->ipp_ff, (int*)&ctxsize);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }
    // Allocate space for ipp bignum context
    ipp_ff_elem = (IppsGFpElement*)SAFE_ALLOC(ctxsize);
    if (!ipp_ff_elem) {
      result = kEpidMemAllocErr;
      break;
    }
    // Initialize ipp bignum context
    // initialize state
    sts = ippsGFpElementInit(&zero, 1, ipp_ff_elem, ff->ipp_ff);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    ff_elem = (FfElement*)SAFE_ALLOC(sizeof(FfElement));
    if (!ff_elem) {
      result = kEpidMemAllocErr;
      break;
    }

    ff_elem->ipp_ff_elem = ipp_ff_elem;

    sts = ippsGFpGetInfo(ff->ipp_ff, &(ff_elem->info));
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    *new_ff_elem = ff_elem;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    SAFE_FREE(ipp_ff_elem);
    SAFE_FREE(ff_elem);
  }
  return result;
}

void DeleteFfElement(FfElement** ff_elem) {
  if (ff_elem) {
    if (*ff_elem) {
      SAFE_FREE((*ff_elem)->ipp_ff_elem);
    }
    SAFE_FREE(*ff_elem);
  }
}

EpidStatus ReadFfElement(FiniteField* ff, void const* ff_elem_str,
                         size_t strlen, FfElement* ff_elem) {
  IppStatus sts;
  int ipp_str_size = (int)strlen;

  if (!ff || !ff_elem_str || !ff_elem) {
    return kEpidBadArgErr;
  }
  if (!ff_elem->ipp_ff_elem || !ff->ipp_ff) {
    return kEpidBadArgErr;
  }

  if (ipp_str_size <= 0) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != ff_elem->info.elementLen) {
    return kEpidBadArgErr;
  }
  sts = ippsGFpSetElementOctString(ff_elem_str, ipp_str_size,
                                   ff_elem->ipp_ff_elem, ff->ipp_ff);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsOutOfRangeErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }

  return kEpidNoErr;
}

EpidStatus WriteFfElement(FiniteField* ff, FfElement const* ff_elem,
                          void* ff_elem_str, size_t strlen) {
  IppStatus sts;
  IppsGFpInfo info;

  if (!ff || !ff_elem_str || !ff_elem) {
    return kEpidBadArgErr;
  }
  if (!ff_elem->ipp_ff_elem || !ff->ipp_ff) {
    return kEpidBadArgErr;
  }
  if (INT_MAX < strlen) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != ff_elem->info.elementLen) {
    return kEpidBadArgErr;
  }

  // check that ippsGFpGetElementOctString does not truncate to fit the
  // buffer
  sts = ippsGFpGetInfo(ff->ipp_ff, &info);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }
  if (info.elementLen * sizeof(Ipp32u) > strlen) return kEpidBadArgErr;

  // get the data
  sts = ippsGFpGetElementOctString(ff_elem->ipp_ff_elem, ff_elem_str,
                                   (int)strlen, ff->ipp_ff);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }

  return kEpidNoErr;
}

EpidStatus FfNeg(FiniteField* ff, FfElement const* a, FfElement* r) {
  IppStatus sts = ippStsNoErr;
  if (!ff || !a || !r) {
    return kEpidBadArgErr;
  } else if (!ff->ipp_ff || !a->ipp_ff_elem || !r->ipp_ff_elem) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != a->info.elementLen ||
      ff->info.elementLen != r->info.elementLen ||
      a->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }
  sts = ippsGFpNeg(a->ipp_ff_elem, r->ipp_ff_elem, ff->ipp_ff);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }
  return kEpidNoErr;
}

EpidStatus FfInv(FiniteField* ff, FfElement const* a, FfElement* r) {
  IppStatus sts = ippStsNoErr;
  // Check required parametersWriteFfElement
  if (!ff || !a || !r) {
    return kEpidBadArgErr;
  } else if (!ff->ipp_ff || !a->ipp_ff_elem || !r->ipp_ff_elem) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != a->info.elementLen ||
      ff->info.elementLen != r->info.elementLen ||
      a->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }
  // Invert the element
  sts = ippsGFpInv(a->ipp_ff_elem, r->ipp_ff_elem, ff->ipp_ff);
  // Check return codes
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts)
      return kEpidBadArgErr;
    else if (ippStsDivByZeroErr == sts)
      return kEpidDivByZeroErr;
    else
      return kEpidMathErr;
  }
  return kEpidNoErr;
}

EpidStatus FfAdd(FiniteField* ff, FfElement const* a, FfElement const* b,
                 FfElement* r) {
  IppStatus sts = ippStsNoErr;
  if (!ff || !a || !b || !r) {
    return kEpidBadArgErr;
  } else if (!ff->ipp_ff || !a->ipp_ff_elem || !b->ipp_ff_elem ||
             !r->ipp_ff_elem) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != a->info.elementLen ||
      ff->info.elementLen != b->info.elementLen ||
      ff->info.elementLen != r->info.elementLen ||
      a->info.elementLen != b->info.elementLen ||
      a->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }

  sts = ippsGFpAdd(a->ipp_ff_elem, b->ipp_ff_elem, r->ipp_ff_elem, ff->ipp_ff);
  if (ippStsContextMatchErr == sts) {
    return kEpidBadArgErr;
  } else if (ippStsNoErr != sts) {
    return kEpidMathErr;
  }
  return kEpidNoErr;
}

EpidStatus FfMul(FiniteField* ff, FfElement const* a, FfElement const* b,
                 FfElement* r) {
  IppStatus sts = ippStsNoErr;
  // Check required parametersWriteFfElement
  if (!ff || !a || !b || !r) {
    return kEpidBadArgErr;
  } else if (!ff->ipp_ff || !a->ipp_ff_elem || !b->ipp_ff_elem ||
             !r->ipp_ff_elem) {
    return kEpidBadArgErr;
  }
  // Multiplies elements
  if (a->info.elementLen != b->info.elementLen &&
      a->info.elementLen == a->info.groundGFdegree * b->info.elementLen) {
    sts = ippsGFpMul_GFpE(a->ipp_ff_elem, b->ipp_ff_elem, r->ipp_ff_elem,
                          ff->ipp_ff);
  } else {
    if (ff->info.elementLen != a->info.elementLen ||
        ff->info.elementLen != b->info.elementLen ||
        ff->info.elementLen != r->info.elementLen ||
        a->info.elementLen != b->info.elementLen ||
        a->info.elementLen != r->info.elementLen) {
      return kEpidBadArgErr;
    }
    sts =
        ippsGFpMul(a->ipp_ff_elem, b->ipp_ff_elem, r->ipp_ff_elem, ff->ipp_ff);
  }
  // Check return codes
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts)
      return kEpidBadArgErr;
    else
      return kEpidMathErr;
  }
  return kEpidNoErr;
}

EpidStatus FfIsZero(FiniteField* ff, FfElement const* a, bool* is_zero) {
  IppStatus sts = ippStsNoErr;
  int ipp_result = IPP_IS_NE;
  // Check required parameters
  if (!ff || !a || !is_zero) {
    return kEpidBadArgErr;
  } else if (!ff->ipp_ff || !a->ipp_ff_elem) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != a->info.elementLen) {
    return kEpidBadArgErr;
  }
  // Check if the element is zero
  sts = ippsGFpIsZeroElement(a->ipp_ff_elem, &ipp_result, ff->ipp_ff);
  // Check return codes
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts)
      return kEpidBadArgErr;
    else
      return kEpidMathErr;
  }
  if (IPP_IS_EQ == ipp_result) {
    *is_zero = true;
  } else {
    *is_zero = false;
  }
  return kEpidNoErr;
}

EpidStatus FfExp(FiniteField* ff, FfElement const* a, BigNum const* b,
                 FfElement* r) {
  EpidStatus result = kEpidErr;
  Ipp8u* scratch_buffer = NULL;
  int exp_bit_size = 0;
  int element_size = 0;

  do {
    IppStatus sts = ippStsNoErr;
    // Check required parameters
    if (!ff || !a || !b || !r) {
      result = kEpidBadArgErr;
      break;
    } else if (!ff->ipp_ff || !a->ipp_ff_elem || !r->ipp_ff_elem) {
      result = kEpidBadArgErr;
      break;
    }
    if (ff->info.elementLen != a->info.elementLen ||
        ff->info.elementLen != r->info.elementLen ||
        a->info.elementLen != r->info.elementLen) {
      return kEpidBadArgErr;
    }

    sts = ippsRef_BN(0, &exp_bit_size, 0, b->ipp_bn);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    sts = ippsGFpScratchBufferSize(1, exp_bit_size, ff->ipp_ff, &element_size);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    scratch_buffer = (Ipp8u*)SAFE_ALLOC(element_size);
    if (!scratch_buffer) {
      result = kEpidMemAllocErr;
      break;
    }

    sts = ippsGFpExp(a->ipp_ff_elem, b->ipp_bn, r->ipp_ff_elem, ff->ipp_ff,
                     scratch_buffer);
    // Check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts || ippStsRangeErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }
    result = kEpidNoErr;
  } while (0);
  SAFE_FREE(scratch_buffer);
  return result;
}

EpidStatus FfMultiExp(FiniteField* ff, FfElement const** p, BigNumStr const** b,
                      size_t m, FfElement* r) {
  EpidStatus result = kEpidErr;
  IppsGFpElement** ipp_p = NULL;
  IppsBigNumState** ipp_b = NULL;
  BigNum** bignums = NULL;
  Ipp8u* scratch_buffer = NULL;
  int i = 0;
  int ipp_m = 0;

  // Check required parameters
  if (!ff || !p || !b || !r) {
    return kEpidBadArgErr;
  } else if (!ff->ipp_ff || !r->ipp_ff_elem || m <= 0) {
    return kEpidBadArgErr;
  }
  // because we use ipp function with number of items parameter
  // defined as "int" we need to verify that input length
  // do not exceed INT_MAX to avoid overflow
  if (m > INT_MAX) {
    return kEpidBadArgErr;
  }
  ipp_m = (int)m;

  for (i = 0; i < ipp_m; i++) {
    if (!p[i]) {
      return kEpidBadArgErr;
    }
    if (!p[i]->ipp_ff_elem) {
      return kEpidBadArgErr;
    }
    if (ff->info.elementLen != p[i]->info.elementLen) {
      return kEpidBadArgErr;
    }
  }
  if (ff->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }

  do {
    IppStatus sts = ippStsNoErr;
    int scratch_buffer_size = 0;
    const int exp_bit_size = CHAR_BIT * sizeof(BigNumStr);

    // Allocate memory for finite field elements for ipp call
    ipp_p = (IppsGFpElement**)SAFE_ALLOC(ipp_m * sizeof(IppsGFpElement*));
    if (!ipp_p) {
      result = kEpidMemAllocErr;
      break;
    }
    for (i = 0; i < ipp_m; i++) {
      ipp_p[i] = p[i]->ipp_ff_elem;
    }

    // Create big number elements for ipp call
    // Allocate memory for finite field elements for ipp call
    bignums = (BigNum**)SAFE_ALLOC(ipp_m * sizeof(BigNum*));
    if (!bignums) {
      result = kEpidMemAllocErr;
      break;
    }
    ipp_b = (IppsBigNumState**)SAFE_ALLOC(ipp_m * sizeof(IppsBigNumState*));
    if (!ipp_b) {
      result = kEpidMemAllocErr;
      break;
    }
    // Initialize BigNum and fill ipp array for ipp call
    for (i = 0; i < ipp_m; i++) {
      result = NewBigNum(sizeof(BigNumStr), &bignums[i]);
      if (kEpidNoErr != result) break;
      result = ReadBigNum(b[i], sizeof(BigNumStr), bignums[i]);
      if (kEpidNoErr != result) break;
      ipp_b[i] = bignums[i]->ipp_bn;
    }
    if (kEpidNoErr != result) break;

    // calculate scratch buffer size
    sts = ippsGFpScratchBufferSize(ipp_m, exp_bit_size, ff->ipp_ff,
                                   &scratch_buffer_size);
    if (sts != ippStsNoErr) {
      result = kEpidMathErr;
      break;
    }
    // allocate memory for scratch buffer
    scratch_buffer = (Ipp8u*)SAFE_ALLOC(scratch_buffer_size);
    if (!scratch_buffer) {
      result = kEpidMemAllocErr;
      break;
    }

    sts = ippsGFpMultiExp((const IppsGFpElement* const*)ipp_p,
                          (const IppsBigNumState* const*)ipp_b, ipp_m,
                          r->ipp_ff_elem, ff->ipp_ff, scratch_buffer);
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts || ippStsRangeErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }
    result = kEpidNoErr;
  } while (0);
  if (NULL != bignums) {  // delete big nums only if it was really allocated
    for (i = 0; i < ipp_m; i++) {
      DeleteBigNum(&bignums[i]);
    }
  }
  SAFE_FREE(bignums);
  SAFE_FREE(ipp_p);
  SAFE_FREE(ipp_b);
  SAFE_FREE(scratch_buffer);
  return result;
}

EpidStatus FfSscmMultiExp(FiniteField* ff, FfElement const** p,
                          BigNumStr const** b, size_t m, FfElement* r) {
  // call EcMultiExp directly because its implementation is side channel
  // mitigated already
  return FfMultiExp(ff, p, b, m, r);
}

EpidStatus FfIsEqual(FiniteField* ff, FfElement const* a, FfElement const* b,
                     bool* is_equal) {
  IppStatus sts;
  int result;

  if (!ff || !a || !b || !is_equal) {
    return kEpidBadArgErr;
  }
  if (!ff->ipp_ff || !a->ipp_ff_elem || !b->ipp_ff_elem) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != a->info.elementLen ||
      ff->info.elementLen != b->info.elementLen ||
      a->info.elementLen != b->info.elementLen) {
    return kEpidBadArgErr;
  }

  sts = ippsGFpCmpElement(a->ipp_ff_elem, b->ipp_ff_elem, &result, ff->ipp_ff);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }
  *is_equal = IPP_IS_EQ == result;

  return kEpidNoErr;
}

EpidStatus FfHash(FiniteField* ff, void const* msg, size_t msg_len,
                  HashAlg hash_alg, FfElement* r) {
  EpidStatus result = kEpidErr;
  do {
    IppStatus sts = ippStsNoErr;
    IppHashID hash_id;
    int ipp_msg_len = 0;
    if (!ff || !msg || !r) {
      result = kEpidBadArgErr;
      break;
    } else if (!ff->ipp_ff || !r->ipp_ff_elem || msg_len <= 0) {
      result = kEpidBadArgErr;
      break;
    }
    // because we use ipp function with message length parameter
    // defined as "int" we need to verify that input length
    // do not exceed INT_MAX to avoid overflow
    if (msg_len > INT_MAX) {
      result = kEpidBadArgErr;
      break;
    }
    ipp_msg_len = (int)msg_len;

    if (kSha256 == hash_alg) {
      hash_id = ippSHA256;
    } else if (kSha384 == hash_alg) {
      hash_id = ippSHA384;
    } else if (kSha512 == hash_alg) {
      hash_id = ippSHA512;
    } else {
      result = kEpidHashAlgorithmNotSupported;
      break;
    }
    if (ff->info.elementLen != r->info.elementLen) {
      return kEpidBadArgErr;
    }
    sts = ippsGFpSetElementHash(msg, ipp_msg_len, hash_id, r->ipp_ff_elem,
                                ff->ipp_ff);
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts || ippStsBadArgErr == sts ||
          ippStsLengthErr == sts) {
        return kEpidBadArgErr;
      } else {
        return kEpidMathErr;
      }
    }
    result = kEpidNoErr;
  } while (0);
  return result;
}

/// Number of tries for RNG
#define RNG_WATCHDOG (10)
EpidStatus FfGetRandom(FiniteField* ff, BigNumStr const* low_bound,
                       BitSupplier rnd_func, void* rnd_param, FfElement* r) {
  EpidStatus result = kEpidErr;
  IppsGFpElement* low = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    unsigned int ctxsize = 0;
    unsigned int rngloopCount = RNG_WATCHDOG;
    Ipp32u bnu_low_bound[sizeof(BigNumStr) / sizeof(Ipp32u)];
    int bnu_size;
    if (!ff || !low_bound || !rnd_func || !r) {
      result = kEpidBadArgErr;
      break;
    }
    if (!ff->ipp_ff || !r->ipp_ff_elem) {
      result = kEpidBadArgErr;
      break;
    }
    if (ff->info.elementLen != r->info.elementLen) {
      return kEpidBadArgErr;
    }
    // create a new FfElement to hold low_bound
    sts = ippsGFpElementGetSize(ff->ipp_ff, (int*)&ctxsize);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }
    // Allocate space for ipp Ff Element context
    low = (IppsGFpElement*)SAFE_ALLOC(ctxsize);
    if (!low) {
      result = kEpidMemAllocErr;
      break;
    }
    bnu_size = OctStr2Bnu(bnu_low_bound, low_bound, sizeof(*low_bound));
    if (bnu_size < 0) {
      result = kEpidMathErr;
      break;
    }
    // initialize state
    sts = ippsGFpElementInit(bnu_low_bound, bnu_size, low, ff->ipp_ff);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }
    do {
      int cmpResult = IPP_IS_NE;
      sts = ippsGFpSetElementRandom((IppBitSupplier)rnd_func, rnd_param,
                                    r->ipp_ff_elem, ff->ipp_ff);
      if (ippStsNoErr != sts) {
        result = kEpidMathErr;
        break;
      }
      sts = ippsGFpCmpElement(r->ipp_ff_elem, low, &cmpResult, ff->ipp_ff);
      if (ippStsNoErr != sts) {
        result = kEpidMathErr;
        break;
      }
      if (IPP_IS_LT != cmpResult) {
        // we have a valid value, proceed
        result = kEpidNoErr;
        break;
      } else {
        result = kEpidRandMaxIterErr;
        continue;
      }
    } while (--rngloopCount);
  } while (0);
  SAFE_FREE(low);
  return result;
}
