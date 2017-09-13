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
#include <stdint.h>
#include "epid/common/math/finitefield.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/math/src/finitefield-internal.h"
#include "epid/common/src/memory.h"
#include "ext/ipp/include/ippcp.h"
#include "ext/ipp/include/ippcpepid.h"

/// Number of leading zero bits in 32 bit integer x.
static size_t Nlz32(uint32_t x) {
  size_t nlz = sizeof(x) * 8;
  if (x) {
    nlz = 0;
    if (0 == (x & 0xFFFF0000)) {
      nlz += 16;
      x <<= 16;
    }
    if (0 == (x & 0xFF000000)) {
      nlz += 8;
      x <<= 8;
    }
    if (0 == (x & 0xF0000000)) {
      nlz += 4;
      x <<= 4;
    }
    if (0 == (x & 0xC0000000)) {
      nlz += 2;
      x <<= 2;
    }
    if (0 == (x & 0x80000000)) {
      nlz++;
    }
  }
  return nlz;
}

/// Bit size of bit number representated as array of Ipp32u.
#define BNU_BITSIZE(bnu, len) \
  ((len) * sizeof(Ipp32u) * 8 - Nlz32((bnu)[(len)-1]))

/// Convert bit size to byte size
#define BIT2BYTE_SIZE(bits) (((bits) + 7) >> 3)

/// Initializes a FiniteField structure
EpidStatus InitFiniteFieldFromIpp(IppsGFpState* ipp_ff, FiniteField* ff) {
  EpidStatus result = kEpidErr;
  IppStatus sts = ippStsNoErr;
  IppsGFpInfo basic_info;
  Ipp32u* basic_modulus = NULL;

  if (!ipp_ff || !ff) return kEpidBadArgErr;

  do {
    memset(ff, 0, sizeof(*ff));

    // set FiniteField::info
    sts = ippsGFpGetInfo(ipp_ff, &(ff->info));
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    // set FiniteField::ipp_ff context
    ff->ipp_ff = ipp_ff;

    // set FiniteField::prime_modulus_size
    sts = ippsGFpGetInfo(ff->info.pBasicGF, &basic_info);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    basic_modulus = (Ipp32u*)SAFE_ALLOC(basic_info.elementLen * sizeof(Ipp32u));
    if (!basic_modulus) {
      result = kEpidMemAllocErr;
      break;
    }

    sts = ippsGFpGetModulus(ff->info.pBasicGF, basic_modulus);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    ff->prime_modulus_size =
        BIT2BYTE_SIZE(BNU_BITSIZE(basic_modulus, basic_info.elementLen));

    result = kEpidNoErr;
  } while (0);

  SAFE_FREE(basic_modulus);

  return result;
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

    bit_size = (int)OctStrBitSize(prime->data.data, sizeof(prime->data.data));

    bnu_size = OctStr2Bnu(bnu, prime, sizeof(*prime));
    if (bnu_size < 0) {
      result = kEpidMathErr;
      break;
    }

    // skip high order zeros from BNU
    while (bnu_size > 1 && 0 == bnu[bnu_size - 1]) {
      bnu_size--;
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

EpidStatus NewFiniteFieldViaPolynomialExtension(FiniteField const* ground_field,
                                                BigNumStr const* irr_polynomial,
                                                int degree, FiniteField** ff) {
  EpidStatus result = kEpidErr;
  IppsGFpState* ipp_finitefield_ctx = NULL;
  FiniteField* finitefield_ptr = NULL;
  Ipp32u* irr_polynomial_bnu = NULL;
  int i;
  do {
    IppStatus sts = ippStsNoErr;
    int state_size_in_bytes = 0;
    if (!ground_field || !irr_polynomial || !ff) {
      result = kEpidBadArgErr;
      break;
    }
    if (degree < 1 || degree > (int)(INT_MAX / sizeof(BigNumStr)) ||
        !ground_field->ipp_ff) {
      result = kEpidBadArgErr;
      break;
    }

    // irr_polynomial to IPP representation
    irr_polynomial_bnu = (Ipp32u*)SAFE_ALLOC(sizeof(BigNumStr) * degree);
    if (!irr_polynomial_bnu) {
      result = kEpidMemAllocErr;
      break;
    }
    for (i = 0; i < degree; ++i) {
      OctStr2Bnu(irr_polynomial_bnu + (i * sizeof(BigNumStr) / 4),
                 &irr_polynomial[i], sizeof(irr_polynomial[i]));
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
    sts = ippsGFpxInit(ground_field->ipp_ff, irr_polynomial_bnu, degree,
                       ipp_finitefield_ctx);
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

  SAFE_FREE(irr_polynomial_bnu);
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
  size_t strlen_required = 0;
  int ipp_str_size = 0;
  uint8_t const* str = (uint8_t const*)ff_elem_str;

  if (!ff || !ff_elem_str || !ff_elem) {
    return kEpidBadArgErr;
  }
  if (!ff_elem->ipp_ff_elem || !ff->ipp_ff) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != ff_elem->info.elementLen) {
    return kEpidBadArgErr;
  }

  // ippsGFpSetElementOctString expects serialized value consists
  // of "degree" number of chunks, where each chunk is of modulus byte size.
  strlen_required = ff->prime_modulus_size * ff->info.basicGFdegree;

  // Remove leading zeros when de-serealizing finite field of degree 1.
  // This takes care of serialization chunk size adjustments when importing
  // a big numbers.
  if (1 == ff->info.basicGFdegree) {
    while (strlen_required < strlen && 0 == *str) {
      str++;
      strlen--;
    }
  }

  // Check if serialized value does not exceed ippsGFpSetElementOctString
  // expected size.
  if (strlen_required < strlen) {
    return kEpidBadArgErr;
  }

  ipp_str_size = (int)strlen;
  if (ipp_str_size <= 0) {
    return kEpidBadArgErr;
  }

  sts = ippsGFpSetElementOctString(str, ipp_str_size, ff_elem->ipp_ff_elem,
                                   ff->ipp_ff);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsOutOfRangeErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }

  return kEpidNoErr;
}

/// Gets the prime value of a finite field
/*!
  This function returns a new bignum containing the field's prime value.
  A new bignum is returned so that callers do not have to figure out the proper
  size.

  This function only works with non-composite fields.

  \param[in] ff
  The field.
  \param[out] bn
  The target BigNum.

  \returns ::EpidStatus
*/
EpidStatus GetFiniteFieldPrime(FiniteField* ff, BigNum** bn) {
  EpidStatus result = kEpidErr;
  IppStatus sts;
  BigNum* prime_bn = NULL;
  Ipp32u* prime_bnu = NULL;
  if (!ff || !bn) {
    return kEpidBadArgErr;
  }
  if (!ff->ipp_ff) {
    return kEpidBadArgErr;
  }
  if (ff->info.basicGFdegree != 1 || ff->info.groundGFdegree != 1) {
    return kEpidBadArgErr;
  }
  do {
    size_t elem_dword_size = ff->info.elementLen;
    size_t elem_bytes_size = elem_dword_size * sizeof(Ipp32u);
    result = NewBigNum(elem_bytes_size, &prime_bn);
    if (kEpidNoErr != result) {
      break;
    }
    prime_bnu = (Ipp32u*)SAFE_ALLOC(elem_bytes_size);
    if (NULL == prime_bnu) {
      result = kEpidMemAllocErr;
      break;
    }
    sts = ippsGFpGetModulus(ff->ipp_ff, prime_bnu);
    result = InitBigNumFromBnu(prime_bnu, elem_dword_size, prime_bn);
    if (kEpidNoErr != result) {
      break;
    }
    result = kEpidNoErr;
  } while (0);
  SAFE_FREE(prime_bnu);
  if (kEpidNoErr != result) {
    DeleteBigNum(&prime_bn);
  } else {
    *bn = prime_bn;
  }
  return result;
}

EpidStatus InitFfElementFromBn(FiniteField* ff, BigNum* bn,
                               FfElement* ff_elem) {
  EpidStatus result = kEpidErr;
  BigNum* prime_bn = NULL;
  BigNum* mod_bn = NULL;
  Ipp32u* mod_str = NULL;

  if (!ff || !bn || !ff_elem) {
    return kEpidBadArgErr;
  }
  if (!ff_elem->ipp_ff_elem || !ff->ipp_ff) {
    return kEpidBadArgErr;
  }
  if (ff->info.basicGFdegree != 1 || ff->info.groundGFdegree != 1) {
    return kEpidBadArgErr;
  }
  if (ff->info.elementLen != ff_elem->info.elementLen) {
    return kEpidBadArgErr;
  }
  do {
    size_t elem_size = ff->info.elementLen * sizeof(Ipp32u);
    result = NewBigNum(elem_size, &mod_bn);
    if (kEpidNoErr != result) {
      break;
    }
    result = GetFiniteFieldPrime(ff, &prime_bn);
    if (kEpidNoErr != result) {
      break;
    }

    result = BigNumMod(bn, prime_bn, mod_bn);
    if (kEpidNoErr != result) {
      break;
    }
    mod_str = (Ipp32u*)SAFE_ALLOC(elem_size);
    if (NULL == mod_str) {
      result = kEpidMemAllocErr;
      break;
    }
    result = WriteBigNum(mod_bn, elem_size, mod_str);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(ff, mod_str, elem_size, ff_elem);
    if (kEpidNoErr != result) {
      break;
    }
    result = kEpidNoErr;
  } while (0);
  SAFE_FREE(mod_str);
  DeleteBigNum(&prime_bn);
  DeleteBigNum(&mod_bn);
  return result;
}

EpidStatus WriteFfElement(FiniteField* ff, FfElement const* ff_elem,
                          void* ff_elem_str, size_t strlen) {
  IppStatus sts;
  size_t strlen_required = 0;
  size_t pad = 0;
  uint8_t* str = (uint8_t*)ff_elem_str;

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

  // ippsGFpGetElementOctString serialize value into a buffer consists
  // of "degree" number of chunks, where each chunk is of modulus byte size.
  strlen_required = ff->prime_modulus_size * ff->info.basicGFdegree;

  // add zero padding for extension of a degree 1 (a prime field)
  // so it can be deserialized into big number correctly.
  if (1 == ff->info.basicGFdegree && strlen_required < strlen) {
    pad = strlen - strlen_required;
    memset(str, 0, pad);
    strlen -= pad;
    str += pad;
  }

  // Check if output buffer meets ippsGFpGetElementOctString expectations.
  if (strlen_required != strlen) return kEpidBadArgErr;

  // get the data
  sts = ippsGFpGetElementOctString(ff_elem->ipp_ff_elem, str, (int)strlen,
                                   ff->ipp_ff);
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

EpidStatus FfSub(FiniteField* ff, FfElement const* a, FfElement const* b,
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

  sts = ippsGFpSub(a->ipp_ff_elem, b->ipp_ff_elem, r->ipp_ff_elem, ff->ipp_ff);
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

EpidStatus FfMultiExpBn(FiniteField* ff, FfElement const** p, BigNum const** b,
                        size_t m, FfElement* r) {
  IppStatus sts = ippStsNoErr;
  EpidStatus result = kEpidErr;
  IppsGFpElement** ipp_p = NULL;
  IppsBigNumState** ipp_b = NULL;
  Ipp8u* scratch_buffer = NULL;

  size_t s = 0;
  int exp_bit_size = 0;
  size_t i = 0;
  int ipp_m = 0;

  // Check required parameters
  if (!ff || !p || !b || !r) {
    return kEpidBadArgErr;
  } else if (!ff->ipp_ff || !r->ipp_ff_elem || m <= 0) {
    return kEpidBadArgErr;
  } else if (ff->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }
  for (s = 0; s < m; s++) {
    if (!p[s] || !b[s]) {
      return kEpidBadArgErr;
    }
  }

  // because we use ipp function with number of items parameter
  // defined as "int" we need to verify that input length
  // do not exceed INT_MAX to avoid overflow
  if (m > INT_MAX) {
    return kEpidBadArgErr;
  }
  ipp_m = (int)m;
  for (i = 0; i < m; i++) {
    int b_size = 0;
    if (!p[i]) {
      return kEpidBadArgErr;
    }
    if (!p[i]->ipp_ff_elem) {
      return kEpidBadArgErr;
    }
    if (ff->info.elementLen != p[i]->info.elementLen) {
      return kEpidBadArgErr;
    }
    sts = ippsGetSize_BN(b[i]->ipp_bn, &b_size);
    if (ippStsNoErr != sts) {
      return kEpidBadArgErr;
    }
    b_size *= (sizeof(Ipp32u) * CHAR_BIT);
    if (b_size > exp_bit_size) {
      exp_bit_size = b_size;
    }
  }

  do {
    int scratch_buffer_size = 0;

    // Allocate memory for finite field elements for ipp call
    ipp_p = (IppsGFpElement**)SAFE_ALLOC(m * sizeof(IppsGFpElement*));
    if (!ipp_p) {
      result = kEpidMemAllocErr;
      break;
    }
    for (i = 0; i < m; i++) {
      ipp_p[i] = p[i]->ipp_ff_elem;
    }

    ipp_b = (IppsBigNumState**)SAFE_ALLOC(m * sizeof(IppsBigNumState*));
    if (!ipp_b) {
      result = kEpidMemAllocErr;
      break;
    }
    // fill ipp array for ipp call
    for (i = 0; i < m; i++) {
      ipp_b[i] = b[i]->ipp_bn;
    }

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

  SAFE_FREE(scratch_buffer);
  SAFE_FREE(ipp_b);
  SAFE_FREE(ipp_p);
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

EpidStatus FfSqrt(FiniteField* ff, FfElement const* a, FfElement* r) {
  EpidStatus result = kEpidErr;
  Ipp8u one_str = 1;
  BigNumStr qm1_str;
  const BigNumStr zero_str = {0};
  bool is_equal = false;
  unsigned int s;
  bool is_even = false;
  unsigned int i;
  BigNum* prime = NULL;
  BigNum* qm1 = NULL;
  BigNum* one = NULL;
  FfElement* qm1_ffe = NULL;
  BigNum* two = NULL;
  BigNum* qm1d2 = NULL;
  BigNum* remainder = NULL;
  FfElement* g = NULL;
  FfElement* gg = NULL;
  BigNum* t = NULL;
  BigNum* e = NULL;
  BigNum* j = NULL;
  BigNum* qm1dj = NULL;
  FfElement* ge = NULL;
  FfElement* h = NULL;
  FfElement* temp = NULL;
  FfElement* one_ffe = NULL;
  BigNum* ed2 = NULL;
  FfElement* ged2 = NULL;
  BigNum* tp1d2 = NULL;
  FfElement* gtp1d2 = NULL;
  FfElement* dd = NULL;

  if (!ff || !a || !r) {
    return kEpidBadArgErr;
  }
  do {
    result = GetFiniteFieldPrime(ff, &prime);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &qm1);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &one);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &qm1_ffe);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &two);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &qm1d2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &remainder);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &g);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &gg);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &t);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &e);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &j);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &qm1dj);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &ge);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &h);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &temp);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &one_ffe);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &ed2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &ged2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(BigNumStr) * CHAR_BIT, &tp1d2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &gtp1d2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(ff, &dd);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadBigNum(&one_str, sizeof(one_str), one);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumSub(prime, one, qm1);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumAdd(one, one, two);
    if (kEpidNoErr != result) {
      break;
    }
    result = InitFfElementFromBn(ff, one, one_ffe);
    if (kEpidNoErr != result) {
      break;
    }
    result = WriteBigNum(qm1, sizeof(qm1_str), &qm1_str);
    if (kEpidNoErr != result) {
      break;
    }
    result = InitFfElementFromBn(ff, qm1, qm1_ffe);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumDiv(qm1, two, qm1d2, remainder);
    if (kEpidNoErr != result) {
      break;
    }

    // 1. Choose an element g in Fq.
    result = ReadFfElement(ff, &one_str, sizeof(one_str), g);
    if (kEpidNoErr != result) {
      break;
    }
    // try small values for g starting from 2 until
    // it meets the requirements from the step 2
    do {
      result = FfAdd(ff, g, one_ffe, g);
      if (kEpidNoErr != result) {
        break;
      }

      // 2. Check whether g^((q-1)/2) mod q = q-1. If not, go to step 1.
      result = FfExp(ff, g, qm1d2, gg);
      if (kEpidNoErr != result) {
        break;
      }

      result = FfIsEqual(ff, gg, qm1_ffe, &is_equal);
      if (kEpidNoErr != result) {
        break;
      }
    } while (!is_equal);
    if (kEpidNoErr != result) {
      break;
    }

    // 3. Set t = q-1, s = 0.
    result = ReadBigNum(&qm1_str, sizeof(qm1_str), t);
    if (kEpidNoErr != result) {
      break;
    }
    s = 0;
    // 4. While (t is even number)
    //    t = t/2, s = s+1.
    result = BigNumIsEven(t, &is_even);
    if (kEpidNoErr != result) {
      break;
    }

    while (is_even) {
      result = BigNumDiv(t, two, t, remainder);
      if (kEpidNoErr != result) {
        break;
      }
      s = s + 1;
      result = BigNumIsEven(t, &is_even);
      if (kEpidNoErr != result) {
        break;
      }
    }
    // 5. Note that g, s, t can be pre-computed and used for all
    //    future computations.
    //    Also note that q-1 = (2^s)*t where t is an odd integer.

    // 6. e = 0.
    result = ReadBigNum(&zero_str, sizeof(zero_str), e);
    if (kEpidNoErr != result) {
      break;
    }

    // 7. For i = 2, ..., s
    //        j = 2^i,
    //        if (a ? g^(-e))^((q-1)/j) mod q != 1, then set e = e + j/2.
    for (i = 2; i <= s; i++) {
      result = BigNumPow2N(i, j);
      if (kEpidNoErr != result) {
        break;
      }
      result = BigNumDiv(qm1, j, qm1dj, remainder);
      if (kEpidNoErr != result) {
        break;
      }
      result = FfExp(ff, g, e, ge);
      if (kEpidNoErr != result) {
        break;
      }
      // 8. Compute h = (a * g^(-e)) mod q.
      result = FfInv(ff, ge, ge);
      if (kEpidNoErr != result) {
        break;
      }
      result = FfMul(ff, a, ge, h);
      if (kEpidNoErr != result) {
        break;
      }
      result = FfExp(ff, h, qm1dj, temp);
      if (kEpidNoErr != result) {
        break;
      }
      result = FfIsEqual(ff, temp, one_ffe, &is_equal);
      if (!is_equal) {
        result = BigNumDiv(j, two, j, remainder);
        if (kEpidNoErr != result) {
          break;
        }
        result = BigNumAdd(e, j, e);
        if (kEpidNoErr != result) {
          break;
        }
      }
    }

    // 8. Compute h = (a * g^(-e)) mod q.
    result = FfExp(ff, g, e, ge);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfInv(ff, ge, ge);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfMul(ff, a, ge, h);
    if (kEpidNoErr != result) {
      break;
    }

    // 9. Compute r = d = (g^(e/2) * h^((t+1)/2)) mod q.
    result = BigNumDiv(e, two, ed2, remainder);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfExp(ff, g, ed2, ged2);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumAdd(t, one, tp1d2);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumDiv(tp1d2, two, tp1d2, remainder);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfExp(ff, h, tp1d2, gtp1d2);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfMul(ff, ged2, gtp1d2, r);
    if (kEpidNoErr != result) {
      break;
    }
    // 10. Verify whether a = d^2 mod q. If so, return r, otherwise, return
    // fail.
    result = FfMul(ff, r, r, dd);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfIsEqual(ff, dd, a, &is_equal);
    if (kEpidNoErr != result) {
      break;
    }
    if (!is_equal) {
      result = kEpidMathQuadraticNonResidueError;
      break;
    }
    result = kEpidNoErr;
  } while (0);
  DeleteFfElement(&dd);
  DeleteFfElement(&gtp1d2);
  DeleteBigNum(&tp1d2);
  DeleteFfElement(&ged2);
  DeleteBigNum(&ed2);
  DeleteFfElement(&one_ffe);
  DeleteFfElement(&temp);
  DeleteFfElement(&h);
  DeleteFfElement(&ge);
  DeleteBigNum(&qm1dj);
  DeleteBigNum(&j);
  DeleteBigNum(&e);
  DeleteBigNum(&t);
  DeleteFfElement(&gg);
  DeleteFfElement(&g);
  DeleteBigNum(&remainder);
  DeleteBigNum(&qm1d2);
  DeleteBigNum(&two);
  DeleteFfElement(&qm1_ffe);
  DeleteBigNum(&one);
  DeleteBigNum(&qm1);
  DeleteBigNum(&prime);
  return result;
}
