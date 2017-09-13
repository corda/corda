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
* \brief EPID 1.1 pairing implementation.
*/

#include "epid/common/math/tatepairing.h"
#include "epid/common/1.1/types.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/math/src/finitefield-internal.h"
#include "epid/common/math/src/ecgroup-internal.h"
#include "epid/common/math/src/tatepairing-internal.h"
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

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// Count of elements in array
#define COUNT_OF(a) (sizeof(a) / sizeof((a)[0]))

#pragma pack(1)
/// Data for element in Fq
typedef struct FqElemDat {
  Ipp32u x[sizeof(FqElemStr) / sizeof(Ipp32u)];  ///< element in Fq
} FqElemDat;
/// Data for element in Fq
typedef struct Fq3ElemDat {
  FqElemDat x[3];  ///< element in Fq3
} Fq3ElemDat;
#pragma pack()

// Forward Declarations
static EpidStatus Fq6FromFq(FiniteField* fq6, FiniteField* fq,
                            FfElement const* a, FfElement* r);

static EpidStatus JoinFq3(Epid11PairingState* ps, FfElement const* a,
                          FfElement const* b, FfElement* r);

static EpidStatus SplitFq6(Epid11PairingState* ps, FfElement const* a,
                           FfElement* a0, FfElement* a1);

static EpidStatus FinalExp(Epid11PairingState* ps, FfElement const* r,
                           FfElement* d);

static EpidStatus Transform(Epid11PairingState* ps, FfElement const* a,
                            FfElement* b);

EpidStatus NewEpid11PairingState(EcGroup const* ga, EcGroup const* gb,
                                 FiniteField const* ff,
                                 Epid11PairingState** ps) {
  EpidStatus result = kEpidErr;
  Epid11PairingState* paring_state_ctx = NULL;
  BigNum* tmp = NULL;
  BigNum* p = NULL;
  BigNum* q = NULL;
  FfElement* qnr = NULL;
  FfElement* inv_qnr = NULL;
  FfElement* neg_qnr = NULL;
  Fq3ElemStr fq3_str = {0};
  FqElemDat q_data = {0};
  int i = 0;

  do {
    IppStatus sts = ippStsNoErr;
    IppsGFpState* Fq3 = NULL;
    IppsGFpState* Fq = NULL;
    IppsGFpInfo info = {0};
    Fq3ElemDat ff_modulus[3] = {0};
    uint8_t one_str[] = {1};
    const Ipp32u* p_data = NULL;
    int p_len = 0;
    uint8_t remainder_str = 0xff;
    Fq3ElemStr trans_100 = {0};
    Fq3ElemStr trans_010 = {0};

    // validate inputs
    if (!ga || !gb || !ff || !ps) {
      result = kEpidBadArgErr;
      break;
    }
    if (!ga->ipp_ec || !gb->ipp_ec || !ff->ipp_ff) {
      result = kEpidBadArgErr;
      break;
    }
    if (1 != ga->info.basicGFdegree || 3 != gb->info.basicGFdegree ||
        6 != ff->info.basicGFdegree ||
        sizeof(Epid11G1ElemStr) != (ga->info.elementLen << 3) ||
        sizeof(Epid11G2ElemStr) != (gb->info.elementLen << 3) ||
        sizeof(Epid11GtElemStr) != (ff->info.elementLen << 2)) {
      result = kEpidBadArgErr;
      break;
    }
    paring_state_ctx =
        (Epid11PairingState*)SAFE_ALLOC(sizeof(Epid11PairingState));
    if (!paring_state_ctx) {
      result = kEpidMemAllocErr;
      break;
    }

    // store EPID fields and groups
    paring_state_ctx->ga = (EcGroup*)ga;
    paring_state_ctx->gb = (EcGroup*)gb;
    paring_state_ctx->ff = (FiniteField*)ff;

    // get Fq3, Fq
    sts = ippsGFpGetInfo(ff->ipp_ff, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq3 = (IppsGFpState*)info.pGroundGF;
    result = InitFiniteFieldFromIpp(Fq3, &(paring_state_ctx->Fq3));
    BREAK_ON_EPID_ERROR(result);

    sts = ippsGFpGetInfo(Fq3, &info);
    BREAK_ON_IPP_ERROR(sts, result);
    Fq = (IppsGFpState*)info.pGroundGF;
    result = InitFiniteFieldFromIpp(Fq, &(paring_state_ctx->Fq));
    BREAK_ON_EPID_ERROR(result);

    // compute fq3_inv_constant = (inverse(qnr), 0, 0)
    result = NewFfElement(&paring_state_ctx->Fq3,
                          &paring_state_ctx->fq3_inv_constant);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&paring_state_ctx->Fq, &neg_qnr);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&paring_state_ctx->Fq, &qnr);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&paring_state_ctx->Fq, &inv_qnr);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpGetModulus(ff->ipp_ff, (Ipp32u*)&ff_modulus);
    BREAK_ON_IPP_ERROR(sts, result);
    sts =
        ippsGFpSetElement(ff_modulus[0].x[0].x, COUNT_OF(ff_modulus[0].x[0].x),
                          neg_qnr->ipp_ff_elem, Fq);
    BREAK_ON_IPP_ERROR(sts, result);
    result = FfNeg(&paring_state_ctx->Fq, neg_qnr, qnr);
    BREAK_ON_EPID_ERROR(result);
    result = FfInv(&paring_state_ctx->Fq, qnr, inv_qnr);
    BREAK_ON_EPID_ERROR(result);
    result = WriteFfElement(&paring_state_ctx->Fq, inv_qnr, &fq3_str.a[0],
                            sizeof(fq3_str.a[0]));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&paring_state_ctx->Fq3, &fq3_str, sizeof(fq3_str),
                           paring_state_ctx->fq3_inv_constant);
    BREAK_ON_EPID_ERROR(result);

    // compute fq3_inv2_constant = (inverse(qnr)^2, 0, 0)
    // inv_qnr = inv_qnr^2
    result = NewFfElement(&paring_state_ctx->Fq3,
                          &paring_state_ctx->fq3_inv2_constant);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(&paring_state_ctx->Fq, inv_qnr, inv_qnr, inv_qnr);
    BREAK_ON_EPID_ERROR(result);
    result = WriteFfElement(&paring_state_ctx->Fq, inv_qnr, &fq3_str.a[0],
                            sizeof(fq3_str.a[0]));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&paring_state_ctx->Fq3, &fq3_str, sizeof(fq3_str),
                           paring_state_ctx->fq3_inv2_constant);
    BREAK_ON_EPID_ERROR(result);

    // save parameter q for future use
    sts = ippsGFpGetModulus(Fq, (Ipp32u*)&q_data);
    BREAK_ON_IPP_ERROR(sts, result);
    result = NewBigNum(sizeof(BigNumStr), &q);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsSet_BN(IppsBigNumPOS, sizeof(q_data) / sizeof(Ipp32u),
                     (Ipp32u*)&q_data, q->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);

    // save parameters a and p for future use
    result = NewFfElement(&paring_state_ctx->Fq, &paring_state_ctx->a);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(BigNumStr), &p);
    BREAK_ON_EPID_ERROR(result);
    sts = ippsGFpECGet(ga->ipp_ec, NULL, paring_state_ctx->a->ipp_ff_elem, NULL,
                       NULL, NULL, &p_data, &p_len, NULL, NULL);
    BREAK_ON_IPP_ERROR(sts, result);
    if (p_len * sizeof(*p_data) > sizeof(BigNumStr)) {
      result = kEpidErr;  // order size is unexpected
      break;
    }
    sts = ippsSet_BN(IppsBigNumPOS, p_len, p_data, p->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    // compute p bit size requred for pairing
    sts = ippsGetOctString_BN((Ipp8u*)&paring_state_ctx->p,
                              sizeof(paring_state_ctx->p), p->ipp_bn);
    BREAK_ON_IPP_ERROR(sts, result);
    paring_state_ctx->p_bitsize = OctStrBitSize(
        paring_state_ctx->p.data.data, sizeof(paring_state_ctx->p.data.data));

    // compute final_exp_constant = (q^2 - q + 1)/p
    result =
        NewBigNum(2 * sizeof(BigNumStr), &paring_state_ctx->final_exp_constant);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(BigNumStr), &tmp);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(one_str, sizeof(one_str), tmp);
    BREAK_ON_EPID_ERROR(result);

    result = BigNumMul(q, q, paring_state_ctx->final_exp_constant);
    BREAK_ON_EPID_ERROR(result);
    result = BigNumSub(paring_state_ctx->final_exp_constant, q,
                       paring_state_ctx->final_exp_constant);
    BREAK_ON_EPID_ERROR(result);
    result = BigNumAdd(paring_state_ctx->final_exp_constant, tmp,
                       paring_state_ctx->final_exp_constant);
    BREAK_ON_EPID_ERROR(result);
    result = BigNumDiv(paring_state_ctx->final_exp_constant, p,
                       paring_state_ctx->final_exp_constant, tmp);
    BREAK_ON_EPID_ERROR(result);
    result = WriteBigNum(tmp, sizeof(remainder_str), &remainder_str);
    if (kEpidNoErr != result || 0 != remainder_str) {
      result = kEpidBadArgErr;  // p does not divide (q^2 - q + 1)
      break;
    }

    for (i = 0; i < 3; i++) {
      result =
          NewFfElement(&paring_state_ctx->Fq3, &(paring_state_ctx->alpha_q[i]));
      BREAK_ON_EPID_ERROR(result);
    }
    BREAK_ON_EPID_ERROR(result);
    /* t^(0*q) */
    trans_100.a[0].data.data[31] = 1;
    result = ReadFfElement(&paring_state_ctx->Fq3, &trans_100,
                           sizeof(trans_100), paring_state_ctx->alpha_q[0]);
    BREAK_ON_EPID_ERROR(result);
    /* t^(1*q) */
    trans_010.a[1].data.data[31] = 1;
    result = ReadFfElement(&paring_state_ctx->Fq3, &trans_010,
                           sizeof(trans_010), paring_state_ctx->alpha_q[1]);
    BREAK_ON_EPID_ERROR(result);
    result = FfExp(&paring_state_ctx->Fq3, paring_state_ctx->alpha_q[1], q,
                   paring_state_ctx->alpha_q[1]);
    BREAK_ON_EPID_ERROR(result);
    /* t^(2*q) */
    result = FfMul(&paring_state_ctx->Fq3, paring_state_ctx->alpha_q[1],
                   paring_state_ctx->alpha_q[1], paring_state_ctx->alpha_q[2]);
    BREAK_ON_EPID_ERROR(result);

    *ps = paring_state_ctx;
    result = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&fq3_str, sizeof(fq3_str));
  EpidZeroMemory(&q_data, sizeof(q_data));
  DeleteBigNum(&p);
  DeleteBigNum(&q);
  DeleteBigNum(&tmp);
  DeleteFfElement(&qnr);
  DeleteFfElement(&inv_qnr);
  DeleteFfElement(&neg_qnr);
  if (kEpidNoErr != result) {
    if (paring_state_ctx) {
      DeleteFfElement(&paring_state_ctx->a);
      DeleteFfElement(&paring_state_ctx->fq3_inv_constant);
      DeleteFfElement(&paring_state_ctx->fq3_inv2_constant);
      DeleteBigNum(&paring_state_ctx->final_exp_constant);
      for (i = 0; i < 3; i++) {
        DeleteFfElement(&(paring_state_ctx->alpha_q[i]));
      }
      SAFE_FREE(paring_state_ctx);
    }
  }
  return result;
}

void DeleteEpid11PairingState(Epid11PairingState** ps) {
  size_t i;
  if (ps && *ps) {
    DeleteFfElement(&(*ps)->a);
    DeleteFfElement(&(*ps)->fq3_inv_constant);
    DeleteFfElement(&(*ps)->fq3_inv2_constant);
    DeleteBigNum(&(*ps)->final_exp_constant);
    for (i = 0; i < 3; i++) {
      DeleteFfElement(&(*ps)->alpha_q[i]);
    }
    SAFE_FREE(*ps);
  }
}

EpidStatus Epid11Pairing(Epid11PairingState* ps, EcPoint const* a,
                         EcPoint const* b, FfElement* d) {
  EpidStatus result = kEpidErr;
  IppStatus sts;
  FfElement* b0 = NULL;
  FfElement* b1 = NULL;
  FfElement* pQx = NULL;
  FfElement* pQy = NULL;
  FfElement* px = NULL;
  FfElement* py = NULL;
  FfElement* X = NULL;
  FfElement* Y = NULL;
  FfElement* Z = NULL;
  FfElement* X2 = NULL;
  FfElement* Y2 = NULL;
  FfElement* Z2 = NULL;
  FfElement* w = NULL;
  FfElement* v = NULL;
  FfElement* ty = NULL;
  FfElement* ry = NULL;
  FfElement* tx = NULL;
  FfElement* rx = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;
  FfElement* t3 = NULL;
  FfElement* tt1 = NULL;
  FfElement* tt2 = NULL;
  FfElement* r = NULL;
  Epid11G1ElemStr a_str = {0};
  Epid11G2ElemStr b_str = {0};
  Epid11GtElemStr bx_str = {0};
  Epid11GtElemStr by_str = {0};
  bool is_identity;
  int i;

  if (!ps || !a || !b || !d) return kEpidBadArgErr;

  do {
    Epid11GtElemStr one_fq6 = {0};
    FqElemStr one_fq = {0};
    one_fq6.a[0].a[0].data.data[31] = 1;
    one_fq.data.data[31] = 1;

    // If P = O, point at infinity, then return r = 1
    result = EcIsIdentity(ps->ga, a, &is_identity);
    BREAK_ON_EPID_ERROR(result);
    if (is_identity) {
      result = ReadFfElement(ps->ff, &one_fq6, sizeof(one_fq6), d);
      BREAK_ON_EPID_ERROR(result);
      result = kEpidNoErr;
      break;
    }

    // Let Q = (Q.x, Q.y), where Q.x and Q.y are elements in Fqd.
    result = NewFfElement(&ps->Fq3, &b0);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq3, &b1);
    BREAK_ON_EPID_ERROR(result);
    result = WriteEcPoint(ps->gb, b, &b_str, sizeof(b_str));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&ps->Fq3, &b_str.x, sizeof(b_str.x), b0);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&ps->Fq3, &b_str.y, sizeof(b_str.y), b1);
    BREAK_ON_EPID_ERROR(result);

    // Now we compute Qx, Qy, two elements in GT, as follows.
    result = NewFfElement(ps->ff, &pQx);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &pQy);
    BREAK_ON_EPID_ERROR(result);

    // Compute Qx = (inv * Q.x, 0).
    result = FfMul(&ps->Fq3, ps->fq3_inv_constant, b0, b0);
    BREAK_ON_EPID_ERROR(result);
    result = WriteFfElement(&ps->Fq3, b0, &bx_str.a[0], sizeof(bx_str.a[0]));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ps->ff, &bx_str, sizeof(bx_str), pQx);
    BREAK_ON_EPID_ERROR(result);

    // Compute Qy = (0, inv^2 * Q.y).
    result = FfMul(&ps->Fq3, ps->fq3_inv2_constant, b1, b1);
    BREAK_ON_EPID_ERROR(result);
    result = WriteFfElement(&ps->Fq3, b1, &by_str.a[1], sizeof(by_str.a[1]));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ps->ff, &by_str, sizeof(by_str), pQy);
    BREAK_ON_EPID_ERROR(result);

    // Let P = (px, py), where px, py are big integers.
    result = NewFfElement(&ps->Fq, &px);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &py);
    BREAK_ON_EPID_ERROR(result);
    result = WriteEcPoint(ps->ga, a, &a_str, sizeof(a_str));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&ps->Fq, &a_str.x, sizeof(a_str.x), px);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&ps->Fq, &a_str.y, sizeof(a_str.y), py);
    BREAK_ON_EPID_ERROR(result);

    // Let X, Y, Z, X', Y', Z', w, v, ty, ry be elements in Fq.
    result = NewFfElement(&ps->Fq, &X);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &Y);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &Z);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &X2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &Y2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &Z2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &w);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &v);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &ty);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &ry);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &t1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &t2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq, &t3);
    BREAK_ON_EPID_ERROR(result);

    // Let tx, rx be elements in GT.
    result = NewFfElement(ps->ff, &tx);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &rx);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &tt1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &tt2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &r);
    BREAK_ON_EPID_ERROR(result);

    // Set X = px,
    result = ReadFfElement(&ps->Fq, &a_str.x, sizeof(a_str.x), X);
    BREAK_ON_EPID_ERROR(result);

    // Y = py,
    result = ReadFfElement(&ps->Fq, &a_str.y, sizeof(a_str.y), Y);
    BREAK_ON_EPID_ERROR(result);

    // Z = 1,
    result = ReadFfElement(&ps->Fq, &one_fq, sizeof(one_fq), Z);
    BREAK_ON_EPID_ERROR(result);

    // ry = 1.
    result = ReadFfElement(&ps->Fq, &one_fq, sizeof(one_fq), ry);
    BREAK_ON_EPID_ERROR(result);

    // Set rx = 1, identity element of GT.
    result = ReadFfElement(ps->ff, &one_fq6, sizeof(one_fq6), rx);
    BREAK_ON_EPID_ERROR(result);

    // Let pn ... p1 p0 be the binary representation of p
    // For i = n-1, ..., 0 do
    for (i = (int)ps->p_bitsize - 2; i >= 0; i--) {
      bool pi = ps->p.data.data[sizeof(ps->p) - 1 - (i >> 3)] & (1 << (i & 7));

      result = FfMul(&ps->Fq, Z, Z, ty);  // ty = Z^2
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, ty, ty, t1);  // t1 = Z^4
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, t1, ps->a, t1);  // t1 = a*Z^4
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, X, X, w);  // w = X^2
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ps->Fq, w, w, t2);  // t2 = 2 * X^2
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ps->Fq, w, t2, w);  // w = 3 * X^2
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ps->Fq, w, t1, w);  // w = 3 * X^2 + a * Z^4
      BREAK_ON_EPID_ERROR(result);

      result = FfMul(&ps->Fq, Y, Y, t1);  // t1 = Y^2
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ps->Fq, t1, t1, t3);  // t3 = 2* Y^2
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, t3, X, v);  // v = 2 * X * Y^2
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ps->Fq, v, v, v);  // v = 4 * X * Y^2
      BREAK_ON_EPID_ERROR(result);

      result = FfMul(&ps->Fq, w, w, X2);  // X2 = w^2
      BREAK_ON_EPID_ERROR(result);
      result = FfSub(&ps->Fq, X2, v, X2);  // X2 = w^2 - v
      BREAK_ON_EPID_ERROR(result);
      result = FfSub(&ps->Fq, X2, v, X2);  // X2 = w^2 - 2 * w
      BREAK_ON_EPID_ERROR(result);

      result = FfMul(&ps->Fq, t3, t3, t3);  // t3 = 4 * Y^4
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ps->Fq, t3, t3, t3);  // t3 = 8 * Y^4
      BREAK_ON_EPID_ERROR(result);
      result = FfSub(&ps->Fq, v, X2, Y2);  // Y2 = v - X2
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, Y2, w, Y2);  // Y2 = w * (v - X2)
      BREAK_ON_EPID_ERROR(result);
      result = FfSub(&ps->Fq, Y2, t3, Y2);  // Y2 = w * (v - X2) - 8 * Y^4
      BREAK_ON_EPID_ERROR(result);

      result = FfMul(&ps->Fq, Y, Z, Z2);  // Z2 = Y * Z
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(&ps->Fq, Z2, Z2, Z2);  // Z2 = 2 * Y * Z
      BREAK_ON_EPID_ERROR(result);

      /* compute line */
      result = FfMul(&ps->Fq, ty, w, t2);  // t2 = w * Z^2
      BREAK_ON_EPID_ERROR(result);
      result = Fq6FromFq(ps->ff, &ps->Fq, t2, tt2);
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(ps->ff, pQx, tt2, tt1);  // tt1 = w * Z^2 * Qx
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, w, X, t2);  // t2 = w * X
      BREAK_ON_EPID_ERROR(result);
      result = FfSub(&ps->Fq, t2, t1, t2);  // t2 = w * X - Y^2
      BREAK_ON_EPID_ERROR(result);
      result = FfSub(&ps->Fq, t2, t1, t2);  // t2 = w * X - 2 * Y^2
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, ty, Z2, ty);  // ty = Z2 * Z^2
      BREAK_ON_EPID_ERROR(result);
      result = Fq6FromFq(ps->ff, &ps->Fq, ty, tt2);
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(ps->ff, pQy, tt2, tx);  // tx = ty * Qy
      BREAK_ON_EPID_ERROR(result);
      result = FfSub(ps->ff, tx, tt1, tx);  // tx = ty * Qy - w * Z^2 * Qx
      BREAK_ON_EPID_ERROR(result);
      result = Fq6FromFq(ps->ff, &ps->Fq, t2, tt2);
      BREAK_ON_EPID_ERROR(result);
      result = FfAdd(ps->ff, tx, tt2,
                     tx);  // tx = ty * Qy - w * Z^2 * Qx + w * X - 2 * Y^2
      BREAK_ON_EPID_ERROR(result);

      sts = ippsGFpCpyElement(X2->ipp_ff_elem, X->ipp_ff_elem,
                              ps->Fq.ipp_ff);  // X = X2
      BREAK_ON_IPP_ERROR(sts, result);
      sts = ippsGFpCpyElement(Y2->ipp_ff_elem, Y->ipp_ff_elem,
                              ps->Fq.ipp_ff);  // Y = Y2
      BREAK_ON_IPP_ERROR(sts, result);
      sts = ippsGFpCpyElement(Z2->ipp_ff_elem, Z->ipp_ff_elem,
                              ps->Fq.ipp_ff);  // Z = Z2
      BREAK_ON_IPP_ERROR(sts, result);

      /* udpate rx, ry */
      result = FfMul(ps->ff, rx, rx, tt1);  // tt1 = rx * rx
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(ps->ff, tx, tt1, rx);  // rx = tx * rx * rx
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, ry, ry, t1);  // t1 = ry * ry
      BREAK_ON_EPID_ERROR(result);
      result = FfMul(&ps->Fq, ty, t1, ry);  // ry = ty * ry * ry
      BREAK_ON_EPID_ERROR(result);

      if (pi && i) {
        result = FfMul(&ps->Fq, Z, Z, t1);  // t1 = Z^2
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, px, t1, w);  // w = px * Z^2
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(&ps->Fq, w, X, w);  // w = px * Z^2 - X
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, t1, Z, t1);  // t1 = Z^3
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, py, t1, v);  // v = py * Z^3
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(&ps->Fq, v, Y, v);  // v = py * Z^3 - Y
        BREAK_ON_EPID_ERROR(result);

        result = FfMul(&ps->Fq, w, w, t1);  // t1 = w^2
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, w, t1, t2);  // t2 = w^3
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, X, t1, t3);  // t3 = X * w^2
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, v, v, X2);  // X2 = v^2
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(&ps->Fq, X2, t2, X2);  // X2 = v^2 - w^3
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(&ps->Fq, X2, t3, X2);  // X2 = v^2 - w^3 - X * w^2
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(&ps->Fq, X2, t3, X2);  // X2 = v^2 - w^3 - 2 * X * w^2
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(&ps->Fq, t3, X2, Y2);  // Y2 = X * w^2 - X2
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, Y2, v, Y2);  // Y2 = v * (X * w^2 - X2)
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, t2, Y, t2);  // t2 = Y * w^3
        BREAK_ON_EPID_ERROR(result);
        result =
            FfSub(&ps->Fq, Y2, t2, Y2);  // Y2 = v * (X * w^2 - X2) - Y * w^3
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, w, Z, Z2);  // Z2 = w * Z
        BREAK_ON_EPID_ERROR(result);

        /* compute tx, ty */
        sts = ippsGFpCpyElement(Z2->ipp_ff_elem, ty->ipp_ff_elem,
                                ps->Fq.ipp_ff);  // ty = Z2
        BREAK_ON_IPP_ERROR(sts, result);
        result = Fq6FromFq(ps->ff, &ps->Fq, py, tt2);
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(ps->ff, pQy, tt2, tx);  // tx = Qy - py
        BREAK_ON_EPID_ERROR(result);
        result = Fq6FromFq(ps->ff, &ps->Fq, Z2, tt2);
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(ps->ff, tx, tt2, tx);  // tx = Z2 * (Qy - py)
        BREAK_ON_EPID_ERROR(result);
        result = Fq6FromFq(ps->ff, &ps->Fq, px, tt2);
        BREAK_ON_EPID_ERROR(result);
        result = FfSub(ps->ff, pQx, tt2, tt1);  // tt1 = Qx - px
        BREAK_ON_EPID_ERROR(result);
        result = Fq6FromFq(ps->ff, &ps->Fq, v, tt2);
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(ps->ff, tt1, tt2, tt1);  // tt1 = v * (Qx - px)
        BREAK_ON_EPID_ERROR(result);
        result =
            FfSub(ps->ff, tx, tt1, tx);  // tx = Z2 * (Qy - py) - v * (Qx - px)
        BREAK_ON_EPID_ERROR(result);

        sts = ippsGFpCpyElement(X2->ipp_ff_elem, X->ipp_ff_elem,
                                ps->Fq.ipp_ff);  // X = X2
        BREAK_ON_IPP_ERROR(sts, result);
        sts = ippsGFpCpyElement(Y2->ipp_ff_elem, Y->ipp_ff_elem,
                                ps->Fq.ipp_ff);  // Y = Y2
        BREAK_ON_IPP_ERROR(sts, result);
        sts = ippsGFpCpyElement(Z2->ipp_ff_elem, Z->ipp_ff_elem,
                                ps->Fq.ipp_ff);  // Z = Z2
        BREAK_ON_IPP_ERROR(sts, result);

        /* udpate rx, ry */
        result = FfMul(ps->ff, rx, tx, rx);  // rx = rx * tx
        BREAK_ON_EPID_ERROR(result);
        result = FfMul(&ps->Fq, ry, ty, ry);  // ry = ry * ty
        BREAK_ON_EPID_ERROR(result);
      }
    }
    BREAK_ON_EPID_ERROR(result);

    result = FfInv(&ps->Fq, ry, ry);  // ry = ry^-1
    BREAK_ON_EPID_ERROR(result);
    result = Fq6FromFq(ps->ff, &ps->Fq, ry, tt2);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(ps->ff, rx, tt2, r);  // r = rx * ry
    BREAK_ON_EPID_ERROR(result);

    result = FinalExp(ps, r, d);
    BREAK_ON_EPID_ERROR(result);

    result = kEpidNoErr;
    BREAK_ON_EPID_ERROR(result);
  } while (0);

  EpidZeroMemory(&a_str, sizeof(a_str));
  EpidZeroMemory(&b_str, sizeof(b_str));
  EpidZeroMemory(&bx_str, sizeof(bx_str));
  EpidZeroMemory(&by_str, sizeof(by_str));
  DeleteFfElement(&b0);
  DeleteFfElement(&b1);
  DeleteFfElement(&pQx);
  DeleteFfElement(&pQy);
  DeleteFfElement(&px);
  DeleteFfElement(&py);
  DeleteFfElement(&X);
  DeleteFfElement(&Y);
  DeleteFfElement(&Z);
  DeleteFfElement(&X2);
  DeleteFfElement(&Y2);
  DeleteFfElement(&Z2);
  DeleteFfElement(&w);
  DeleteFfElement(&v);
  DeleteFfElement(&ty);
  DeleteFfElement(&ry);
  DeleteFfElement(&tx);
  DeleteFfElement(&rx);
  DeleteFfElement(&t1);
  DeleteFfElement(&t2);
  DeleteFfElement(&t3);
  DeleteFfElement(&tt1);
  DeleteFfElement(&tt2);
  DeleteFfElement(&r);
  return result;
}

static EpidStatus Fq6FromFq(FiniteField* fq6, FiniteField* fq,
                            FfElement const* a, FfElement* r) {
  EpidStatus result = kEpidErr;
  // initialize all Fq6 coefficients to 0
  Fq6ElemStr r_str = {0};

  if (!fq6 || !fq || !a || !r) return kEpidBadArgErr;

  do {
    // set Fq6 degree zero coefficient to 'a'
    result = WriteFfElement(fq, a, &r_str.a[0].a[0], sizeof(r_str.a[0].a[0]));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(fq6, &r_str, sizeof(r_str), r);
    BREAK_ON_EPID_ERROR(result);
    result = kEpidNoErr;
  } while (0);
  EpidZeroMemory(&r_str, sizeof(r_str));
  return result;
}

/// Set r from Fq6 to (a, b), where a and b from Fq3
static EpidStatus JoinFq3(Epid11PairingState* ps, FfElement const* a,
                          FfElement const* b, FfElement* r) {
  EpidStatus result = kEpidErr;
  Epid11GtElemStr r_str = {0};

  do {
    // validate inputs
    if (!ps || !a || !b || !r) {
      result = kEpidBadArgErr;
      break;
    }
    result = WriteFfElement(&ps->Fq3, a, &r_str.a[0], sizeof(r_str.a[0]));
    BREAK_ON_EPID_ERROR(result);
    result = WriteFfElement(&ps->Fq3, b, &r_str.a[1], sizeof(r_str.a[1]));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ps->ff, &r_str, sizeof(r_str), r);
    BREAK_ON_EPID_ERROR(result);
    result = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&r_str, sizeof(r_str));
  return result;
}

/// Set a0 and a1 from Fq3 to a0' and a1', where a = (a0', a1') from Fq6
static EpidStatus SplitFq6(Epid11PairingState* ps, FfElement const* a,
                           FfElement* a0, FfElement* a1) {
  EpidStatus result = kEpidErr;
  Epid11GtElemStr a_str = {0};

  do {
    // validate inputs
    if (!ps || !a0 || !a1 || !a) {
      result = kEpidBadArgErr;
      break;
    }
    result = WriteFfElement(ps->ff, a, &a_str, sizeof(a_str));
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&ps->Fq3, &a_str.a[0], sizeof(a_str.a[0]), a0);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(&ps->Fq3, &a_str.a[1], sizeof(a_str.a[1]), a1);
    BREAK_ON_EPID_ERROR(result);
    result = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&a_str, sizeof(a_str));
  return result;
}

static EpidStatus FinalExp(Epid11PairingState* ps, FfElement const* r,
                           FfElement* d) {
  EpidStatus result = kEpidErr;
  FfElement* r0 = NULL;
  FfElement* r1 = NULL;
  FfElement* neg_r1 = NULL;
  FfElement* x = NULL;
  FfElement* y = NULL;
  FfElement* neg_y = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;
  FfElement* t3 = NULL;
  FfElement* t4 = NULL;
  FfElement* d1 = NULL;
  FfElement* d2 = NULL;
  FfElement* inv_d2 = NULL;
  do {
    // validate inputs
    if (!ps || !r || !d) {
      result = kEpidBadArgErr;
      break;
    }

    // a.Let r = (r[0], r[1]), where r[0] and r[1] are elements in Fqd,
    result = NewFfElement(&ps->Fq3, &r0);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq3, &r1);
    BREAK_ON_EPID_ERROR(result);
    result = SplitFq6(ps, r, r0, r1);
    BREAK_ON_EPID_ERROR(result);

    // b.Compute x = transform(r[0]), where x is an element in Fqd,
    result = NewFfElement(&ps->Fq3, &x);
    BREAK_ON_EPID_ERROR(result);
    result = Transform(ps, r0, x);
    BREAK_ON_EPID_ERROR(result);

    // c.Compute y = transform(r[1]), where x is an element in Fqd,
    result = NewFfElement(&ps->Fq3, &y);
    BREAK_ON_EPID_ERROR(result);
    result = Transform(ps, r1, y);
    BREAK_ON_EPID_ERROR(result);

    // d.Let t1, t2, t3, t4 be four variables in GT,
    result = NewFfElement(ps->ff, &t1);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &t2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &t3);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ps->ff, &t4);

    //
    // e.t1 = (x, y), t2 = (r[0], -r[1]), t3 = (x, -y), t4 = (r[0], r[1]),
    //

    // t1 = (x, y)
    result = JoinFq3(ps, x, y, t1);
    BREAK_ON_EPID_ERROR(result);

    // t2 = (r[0], -r[1])
    result = NewFfElement(&ps->Fq3, &neg_r1);
    BREAK_ON_EPID_ERROR(result);
    result = FfNeg(&ps->Fq3, r1, neg_r1);
    BREAK_ON_EPID_ERROR(result);
    result = JoinFq3(ps, r0, neg_r1, t2);
    BREAK_ON_EPID_ERROR(result);

    // t3 = (x, -y)
    result = NewFfElement(&ps->Fq3, &neg_y);
    BREAK_ON_EPID_ERROR(result);
    result = FfNeg(&ps->Fq3, y, neg_y);
    BREAK_ON_EPID_ERROR(result);
    result = JoinFq3(ps, x, neg_y, t3);
    BREAK_ON_EPID_ERROR(result);

    // t4 = (r[0], r[1])
    result = JoinFq3(ps, r0, r1, t4);
    BREAK_ON_EPID_ERROR(result);

    //
    // f. d = (t1 * t2) / (t3 * t4),
    //

    // d1 = t1 * t2
    result = NewFfElement(ps->ff, &d1);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(ps->ff, t1, t2, d1);
    BREAK_ON_EPID_ERROR(result);

    // d2 = t3 * t4
    result = NewFfElement(ps->ff, &d2);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(ps->ff, t3, t4, d2);
    BREAK_ON_EPID_ERROR(result);

    // d = d1 / d2
    result = NewFfElement(ps->ff, &inv_d2);
    BREAK_ON_EPID_ERROR(result);
    result = FfInv(ps->ff, d2, inv_d2);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(ps->ff, d1, inv_d2, d);
    BREAK_ON_EPID_ERROR(result);

    // g.Compute d = GT.exp(d, (q2 - q + 1) / p).
    result = FfExp(ps->ff, d, ps->final_exp_constant, d);
    BREAK_ON_EPID_ERROR(result);

    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&r0);
  DeleteFfElement(&r1);
  DeleteFfElement(&neg_r1);
  DeleteFfElement(&x);
  DeleteFfElement(&y);
  DeleteFfElement(&neg_y);
  DeleteFfElement(&t1);
  DeleteFfElement(&t2);
  DeleteFfElement(&t3);
  DeleteFfElement(&t4);
  DeleteFfElement(&d1);
  DeleteFfElement(&d2);
  DeleteFfElement(&inv_d2);
  return result;
}

static EpidStatus Transform(Epid11PairingState* ps, FfElement const* a,
                            FfElement* b) {
  EpidStatus result = kEpidErr;
  FfElement* tmp = NULL;
  Fq3ElemStr zero = {0};
  Fq3ElemStr a_str = {0};
  Fq3ElemStr tmp_str = {0};
  int i = 0;

  if (!ps || !a || !b) return kEpidBadArgErr;

  do {
    result = WriteFfElement(&ps->Fq3, a, &a_str, sizeof(a_str));
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(&ps->Fq3, &tmp);
    BREAK_ON_EPID_ERROR(result);
    // b = 0
    result = ReadFfElement(&ps->Fq3, &zero, sizeof(zero), b);
    BREAK_ON_EPID_ERROR(result);
    for (i = 0; i < 3; i++) {
      // tmp = (a[0][i], 0, 0)
      tmp_str.a[0] = a_str.a[i];
      result = ReadFfElement(&ps->Fq3, &tmp_str, sizeof(tmp_str), tmp);
      BREAK_ON_EPID_ERROR(result);
      // tmp *= alpha_q[i]
      result = FfMul(&ps->Fq3, ps->alpha_q[i], tmp, tmp);
      BREAK_ON_EPID_ERROR(result);
      // b += tmp
      result = FfAdd(&ps->Fq3, tmp, b, b);
      BREAK_ON_EPID_ERROR(result);
    }
    BREAK_ON_EPID_ERROR(result);
    result = kEpidNoErr;
  } while (0);

  EpidZeroMemory(&a_str, sizeof(a_str));
  EpidZeroMemory(&tmp_str, sizeof(tmp_str));
  DeleteFfElement(&tmp);
  return result;
}
