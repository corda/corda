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
 * \brief Elliptic curve group implementation.
 */

#include <string.h>
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/math/src/ecgroup-internal.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/src/finitefield-internal.h"
#include "epid/common/math/hash.h"
#include "epid/common/src/memory.h"
#include "epid/common/src/endian_convert.h"
#include "ext/ipp/include/ippcp.h"
#include "ext/ipp/include/ippcpepid.h"
#include "epid/common/1.1/types.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }  /// Handle Ipp Errors with Break
#define BREAK_ON_IPP_ERROR(sts, ret)           \
  {                                            \
    IppStatus temp_sts = (sts);                \
    if (ippStsNoErr != temp_sts) {             \
      if (ippStsContextMatchErr == temp_sts) { \
        (ret) = kEpidMathErr;                  \
      } else {                                 \
        (ret) = kEpidBadArgErr;                \
      }                                        \
      break;                                   \
    }                                          \
  }

EpidStatus NewEcGroup(FiniteField const* ff, FfElement const* a,
                      FfElement const* b, FfElement const* x,
                      FfElement const* y, BigNum const* order,
                      BigNum const* cofactor, EcGroup** g) {
  EpidStatus result = kEpidNoErr;
  IppsGFpECState* state = NULL;
  Ipp8u* scratch_buffer = NULL;
  EcGroup* grp = NULL;
  do {
    IppStatus ipp_status;
    int stateSize = 0;
    int scratch_size = 0;
    Ipp32u* order_bnu;
    Ipp32u* cofactor_bnu;
    int order_bnu_size;
    int cofactor_bnu_size;
    IppsBigNumSGN sgn;
    // validate input pointers
    if (!ff || !a || !b || !x || !y || !order || !cofactor || !g) {
      result = kEpidBadArgErr;
      break;
    }
    if (ff->info.elementLen != a->info.elementLen ||
        ff->info.elementLen != b->info.elementLen ||
        ff->info.elementLen != x->info.elementLen ||
        ff->info.elementLen != y->info.elementLen ||
        a->info.elementLen != b->info.elementLen ||
        a->info.elementLen != x->info.elementLen ||
        a->info.elementLen != y->info.elementLen ||
        b->info.elementLen != x->info.elementLen ||
        b->info.elementLen != y->info.elementLen ||
        x->info.elementLen != y->info.elementLen) {
      result = kEpidBadArgErr;
      break;
    }

    // construct the ECPrimeField
    ipp_status = ippsGFpECGetSize(ff->ipp_ff, &stateSize);
    if (ippStsNoErr != ipp_status) {
      if (ippStsSizeErr == ipp_status) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }

    grp = (EcGroup*)SAFE_ALLOC(sizeof(EcGroup));
    if (!grp) {
      result = kEpidMemAllocErr;
      break;
    }

    state = (IppsGFpECState*)SAFE_ALLOC(stateSize);
    if (!state) {
      result = kEpidMemAllocErr;
      break;
    }

    ipp_status = ippsRef_BN(&sgn, &order_bnu_size, &order_bnu, order->ipp_bn);
    order_bnu_size /= sizeof(CHAR_BIT) * 4;
    if (ippStsNoErr != ipp_status) {
      result = kEpidMathErr;
      break;
    }

    ipp_status =
        ippsRef_BN(&sgn, &cofactor_bnu_size, &cofactor_bnu, cofactor->ipp_bn);
    cofactor_bnu_size /= sizeof(CHAR_BIT) * 4;
    if (ippStsNoErr != ipp_status) {
      result = kEpidMathErr;
      break;
    }

    ipp_status =
        ippsGFpECInit(a->ipp_ff_elem, b->ipp_ff_elem, x->ipp_ff_elem,
                      y->ipp_ff_elem, order_bnu, order_bnu_size, cofactor_bnu,
                      cofactor_bnu_size, ff->ipp_ff, state);
    if (ippStsNoErr != ipp_status) {
      result = kEpidMathErr;
      break;
    }

    // allocate scratch buffer
    ipp_status = ippsGFpECScratchBufferSize(1, state, &scratch_size);
    // check return codes
    if (ippStsNoErr != ipp_status) {
      if (ippStsContextMatchErr == ipp_status)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    // allocate scratch buffer
    scratch_buffer = (Ipp8u*)SAFE_ALLOC(scratch_size);
    if (!scratch_buffer) {
      result = kEpidMemAllocErr;
      break;
    }

    grp->info = ff->info;
    grp->ipp_ec = state;
    grp->scratch_buffer = scratch_buffer;
    *g = grp;
  } while (0);

  if (kEpidNoErr != result) {
    // we had a problem during init, free any allocated memory
    SAFE_FREE(state);
    SAFE_FREE(scratch_buffer);
    SAFE_FREE(grp);
  }
  return result;
}

void DeleteEcGroup(EcGroup** g) {
  if (!g || !(*g)) {
    return;
  }
  if ((*g)->ipp_ec) {
    SAFE_FREE((*g)->ipp_ec);
    (*g)->ipp_ec = NULL;
  }
  if ((*g)->scratch_buffer) {
    SAFE_FREE((*g)->scratch_buffer);
    (*g)->scratch_buffer = NULL;
  }
  SAFE_FREE(*g);
  *g = NULL;
}

EpidStatus NewEcPoint(EcGroup const* g, EcPoint** p) {
  EpidStatus result = kEpidErr;
  IppsGFpECPoint* ec_pt_context = NULL;
  EcPoint* ecpoint = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    int sizeInBytes = 0;
    // validate inputs
    if (!g || !p) {
      result = kEpidBadArgErr;
      break;
    } else if (!g->ipp_ec) {
      result = kEpidBadArgErr;
      break;
    }
    // get size
    sts = ippsGFpECPointGetSize(g->ipp_ec, &sizeInBytes);
    if (ippStsContextMatchErr == sts) {
      result = kEpidBadArgErr;
      break;
    } else if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }
    // allocate memory
    ec_pt_context = (IppsGFpECPoint*)SAFE_ALLOC(sizeInBytes);
    if (!ec_pt_context) {
      result = kEpidMemAllocErr;
      break;
    }
    // Initialize
    sts = ippsGFpECPointInit(NULL, NULL, ec_pt_context, g->ipp_ec);
    if (ippStsContextMatchErr == sts) {
      result = kEpidBadArgErr;
      break;
    } else if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }
    ecpoint = SAFE_ALLOC(sizeof(EcPoint));
    if (!ecpoint) {
      result = kEpidMemAllocErr;
      break;
    }
    ecpoint->info = g->info;
    ecpoint->ipp_ec_pt = ec_pt_context;
    *p = ecpoint;
    result = kEpidNoErr;
  } while (0);
  if (kEpidNoErr != result) {
    SAFE_FREE(ec_pt_context);
    SAFE_FREE(ecpoint);
  }
  return result;
}

void DeleteEcPoint(EcPoint** p) {
  if (p) {
    if (*p) {
      SAFE_FREE((*p)->ipp_ec_pt);
    }
    SAFE_FREE(*p);
  }
}

/// Check and initialize element if it is in elliptic curve group.
/*!
  This is internal function.
  Takes a value p as input. If p is indeed an element of g, it
  outputs true, otherwise, it outputs false.

  This is only used to check if input buffer are actually valid
  elements in group. If p is in g, this fills p and initializes it to
  internal FfElement format.

  \param[in] g
  The eliptic curve group in which to perform the check
  \param[in] p_str
  Serialized eliptic curve group element to check
  \param[in] strlen
  The size of p_str in bytes.
  \param[out] p
  Deserialized value of p_str
  \param[out] in_group
  Result of the check

  \returns ::EpidStatus

  \see NewEcPoint
*/
EpidStatus eccontains(EcGroup* g, void const* p_str, size_t strlen, EcPoint* p,
                      bool* in_group) {
  EpidStatus result = kEpidErr;
  IppStatus sts = ippStsNoErr;
  FiniteField fp;
  FfElement* fp_x = NULL;
  FfElement* fp_y = NULL;
  Ipp8u const* byte_str = (Ipp8u const*)p_str;
  IppECResult ec_result = ippECPointIsNotValid;
  int ipp_half_strlen = (int)strlen / 2;

  if (!g || !p_str || !p || !in_group) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || !p->ipp_ec_pt) {
    return kEpidBadArgErr;
  }

  if (INT_MAX < strlen || strlen <= 0 || strlen & 0x1) {
    return kEpidBadArgErr;
  }

  do {
    size_t i = 0;
    // if the string is all zeros then we take it as point at infinity
    for (i = 0; i < strlen; i++) {
      if (0 != byte_str[i]) {
        break;
      }
    }
    if (i >= strlen) {
      // p_str is point at infinity! Set it and we are done
      sts = ippsGFpECSetPointAtInfinity(p->ipp_ec_pt, g->ipp_ec);
      // check return codes
      if (ippStsNoErr != sts) {
        if (ippStsContextMatchErr == sts)
          result = kEpidBadArgErr;
        else
          result = kEpidMathErr;
        break;
      }
      *in_group = true;
      result = kEpidNoErr;
      break;
    }
    // get finite field
    sts = ippsGFpECGet(g->ipp_ec, (const IppsGFpState**)&(fp.ipp_ff), 0, 0, 0,
                       0, 0, 0, 0, 0);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    // create element X
    result = NewFfElement(&fp, &fp_x);
    if (kEpidNoErr != result) {
      break;
    }

    // create element Y
    result = NewFfElement(&fp, &fp_y);
    if (kEpidNoErr != result) {
      break;
    }

    // set element X data
    sts = ippsGFpSetElementOctString(byte_str, ipp_half_strlen,
                                     fp_x->ipp_ff_elem, fp.ipp_ff);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts || ippStsOutOfRangeErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    // set element Y data
    sts =
        ippsGFpSetElementOctString(byte_str + ipp_half_strlen, ipp_half_strlen,
                                   fp_y->ipp_ff_elem, fp.ipp_ff);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts || ippStsOutOfRangeErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    // set point from elements
    sts = ippsGFpECSetPoint(fp_x->ipp_ff_elem, fp_y->ipp_ff_elem, p->ipp_ec_pt,
                            g->ipp_ec);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    // verify the point is actually on the curve
    sts = ippsGFpECTstPoint(p->ipp_ec_pt, &ec_result, g->ipp_ec,
                            g->scratch_buffer);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    *in_group = (ippECValid == ec_result);
    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&fp_x);
  DeleteFfElement(&fp_y);
  return result;
}

EpidStatus ReadEcPoint(EcGroup* g, void const* p_str, size_t strlen,
                       EcPoint* p) {
  EpidStatus result;
  bool in_group = false;

  if (!g || !p_str || !p) {
    return kEpidBadArgErr;
  }
  if (0 == strlen) {
    return kEpidBadArgErr;
  }

  result = eccontains(g, p_str, strlen, p, &in_group);
  if (kEpidNoErr != result) {
    return result;
  }
  if (in_group == false) {
    IppStatus sts = ippsGFpECPointInit(NULL, NULL, p->ipp_ec_pt, g->ipp_ec);
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else if (ippStsNoErr != sts) {
      return kEpidMathErr;
    }
    return kEpidBadArgErr;
  }
  return kEpidNoErr;
}

EpidStatus WriteEcPoint(EcGroup* g, EcPoint const* p, void* p_str,
                        size_t strlen) {
  EpidStatus result = kEpidErr;
  FiniteField fp;
  FfElement* fp_x = NULL;
  FfElement* fp_y = NULL;
  Ipp8u* byte_str = (Ipp8u*)p_str;
  IppStatus sts = ippStsNoErr;
  int ipp_half_strlen = (int)strlen / 2;

  if (!g || !p || !p_str) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || !p->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  if (INT_MAX < strlen) {
    return kEpidBadArgErr;
  }

  if (INT_MAX < strlen || strlen <= 0 || strlen & 0x1) {
    return kEpidBadArgErr;
  }

  do {
    // get finite field
    sts = ippsGFpECGet(g->ipp_ec, (const IppsGFpState**)&(fp.ipp_ff), 0, 0, 0,
                       0, 0, 0, 0, 0);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    // create element X
    result = NewFfElement(&fp, &fp_x);
    if (kEpidNoErr != result) {
      break;
    }

    // create element Y
    result = NewFfElement(&fp, &fp_y);
    if (kEpidNoErr != result) {
      break;
    }

    // get elements from point
    sts = ippsGFpECGetPoint(p->ipp_ec_pt, fp_x->ipp_ff_elem, fp_y->ipp_ff_elem,
                            g->ipp_ec);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsPointAtInfinity == sts) {
        memset(p_str, 0, strlen);
        result = kEpidNoErr;
      } else if (ippStsContextMatchErr == sts) {
        result = kEpidBadArgErr;
      } else {
        result = kEpidMathErr;
      }
      break;
    }

    // get element X data
    sts = ippsGFpGetElementOctString(fp_x->ipp_ff_elem, byte_str,
                                     ipp_half_strlen, fp.ipp_ff);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }

    // get element Y data
    sts = ippsGFpGetElementOctString(fp_y->ipp_ff_elem,
                                     byte_str + ipp_half_strlen,
                                     ipp_half_strlen, fp.ipp_ff);
    // check return codes
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }
    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&fp_x);
  DeleteFfElement(&fp_y);

  return result;
}

EpidStatus EcMul(EcGroup* g, EcPoint const* a, EcPoint const* b, EcPoint* r) {
  IppStatus sts = ippStsNoErr;
  if (!g || !a || !b || !r) {
    return kEpidBadArgErr;
  } else if (!g->ipp_ec || !a->ipp_ec_pt || !b->ipp_ec_pt || !r->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  if (g->info.elementLen != a->info.elementLen ||
      g->info.elementLen != b->info.elementLen ||
      g->info.elementLen != r->info.elementLen ||
      a->info.elementLen != b->info.elementLen ||
      a->info.elementLen != r->info.elementLen ||
      b->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }
  // Multiplies elliptic curve points
  sts = ippsGFpECAddPoint(a->ipp_ec_pt, b->ipp_ec_pt, r->ipp_ec_pt, g->ipp_ec);
  // Check return codes
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts)
      return kEpidBadArgErr;
    else
      return kEpidMathErr;
  }
  return kEpidNoErr;
}

EpidStatus EcExp(EcGroup* g, EcPoint const* a, BigNumStr const* b, EcPoint* r) {
  EpidStatus result = kEpidErr;
  BigNum* b_bn = NULL;
  do {
    IppStatus sts = ippStsNoErr;

    // Check required parameters
    if (!g || !a || !b || !r) {
      result = kEpidBadArgErr;
      break;
    } else if (!g->ipp_ec || !a->ipp_ec_pt || !r->ipp_ec_pt) {
      result = kEpidBadArgErr;
      break;
    }
    if (g->info.elementLen != a->info.elementLen ||
        g->info.elementLen != r->info.elementLen ||
        a->info.elementLen != r->info.elementLen) {
      result = kEpidBadArgErr;
      break;
    }

    // Create and initialize big number element for ipp call
    result = NewBigNum(sizeof(((BigNumStr*)0)->data.data), &b_bn);
    if (kEpidNoErr != result) break;
    result = ReadBigNum(b, sizeof(*b), b_bn);
    if (kEpidNoErr != result) break;

    sts = ippsGFpECMulPoint(a->ipp_ec_pt, b_bn->ipp_bn, r->ipp_ec_pt, g->ipp_ec,
                            g->scratch_buffer);
    if (ippStsNoErr != sts) {
      if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
          ippStsOutOfRangeErr == sts)
        result = kEpidBadArgErr;
      else
        result = kEpidMathErr;
      break;
    }
    result = kEpidNoErr;
  } while (0);
  DeleteBigNum(&b_bn);
  return result;
}

EpidStatus EcSscmExp(EcGroup* g, EcPoint const* a, BigNumStr const* b,
                     EcPoint* r) {
  // call EcExp directly because its implementation is side channel
  // mitigated already
  return EcExp(g, a, b, r);
}

EpidStatus EcMultiExp(EcGroup* g, EcPoint const** a, BigNumStr const** b,
                      size_t m, EcPoint* r) {
  EpidStatus result = kEpidErr;
  BigNum* b_bn = NULL;
  EcPoint* ecp_t = NULL;
  int i = 0;
  int ii = 0;
  int ipp_m = 0;

  if (!g || !a || !b || !r) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || m <= 0) {
    return kEpidBadArgErr;
  }
  // because we use ipp function with number of items parameter
  // defined as "int" we need to verify that input length
  // do not exceed INT_MAX to avoid overflow
  if (m > INT_MAX) {
    return kEpidBadArgErr;
  }
  ipp_m = (int)m;
  // Verify that ec points are not NULL
  for (i = 0; i < ipp_m; i++) {
    if (!a[i]) {
      return kEpidBadArgErr;
    }
    if (!a[i]->ipp_ec_pt) {
      return kEpidBadArgErr;
    }
    if (g->info.elementLen != a[i]->info.elementLen) {
      return kEpidBadArgErr;
    }
    for (ii = i + 1; ii < ipp_m; ii++) {
      if (a[i]->info.elementLen != a[ii]->info.elementLen) {
        return kEpidBadArgErr;
      }
    }
  }
  if (g->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }

  do {
    IppStatus sts = ippStsNoErr;

    // Create big number element for ipp call
    result = NewBigNum(sizeof(((BigNumStr*)0)->data.data), &b_bn);
    if (kEpidNoErr != result) break;
    // Create temporal EcPoint element
    result = NewEcPoint(g, &ecp_t);
    if (kEpidNoErr != result) break;

    for (i = 0; i < ipp_m; i++) {
      // Initialize big number element for ipp call
      result = ReadBigNum(b[i], sizeof(BigNumStr), b_bn);
      if (kEpidNoErr != result) break;

      sts = ippsGFpECMulPoint(a[i]->ipp_ec_pt, b_bn->ipp_bn, ecp_t->ipp_ec_pt,
                              g->ipp_ec, g->scratch_buffer);
      if (ippStsNoErr != sts) {
        if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
            ippStsOutOfRangeErr == sts)
          result = kEpidBadArgErr;
        else
          result = kEpidMathErr;
        break;
      }
      if (1 == m) {
        sts = ippsGFpECCpyPoint(ecp_t->ipp_ec_pt, r->ipp_ec_pt, g->ipp_ec);
        if (ippStsNoErr != sts) {
          result = kEpidMathErr;
          break;
        }
      } else {
        sts = ippsGFpECAddPoint(ecp_t->ipp_ec_pt, r->ipp_ec_pt, r->ipp_ec_pt,
                                g->ipp_ec);
        if (ippStsNoErr != sts) {
          result = kEpidMathErr;
          break;
        }
      }
    }
    if (kEpidNoErr != result) break;

    result = kEpidNoErr;
  } while (0);
  DeleteBigNum(&b_bn);
  DeleteEcPoint(&ecp_t);

  return result;
}

EpidStatus EcMultiExpBn(EcGroup* g, EcPoint const** a, BigNum const** b,
                        size_t m, EcPoint* r) {
  EpidStatus result = kEpidErr;
  EcPoint* ecp_t = NULL;
  int i = 0;
  int ii = 0;
  int ipp_m = 0;

  if (!g || !a || !b || !r) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || m <= 0) {
    return kEpidBadArgErr;
  }
  // because we use ipp function with number of items parameter
  // defined as "int" we need to verify that input length
  // do not exceed INT_MAX to avoid overflow
  if (m > INT_MAX) {
    return kEpidBadArgErr;
  }
  ipp_m = (int)m;
  // Verify that ec points are not NULL
  for (i = 0; i < ipp_m; i++) {
    if (!a[i]) {
      return kEpidBadArgErr;
    }
    if (!a[i]->ipp_ec_pt) {
      return kEpidBadArgErr;
    }
    if (!b[i]) {
      return kEpidBadArgErr;
    }
    if (!b[i]->ipp_bn) {
      return kEpidBadArgErr;
    }
    if (g->info.elementLen != a[i]->info.elementLen) {
      return kEpidBadArgErr;
    }
    for (ii = i + 1; ii < ipp_m; ii++) {
      if (a[i]->info.elementLen != a[ii]->info.elementLen) {
        return kEpidBadArgErr;
      }
    }
  }
  if (g->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }

  do {
    IppStatus sts = ippStsNoErr;

    // Create temporal EcPoint element
    result = NewEcPoint(g, &ecp_t);
    if (kEpidNoErr != result) break;

    for (i = 0; i < ipp_m; i++) {
      sts = ippsGFpECMulPoint(a[i]->ipp_ec_pt, b[i]->ipp_bn, ecp_t->ipp_ec_pt,
                              g->ipp_ec, g->scratch_buffer);
      if (ippStsNoErr != sts) {
        if (ippStsContextMatchErr == sts || ippStsRangeErr == sts ||
            ippStsOutOfRangeErr == sts)
          result = kEpidBadArgErr;
        else
          result = kEpidMathErr;
        break;
      }
      if (1 == m) {
        sts = ippsGFpECCpyPoint(ecp_t->ipp_ec_pt, r->ipp_ec_pt, g->ipp_ec);
        if (ippStsNoErr != sts) {
          result = kEpidMathErr;
          break;
        }
      } else {
        sts = ippsGFpECAddPoint(ecp_t->ipp_ec_pt, r->ipp_ec_pt, r->ipp_ec_pt,
                                g->ipp_ec);
        if (ippStsNoErr != sts) {
          result = kEpidMathErr;
          break;
        }
      }
    }
    if (kEpidNoErr != result) break;

    result = kEpidNoErr;
  } while (0);
  DeleteEcPoint(&ecp_t);

  return result;
}

EpidStatus EcSscmMultiExp(EcGroup* g, EcPoint const** a, BigNumStr const** b,
                          size_t m, EcPoint* r) {
  // call EcMultiExp directly because its implementation is side channel
  // mitigated already
  return EcMultiExp(g, a, b, m, r);
}

EpidStatus EcGetRandom(EcGroup* g, BitSupplier rnd_func, void* rnd_func_param,
                       EcPoint* r) {
  IppStatus sts = ippStsNoErr;
  if (!g || !rnd_func || !r) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || !g->scratch_buffer) {
    return kEpidBadArgErr;
  }
  if (g->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }

  sts = ippsGFpECSetPointRandom((IppBitSupplier)rnd_func, rnd_func_param,
                                r->ipp_ec_pt, g->ipp_ec, g->scratch_buffer);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }
  return kEpidNoErr;
}

EpidStatus EcInGroup(EcGroup* g, void const* p_str, size_t strlen,
                     bool* in_group) {
  EpidStatus result = kEpidErr;
  EcPoint* p = NULL;

  if (!g || !p_str || !in_group) {
    return kEpidBadArgErr;
  }
  if (0 == strlen) {
    return kEpidBadArgErr;
  }

  if (strlen != sizeof(G1ElemStr) && strlen != sizeof(G2ElemStr)) {
    *in_group = false;
    return kEpidBadArgErr;
  } else {
    if (strlen == sizeof(G1ElemStr)) {
      // check info.elementlen with strlen
      // multiply by 2 for x,y and 4 multiply to convert dword to bytes
      size_t info_elementLen_in_byte = (g->info.elementLen) * 2 * 4;
      if (info_elementLen_in_byte != strlen) {
        *in_group = false;
        return kEpidBadArgErr;
      }
      // check Fq basic and ground degree
      if (g->info.basicGFdegree != 1 || g->info.groundGFdegree != 1) {
        *in_group = false;
        return kEpidBadArgErr;
      }
    }
    if (strlen == sizeof(G2ElemStr)) {
      // check info.elementlen with strlen
      // multiply by 2 for x,y and 4 multiply to convert dword to bytes
      size_t info_elementLen_in_byte = (g->info.elementLen) * 2 * 4;
      IppStatus sts = ippStsNoErr;
      IppsGFpInfo ground_info = {0};
      if (info_elementLen_in_byte != strlen) {
        *in_group = false;
        return kEpidBadArgErr;
      }
      // check Fq2 basic and ground degree
      if (g->info.basicGFdegree != 2 || g->info.groundGFdegree != 2) {
        *in_group = false;
        return kEpidBadArgErr;
      }
      // check Fq basic and ground degree
      sts = ippsGFpGetInfo(g->info.pGroundGF, &ground_info);
      if (ippStsNoErr != sts) {
        if (ippStsContextMatchErr == sts) {
          *in_group = false;
          return kEpidMathErr;
        } else {
          *in_group = false;
          return kEpidBadArgErr;
        }
      }

      if (ground_info.basicGFdegree != 1 || ground_info.groundGFdegree != 1) {
        *in_group = false;
        return kEpidBadArgErr;
      }
    }
  }

  do {
    result = NewEcPoint(g, &p);
    if (kEpidNoErr != result) break;

    result = eccontains(g, p_str, strlen, p, in_group);
    if (kEpidNoErr != result) break;

    result = kEpidNoErr;
  } while (0);

  DeleteEcPoint(&p);

  return result;
}

/// The number of attempts to hash a message to an element
#define EPID_ECHASH_WATCHDOG (50)

#pragma pack(1)
/// 336 bit octet string
typedef struct OctStr336 {
  unsigned char data[336 / CHAR_BIT];  ///< 336 bit data
} OctStr336;
#pragma pack()

/*!
Returns the first bit and the next 336 bits of str in octet string.

\param[in] str hash string
\param[in] str_len hash string lengh in bytes
\param[out] first_bit first bit of str
\param[out] t pointer to the first 336 bits of input str after the first bit
\param[in] t_len length of t octet string

\returns ::EpidStatus
*/
static EpidStatus SplitHashBits(void const* str, size_t str_len,
                                uint32_t* first_bit, OctStr336* t) {
  // this is 336bits /8 bits per byte = 42 bytes
  OctStr336 next336 = {0};
  size_t i = 0;
  if (!str || !first_bit || !t) return kEpidBadArgErr;
  if (str_len < sizeof(next336) + 1) {
    // we need at least 337 bits!
    return kEpidBadArgErr;
  }

  for (i = 0; i < sizeof(next336); i++) {
    // This is not overflowing since str length was assured to
    // be at least one byte greater than needed for 336 bits. We are
    // carrying in the first bit of that byte.
    uint8_t carry = ((((uint8_t const*)str)[i + 1] & 0x80) >> 7);
    next336.data[i] = (((((uint8_t const*)str)[i] << 1) & 0xFF) | carry) & 0xFF;
  }
  *first_bit = ((((uint8_t const*)str)[0] & 0x80) >> 7);
  *t = next336;
  return kEpidNoErr;
}

EpidStatus Epid11EcHash(EcGroup* g, void const* msg, size_t msg_len,
                        EcPoint* r) {
  EpidStatus result = kEpidErr;

#pragma pack(1)
  struct {
    uint32_t msg_len;
    uint8_t msg[1];
  }* hash_buf = NULL;
#pragma pack()
  size_t hash_buf_size = 0;

  FfElement* a = NULL;
  FfElement* b = NULL;

  FfElement* rx = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;

  BigNum* q = NULL;
  BigNum* t_bn = NULL;
  BigNum* h_bn = NULL;

  FiniteField ff = {0};

  // check parameters
  if ((!msg && msg_len > 0) || !r || !g) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || !r->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  if (g->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }

  // mitigate hash_buf_size and msg_len overflow
  if (INT_MAX - sizeof(uint32_t) < msg_len) {
    return kEpidBadArgErr;
  }

  do {
    IppStatus sts;
    uint32_t i = 0;
    uint32_t ip1 = 0;
    uint32_t high_bit = 0;

    IppsGFpState* ipp_ff = NULL;
    uint32_t const* h = NULL;  // cofactor
    int h_len = 0;

    int sqrt_loop_count = 2 * EPID_ECHASH_WATCHDOG;
    Sha256Digest message_digest[2] = {0};
    OctStr336 t = {0};

    hash_buf_size = sizeof(*hash_buf) - sizeof(hash_buf->msg) + msg_len;
    hash_buf = SAFE_ALLOC(hash_buf_size);
    if (!hash_buf) {
      result = kEpidMemAllocErr;
      break;
    }

    sts = ippsGFpECGet(g->ipp_ec, (const IppsGFpState**)&ipp_ff, 0, 0, 0, 0, 0,
                       0, &h, &h_len);
    BREAK_ON_IPP_ERROR(sts, result);
    result = InitFiniteFieldFromIpp(ipp_ff, &ff);
    BREAK_ON_EPID_ERROR(result);

    result = NewFfElement(&ff, &a);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ff, &b);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ff, &rx);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ff, &t1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ff, &t2);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(t), &t_bn);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(h_len, &h_bn);
    BREAK_ON_EPID_ERROR(result);

    sts = ippsGFpECGet(g->ipp_ec, 0, a->ipp_ff_elem, b->ipp_ff_elem, 0, 0, 0, 0,
                       0, 0);
    BREAK_ON_IPP_ERROR(sts, result);

    result = InitBigNumFromBnu(h, h_len, h_bn);
    BREAK_ON_EPID_ERROR(result);

    // compute H = hash (i || m) || Hash (i+1 || m) where (i =ipp32u)
    // copy variable length message to the buffer to hash
    if (0 != memcpy_S(hash_buf->msg,
                      hash_buf_size - sizeof(*hash_buf) + sizeof(hash_buf->msg),
                      msg, msg_len)) {
      result = kEpidErr;
      break;
    }

    do {
      result = kEpidErr;

      // set hash (i || m) portion
      hash_buf->msg_len = ntohl(i);
      result = Sha256MessageDigest(hash_buf, hash_buf_size, &message_digest[0]);
      BREAK_ON_EPID_ERROR(result);
      // set hash (i+1 || m) portion
      ip1 = i + 1;
      hash_buf->msg_len = ntohl(ip1);
      result = Sha256MessageDigest(hash_buf, hash_buf_size, &message_digest[1]);
      BREAK_ON_EPID_ERROR(result);
      // let b = first bit of H
      // t = next 336bits of H (336 = length(q) + slen)
      result =
          SplitHashBits(message_digest, sizeof(message_digest), &high_bit, &t);
      BREAK_ON_EPID_ERROR(result);
      result = ReadBigNum(&t, sizeof(t), t_bn);
      BREAK_ON_EPID_ERROR(result);
      // compute rx = t mod q (aka prime field based on q)
      result = InitFfElementFromBn(&ff, t_bn, rx);
      BREAK_ON_EPID_ERROR(result);

      // t1 = (rx^3 + a*rx + b) mod q
      result = FfMul(&ff, rx, rx, t1);
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ff, t1, rx, t1);
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ff, a, rx, t2);
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ff, t1, t2, t1);
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ff, t1, b, t1);
      BREAK_ON_EPID_ERROR(result);

      // t2 = &ff.sqrt(t1)
      result = FfSqrt(&ff, t1, t2);
      if (kEpidMathQuadraticNonResidueError == result) {
        // if sqrt fail set i = i+ 2 and repeat from top
        i += 2;
        continue;
      } else if (kEpidNoErr != result) {
        result = kEpidErr;
      }
      break;
    } while (--sqrt_loop_count);

    BREAK_ON_EPID_ERROR(result);
    // reset to fail to catch other errors
    result = kEpidErr;

    // y[0] = min (t2, q-t2), y[1] = max(t2, q-t2)
    if (0 == high_bit) {
      // q-t2 = &ff.neg(t2)
      result = FfNeg(&ff, t2, t2);
      BREAK_ON_EPID_ERROR(result);
    }

    // Ry = y[b]
    sts = ippsGFpECSetPoint(rx->ipp_ff_elem, t2->ipp_ff_elem, r->ipp_ec_pt,
                            g->ipp_ec);
    BREAK_ON_IPP_ERROR(sts, result);
    // R = E(&ff).exp(R,h)
    sts = ippsGFpECMulPoint(r->ipp_ec_pt, h_bn->ipp_bn, r->ipp_ec_pt, g->ipp_ec,
                            g->scratch_buffer);
    BREAK_ON_IPP_ERROR(sts, result);

    result = kEpidNoErr;
  } while (0);

  SAFE_FREE(hash_buf);
  DeleteFfElement(&a);
  DeleteFfElement(&b);
  DeleteFfElement(&rx);
  DeleteFfElement(&t1);
  DeleteFfElement(&t2);
  DeleteBigNum(&h_bn);
  DeleteBigNum(&t_bn);
  DeleteBigNum(&q);

  return result;
}

EpidStatus EcHash(EcGroup* g, void const* msg, size_t msg_len, HashAlg hash_alg,
                  EcPoint* r) {
  IppStatus sts = ippStsNoErr;
  IppHashID hash_id;
  int ipp_msg_len = 0;
  Ipp32u i = 0;
  if (!g || (!msg && msg_len > 0) || !r) {
    return kEpidBadArgErr;
  } else if (!g->ipp_ec || !r->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  // because we use ipp function with message length parameter
  // defined as "int" we need to verify that input length
  // do not exceed INT_MAX to avoid overflow
  if (msg_len > INT_MAX) {
    return kEpidBadArgErr;
  }
  ipp_msg_len = (int)msg_len;
  if (kSha256 == hash_alg) {
    hash_id = ippSHA256;
  } else if (kSha384 == hash_alg) {
    hash_id = ippSHA384;
  } else if (kSha512 == hash_alg) {
    hash_id = ippSHA512;
  } else {
    return kEpidHashAlgorithmNotSupported;
  }
  if (g->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }

  do {
    sts = ippsGFpECSetPointHash(i, msg, ipp_msg_len, hash_id, r->ipp_ec_pt,
                                g->ipp_ec, g->scratch_buffer);
  } while (ippStsQuadraticNonResidueErr == sts && i++ < EPID_ECHASH_WATCHDOG);

  if (ippStsContextMatchErr == sts || ippStsBadArgErr == sts ||
      ippStsLengthErr == sts) {
    return kEpidBadArgErr;
  }
  if (ippStsNoErr != sts) {
    return kEpidMathErr;
  }

  return kEpidNoErr;
}

EpidStatus EcMakePoint(EcGroup* g, FfElement const* x, EcPoint* r) {
  IppStatus sts = ippStsNoErr;
  if (!g || !x || !r) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || !x->ipp_ff_elem || !r->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  if (g->info.elementLen != x->info.elementLen ||
      g->info.elementLen != r->info.elementLen ||
      x->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }
  sts = ippsGFpECMakePoint(x->ipp_ff_elem, r->ipp_ec_pt, g->ipp_ec);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts || ippStsQuadraticNonResidueErr == sts ||
        ippStsBadArgErr == sts)
      return kEpidBadArgErr;
    else
      return kEpidMathErr;
  }
  return kEpidNoErr;
}

EpidStatus EcInverse(EcGroup* g, EcPoint const* p, EcPoint* r) {
  IppStatus sts = ippStsNoErr;
  if (!g || !p || !r) {
    return kEpidBadArgErr;
  } else if (!g->ipp_ec || !p->ipp_ec_pt || !r->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  if (g->info.elementLen != p->info.elementLen ||
      g->info.elementLen != r->info.elementLen ||
      p->info.elementLen != r->info.elementLen) {
    return kEpidBadArgErr;
  }
  // Inverses elliptic curve point
  sts = ippsGFpECNegPoint(p->ipp_ec_pt, r->ipp_ec_pt, g->ipp_ec);
  // Check return codes
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts)
      return kEpidBadArgErr;
    else
      return kEpidMathErr;
  }
  return kEpidNoErr;
}

EpidStatus EcIsEqual(EcGroup* g, EcPoint const* a, EcPoint const* b,
                     bool* is_equal) {
  IppStatus sts;
  IppECResult result;

  if (!g || !a || !b || !is_equal) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || !a->ipp_ec_pt || !b->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  if (g->info.elementLen != a->info.elementLen ||
      g->info.elementLen != b->info.elementLen ||
      a->info.elementLen != b->info.elementLen) {
    return kEpidBadArgErr;
  }

  sts = ippsGFpECCmpPoint(a->ipp_ec_pt, b->ipp_ec_pt, &result, g->ipp_ec);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }
  *is_equal = ippECPointIsEqual == result;

  return kEpidNoErr;
}

EpidStatus EcIsIdentity(EcGroup* g, EcPoint const* p, bool* is_identity) {
  IppStatus sts;
  IppECResult result;

  if (!g || !p || !is_identity) {
    return kEpidBadArgErr;
  }
  if (!g->ipp_ec || !p->ipp_ec_pt) {
    return kEpidBadArgErr;
  }
  if (g->info.elementLen != p->info.elementLen) {
    return kEpidBadArgErr;
  }

  sts = ippsGFpECTstPoint(p->ipp_ec_pt, &result, g->ipp_ec, g->scratch_buffer);
  if (ippStsNoErr != sts) {
    if (ippStsContextMatchErr == sts) {
      return kEpidBadArgErr;
    } else {
      return kEpidMathErr;
    }
  }
  *is_identity = ippECPointIsAtInfinite == result;

  return kEpidNoErr;
}
