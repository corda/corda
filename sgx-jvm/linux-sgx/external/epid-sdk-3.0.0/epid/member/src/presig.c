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
 * \brief EpidComputePreSig implementation.
 */

#include "epid/member/src/context.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// Count of elements in array
#define COUNT_OF(A) (sizeof(A) / sizeof((A)[0]))

EpidStatus EpidComputePreSig(MemberCtx const* ctx,
                             PreComputedSignature* precompsig) {
  EpidStatus res = kEpidNotImpl;

  EcPoint* B = NULL;
  EcPoint* K = NULL;
  EcPoint* T = NULL;
  EcPoint* R1 = NULL;

  FfElement* R2 = NULL;

  FfElement* a = NULL;
  FfElement* b = NULL;
  FfElement* rx = NULL;
  FfElement* rf = NULL;
  FfElement* ra = NULL;
  FfElement* rb = NULL;
  FfElement* t1 = NULL;
  FfElement* t2 = NULL;
  FfElement* f = NULL;

  if (!ctx || !precompsig) return kEpidBadArgErr;
  if (!ctx->epid2_params || !ctx->pub_key || !ctx->priv_key)
    return kEpidBadArgErr;

  do {
    // handy shorthands:
    EcGroup* G1 = ctx->epid2_params->G1;
    FiniteField* GT = ctx->epid2_params->GT;
    FiniteField* Fp = ctx->epid2_params->Fp;
    EcPoint* h2 = ctx->pub_key->h2;
    EcPoint* A = ctx->priv_key->A;
    FfElement* x = ctx->priv_key->x;
    BigNumStr f_str = {0};
    BigNumStr a_str = {0};
    BigNumStr t1_str = {0};
    BigNumStr rf_str = {0};
    BigNumStr t2_str = {0};
    BigNumStr ra_str = {0};
    static const BigNumStr one = {
        {{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}}};

    if (!G1 || !GT || !Fp || !h2 || !A || !x || !ctx->priv_key->f ||
        !ctx->e12 || !ctx->e22 || !ctx->e2w || !ctx->ea2) {
      res = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(res);
    }
    f = ctx->priv_key->f;
    // The following variables B, K, T, R1 (elements of G1), R2
    // (elements of GT), a, b, rx, rf, ra, rb, t1, t2 (256-bit
    // integers) are used.
    res = NewEcPoint(G1, &B);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &K);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &T);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &R1);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(GT, &R2);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &a);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &b);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &rx);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &rf);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &ra);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &rb);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &t1);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &t2);
    BREAK_ON_EPID_ERROR(res);
    // 1. The member expects the pre-computation is done (e12, e22, e2w,
    //    ea2). Refer to Section 3.5 for the computation of these
    //    values.

    // 2. The member verifies gid in public key matches gid in private
    //    key.
    // 3. The member computes B = G1.getRandom().
    res = EcGetRandom(G1, ctx->rnd_func, ctx->rnd_param, B);
    BREAK_ON_EPID_ERROR(res);
    // 4. The member computes K = G1.sscmExp(B, f).
    res = WriteFfElement(Fp, f, &f_str, sizeof(f_str));
    BREAK_ON_EPID_ERROR(res);
    res = EcExp(G1, B, &f_str, K);
    BREAK_ON_EPID_ERROR(res);
    // 5. The member chooses randomly an integers a from [1, p-1].
    res = FfGetRandom(Fp, &one, ctx->rnd_func, ctx->rnd_param, a);
    BREAK_ON_EPID_ERROR(res);
    // 6. The member computes T = G1.sscmExp(h2, a).
    res = WriteFfElement(Fp, a, &a_str, sizeof(a_str));
    BREAK_ON_EPID_ERROR(res);
    res = EcExp(G1, h2, &a_str, T);
    BREAK_ON_EPID_ERROR(res);
    // 7. The member computes T = G1.mul(T, A).
    res = EcMul(G1, T, A, T);
    BREAK_ON_EPID_ERROR(res);
    // 8. The member computes b = (a * x) mod p.
    res = FfMul(Fp, a, x, b);
    BREAK_ON_EPID_ERROR(res);
    // 9. The member chooses rx, rf, ra, rb randomly from [1, p-1].
    res = FfGetRandom(Fp, &one, ctx->rnd_func, ctx->rnd_param, rx);
    BREAK_ON_EPID_ERROR(res);
    res = FfGetRandom(Fp, &one, ctx->rnd_func, ctx->rnd_param, rf);
    BREAK_ON_EPID_ERROR(res);
    res = FfGetRandom(Fp, &one, ctx->rnd_func, ctx->rnd_param, ra);
    BREAK_ON_EPID_ERROR(res);
    res = FfGetRandom(Fp, &one, ctx->rnd_func, ctx->rnd_param, rb);
    BREAK_ON_EPID_ERROR(res);
    // 10. The member computes t1 = (- rx) mod p.
    res = FfNeg(Fp, rx, t1);
    BREAK_ON_EPID_ERROR(res);
    // 11. The member computes t2 = (rb - a * rx) mod p.
    res = FfMul(Fp, a, rx, t2);
    BREAK_ON_EPID_ERROR(res);
    res = FfNeg(Fp, t2, t2);
    BREAK_ON_EPID_ERROR(res);
    res = FfAdd(Fp, rb, t2, t2);
    BREAK_ON_EPID_ERROR(res);
    // 12. The member computes R1 = G1.sscmExp(B, rf).
    res = WriteFfElement(Fp, rf, &rf_str, sizeof(rf_str));
    BREAK_ON_EPID_ERROR(res);
    res = EcExp(G1, B, &rf_str, R1);
    BREAK_ON_EPID_ERROR(res);
    // 13. The member computes R2 = GT.sscmMultiExp(ea2, t1, e12, rf,
    //     e22, t2, e2w, ra).
    res = WriteFfElement(Fp, t1, &t1_str, sizeof(t1_str));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, t2, &t2_str, sizeof(t2_str));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, ra, &ra_str, sizeof(ra_str));
    BREAK_ON_EPID_ERROR(res);
    {
      FfElement const* points[4];
      BigNumStr const* exponents[4];
      points[0] = ctx->ea2;
      points[1] = ctx->e12;
      points[2] = ctx->e22;
      points[3] = ctx->e2w;
      exponents[0] = &t1_str;
      exponents[1] = &rf_str;
      exponents[2] = &t2_str;
      exponents[3] = &ra_str;
      res = FfMultiExp(GT, points, exponents, COUNT_OF(points), R2);
      BREAK_ON_EPID_ERROR(res);
    }
    // 14. The member sets and outputs pre-sigma = (B, K, T, a, b, rx,
    //     rf, ra, rb, R1, R2).
    res = WriteEcPoint(G1, B, &precompsig->B, sizeof(precompsig->B));
    BREAK_ON_EPID_ERROR(res);
    res = WriteEcPoint(G1, K, &precompsig->K, sizeof(precompsig->K));
    BREAK_ON_EPID_ERROR(res);
    res = WriteEcPoint(G1, T, &precompsig->T, sizeof(precompsig->T));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, a, &precompsig->a, sizeof(precompsig->a));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, b, &precompsig->b, sizeof(precompsig->b));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, rx, &precompsig->rx, sizeof(precompsig->rx));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, rf, &precompsig->rf, sizeof(precompsig->rf));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, ra, &precompsig->ra, sizeof(precompsig->ra));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, rb, &precompsig->rb, sizeof(precompsig->rb));
    BREAK_ON_EPID_ERROR(res);
    res = WriteEcPoint(G1, R1, &precompsig->R1, sizeof(precompsig->R1));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(GT, R2, &precompsig->R2, sizeof(precompsig->R2));
    BREAK_ON_EPID_ERROR(res);
    // 15. The member stores pre-sigma in the secure storage of the
    //     member.
    res = kEpidNoErr;
  } while (0);

  f = NULL;
  DeleteEcPoint(&B);
  DeleteEcPoint(&K);
  DeleteEcPoint(&T);
  DeleteEcPoint(&R1);
  DeleteFfElement(&R2);
  DeleteFfElement(&a);
  DeleteFfElement(&b);
  DeleteFfElement(&rx);
  DeleteFfElement(&rf);
  DeleteFfElement(&ra);
  DeleteFfElement(&rb);
  DeleteFfElement(&t1);
  DeleteFfElement(&t2);

  return (res);
}
