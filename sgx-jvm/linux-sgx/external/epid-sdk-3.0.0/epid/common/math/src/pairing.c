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
 * \brief Pairing implementation.
 */

#include <limits.h>
#include "epid/common/math/pairing.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/math/src/finitefield-internal.h"
#include "epid/common/math/src/ecgroup-internal.h"
#include "epid/common/math/src/pairing-internal.h"
#include "epid/common/src/memory.h"
#include "ext/ipp/include/ippcp.h"
#include "ext/ipp/include/ippcpepid.h"

/// Handle Ipp Errors with Break
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
/// Handle Ipp Errors with Return
#define RETURN_ON_IPP_ERROR(sts)               \
  {                                            \
    IppStatus temp_sts = (sts);                \
    if (ippStsNoErr != temp_sts) {             \
      if (ippStsContextMatchErr == temp_sts) { \
        return kEpidMathErr;                   \
      } else {                                 \
        return kEpidBadArgErr;                 \
      }                                        \
    }                                          \
  }
/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

#pragma pack(1)
/// Data for element in Fq
typedef struct FqElemDat {
  Ipp32u x[sizeof(FqElemStr) / sizeof(Ipp32u)];  ///< element in Fq
} FqElemDat;
/// Data for element in  Fq2
typedef struct Fq2ElemDat {
  FqElemDat x[2];  ///< element in Fq2
} Fq2ElemDat;
/// Data for element in  Fq2^3
typedef struct Fq6ElemDat {
  Fq2ElemDat x[3];  ///< element in Fq6
} Fq6ElemDat;
/// Data for element in  Fq2^3^2
typedef struct Fq12ElemDat {
  Fq6ElemDat x[2];  ///< element in Fq12
} Fq12ElemDat;
#pragma pack()

// Forward Declarations
static EpidStatus FinalExp(PairingState* ps, FfElement* d, FfElement const* h);

static EpidStatus PiOp(PairingState* ps, FfElement* x_out, FfElement* y_out,
                       FfElement const* x, FfElement const* y, const int e);

static EpidStatus FrobeniusOp(PairingState* ps, FfElement* d_out,
                              FfElement const* a, const int e);

static EpidStatus Line(FiniteField* gt, FfElement* f, FfElement* x_out,
                       FfElement* y_out, FfElement* z_out, FfElement* z2_out,
                       FfElement const* px, FfElement const* py,
                       FfElement const* x, FfElement const* y,
                       FfElement const* z, FfElement const* z2,
                       FfElement const* qx, FfElement const* qy);

static EpidStatus Tangent(FiniteField* gt, FfElement* f, FfElement* x_out,
                          FfElement* y_out, FfElement* z_out, FfElement* z2_out,
                          FfElement const* px, FfElement const* py,
                          FfElement const* x, FfElement const* y,
                          FfElement const* z, FfElement const* z2);

static EpidStatus Ternary(int* s, int* n, int max_elements, BigNum const* x);

static int Bit(Ipp32u const* num, Ipp32u bit_index);

static EpidStatus MulXiFast(FfElement* e, FfElement const* a, PairingState* ps);

static EpidStatus MulV(FfElement* e, FfElement* a, PairingState* ps);

static EpidStatus Fq6MulGFpE2(FfElement* e, FfElement* a, FfElement* b0,
                              FfElement* b1, PairingState* ps);

static EpidStatus MulSpecial(FfElement* e, FfElement const* a,
                             FfElement const* b, PairingState* ps);

static EpidStatus SquareCyclotomic(PairingState* ps, FfElement* e_out,
                                   FfElement const* a_in);

static EpidStatus ExpCyclotomic(PairingState* ps, FfElement* e,
                                FfElement const* a, BigNum const* b);

// Implementation

EpidStatus NewPairingState(EcGroup const* ga, EcGroup const* gb,
                           FiniteField* ff, BigNumStr const* t, bool neg,
                           PairingState** ps) {
  EpidStatus result = kEpidErr;
  FfElement* xi = NULL;
  PairingState* paring_state_ctx = NULL;
  BigNum* e = NULL;
  BigNum* one = NULL;
  BigNum* q = NULL;
  BigNum* six = NULL;
  Ipp8u* scratch_buffer = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    IppsGFpState* Fq6 = NULL;
    IppsGFpState* Fq2 = NULL;
    IppsGFpState* Fq = NULL;
    FiniteField Ffq2;
    IppsGFpInfo info = {0};
    Fq2ElemDat Fq6IrrPolynomial[3 + 1] = {0};
    uint8_t one_str[] = {1};
    uint8_t six_str[] = {6};
    FqElemDat qDat = {0};
    int i = 0;
    int j = 0;
    int bufferSize = 0;
    int bitSize = 0;
    // validate inputs
    if (!ga || !gb || !ff || !t || !ps) {
      result = kEpidBadArgErr;
      break;
    }
    if (!ga->ipp_ec || !gb->ipp_ec || !ff->ipp_ff) {
      result = kEpidBadArgErr;
      break;
    }
    // get Fq6, Fq2, Fq
    sts = ippsGFpGetInfo(ff->ipp_ff, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq6 = (IppsGFpState*)info.pGroundGF;
    sts = ippsGFpGetInfo(Fq6, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq2 = (IppsGFpState*)info.pGroundGF;
    result = InitFiniteFieldFromIpp(Fq2, &Ffq2);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpGetInfo(Fq2, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq = (IppsGFpState*)info.pGroundGF;
    // now get ref to modulus of Fq
    sts = ippsGFpGetModulus(Fq, (Ipp32u*)&qDat);
    BREAK_ON_IPP_ERROR(sts, result);
    // extract xi from Fq6 irr poly
    result = NewFfElement(&Ffq2, &xi);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpGetModulus(Fq6, (Ipp32u*)&Fq6IrrPolynomial[0]);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u const*)&Fq6IrrPolynomial[0],
                            sizeof(Fq6IrrPolynomial[0]) / sizeof(Ipp32u),
                            xi->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // first coefficent is -xi
    sts = ippsGFpNeg(xi->ipp_ff_elem, xi->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);

    paring_state_ctx = (PairingState*)SAFE_ALLOC(sizeof(PairingState));
    if (!paring_state_ctx) {
      result = kEpidMemAllocErr;
      break;
    }

    // 1. Set param(pairing) = (param(G1), param(G2), param(GT), t, neg)
    paring_state_ctx->ga = (EcGroup*)ga;
    paring_state_ctx->gb = (EcGroup*)gb;
    paring_state_ctx->ff = ff;
    result = NewBigNum(sizeof(BigNumStr), &paring_state_ctx->t);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(t, sizeof(BigNumStr), paring_state_ctx->t);
    BREAK_ON_EPID_ERROR(result);
    paring_state_ctx->neg = neg;
    result = InitFiniteFieldFromIpp(Fq6, &(paring_state_ctx->Fq6));
    BREAK_ON_EPID_ERROR(result);
    result = InitFiniteFieldFromIpp(Fq2, &(paring_state_ctx->Fq2));
    BREAK_ON_EPID_ERROR(result);
    result = InitFiniteFieldFromIpp(Fq, &(paring_state_ctx->Fq));
    BREAK_ON_EPID_ERROR(result);
    // 2. Let g[0][0], ..., g[0][4], g[1][0], ..., g[1][4], g[2][0], ...,
    // g[2][4] be 15 elements in Fq2.
    for (i = 0; i < 3; i++) {
      for (j = 0; j < 5; j++) {
        result = NewFfElement(&Ffq2, &paring_state_ctx->g[i][j]);
        BREAK_ON_EPID_ERROR(result);
      }
    }
    // 3. Compute a big integer e = (q - 1)/6.
    result = NewBigNum(sizeof(BigNumStr), &one);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(one_str, sizeof(one_str), one);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(BigNumStr), &q);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsSet_BN(IppsBigNumPOS, sizeof(qDat) / sizeof(Ipp32u),
                     (Ipp32u*)&qDat, q->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    result = NewBigNum(sizeof(BigNumStr), &e);
    BREAK_ON_EPID_ERROR(result);
    // q - 1
    sts = ippsSub_BN(q->ipp_bn, one->ipp_bn, e->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    result = NewBigNum(sizeof(BigNumStr), &six);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(six_str, sizeof(six_str), six);
    BREAK_ON_EPID_ERROR(result);
    // e = (q - 1)/6
    // reusing one as remainder here
    sts = ippsDiv_BN(e->ipp_bn, six->ipp_bn, e->ipp_bn, one->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    // 4. Compute g[0][0] = Fq2.exp(xi, e).
    sts = ippsRef_BN(0, &bitSize, 0, e->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpScratchBufferSize(1, bitSize, Fq2, &bufferSize);
    BREAK_ON_IPP_ERROR(sts, result);
    scratch_buffer = (Ipp8u*)SAFE_ALLOC(bufferSize);
    if (!scratch_buffer) {
      result = kEpidMemAllocErr;
      break;
    }
    sts =
        ippsGFpExp(xi->ipp_ff_elem, e->ipp_bn,
                   paring_state_ctx->g[0][0]->ipp_ff_elem, Fq2, scratch_buffer);
    BREAK_ON_IPP_ERROR(sts, result);
    // 5. For i = 0, ..., 4, compute
    for (i = 0; i < 5; i++) {
      // a. If i > 0, compute g[0][i] = Fq2.mul(g[0][i-1], g[0][0]).
      if (i > 0) {
        sts = ippsGFpMul(paring_state_ctx->g[0][i - 1]->ipp_ff_elem,
                         paring_state_ctx->g[0][0]->ipp_ff_elem,
                         paring_state_ctx->g[0][i]->ipp_ff_elem, Fq2);
      }
      // b. Compute g[1][i] = Fq2.conjugate(g[0][i]),
      sts = ippsGFpConj(paring_state_ctx->g[0][i]->ipp_ff_elem,
                        paring_state_ctx->g[1][i]->ipp_ff_elem, Fq2);
      // c. Compute g[1][i] = Fq2.mul(g[0][i], g[1][i]),
      sts = ippsGFpMul(paring_state_ctx->g[0][i]->ipp_ff_elem,
                       paring_state_ctx->g[1][i]->ipp_ff_elem,
                       paring_state_ctx->g[1][i]->ipp_ff_elem, Fq2);
      // d. Compute g[2][i] = Fq2.mul(g[0][i], g[1][i]).
      sts = ippsGFpMul(paring_state_ctx->g[0][i]->ipp_ff_elem,
                       paring_state_ctx->g[1][i]->ipp_ff_elem,
                       paring_state_ctx->g[2][i]->ipp_ff_elem, Fq2);
    }
    // 6. Save g[0][0], ..., g[0][4], g[1][0], ..., g[1][4], g[2][0], ...,
    // g[2][4]
    //    for the pairing operations.
    *ps = paring_state_ctx;
    result = kEpidNoErr;
  } while (0);
  SAFE_FREE(scratch_buffer)
  DeleteBigNum(&six);
  DeleteBigNum(&e);
  DeleteBigNum(&q);
  DeleteBigNum(&one);
  DeleteFfElement(&xi);
  if (kEpidNoErr != result) {
    if (paring_state_ctx) {
      int i = 0;
      int j = 0;
      for (i = 0; i < 3; i++) {
        for (j = 0; j < 5; j++) {
          DeleteFfElement(&paring_state_ctx->g[i][j]);
        }
      }
      DeleteBigNum(&paring_state_ctx->t);
      SAFE_FREE(paring_state_ctx);
    }
  }
  return result;
}

void DeletePairingState(PairingState** ps) {
  if (!ps) {
    return;
  }
  if (!*ps) {
    return;
  }
  if (ps) {
    if (*ps) {
      int i = 0;
      int j = 0;
      for (i = 0; i < 3; i++) {
        for (j = 0; j < 5; j++) {
          DeleteFfElement(&(*ps)->g[i][j]);
        }
      }
      DeleteBigNum(&(*ps)->t);
      (*ps)->ga = NULL;
      (*ps)->gb = NULL;
      (*ps)->ff = NULL;
    }
    SAFE_FREE(*ps);
  }
}

EpidStatus Pairing(PairingState* ps, FfElement* d, EcPoint const* a,
                   EcPoint const* b) {
  EpidStatus result = kEpidErr;
  FfElement* ax = NULL;
  FfElement* ay = NULL;
  FfElement* bx = NULL;
  FfElement* by = NULL;
  FfElement* x = NULL;
  FfElement* y = NULL;
  FfElement* z = NULL;
  FfElement* z2 = NULL;
  FfElement* bx_ = NULL;
  FfElement* by_ = NULL;
  FfElement* f = NULL;
  BigNum* s = NULL;
  BigNum* two = NULL;
  BigNum* six = NULL;
  FfElement* neg_qy = NULL;

  do {
    IppStatus sts = ippStsNoErr;
    Ipp32u two_dat[] = {2};
    Ipp32u six_dat[] = {6};
    Ipp32u one_dat[] = {1};
    int s_ternary[sizeof(BigNumStr) * CHAR_BIT] = {0};
    int i = 0;
    int n = 0;
    // check parameters
    if (!ps || !d || !a || !b) {
      result = kEpidBadArgErr;
      break;
    }
    if (!d->ipp_ff_elem || !a->ipp_ec_pt || !b->ipp_ec_pt || !ps->ff ||
        !ps->ff->ipp_ff || !ps->Fq.ipp_ff || !ps->Fq2.ipp_ff || !ps->t ||
        !ps->t->ipp_bn || !ps->ga || !ps->ga->ipp_ec || !ps->gb ||
        !ps->gb->ipp_ec) {
      result = kEpidBadArgErr;
      break;
    }
    // Let ax, ay be elements in Fq. Let bx, by, x, y, z, z2, bx', by'
    // be elements in Fq2. Let f be a variable in GT.
    result = NewFfElement(&ps->Fq, &ax);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &ay);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &bx);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &by);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &x);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &y);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &z);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &z2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &bx_);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &by_);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &f);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq2, &neg_qy);
    BREAK_ON_EPID_ERROR(result);

    // 1. If neg = 0, compute integer s = 6t + 2, otherwise, compute
    // s = 6t - 2
    result = NewBigNum(sizeof(BigNumStr), &s);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(BigNumStr), &two);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsSet_BN(IppsBigNumPOS, sizeof(two_dat) / sizeof(Ipp32u), two_dat,
                     two->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    result = NewBigNum(sizeof(BigNumStr), &six);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsSet_BN(IppsBigNumPOS, sizeof(six_dat) / sizeof(Ipp32u), six_dat,
                     six->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsMul_BN(six->ipp_bn, ps->t->ipp_bn, s->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    if (ps->neg) {
      sts = ippsSub_BN(s->ipp_bn, two->ipp_bn, s->ipp_bn);
      BREAK_ON_IPP_ERROR(sts, result);
    } else {
      sts = ippsAdd_BN(s->ipp_bn, two->ipp_bn, s->ipp_bn);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 2. Let sn...s1s0 be the ternary representation of s, that is s =
    // s0 + 2*s1 + ... + 2^n*sn, where si is in {-1, 0, 1}.
    result =
        Ternary(s_ternary, &n, sizeof(s_ternary) / sizeof(s_ternary[0]), s);
    BREAK_ON_EPID_ERROR(result);
    // 3. Set (ax, ay) = E(Fq).outputPoint(a)
    sts = ippsGFpECGetPoint(a->ipp_ec_pt, ax->ipp_ff_elem, ay->ipp_ff_elem,
                            ps->ga->ipp_ec);
    BREAK_ON_IPP_ERROR(sts, result);
    // 4. Set (bx, by) = E(Fq2).outputPoint(b).
    sts = ippsGFpECGetPoint(b->ipp_ec_pt, bx->ipp_ff_elem, by->ipp_ff_elem,
                            ps->gb->ipp_ec);
    BREAK_ON_IPP_ERROR(sts, result);
    // 5. Set X = bx, Y = by, Z = Z2 = 1.
    sts = ippsGFpCpyElement(bx->ipp_ff_elem, x->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpCpyElement(by->ipp_ff_elem, y->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement(one_dat, sizeof(one_dat) / sizeof(Ipp32u),
                            z->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement(one_dat, sizeof(one_dat) / sizeof(Ipp32u),
                            z2->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 6. Set d = 1.
    sts = ippsGFpSetElement(one_dat, sizeof(one_dat) / sizeof(Ipp32u),
                            d->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 7. For i = n-1, ..., 0, do the following:
    for (i = n - 1; i >= 0; i--) {
      // a. Set (f, x, y, z, z2) = tangent(ax, ay, x, y, z, z2),
      result = Tangent(ps->ff, f, x, y, z, z2, ax, ay, x, y, z, z2);
      BREAK_ON_EPID_ERROR(result);
      // b. Set d = Fq12.square(d),
      sts = ippsGFpMul(d->ipp_ff_elem, d->ipp_ff_elem, d->ipp_ff_elem,
                       ps->ff->ipp_ff);
      BREAK_ON_IPP_ERROR(sts, result);
      // c. Set d = Fq12.mulSpecial(d, f),
      result = MulSpecial(d, d, f, ps);
      BREAK_ON_EPID_ERROR(result);
      // d. If s[i] = -1 then
      if (-1 == s_ternary[i]) {
        // i. Set (f, x, y, z, z2) = line(ax, ay, x, y, z, z2, bx,
        // -by),
        BREAK_ON_EPID_ERROR(result);
        sts = ippsGFpNeg(by->ipp_ff_elem, neg_qy->ipp_ff_elem, ps->Fq2.ipp_ff);
        BREAK_ON_IPP_ERROR(sts, result);
        result = Line(ps->ff, f, x, y, z, z2, ax, ay, x, y, z, z2, bx, neg_qy);
        BREAK_ON_EPID_ERROR(result);
        // ii. Set d = Fq12.mulSpecial(d, f).
        result = MulSpecial(d, d, f, ps);
        BREAK_ON_EPID_ERROR(result);
      }
      // e. If s[i] = 1 then
      if (1 == s_ternary[i]) {
        // i. Set (f, x, y, z, z2) = line(ax, ay, x, y, z, z2, bx,
        // by),
        result = Line(ps->ff, f, x, y, z, z2, ax, ay, x, y, z, z2, bx, by);
        BREAK_ON_EPID_ERROR(result);
        // ii. Set d = Fq12.mulSpecial(d, f).
        result = MulSpecial(d, d, f, ps);
        BREAK_ON_EPID_ERROR(result);
      }
    }

    // 8. if neg = true,
    if (ps->neg) {
      // a. Set Y = Fq2.negate(y),
      sts = ippsGFpNeg(y->ipp_ff_elem, y->ipp_ff_elem, ps->Fq2.ipp_ff);
      BREAK_ON_IPP_ERROR(sts, result);
      // b. Set d = Fq12.conjugate(d).
      sts = ippsGFpConj(d->ipp_ff_elem, d->ipp_ff_elem, ps->ff->ipp_ff);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 9. Set (bx', by') = Pi-op(bx, by, 1).
    result = PiOp(ps, bx_, by_, bx, by, 1);
    BREAK_ON_EPID_ERROR(result);
    // 10. Set (f, x, y, z, z2) = line(ax, ay, x, y, z, z2, bx', by').
    result = Line(ps->ff, f, x, y, z, z2, ax, ay, x, y, z, z2, bx_, by_);
    BREAK_ON_EPID_ERROR(result);
    // 11. Set d = Fq12.mulSpecial(d, f).
    result = MulSpecial(d, d, f, ps);
    BREAK_ON_EPID_ERROR(result);
    // 12. Set (bx', by') = piOp(bx, by, 2).
    result = PiOp(ps, bx_, by_, bx, by, 2);
    BREAK_ON_EPID_ERROR(result);
    // 13. Set by' = Fq2.negate(by').
    sts = ippsGFpNeg(by_->ipp_ff_elem, by_->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 14. Set (f, x, y, z, z2) = line(ax, ay, x, y, z, z2, bx', by').
    result = Line(ps->ff, f, x, y, z, z2, ax, ay, x, y, z, z2, bx_, by_);
    BREAK_ON_EPID_ERROR(result);
    // 15. Set d = Fq12.mulSpecial(d, f).
    result = MulSpecial(d, d, f, ps);
    BREAK_ON_EPID_ERROR(result);
    // 16. Set d = finalExp(d).
    result = FinalExp(ps, d, d);
    BREAK_ON_EPID_ERROR(result);
    // 17. Return d.
    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&ax);
  DeleteFfElement(&ay);
  DeleteFfElement(&bx);
  DeleteFfElement(&by);
  DeleteFfElement(&x);
  DeleteFfElement(&y);
  DeleteFfElement(&z);
  DeleteFfElement(&z2);
  DeleteFfElement(&bx_);
  DeleteFfElement(&by_);
  DeleteFfElement(&f);
  DeleteFfElement(&neg_qy);

  DeleteBigNum(&s);
  DeleteBigNum(&two);
  DeleteBigNum(&six);

  return result;
}

/*
d = finalExp(h)
Input: h (an element in GT)
Output: d (an element in GT) where d = GT.exp(h, (q^12-1)/p)
*/
static EpidStatus FinalExp(PairingState* ps, FfElement* d, FfElement const* h) {
  EpidStatus result = kEpidErr;
  FfElement* f = NULL;
  FfElement* f1 = NULL;
  FfElement* f2 = NULL;
  FfElement* f3 = NULL;
  FfElement* ft1 = NULL;
  FfElement* ft2 = NULL;
  FfElement* ft3 = NULL;
  FfElement* fp1 = NULL;
  FfElement* fp2 = NULL;
  FfElement* fp3 = NULL;
  FfElement* y0 = NULL;
  FfElement* y1 = NULL;
  FfElement* y2 = NULL;
  FfElement* y3 = NULL;
  FfElement* y4 = NULL;
  FfElement* y5 = NULL;
  FfElement* y6 = NULL;
  FfElement* t0 = NULL;
  FfElement* t1 = NULL;
  do {
    IppStatus sts = ippStsNoErr;
    // Check parameters
    if (!ps || !d || !h) {
      result = kEpidBadArgErr;
      break;
    }
    if (!d->ipp_ff_elem || !h->ipp_ff_elem || !ps->ff || !ps->ff->ipp_ff ||
        !ps->t || !ps->t->ipp_bn) {
      result = kEpidBadArgErr;
      break;
    }
    // Let f, f1, f2, f3, ft1, ft2, ft3, fp1, fp2, fp3, y0, y1, y2,
    // y3, y4, y5, y6, t0, t1 be temporary variables in GT. All the
    // following operations are computed in Fq12 unless explicitly
    // specified.
    result = NewFfElement(ps->ff, &f);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &f1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &f2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &f3);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &ft1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &ft2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &ft3);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &fp1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &fp2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &fp3);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &y0);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &y1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &y2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &y3);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &y4);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &y5);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &y6);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &t0);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &t1);
    BREAK_ON_EPID_ERROR(result);
    // 1.  Set f1 = Fq12.conjugate(h).
    sts = ippsGFpConj(h->ipp_ff_elem, f1->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 2.  Set f2 = Fq12.inverse(h).
    sts = ippsGFpInv(h->ipp_ff_elem, f2->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 3.  Set f = f1 * f2.
    sts = ippsGFpMul(f1->ipp_ff_elem, f2->ipp_ff_elem, f->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 4.  Set f3 = frobeniusOp(f, 2).
    result = FrobeniusOp(ps, f3, f, 2);
    BREAK_ON_EPID_ERROR(result);
    // 5.  Set f = f3 * f.
    sts = ippsGFpMul(f3->ipp_ff_elem, f->ipp_ff_elem, f->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 6.  Set ft1 = Fq12.expCyclotomic (f, t).
    result = ExpCyclotomic(ps, ft1, f, ps->t);
    BREAK_ON_EPID_ERROR(result);
    // 7.  If neg = true, ft1 = Fq12.conjugate(ft1).
    if (ps->neg) {
      sts = ippsGFpConj(ft1->ipp_ff_elem, ft1->ipp_ff_elem, ps->ff->ipp_ff);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 8.  Set ft2 = Fq12.expCyclotomic (ft1, t).
    result = ExpCyclotomic(ps, ft2, ft1, ps->t);
    BREAK_ON_EPID_ERROR(result);
    // 9.  If neg = true, ft2 = Fq12.conjugate(ft2).
    if (ps->neg) {
      sts = ippsGFpConj(ft2->ipp_ff_elem, ft2->ipp_ff_elem, ps->ff->ipp_ff);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 10. Set ft3 = Fq12.expCyclotomic (ft2, t).
    result = ExpCyclotomic(ps, ft3, ft2, ps->t);
    BREAK_ON_EPID_ERROR(result);
    // 11. If neg = true, ft3 = Fq12.conjugate(ft3).
    if (ps->neg) {
      sts = ippsGFpConj(ft3->ipp_ff_elem, ft3->ipp_ff_elem, ps->ff->ipp_ff);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 12. Set fp1 = frobeniusOp(f, 1).
    result = FrobeniusOp(ps, fp1, f, 1);
    BREAK_ON_EPID_ERROR(result);
    // 13. Set fp2 = frobeniusOp(f, 2).
    result = FrobeniusOp(ps, fp2, f, 2);
    BREAK_ON_EPID_ERROR(result);
    // 14. Set fp3 = frobeniusOp(f, 3).
    result = FrobeniusOp(ps, fp3, f, 3);
    BREAK_ON_EPID_ERROR(result);
    // 15. Set y0 = fp1 * fp2 * fp3.
    sts = ippsGFpMul(fp1->ipp_ff_elem, fp2->ipp_ff_elem, y0->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(y0->ipp_ff_elem, fp3->ipp_ff_elem, y0->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 16. Set y1 = Fq12.conjugate(f).
    sts = ippsGFpConj(f->ipp_ff_elem, y1->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 17. Set y2 = frobeniusOp(ft2, 2).
    result = FrobeniusOp(ps, y2, ft2, 2);
    BREAK_ON_EPID_ERROR(result);
    // 18. Set y3 = frobeniusOp(ft1, 1).
    result = FrobeniusOp(ps, y3, ft1, 1);
    BREAK_ON_EPID_ERROR(result);
    // 19. Set y3 = Fq12.conjugate(y3).
    sts = ippsGFpConj(y3->ipp_ff_elem, y3->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 20. Set y4 = frobeniusOp(ft2, 1).
    result = FrobeniusOp(ps, y4, ft2, 1);
    BREAK_ON_EPID_ERROR(result);
    // 21. Set y4 = y4 * ft1.
    sts = ippsGFpMul(y4->ipp_ff_elem, ft1->ipp_ff_elem, y4->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 22. Set y4 = Fq12.conjugate(y4).
    sts = ippsGFpConj(y4->ipp_ff_elem, y4->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 23. Set y5 = Fq12.conjugate(ft2).
    sts = ippsGFpConj(ft2->ipp_ff_elem, y5->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 24. Set y6 = frobeniusOp(ft3, 1).
    result = FrobeniusOp(ps, y6, ft3, 1);
    BREAK_ON_EPID_ERROR(result);
    // 25. Set y6 = y6 * ft3.
    sts = ippsGFpMul(y6->ipp_ff_elem, ft3->ipp_ff_elem, y6->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 26. Set y6 = Fq12.conjugate(y6).
    sts = ippsGFpConj(y6->ipp_ff_elem, y6->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 27. Set t0 = Fq12.squareCyclotomic(y6).
    result = SquareCyclotomic(ps, t0, y6);
    BREAK_ON_EPID_ERROR(result);
    // 28. Set t0 = t0 * y4 * y5.
    sts = ippsGFpMul(t0->ipp_ff_elem, y4->ipp_ff_elem, t0->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(t0->ipp_ff_elem, y5->ipp_ff_elem, t0->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 29. Set t1 = y3 * y5 * t0.
    sts = ippsGFpMul(y3->ipp_ff_elem, y5->ipp_ff_elem, t1->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(t1->ipp_ff_elem, t0->ipp_ff_elem, t1->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 30. Set t0 = t0 * y2.
    sts = ippsGFpMul(t0->ipp_ff_elem, y2->ipp_ff_elem, t0->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 31. Set t1 = Fq12.squareCyclotomic(t1).
    result = SquareCyclotomic(ps, t1, t1);
    BREAK_ON_EPID_ERROR(result);
    // 32. Set t1 = t1 * t0.
    sts = ippsGFpMul(t1->ipp_ff_elem, t0->ipp_ff_elem, t1->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 33. Set t1 = Fq12.squareCyclotomic(t1).
    result = SquareCyclotomic(ps, t1, t1);
    BREAK_ON_EPID_ERROR(result);
    // 34. Set t0 = t1 * y1.
    sts = ippsGFpMul(t1->ipp_ff_elem, y1->ipp_ff_elem, t0->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 35. Set t1 = t1 * y0.
    sts = ippsGFpMul(t1->ipp_ff_elem, y0->ipp_ff_elem, t1->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 36. Set t0 = Fq12.squareCyclotomic(t0).
    result = SquareCyclotomic(ps, t0, t0);
    BREAK_ON_EPID_ERROR(result);
    // 37. Set d = t1 * t0.
    sts = ippsGFpMul(t1->ipp_ff_elem, t0->ipp_ff_elem, d->ipp_ff_elem,
                     ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 38. Return d.
    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&f);
  DeleteFfElement(&f1);
  DeleteFfElement(&f2);
  DeleteFfElement(&f3);
  DeleteFfElement(&ft1);
  DeleteFfElement(&ft2);
  DeleteFfElement(&ft3);
  DeleteFfElement(&fp1);
  DeleteFfElement(&fp2);
  DeleteFfElement(&fp3);
  DeleteFfElement(&y0);
  DeleteFfElement(&y1);
  DeleteFfElement(&y2);
  DeleteFfElement(&y3);
  DeleteFfElement(&y4);
  DeleteFfElement(&y5);
  DeleteFfElement(&y6);
  DeleteFfElement(&t0);
  DeleteFfElement(&t1);

  return result;
}

/*
(x', y') = piOp(x, y, e)
Input: x, y (elements in Fq2), e (an integer of value 1 or 2)
Output: x', y' (elements in Fq2)
*/
static EpidStatus PiOp(PairingState* ps, FfElement* x_out, FfElement* y_out,
                       FfElement const* x, FfElement const* y, const int e) {
  IppStatus sts = ippStsNoErr;
  IppsGFpState* Fq2 = 0;
  IppsGFpState* Fq6 = 0;
  FiniteField* Fq12 = 0;
  IppsGFpInfo info = {0};
  // check parameters
  if (!ps || !x_out || !y_out || !x || !y) {
    return kEpidBadArgErr;
  }
  if (e < 1 || e > 3) {
    return kEpidBadArgErr;
  }
  Fq12 = ps->ff;
  // get Fq6, Fq2
  sts = ippsGFpGetInfo(Fq12->ipp_ff, &info);
  RETURN_ON_IPP_ERROR(sts);
  Fq6 = (IppsGFpState*)info.pGroundGF;
  sts = ippsGFpGetInfo(Fq6, &info);
  RETURN_ON_IPP_ERROR(sts);
  Fq2 = (IppsGFpState*)info.pGroundGF;
  // 1. Set x' = x and y' = y.
  sts = ippsGFpCpyElement(x->ipp_ff_elem, x_out->ipp_ff_elem, Fq2);
  RETURN_ON_IPP_ERROR(sts);
  sts = ippsGFpCpyElement(y->ipp_ff_elem, y_out->ipp_ff_elem, Fq2);
  RETURN_ON_IPP_ERROR(sts);
  if (1 == e) {
    // 2. If e = 1,
    //   a. Compute x' = Fq2.conjugate(x').
    sts = ippsGFpConj(x_out->ipp_ff_elem, x_out->ipp_ff_elem, Fq2);
    RETURN_ON_IPP_ERROR(sts);
    //   b. Compute y' = Fq2.conjugate(y').
    sts = ippsGFpConj(y_out->ipp_ff_elem, y_out->ipp_ff_elem, Fq2);
    RETURN_ON_IPP_ERROR(sts);
  }
  // 3. Compute x' = Fq2.mul(x', g[e-1][1]).
  sts = ippsGFpMul(x_out->ipp_ff_elem, ps->g[e - 1][1]->ipp_ff_elem,
                   x_out->ipp_ff_elem, Fq2);
  RETURN_ON_IPP_ERROR(sts);
  // 4. Compute y' = Fq2.mul(y', g[e-1][2]).
  sts = ippsGFpMul(y_out->ipp_ff_elem, ps->g[e - 1][2]->ipp_ff_elem,
                   y_out->ipp_ff_elem, Fq2);
  RETURN_ON_IPP_ERROR(sts);
  // 5. Return (x', y').
  return kEpidNoErr;
}

/*
d = frobeniusOp(a, e)
Input: a (an element in GT), e (an integer of value 1, 2, or 3)
Output: d (an element in GT) such that d = GT.exp(a, qe)

*/
static EpidStatus FrobeniusOp(PairingState* ps, FfElement* d_out,
                              FfElement const* a, const int e) {
  EpidStatus result = kEpidErr;
  FfElement* d[6] = {0};
  size_t i = 0;
  Fq12ElemDat a_dat = {0};
  Fq12ElemDat d_dat = {0};
  do {
    IppStatus sts = ippStsNoErr;
    // check parameters
    if (!ps || !d_out || !a) {
      return kEpidBadArgErr;
    }
    if (e < 1 || e > 3 || !d_out->ipp_ff_elem || !a->ipp_ff_elem || !ps->ff ||
        !ps->ff->ipp_ff || !ps->Fq2.ipp_ff) {
      return kEpidBadArgErr;
    }

    for (i = 0; i < sizeof(d) / sizeof(FfElement*); i++) {
      result = NewFfElement(&ps->Fq2, &d[i]);
      BREAK_ON_EPID_ERROR(result);
    }

    // 1.  Let a = ((a[0], a[2], a[4]), (a[1], a[3], a[5])).
    sts = ippsGFpGetElement(a->ipp_ff_elem, (Ipp32u*)&a_dat,
                            sizeof(a_dat) / sizeof(Ipp32u), ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 2.  Let d = ((d[0], d[2], d[4]), (d[1], d[3], d[5])).
    // 3.  For i = 0, ..., 5,
    //   a. set d[i] = a[i].
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[0].x[0],
                            sizeof(a_dat.x[0].x[0]) / sizeof(Ipp32u),
                            d[0]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[0].x[1],
                            sizeof(a_dat.x[0].x[1]) / sizeof(Ipp32u),
                            d[2]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[0].x[2],
                            sizeof(a_dat.x[0].x[2]) / sizeof(Ipp32u),
                            d[4]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[1].x[0],
                            sizeof(a_dat.x[1].x[0]) / sizeof(Ipp32u),
                            d[1]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[1].x[1],
                            sizeof(a_dat.x[1].x[1]) / sizeof(Ipp32u),
                            d[3]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[1].x[2],
                            sizeof(a_dat.x[1].x[2]) / sizeof(Ipp32u),
                            d[5]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);

    // b. If e = 1 or 3, set d[i] = Fq2.conjugate(d[i]).
    if (1 == e || 3 == e) {
      for (i = 0; i < sizeof(d) / sizeof(FfElement*); i++) {
        sts = ippsGFpConj(d[i]->ipp_ff_elem, d[i]->ipp_ff_elem, ps->Fq2.ipp_ff);
        BREAK_ON_IPP_ERROR(sts, result);
      }
    }
    // 4.  For i = 1, ..., 5, compute d[i] = Fq2.mul(d[i], g[e-1][i-1]).
    for (i = 1; i < sizeof(d) / sizeof(FfElement*); i++) {
      sts = ippsGFpMul(d[i]->ipp_ff_elem, ps->g[e - 1][i - 1]->ipp_ff_elem,
                       d[i]->ipp_ff_elem, ps->Fq2.ipp_ff);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 5.  Return d.
    sts = ippsGFpGetElement(d[0]->ipp_ff_elem, (Ipp32u*)&d_dat.x[0].x[0],
                            sizeof(d_dat.x[0].x[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(d[2]->ipp_ff_elem, (Ipp32u*)&d_dat.x[0].x[1],
                            sizeof(d_dat.x[0].x[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(d[4]->ipp_ff_elem, (Ipp32u*)&d_dat.x[0].x[2],
                            sizeof(d_dat.x[0].x[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(d[1]->ipp_ff_elem, (Ipp32u*)&d_dat.x[1].x[0],
                            sizeof(d_dat.x[1].x[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(d[3]->ipp_ff_elem, (Ipp32u*)&d_dat.x[1].x[1],
                            sizeof(d_dat.x[1].x[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(d[5]->ipp_ff_elem, (Ipp32u*)&d_dat.x[1].x[2],
                            sizeof(d_dat.x[1].x[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&d_dat, sizeof(d_dat) / sizeof(Ipp32u),
                            d_out->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    result = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&a_dat, sizeof(a_dat));
  EpidZeroMemory(&d_dat, sizeof(d_dat));
  for (i = 0; i < sizeof(d) / sizeof(FfElement*); i++) {
    DeleteFfElement(&d[i]);
  }

  return result;
}

/*
(f, X', Y', Z', Z2') = line(Px, Py, X, Y, Z, Z2, Qx, Qy)
Input: Px, Py (elements in Fq), X, Y, Z, Z2, Qx, Qy (elements in Fq2)
Output: f (an element in GT), X', Y', Z', Z2' (elements in Fq2)
*/
static EpidStatus Line(FiniteField* gt, FfElement* f, FfElement* x_out,
                       FfElement* y_out, FfElement* z_out, FfElement* z2_out,
                       FfElement const* px, FfElement const* py,
                       FfElement const* x, FfElement const* y,
                       FfElement const* z, FfElement const* z2,
                       FfElement const* qx, FfElement const* qy) {
  EpidStatus result = kEpidNotImpl;
  FfElement* t0 = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;
  FfElement* t3 = NULL;
  FfElement* t4 = NULL;
  FfElement* t5 = NULL;
  FfElement* t6 = NULL;
  FfElement* t7 = NULL;
  FfElement* t8 = NULL;
  FfElement* t9 = NULL;
  FfElement* t10 = NULL;
  FfElement* t = NULL;
  Fq12ElemDat fDat = {0};
  do {
    IppStatus sts = ippStsNoErr;
    IppsGFpState* Fq2 = 0;
    IppsGFpState* Fq6 = 0;
    IppsGFpInfo info = {0};
    FiniteField Ffq2;

    // check parameters
    if (!f || !x_out || !y_out || !z_out || !z2_out || !px || !py || !x || !y ||
        !z || !z2 || !qx || !qy || !gt) {
      result = kEpidBadArgErr;
      break;
    }
    if (!f->ipp_ff_elem || !x_out->ipp_ff_elem || !y_out->ipp_ff_elem ||
        !z_out->ipp_ff_elem || !z2_out->ipp_ff_elem || !px->ipp_ff_elem ||
        !py->ipp_ff_elem || !x->ipp_ff_elem || !y->ipp_ff_elem ||
        !z->ipp_ff_elem || !z2->ipp_ff_elem || !qx->ipp_ff_elem ||
        !qy->ipp_ff_elem || !gt->ipp_ff) {
      result = kEpidBadArgErr;
      break;
    }
    // get Fq6, Fq2
    sts = ippsGFpGetInfo(gt->ipp_ff, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq6 = (IppsGFpState*)info.pGroundGF;
    sts = ippsGFpGetInfo(Fq6, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq2 = (IppsGFpState*)info.pGroundGF;
    result = InitFiniteFieldFromIpp(Fq2, &Ffq2);
    BREAK_ON_EPID_ERROR(result);
    // Let t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10 be temporary
    // elements in Fq2. All the following operations are computed in
    // Fq2 unless explicitly specified.
    result = NewFfElement(&Ffq2, &t0);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t1);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t3);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t4);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t5);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t6);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t7);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t8);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t9);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t10);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(&Ffq2, &t);
    if (kEpidNoErr != result) {
      break;
    }
    // 1. Set t0 = Qx * Z2.
    sts =
        ippsGFpMul(qx->ipp_ff_elem, z2_out->ipp_ff_elem, t0->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 2. Set t1 = (Qy + Z)^2 - Qy * Qy - Z2.
    sts = ippsGFpAdd(qy->ipp_ff_elem, z->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(t1->ipp_ff_elem, t1->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(qy->ipp_ff_elem, qy->ipp_ff_elem, t->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t1->ipp_ff_elem, t->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t1->ipp_ff_elem, z2->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 3. Set t1 = t1 * Z2.
    sts =
        ippsGFpMul(t1->ipp_ff_elem, z2_out->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 4. Set t2 = t0 - X.
    sts = ippsGFpSub(t0->ipp_ff_elem, x->ipp_ff_elem, t2->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    //  5. Set t3 = t2 * t2.
    sts = ippsGFpMul(t2->ipp_ff_elem, t2->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 6. Set t4 = 4 * t3.
    sts = ippsGFpAdd(t3->ipp_ff_elem, t3->ipp_ff_elem, t4->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(t4->ipp_ff_elem, t4->ipp_ff_elem, t4->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 7. Set t5 = t4 * t2.
    sts = ippsGFpMul(t4->ipp_ff_elem, t2->ipp_ff_elem, t5->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 8. Set t6 = t1 - Y - Y.
    sts = ippsGFpSub(t1->ipp_ff_elem, y->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t6->ipp_ff_elem, y->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 9. Set t9 = t6 * Qx.
    sts = ippsGFpMul(t6->ipp_ff_elem, qx->ipp_ff_elem, t9->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 10. Set t7 = X * t4.
    sts = ippsGFpMul(x->ipp_ff_elem, t4->ipp_ff_elem, t7->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 11. X' = t6 * t6 - t5 - t7 - t7.
    sts = ippsGFpMul(t6->ipp_ff_elem, t6->ipp_ff_elem, x_out->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(x_out->ipp_ff_elem, t5->ipp_ff_elem, x_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(x_out->ipp_ff_elem, t7->ipp_ff_elem, x_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(x_out->ipp_ff_elem, t7->ipp_ff_elem, x_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 12. Set Z' = (Z + t2)^2 - Z2 - t3.
    sts = ippsGFpAdd(z->ipp_ff_elem, t2->ipp_ff_elem, z_out->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(z_out->ipp_ff_elem, z_out->ipp_ff_elem, z_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(z_out->ipp_ff_elem, z2->ipp_ff_elem, z_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(z_out->ipp_ff_elem, t3->ipp_ff_elem, z_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 13. Set t10 = Qy + Z'.
    sts =
        ippsGFpAdd(qy->ipp_ff_elem, z_out->ipp_ff_elem, t10->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 14. Set t8 = (t7 - X') * t6.
    sts = ippsGFpSub(t7->ipp_ff_elem, x_out->ipp_ff_elem, t8->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(t8->ipp_ff_elem, t6->ipp_ff_elem, t8->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 15. Set t0 = 2 * Y * t5.
    sts = ippsGFpMul(y->ipp_ff_elem, t5->ipp_ff_elem, t0->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(t0->ipp_ff_elem, t0->ipp_ff_elem, t0->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 16. Set Y' = t8 - t0.
    sts = ippsGFpSub(t8->ipp_ff_elem, t0->ipp_ff_elem, y_out->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 17. Set Z2' = Z' * Z'.
    sts = ippsGFpMul(z_out->ipp_ff_elem, z_out->ipp_ff_elem,
                     z2_out->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 18. Set t10 = t10 * t10 - Qy * Qy - Z2'.
    sts = ippsGFpMul(t10->ipp_ff_elem, t10->ipp_ff_elem, t10->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t10->ipp_ff_elem, t->ipp_ff_elem, t10->ipp_ff_elem,
                     Fq2);  // t still Qy*Qy
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t10->ipp_ff_elem, z2_out->ipp_ff_elem, t10->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 19. Set t9 = t9 + t9 - t10.
    sts = ippsGFpAdd(t9->ipp_ff_elem, t9->ipp_ff_elem, t9->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t9->ipp_ff_elem, t10->ipp_ff_elem, t9->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 20. Set t10 = Fq2.mul(Z', Py).
    sts = ippsGFpMul_GFpE(z_out->ipp_ff_elem, py->ipp_ff_elem, t10->ipp_ff_elem,
                          Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 21. Set t10 = t10 + t10.
    sts = ippsGFpAdd(t10->ipp_ff_elem, t10->ipp_ff_elem, t10->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 22. Set t6 = -t6.
    sts = ippsGFpNeg(t6->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 23. Set t1 = Fq2.mul(t6, Px).
    sts =
        ippsGFpMul_GFpE(t6->ipp_ff_elem, px->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 24. Set t1 = t1 + t1.
    sts = ippsGFpAdd(t1->ipp_ff_elem, t1->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 25. Set f = ((t10, 0, 0), (t1, t9, 0)).
    sts = ippsGFpGetElement(t10->ipp_ff_elem, (Ipp32u*)&fDat.x[0].x[0],
                            sizeof(fDat.x[0].x[0]) / sizeof(Ipp32u), Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(t1->ipp_ff_elem, (Ipp32u*)&fDat.x[1].x[0],
                            sizeof(fDat.x[1].x[0]) / sizeof(Ipp32u), Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(t9->ipp_ff_elem, (Ipp32u*)&fDat.x[1].x[1],
                            sizeof(fDat.x[1].x[1]) / sizeof(Ipp32u), Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&fDat, sizeof(fDat) / sizeof(Ipp32u),
                            f->ipp_ff_elem, gt->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 26. Return (f, X', Y', Z', Z2').
  } while (0);
  EpidZeroMemory(&fDat, sizeof(fDat));
  DeleteFfElement(&t);
  DeleteFfElement(&t10);
  DeleteFfElement(&t9);
  DeleteFfElement(&t8);
  DeleteFfElement(&t7);
  DeleteFfElement(&t6);
  DeleteFfElement(&t5);
  DeleteFfElement(&t4);
  DeleteFfElement(&t3);
  DeleteFfElement(&t2);
  DeleteFfElement(&t1);
  DeleteFfElement(&t0);

  return (result);
}

/*
(f, X', Y', Z', Z2') = tangent(Px, Py, X, Y, Z, Z2)
Input: Px, Py (elements in Fq), X, Y, Z, Z2 (elements in Fq2)
Output: f (an element in GT), X', Y', Z', Z2' (elements in Fq2)
Steps:
*/
static EpidStatus Tangent(FiniteField* gt, FfElement* f, FfElement* x_out,
                          FfElement* y_out, FfElement* z_out, FfElement* z2_out,
                          FfElement const* px, FfElement const* py,
                          FfElement const* x, FfElement const* y,
                          FfElement const* z, FfElement const* z2) {
  EpidStatus result = kEpidErr;
  FfElement* t0 = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;
  FfElement* t3 = NULL;
  FfElement* t4 = NULL;
  FfElement* t5 = NULL;
  FfElement* t6 = NULL;
  Fq12ElemDat fDat = {0};
  do {
    IppStatus sts = ippStsNoErr;
    IppsGFpState* Fq2 = NULL;
    IppsGFpState* Fq6 = NULL;
    FiniteField Ffq2;
    IppsGFpInfo info = {0};
    int i = 0;
    // validate input
    if (!gt || !f || !x_out || !y_out || !z_out || !z2_out || !px || !py ||
        !x || !y || !z || !z2) {
      result = kEpidBadArgErr;
      break;
    }
    if (!gt->ipp_ff || !f->ipp_ff_elem || !x_out->ipp_ff_elem ||
        !y_out->ipp_ff_elem || !z_out->ipp_ff_elem || !z2_out->ipp_ff_elem ||
        !px->ipp_ff_elem || !py->ipp_ff_elem || !x->ipp_ff_elem ||
        !y->ipp_ff_elem || !z->ipp_ff_elem || !z2->ipp_ff_elem) {
      result = kEpidBadArgErr;
      break;
    }
    // get Fq2, Fq6
    sts = ippsGFpGetInfo(gt->ipp_ff, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq6 = (IppsGFpState*)info.pGroundGF;
    sts = ippsGFpGetInfo(Fq6, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq2 = (IppsGFpState*)info.pGroundGF;
    result = InitFiniteFieldFromIpp(Fq2, &Ffq2);
    BREAK_ON_EPID_ERROR(result);
    // Let t0, t1, t2, t3, t4, t5, t6 be elements in Fq2. All the following
    // operations are computed in Fq2 unless explicitly specified.
    // 1. Set t0 = X * X.
    result = NewFfElement(&Ffq2, &t0);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpMul(x->ipp_ff_elem, x->ipp_ff_elem, t0->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 2. Set t1 = Y * Y.
    result = NewFfElement(&Ffq2, &t1);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpMul(y->ipp_ff_elem, y->ipp_ff_elem, t1->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 3. Set t2 = t1 * t1.
    result = NewFfElement(&Ffq2, &t2);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpMul(t1->ipp_ff_elem, t1->ipp_ff_elem, t2->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 4. Set t3 = (t1 + X)^2 - t0 - t2.
    result = NewFfElement(&Ffq2, &t3);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpAdd(t1->ipp_ff_elem, x->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(t3->ipp_ff_elem, t3->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t3->ipp_ff_elem, t0->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t3->ipp_ff_elem, t2->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 5. Set t3 = t3 + t3.
    sts = ippsGFpAdd(t3->ipp_ff_elem, t3->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 6. Set t4 = 3 * t0.
    result = NewFfElement(&Ffq2, &t4);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpAdd(t0->ipp_ff_elem, t0->ipp_ff_elem, t4->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(t4->ipp_ff_elem, t0->ipp_ff_elem, t4->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 7. Set t6 = X + t4.
    result = NewFfElement(&Ffq2, &t6);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpAdd(x->ipp_ff_elem, t4->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 8. Set t5 = t4 * t4.
    result = NewFfElement(&Ffq2, &t5);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpMul(t4->ipp_ff_elem, t4->ipp_ff_elem, t5->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 9. Set X' = t5 - t3 - t3.
    sts = ippsGFpSub(t5->ipp_ff_elem, t3->ipp_ff_elem, x_out->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(x_out->ipp_ff_elem, t3->ipp_ff_elem, x_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 10.Set Z' = (Y + Z)^2 - t1 - Z2.
    sts = ippsGFpAdd(y->ipp_ff_elem, z->ipp_ff_elem, z_out->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(z_out->ipp_ff_elem, z_out->ipp_ff_elem, z_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(z_out->ipp_ff_elem, t1->ipp_ff_elem, z_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(z_out->ipp_ff_elem, z2->ipp_ff_elem, z_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 11.Set Y' = (t3 - X') * t4 - 8 * t2.
    sts = ippsGFpSub(t3->ipp_ff_elem, x_out->ipp_ff_elem, y_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpMul(y_out->ipp_ff_elem, t4->ipp_ff_elem, y_out->ipp_ff_elem,
                     Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    for (i = 0; i < 8; i++) {
      sts = ippsGFpSub(y_out->ipp_ff_elem, t2->ipp_ff_elem, y_out->ipp_ff_elem,
                       Fq2);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 12.Set t3 = -2 * (t4 * Z2).
    sts = ippsGFpMul(t4->ipp_ff_elem, z2->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(t3->ipp_ff_elem, t3->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpNeg(t3->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 13.Set t3 = Fq2.mul(t3, Px).
    sts =
        ippsGFpMul_GFpE(t3->ipp_ff_elem, px->ipp_ff_elem, t3->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 14.Set t6 = t6 * t6 - t0 - t5 - 4 * t1.
    sts = ippsGFpMul(t6->ipp_ff_elem, t6->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t6->ipp_ff_elem, t0->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(t6->ipp_ff_elem, t5->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    for (i = 0; i < 4; i++) {
      sts = ippsGFpSub(t6->ipp_ff_elem, t1->ipp_ff_elem, t6->ipp_ff_elem, Fq2);
      BREAK_ON_IPP_ERROR(sts, result);
    }
    // 15.Set t0 = 2 * (Z' * Z2).
    sts = ippsGFpMul(z_out->ipp_ff_elem, z2->ipp_ff_elem, t0->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(t0->ipp_ff_elem, t0->ipp_ff_elem, t0->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 16.Set t0 = Fq2.mul(t0, Py).
    sts =
        ippsGFpMul_GFpE(t0->ipp_ff_elem, py->ipp_ff_elem, t0->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 17.Set f = ((t0, 0, 0), (t3, t6, 0)).
    sts = ippsGFpGetElement(t0->ipp_ff_elem, (Ipp32u*)&fDat.x[0].x[0],
                            sizeof(fDat.x[0].x[0]) / sizeof(Ipp32u), Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(t3->ipp_ff_elem, (Ipp32u*)&fDat.x[1].x[0],
                            sizeof(fDat.x[1].x[0]) / sizeof(Ipp32u), Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(t6->ipp_ff_elem, (Ipp32u*)&fDat.x[1].x[1],
                            sizeof(fDat.x[1].x[1]) / sizeof(Ipp32u), Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&fDat, sizeof(fDat) / sizeof(Ipp32u),
                            f->ipp_ff_elem, gt->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 18.Set Z2' = Z' * Z'.
    sts = ippsGFpMul(z_out->ipp_ff_elem, z_out->ipp_ff_elem,
                     z2_out->ipp_ff_elem, Fq2);
    BREAK_ON_IPP_ERROR(sts, result);
    // 19.Return (f, X', Y', Z', Z2').
  } while (0);
  EpidZeroMemory(&fDat, sizeof(fDat));
  DeleteFfElement(&t6);
  DeleteFfElement(&t5);
  DeleteFfElement(&t4);
  DeleteFfElement(&t3);
  DeleteFfElement(&t2);
  DeleteFfElement(&t1);
  DeleteFfElement(&t0);
  return result;
}

/*
(sn...s1s0) = ternary(s)
Input: s (big integer)
Output: sn...s1s0 (ternary representation of s)
*/
static EpidStatus Ternary(int* s, int* n, int max_elements, BigNum const* x) {
  /*
  Let xn...x1x0 be binary representation of s.
  Let flag be a Boolean variable.
  1. Set flag = false.
  2. For i = 0, ..., n, do the following:
  a. If xi = 1
  i. If flag = true, set si = 0,
  ii. Else
  1. If xi+1 = 1, set si = -1 and set flag = true,
  2. Else si = 1.
  b. Else
  i. If flag = true, set si = 1 and set flag = false,
  ii. Else set si = 0.
  3. If flag is true
  a. Set n = n+1,
  b. Set sn = 1.
  4. Return sn...s1s0.
  */
  EpidStatus result = kEpidErr;

  do {
    IppStatus sts = ippStsNoErr;
    int flag = 0;
    int i = 0;
    int num_bits = 0;
    Ipp32u* data = 0;

    // check parameters
    if (!s || !n || !x || !x->ipp_bn) {
      result = kEpidBadArgErr;
      break;
    }

    sts = ippsRef_BN(0, &num_bits, &data, x->ipp_bn);
    if (ippStsNoErr != sts) {
      result = kEpidMathErr;
      break;
    }

    if (num_bits + 1 > max_elements) {
      // not enough room for ternary representation
      result = kEpidBadArgErr;
      break;
    }

    // Let xn...x1x0 be binary representation of s. Let flag be a
    // Boolean variable.
    *n = num_bits - 1;
    // 1.  Set flag = false.
    flag = 0;
    // 2.  For i = 0, ..., n, do the following:
    for (i = 0; i < num_bits; i++) {
      if (1 == Bit(data, i)) {
        // a.  If x[i] = 1
        if (flag) {
          // i.  If flag = true, set si = 0,
          s[i] = 0;
        } else {
          // ii. Else
          if ((i < num_bits - 2) && Bit(data, i + 1)) {
            // 1.  If  x[i+1] = 1, set s[i] = -1 and set flag = true,
            s[i] = -1;
            flag = 1;
          } else {
            // 2.  Else s[i] = 1.
            s[i] = 1;
          }
        }
      } else {
        // b.  Else
        if (flag) {
          // i.  If flag = true, set s[i] = 1 and set flag = false,
          s[i] = 1;
          flag = 0;
        } else {
          // ii. Else set s[i] = 0.
          s[i] = 0;
        }
      }
    }
    // 3.  If flag is true
    if (flag) {
      // a.  Set n = n+1,
      *n = *n + 1;
      // b.  Set s[n] = 1.
      s[*n] = 1;
    }
    // 4.  Return sn...s1s0.
    result = kEpidNoErr;
  } while (0);

  return (result);
}

static int Bit(Ipp32u const* num, Ipp32u bit_index) {
  return 0 != (num[bit_index >> 5] & (1 << (bit_index & 0x1F)));
}

/*
e = Fq2.mulXi(a)
Input: a (an element in Fq2)
Output: e (an element in Fq2) where e = a * xi

\note THIS IMPLEMENTATION ASSUMES xi[0] = 2, xi[1] = 1, beta = -1

\note only should work with Fq2

*/
static EpidStatus MulXiFast(FfElement* e, FfElement const* a,
                            PairingState* ps) {
  EpidStatus retvalue = kEpidNotImpl;
  FfElement* a0 = NULL;
  FfElement* a1 = NULL;
  FfElement* e0 = NULL;
  FfElement* e1 = NULL;
  Fq2ElemDat a_dat = {0};
  Fq2ElemDat e_dat = {0};

  do {
    IppStatus sts = ippStsNoErr;
    // check parameters
    if (!e || !a || !ps) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }
    if (!e->ipp_ff_elem || !a->ipp_ff_elem || !ps->Fq.ipp_ff ||
        !ps->Fq2.ipp_ff) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }
    // All the following arithmetic operations are in ps->Fq.
    // 1. Let a = (a[0], a[1]), xi = (xi[0], xi[1]), and e = (e[0], e[1]).
    retvalue = NewFfElement(&(ps->Fq), &a0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq), &a1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq), &e0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq), &e1);
    BREAK_ON_EPID_ERROR(retvalue);

    sts = ippsGFpGetElement(a->ipp_ff_elem, (Ipp32u*)&a_dat,
                            sizeof(a_dat) / sizeof(Ipp32u), ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[0],
                            sizeof(a_dat.x[0]) / sizeof(Ipp32u),
                            a0->ipp_ff_elem, ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[1],
                            sizeof(a_dat.x[1]) / sizeof(Ipp32u),
                            a1->ipp_ff_elem, ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);

    // 4. If xi[0] = 2, xi[1] = 1, beta = -1, then e[0] and e[1] can
    //    be computed as
    //   a. e[0] = a[0] + a[0] - a[1].
    sts = ippsGFpAdd(a0->ipp_ff_elem, a0->ipp_ff_elem, e0->ipp_ff_elem,
                     ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSub(e0->ipp_ff_elem, a1->ipp_ff_elem, e0->ipp_ff_elem,
                     ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    //   b. e[1] = a[0] + a[1] + a[1].
    sts = ippsGFpAdd(a0->ipp_ff_elem, a1->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpAdd(e1->ipp_ff_elem, a1->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 5. Return e = (e[0], e[1]).
    sts = ippsGFpGetElement(e0->ipp_ff_elem, (Ipp32u*)&e_dat.x[0],
                            sizeof(e_dat.x[0]) / sizeof(Ipp32u), ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpGetElement(e1->ipp_ff_elem, (Ipp32u*)&e_dat.x[1],
                            sizeof(e_dat.x[1]) / sizeof(Ipp32u), ps->Fq.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&e_dat, sizeof(e_dat) / sizeof(Ipp32u),
                            e->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    retvalue = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&a_dat, sizeof(a_dat));
  EpidZeroMemory(&e_dat, sizeof(e_dat));
  DeleteFfElement(&a0);
  DeleteFfElement(&a1);
  DeleteFfElement(&e0);
  DeleteFfElement(&e1);

  return (retvalue);
}

/*
e = Fq6.MulV(a)
Input: a (element in Fq6)
Output: e (an element in Fq6) where e = a * V, where V = 0 * v2 + 1 * v + 0

\note only should work with Fq6
*/
static EpidStatus MulV(FfElement* e, FfElement* a, PairingState* ps) {
  EpidStatus retvalue = kEpidNotImpl;
  FfElement* a2 = NULL;
  FfElement* e0 = NULL;
  FfElement* e1 = NULL;
  FfElement* e2 = NULL;
  Fq6ElemDat a_dat = {0};
  Fq6ElemDat e_dat = {0};
  do {
    IppStatus sts = ippStsNoErr;
    // check parameters
    if (!e || !a || !ps) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }
    if (!e->ipp_ff_elem || !a->ipp_ff_elem || !ps->Fq2.ipp_ff ||
        !ps->Fq6.ipp_ff) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }
    // 1. Let a = (a[0], a[1], a[2]) and e = (e[0], e[1], e[2]).
    retvalue = NewFfElement(&(ps->Fq2), &a2);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &e0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &e1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &e2);
    BREAK_ON_EPID_ERROR(retvalue);

    sts = ippsGFpGetElement(a->ipp_ff_elem, (Ipp32u*)&a_dat,
                            sizeof(a_dat) / sizeof(Ipp32u), ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[2],
                            sizeof(a_dat.x[2]) / sizeof(Ipp32u),
                            a2->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 2. e[0] = Fq2.mulXi(a[2]).
    retvalue = MulXiFast(e0, a2, ps);
    BREAK_ON_EPID_ERROR(retvalue);
    // 3. e[1] = a[0].
    e_dat.x[1] = a_dat.x[0];
    // 4. e[2] = a[1].
    e_dat.x[2] = a_dat.x[1];

    sts =
        ippsGFpGetElement(e0->ipp_ff_elem, (Ipp32u*)&e_dat.x[0],
                          sizeof(e_dat.x[0]) / sizeof(Ipp32u), ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&e_dat, sizeof(e_dat) / sizeof(Ipp32u),
                            e->ipp_ff_elem, ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    retvalue = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&a_dat, sizeof(a_dat));
  EpidZeroMemory(&e_dat, sizeof(e_dat));
  DeleteFfElement(&a2);
  DeleteFfElement(&e0);
  DeleteFfElement(&e1);
  DeleteFfElement(&e2);

  return (retvalue);
}

/*
helper for MulSpecial, special args form of Fq6Mul

special args form of Fq6.mul(a,b[0],b[1])
Input: a (elements in Fq6), b[0], b[1] (elements in Fq2)
Output: e (an element in Fq6) where e = a * b, and b = b[1] * v + b[0]

\note assumes a,e are Fq6 elements and b0,b1 are fq2 elements
*/
static EpidStatus Fq6MulGFpE2(FfElement* e, FfElement* a, FfElement* b0,
                              FfElement* b1, PairingState* ps) {
  EpidStatus retvalue = kEpidNotImpl;
  FfElement* t0 = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;
  FfElement* t3 = NULL;
  FfElement* t4 = NULL;
  FfElement* a0 = NULL;
  FfElement* a1 = NULL;
  FfElement* a2 = NULL;
  FfElement* e0 = NULL;
  FfElement* e1 = NULL;
  FfElement* e2 = NULL;
  Fq6ElemDat a_dat = {0};
  Fq6ElemDat e_dat = {0};
  do {
    IppStatus sts = ippStsNoErr;
    // check parameters
    if (!e || !a || !b0 || !b1 || !ps) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }
    if (!e->ipp_ff_elem || !a->ipp_ff_elem || !b0->ipp_ff_elem ||
        !b1->ipp_ff_elem || !ps->Fq2.ipp_ff || !ps->Fq6.ipp_ff) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }

    // Let t0, t1, t3, t4 be temporary variables in Fq2. All the
    // following arithmetic operations are in Fq2.
    retvalue = NewFfElement(&(ps->Fq2), &t0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &t1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &t2);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &t3);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &t4);
    BREAK_ON_EPID_ERROR(retvalue);
    // 1. Let a = (a[0], a[1], a[2]) and e = (e[0], e[1], e[2]).
    retvalue = NewFfElement(&(ps->Fq2), &a0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &a1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &a2);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &e0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &e1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &e2);
    BREAK_ON_EPID_ERROR(retvalue);

    sts = ippsGFpGetElement(a->ipp_ff_elem, (Ipp32u*)&a_dat,
                            sizeof(a_dat) / sizeof(Ipp32u), ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[0],
                            sizeof(a_dat.x[0]) / sizeof(Ipp32u),
                            a0->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[1],
                            sizeof(a_dat.x[1]) / sizeof(Ipp32u),
                            a1->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[2],
                            sizeof(a_dat.x[2]) / sizeof(Ipp32u),
                            a2->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 2. t0 = a[0] * b[0].
    sts = ippsGFpMul(a0->ipp_ff_elem, b0->ipp_ff_elem, t0->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 3. t1 = a[1] * b[1].
    sts = ippsGFpMul(a1->ipp_ff_elem, b1->ipp_ff_elem, t1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 4. t3 = a[1] + a[2].
    sts = ippsGFpAdd(a1->ipp_ff_elem, a2->ipp_ff_elem, t3->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 5. t3 = t3 * b[1].
    sts = ippsGFpMul(t3->ipp_ff_elem, b1->ipp_ff_elem, t3->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 6. t3 = t3 - t1.
    sts = ippsGFpSub(t3->ipp_ff_elem, t1->ipp_ff_elem, t3->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 7. e[0] = Fq2.mulXi(t3) + t0.
    retvalue = MulXiFast(e0, t3, ps);
    BREAK_ON_EPID_ERROR(retvalue);
    sts = ippsGFpAdd(e0->ipp_ff_elem, t0->ipp_ff_elem, e0->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 8. t3 = a[0] + a[1].
    sts = ippsGFpAdd(a0->ipp_ff_elem, a1->ipp_ff_elem, t3->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 9. t4 = b[0] + b[1].
    sts = ippsGFpAdd(b0->ipp_ff_elem, b1->ipp_ff_elem, t4->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 10. t3 = t3 * t4.
    sts = ippsGFpMul(t3->ipp_ff_elem, t4->ipp_ff_elem, t3->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 11. e[1] = t3 - t0 - t1.
    sts = ippsGFpSub(t3->ipp_ff_elem, t0->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSub(e1->ipp_ff_elem, t1->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 12. t3 = a[2] * b[0].
    sts = ippsGFpMul(a2->ipp_ff_elem, b0->ipp_ff_elem, t3->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 13. e[2] = t3 + t1.
    sts = ippsGFpAdd(t3->ipp_ff_elem, t1->ipp_ff_elem, e2->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 14. Return e.
    sts =
        ippsGFpGetElement(e0->ipp_ff_elem, (Ipp32u*)&e_dat.x[0],
                          sizeof(e_dat.x[0]) / sizeof(Ipp32u), ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts =
        ippsGFpGetElement(e1->ipp_ff_elem, (Ipp32u*)&e_dat.x[1],
                          sizeof(e_dat.x[1]) / sizeof(Ipp32u), ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts =
        ippsGFpGetElement(e2->ipp_ff_elem, (Ipp32u*)&e_dat.x[2],
                          sizeof(e_dat.x[2]) / sizeof(Ipp32u), ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&e_dat, sizeof(e_dat) / sizeof(Ipp32u),
                            e->ipp_ff_elem, ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    retvalue = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&a_dat, sizeof(a_dat));
  EpidZeroMemory(&e_dat, sizeof(e_dat));
  DeleteFfElement(&t0);
  DeleteFfElement(&t1);
  DeleteFfElement(&t2);
  DeleteFfElement(&t3);
  DeleteFfElement(&t4);
  DeleteFfElement(&a0);
  DeleteFfElement(&a1);
  DeleteFfElement(&a2);
  DeleteFfElement(&e0);
  DeleteFfElement(&e1);
  DeleteFfElement(&e2);

  return (retvalue);
}

/*
e = Fq12.MulSpecial(a, b)
Input: a, b (elements in Fq12) where b = ((b[0], b[2], b[4]), (b[1], b[3],
b[5])) and b[2] = b[4] = b[5] = 0
Output: e (an element in Fq12) where e = a * b
*/
static EpidStatus MulSpecial(FfElement* e, FfElement const* a,
                             FfElement const* b, PairingState* ps) {
  EpidStatus retvalue = kEpidNotImpl;
  FfElement* t0 = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;
  FfElement* a0 = NULL;
  FfElement* a1 = NULL;
  FfElement* b0 = NULL;
  FfElement* b1 = NULL;
  FfElement* b3 = NULL;
  FfElement* e0 = NULL;
  FfElement* e1 = NULL;
  FfElement* b0plusb1 = NULL;
  Fq12ElemDat a_dat = {0};
  Fq12ElemDat b_dat = {0};
  Fq12ElemDat e_dat = {0};
  do {
    IppStatus sts = ippStsNoErr;

    // check parameters
    if (!e || !a || !b || !ps) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }
    if (!e->ipp_ff_elem || !a->ipp_ff_elem || !b->ipp_ff_elem ||
        !ps->Fq2.ipp_ff || !ps->Fq6.ipp_ff || !ps->ff || !ps->ff->ipp_ff) {
      retvalue = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(retvalue);
    }

    // Let t0, t1, t2 be temporary variables in ps->Fq6.
    retvalue = NewFfElement(&(ps->Fq6), &t0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq6), &t1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq6), &t2);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &b0plusb1);
    BREAK_ON_EPID_ERROR(retvalue);

    // 1.  Let a = (a[0], a[1]) and e = (e[0], e[1]).
    retvalue = NewFfElement(&(ps->Fq6), &a0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq6), &a1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq6), &e0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq6), &e1);
    BREAK_ON_EPID_ERROR(retvalue);

    sts = ippsGFpGetElement(a->ipp_ff_elem, (Ipp32u*)&a_dat,
                            sizeof(a_dat) / sizeof(Ipp32u), ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[0],
                            sizeof(a_dat.x[0]) / sizeof(Ipp32u),
                            a0->ipp_ff_elem, ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&a_dat.x[1],
                            sizeof(a_dat.x[1]) / sizeof(Ipp32u),
                            a1->ipp_ff_elem, ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);

    // 2.  Let b = ((b[0], b[2], b[4]), (b[1], b[3], b[5])) where
    //     b[0], ..., b[5] are elements in ps->Fq2 and b[2] = b[4] = b[5]
    //     = 0.
    retvalue = NewFfElement(&(ps->Fq2), &b0);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &b1);
    BREAK_ON_EPID_ERROR(retvalue);
    retvalue = NewFfElement(&(ps->Fq2), &b3);
    BREAK_ON_EPID_ERROR(retvalue);

    sts = ippsGFpGetElement(b->ipp_ff_elem, (Ipp32u*)&b_dat,
                            sizeof(b_dat) / sizeof(Ipp32u), ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&b_dat.x[0].x[0],
                            sizeof(a_dat.x[0].x[0]) / sizeof(Ipp32u),
                            b0->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&b_dat.x[1].x[0],
                            sizeof(a_dat.x[1].x[0]) / sizeof(Ipp32u),
                            b1->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&b_dat.x[1].x[1],
                            sizeof(a_dat.x[1].x[1]) / sizeof(Ipp32u),
                            b3->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);

    // 3.  t0 = ps->Fq6.mul(a[0], b[0]).
    sts = ippsGFpMul_GFpE(a0->ipp_ff_elem, b0->ipp_ff_elem, t0->ipp_ff_elem,
                          ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 4.  t1 = ps->Fq6.mul(a[1], b[1], b[3]).
    retvalue = Fq6MulGFpE2(t1, a1, b1, b3, ps);
    BREAK_ON_EPID_ERROR(retvalue);
    // 5.  e[0] = ps->Fq6.MulV(t1).
    retvalue = MulV(e0, t1, ps);
    BREAK_ON_EPID_ERROR(retvalue);
    // 6.  e[0] = ps->Fq6.add(t0, e[0]).
    sts = ippsGFpAdd(t0->ipp_ff_elem, e0->ipp_ff_elem, e0->ipp_ff_elem,
                     ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 7.  t2 = ps->Fq6.add(a[0], a[1]).
    sts = ippsGFpAdd(a0->ipp_ff_elem, a1->ipp_ff_elem, t2->ipp_ff_elem,
                     ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 8.  e[1] = ps->Fq6.mul(t2, b[0] + b[1], b[3]).
    sts = ippsGFpAdd(b0->ipp_ff_elem, b1->ipp_ff_elem, b0plusb1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    retvalue = Fq6MulGFpE2(e1, t2, b0plusb1, b3, ps);
    BREAK_ON_EPID_ERROR(retvalue);
    // 9.  e[1] = ps->Fq6.subtract(e[1], t0).
    sts = ippsGFpSub(e1->ipp_ff_elem, t0->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 10. e[1] = ps->Fq6.subtract(e[1], t1).
    sts = ippsGFpSub(e1->ipp_ff_elem, t1->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    // 11. Return e.
    sts =
        ippsGFpGetElement(e0->ipp_ff_elem, (Ipp32u*)&e_dat.x[0],
                          sizeof(e_dat.x[0]) / sizeof(Ipp32u), ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts =
        ippsGFpGetElement(e1->ipp_ff_elem, (Ipp32u*)&e_dat.x[1],
                          sizeof(e_dat.x[1]) / sizeof(Ipp32u), ps->Fq6.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    sts = ippsGFpSetElement((Ipp32u*)&e_dat, sizeof(e_dat) / sizeof(Ipp32u),
                            e->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, retvalue);
    retvalue = kEpidNoErr;
  } while (0);
  EpidZeroMemory(&a_dat, sizeof(a_dat));
  EpidZeroMemory(&b_dat, sizeof(b_dat));
  EpidZeroMemory(&e_dat, sizeof(e_dat));
  DeleteFfElement(&t0);
  DeleteFfElement(&t1);
  DeleteFfElement(&t2);
  DeleteFfElement(&a0);
  DeleteFfElement(&a1);
  DeleteFfElement(&b0);
  DeleteFfElement(&b1);
  DeleteFfElement(&b3);
  DeleteFfElement(&e0);
  DeleteFfElement(&e1);
  DeleteFfElement(&b0plusb1);

  return (retvalue);
}

/*
  (e0, e1) = Fq12.SquareForFq4(a0, a1)
  Input: a0, a1 (elements in Fq2)
  Output: e0, e1 (elements in Fq2) where e = a * a in Fq4
*/
static EpidStatus SquareForFq4(PairingState* ps, FfElement* e0, FfElement* e1,
                               FfElement const* a0, FfElement const* a1) {
  EpidStatus result = kEpidErr;
  FfElement* t0 = NULL;
  FfElement* t1 = NULL;
  FfElement* xi = NULL;
  Fq2ElemStr Fq6IrrPolynomial[3 + 1] = {0};

  // check parameters
  if (!e0 || !e1 || !a0 || !a1 || !ps) return kEpidBadArgErr;

  if (!e0->ipp_ff_elem || !e1->ipp_ff_elem || !a0->ipp_ff_elem ||
      !a1->ipp_ff_elem || !ps->ff || !ps->ff->ipp_ff || !ps->Fq2.ipp_ff ||
      !ps->Fq6.ipp_ff)
    return kEpidBadArgErr;

  do {
    IppStatus sts = ippStsNoErr;

    // extract xi from Fq6 irr poly
    result = NewFfElement(&(ps->Fq2), &xi);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpGetModulus(ps->Fq6.ipp_ff, (Ipp32u*)&Fq6IrrPolynomial[0]);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u const*)&Fq6IrrPolynomial[0],
                            sizeof(Fq6IrrPolynomial[0]) / sizeof(Ipp32u),
                            xi->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // first coefficent is -xi
    sts = ippsGFpNeg(xi->ipp_ff_elem, xi->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);

    // Let t0, t1 be temporary variables in Fq2. All the following
    // operations are computed in Fq2.
    result = NewFfElement(&(ps->Fq2), &t0);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&(ps->Fq2), &t1);
    BREAK_ON_EPID_ERROR(result);

    // 1. Set t0 = a0 * a0.
    sts = ippsGFpMul(a0->ipp_ff_elem, a0->ipp_ff_elem, t0->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 2. Set t1 = a1 * a1.
    sts = ippsGFpMul(a1->ipp_ff_elem, a1->ipp_ff_elem, t1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 3. Set e0 = t1 * xi.
    sts = ippsGFpMul(t1->ipp_ff_elem, xi->ipp_ff_elem, e0->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 4. Set e0 = e0 + t0.
    sts = ippsGFpAdd(e0->ipp_ff_elem, t0->ipp_ff_elem, e0->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 5. Set e1 = a0 + a1.
    sts = ippsGFpAdd(a0->ipp_ff_elem, a1->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 6. Set e1 = e1 * e1 - t0 - t1.
    sts = ippsGFpMul(e1->ipp_ff_elem, e1->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e1->ipp_ff_elem, t0->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e1->ipp_ff_elem, t1->ipp_ff_elem, e1->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 7. Return (e0, e1).
    result = kEpidNoErr;
  } while (0);

  EpidZeroMemory(Fq6IrrPolynomial, sizeof(Fq6IrrPolynomial));
  DeleteFfElement(&t0);
  DeleteFfElement(&t1);
  DeleteFfElement(&xi);

  return (result);
}

/*
  e = Fq12.squareCyclotomic(a)
  Input: a (an element in Fq12)
  Output: e (an element in Fq12) where e = a * a
*/
static EpidStatus SquareCyclotomic(PairingState* ps, FfElement* e_out,
                                   FfElement const* a_in) {
  EpidStatus result = kEpidErr;
  FfElement* t00 = NULL;
  FfElement* t01 = NULL;
  FfElement* t02 = NULL;
  FfElement* t10 = NULL;
  FfElement* t11 = NULL;
  FfElement* t12 = NULL;

  FfElement* a[6] = {0};
  FfElement* e[6] = {0};

  FfElement* xi = NULL;
  int i = 0;
  Fq12ElemStr a_str = {0};
  Fq12ElemStr e_str = {0};
  Fq2ElemStr Fq6IrrPolynomial[3 + 1] = {0};

  // check parameters
  if (!e_out || !a_in || !ps) return kEpidBadArgErr;

  if (!e_out->ipp_ff_elem || !a_in->ipp_ff_elem || !ps->ff || !ps->ff->ipp_ff ||
      !ps->Fq.ipp_ff || !ps->Fq2.ipp_ff || !ps->Fq6.ipp_ff)
    return kEpidBadArgErr;

  do {
    IppStatus sts = ippStsNoErr;

    // extract xi from Fq6 irr poly
    result = NewFfElement(&(ps->Fq2), &xi);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpGetModulus(ps->Fq6.ipp_ff, (Ipp32u*)&Fq6IrrPolynomial);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u const*)&Fq6IrrPolynomial[0],
                            sizeof(Fq6IrrPolynomial[0]) / sizeof(Ipp32u),
                            xi->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // first coefficent is -xi
    sts = ippsGFpNeg(xi->ipp_ff_elem, xi->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);

    // Let t00, t01, t02, t10, t11, t12 be temporary variables in
    // Fq2. All the following operations are computed in Fq2 unless
    // specified otherwise.
    result = NewFfElement(&(ps->Fq2), &t00);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&(ps->Fq2), &t01);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&(ps->Fq2), &t02);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&(ps->Fq2), &t10);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&(ps->Fq2), &t11);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&(ps->Fq2), &t12);
    BREAK_ON_EPID_ERROR(result);
    for (i = 0; i < 6; i++) {
      result = NewFfElement(&(ps->Fq2), &a[i]);
      BREAK_ON_EPID_ERROR(result);
      result = NewFfElement(&(ps->Fq2), &e[i]);
      BREAK_ON_EPID_ERROR(result);
    }
    BREAK_ON_EPID_ERROR(result);
    // 1.  Let a = ((a[0], a[2], a[4]), (a[1], a[3], a[5])).
    sts = ippsGFpGetElement(a_in->ipp_ff_elem, (Ipp32u*)&a_str,
                            sizeof(a_str) / sizeof(Ipp32u), ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_str.a[0].a[0],
                            sizeof(a_str.a[0].a[0]) / sizeof(Ipp32u),
                            a[0]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_str.a[0].a[1],
                            sizeof(a_str.a[0].a[1]) / sizeof(Ipp32u),
                            a[2]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_str.a[0].a[2],
                            sizeof(a_str.a[0].a[2]) / sizeof(Ipp32u),
                            a[4]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_str.a[1].a[0],
                            sizeof(a_str.a[1].a[0]) / sizeof(Ipp32u),
                            a[1]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_str.a[1].a[1],
                            sizeof(a_str.a[1].a[1]) / sizeof(Ipp32u),
                            a[3]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&a_str.a[1].a[2],
                            sizeof(a_str.a[1].a[2]) / sizeof(Ipp32u),
                            a[5]->ipp_ff_elem, ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 2.  Let e = ((e[0], e[2], e[4]), (e[1], e[3], e[5])).

    // 3.  (t00, t11) = Fq12.SquareForFq4(a[0], a[3]).
    result = SquareForFq4(ps, t00, t11, a[0], a[3]);
    BREAK_ON_EPID_ERROR(result);
    // 4.  (t01, t12) = Fq12.SquareForFq4(a[1], a[4]).
    result = SquareForFq4(ps, t01, t12, a[1], a[4]);
    BREAK_ON_EPID_ERROR(result);
    // 5.  (t02, t10) = Fq12.SquareForFq4(a[2], a[5]).
    result = SquareForFq4(ps, t02, t10, a[2], a[5]);
    BREAK_ON_EPID_ERROR(result);
    // 6.  Set t10 = t10 * xi.
    sts = ippsGFpMul(t10->ipp_ff_elem, xi->ipp_ff_elem, t10->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 7.  Set e[0] = 3 * t00 - 2 * a[0].
    sts = ippsGFpAdd(t00->ipp_ff_elem, t00->ipp_ff_elem, e[0]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[0]->ipp_ff_elem, t00->ipp_ff_elem, e[0]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e[0]->ipp_ff_elem, a[0]->ipp_ff_elem, e[0]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e[0]->ipp_ff_elem, a[0]->ipp_ff_elem, e[0]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 8.  Set e[2] = 3 * t01 - 2 * a[2].
    sts = ippsGFpAdd(t01->ipp_ff_elem, t01->ipp_ff_elem, e[2]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[2]->ipp_ff_elem, t01->ipp_ff_elem, e[2]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e[2]->ipp_ff_elem, a[2]->ipp_ff_elem, e[2]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e[2]->ipp_ff_elem, a[2]->ipp_ff_elem, e[2]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 9.  Set e[4] = 3 * t02 - 2 * a[4].
    sts = ippsGFpAdd(t02->ipp_ff_elem, t02->ipp_ff_elem, e[4]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[4]->ipp_ff_elem, t02->ipp_ff_elem, e[4]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e[4]->ipp_ff_elem, a[4]->ipp_ff_elem, e[4]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSub(e[4]->ipp_ff_elem, a[4]->ipp_ff_elem, e[4]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 10. Set e[1] = 3 * t10 + 2 * a[1].
    sts = ippsGFpAdd(t10->ipp_ff_elem, t10->ipp_ff_elem, e[1]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[1]->ipp_ff_elem, t10->ipp_ff_elem, e[1]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[1]->ipp_ff_elem, a[1]->ipp_ff_elem, e[1]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[1]->ipp_ff_elem, a[1]->ipp_ff_elem, e[1]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 11. Set e[3] = 3 * t11 + 2 * a[3].
    sts = ippsGFpAdd(t11->ipp_ff_elem, t11->ipp_ff_elem, e[3]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[3]->ipp_ff_elem, t11->ipp_ff_elem, e[3]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[3]->ipp_ff_elem, a[3]->ipp_ff_elem, e[3]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[3]->ipp_ff_elem, a[3]->ipp_ff_elem, e[3]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 12. Set e[5] = 3 * t12 + 2 * a[5].
    sts = ippsGFpAdd(t12->ipp_ff_elem, t12->ipp_ff_elem, e[5]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[5]->ipp_ff_elem, t12->ipp_ff_elem, e[5]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[5]->ipp_ff_elem, a[5]->ipp_ff_elem, e[5]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpAdd(e[5]->ipp_ff_elem, a[5]->ipp_ff_elem, e[5]->ipp_ff_elem,
                     ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 13. Return e.
    sts = ippsGFpGetElement(e[0]->ipp_ff_elem, (Ipp32u*)&e_str.a[0].a[0],
                            sizeof(e_str.a[0].a[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(e[2]->ipp_ff_elem, (Ipp32u*)&e_str.a[0].a[1],
                            sizeof(e_str.a[0].a[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(e[4]->ipp_ff_elem, (Ipp32u*)&e_str.a[0].a[2],
                            sizeof(e_str.a[0].a[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(e[1]->ipp_ff_elem, (Ipp32u*)&e_str.a[1].a[0],
                            sizeof(e_str.a[0].a[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(e[3]->ipp_ff_elem, (Ipp32u*)&e_str.a[1].a[1],
                            sizeof(e_str.a[0].a[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpGetElement(e[5]->ipp_ff_elem, (Ipp32u*)&e_str.a[1].a[2],
                            sizeof(e_str.a[0].a[0]) / sizeof(Ipp32u),
                            ps->Fq2.ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    sts = ippsGFpSetElement((Ipp32u*)&e_str, sizeof(e_str) / sizeof(Ipp32u),
                            e_out->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    result = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&a_str, sizeof(a_str));
  EpidZeroMemory(&e_str, sizeof(e_str));
  EpidZeroMemory(Fq6IrrPolynomial, sizeof(Fq6IrrPolynomial));
  DeleteFfElement(&t00);
  DeleteFfElement(&t01);
  DeleteFfElement(&t02);
  DeleteFfElement(&t10);
  DeleteFfElement(&t11);
  DeleteFfElement(&t12);

  for (i = 0; i < 6; i++) {
    DeleteFfElement(&a[i]);
    DeleteFfElement(&e[i]);
  }

  DeleteFfElement(&xi);

  return (result);
}

/*
  e = Fq12.expCyclotomic(a, b)
  Input: a (an element in Fq12), b (a non-negative integer)
  Output: e (an element in Fq12) where e = a^b
  Steps:

  2.  Set e = a.
  3.  For i = n-1, ..., 0, do the following:
  e = Fq12.squareCyclotomic(e, e),
  If bi = 1, compute e = Fq12.mul(e, a).
  4.  Return e.
*/
static EpidStatus ExpCyclotomic(PairingState* ps, FfElement* e,
                                FfElement const* a, BigNum const* b) {
  EpidStatus result = kEpidErr;

  // check parameters
  if (!e || !a || !b || !ps) return kEpidBadArgErr;

  if (!e->ipp_ff_elem || !a->ipp_ff_elem || !ps->Fq.ipp_ff || !ps->Fq2.ipp_ff ||
      !b->ipp_bn)
    return kEpidBadArgErr;

  do {
    IppStatus sts = ippStsNoErr;
    int num_bits = 0;
    Ipp32u* b_str = 0;
    int i = 0;

    // 1.  Let bn...b1b0 be the binary representation of b.
    sts = ippsRef_BN(0, &num_bits, &b_str, b->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    // 2.  Set e = a.
    sts = ippsGFpCpyElement(a->ipp_ff_elem, e->ipp_ff_elem, ps->ff->ipp_ff);
    BREAK_ON_IPP_ERROR(sts, result);
    // 3.  For i = n-1, ..., 0, do the following:
    for (i = num_bits - 2; i >= 0; i--) {
      //       e = Fq12.squareCyclotomic(e, e),
      result = SquareCyclotomic(ps, e, e);
      BREAK_ON_EPID_ERROR(result);
      //       If bi = 1, compute e = Fq12.mul(e, a).
      if (1 == Bit(b_str, i)) {
        sts = ippsGFpMul(e->ipp_ff_elem, a->ipp_ff_elem, e->ipp_ff_elem,
                         ps->ff->ipp_ff);
        BREAK_ON_IPP_ERROR(sts, result);
      }
      // 4.  Return e.
    }
    result = kEpidNoErr;
  } while (0);

  return (result);
}
