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
 * \brief VerifyBasicSig implementation.
 */

#include "epid/verifier/api.h"
#include "epid/verifier/src/context.h"
#include "epid/common/src/memory.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// Count of elements in array
#define COUNT_OF(A) (sizeof(A) / sizeof((A)[0]))

EpidStatus EpidVerifyBasicSig(VerifierCtx const* ctx, BasicSignature const* sig,
                              void const* msg, size_t msg_len) {
  EpidStatus res = kEpidNotImpl;

  EcPoint* B = NULL;
  EcPoint* K = NULL;
  EcPoint* T = NULL;
  EcPoint* R1 = NULL;
  EcPoint* t4 = NULL;

  EcPoint* t1 = NULL;

  FfElement* R2 = NULL;
  FfElement* t2 = NULL;

  FfElement* c = NULL;
  FfElement* sx = NULL;
  FfElement* sf = NULL;
  FfElement* sa = NULL;
  FfElement* sb = NULL;
  FfElement* nc = NULL;
  FfElement* nsx = NULL;
  FfElement* c_hash = NULL;

  if (!ctx || !sig) return kEpidBadArgErr;
  if (!msg && (0 != msg_len)) {
    // if message is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->pub_key) return kEpidBadArgErr;

  do {
    bool cmp_result = false;
    BigNumStr c_str = {0};
    BigNumStr sf_str = {0};
    BigNumStr nc_str = {0};
    BigNumStr nsx_str = {0};
    BigNumStr sb_str = {0};
    BigNumStr sa_str = {0};
    // handy shorthands:
    EcGroup* G1 = ctx->epid2_params->G1;
    EcGroup* G2 = ctx->epid2_params->G2;
    FiniteField* GT = ctx->epid2_params->GT;
    FiniteField* Fp = ctx->epid2_params->Fp;
    EcPoint* g1 = ctx->epid2_params->g1;
    EcPoint* g2 = ctx->epid2_params->g2;
    EcPoint* w = ctx->pub_key->w;
    CommitValues commit_values = ctx->commit_values;
    EcPoint* basename_hash = ctx->basename_hash;

    if (!G1 || !G2 || !GT || !Fp || !g1 || !g2 || !w) {
      res = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(res);
    }

    // The following variables B, K, T, R1, t4 (elements of G1), t1
    // (element of G2), R2, t2 (elements of GT), c, sx, sf, sa, sb,
    // nc, nsx, t3 (256-bit integers) are used.
    res = NewEcPoint(G1, &B);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &K);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &T);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &R1);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &t4);
    BREAK_ON_EPID_ERROR(res);

    res = NewEcPoint(G2, &t1);
    BREAK_ON_EPID_ERROR(res);

    res = NewFfElement(GT, &R2);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(GT, &t2);
    BREAK_ON_EPID_ERROR(res);

    res = NewFfElement(Fp, &c);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &sx);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &sf);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &sa);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &sb);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &nc);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &nsx);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &c_hash);
    BREAK_ON_EPID_ERROR(res);

    // 1. The verifier expect pre-computation is done (e12, e22, e2w,
    //    eg12). Refer to Section 3.6 for the computation of these
    //    values.

    // 2. The verifier verifies the basic signature sigma0 as follows:
    //   a. The verifier verifies G1.inGroup(B) = true.
    res = ReadEcPoint(G1, &(sig->B), sizeof(sig->B), B);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    //   b. The verifier verifies that G1.isIdentity(B) is false.
    res = EcIsIdentity(G1, B, &cmp_result);
    BREAK_ON_EPID_ERROR(res);
    if (cmp_result != false) {
      res = kEpidSigInvalid;
      break;
    }
    //   c. If bsn is provided, the verifier verifies B =
    //      G1.hash(bsn).
    if (basename_hash) {
      res = EcIsEqual(G1, basename_hash, B, &cmp_result);
      BREAK_ON_EPID_ERROR(res);
      if (cmp_result != true) {
        res = kEpidSigInvalid;
        break;
      }
    }
    //   d. The verifier verifies G1.inGroup(K) = true.
    res = ReadEcPoint(G1, &(sig->K), sizeof(sig->K), K);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    //   e. The verifier verifies G1.inGroup(T) = true.
    res = ReadEcPoint(G1, &(sig->T), sizeof(sig->T), T);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    //   f. The verifier verifies c, sx, sf, sa, sb in [0, p-1].
    res = ReadFfElement(Fp, &(sig->c), sizeof(sig->c), c);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    res = WriteFfElement(Fp, c, &c_str, sizeof(c_str));
    BREAK_ON_EPID_ERROR(res);
    res = ReadFfElement(Fp, &(sig->sx), sizeof(sig->sx), sx);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    res = ReadFfElement(Fp, &(sig->sf), sizeof(sig->sf), sf);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    res = ReadFfElement(Fp, &(sig->sa), sizeof(sig->sa), sa);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    res = ReadFfElement(Fp, &(sig->sb), sizeof(sig->sb), sb);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }
    //   g. The verifier computes nc = (-c) mod p.
    res = FfNeg(Fp, c, nc);
    BREAK_ON_EPID_ERROR(res);
    //   h. The verifier computes nsx = (-sx) mod p.
    res = FfNeg(Fp, sx, nsx);
    BREAK_ON_EPID_ERROR(res);
    //   i. The verifier computes R1 = G1.multiExp(B, sf, K, nc).
    res = WriteFfElement(Fp, sf, &sf_str, sizeof(sf_str));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, nc, &nc_str, sizeof(nc_str));
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[2];
      BigNumStr const* exponents[2];
      points[0] = B;
      points[1] = K;
      exponents[0] = &sf_str;
      exponents[1] = &nc_str;
      res = EcMultiExp(G1, points, exponents, COUNT_OF(points), R1);
      BREAK_ON_EPID_ERROR(res);
    }
    //   j. The verifier computes t1 = G2.multiExp(g2, nsx, w, nc).
    res = WriteFfElement(Fp, nsx, &nsx_str, sizeof(nsx_str));
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[2];
      BigNumStr const* exponents[2];
      points[0] = g2;
      points[1] = w;
      exponents[0] = &nsx_str;
      exponents[1] = &nc_str;
      res = EcMultiExp(G2, points, exponents, COUNT_OF(points), t1);
      BREAK_ON_EPID_ERROR(res);
    }
    //   k. The verifier computes R2 = pairing(T, t1).
    res = Pairing(ctx->epid2_params->pairing_state, R2, T, t1);
    BREAK_ON_EPID_ERROR(res);
    //   l. The verifier compute t2 = GT.multiExp(e12, sf, e22, sb,
    //      e2w, sa, eg12, c).
    res = WriteFfElement(Fp, sb, &sb_str, sizeof(sb_str));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, sa, &sa_str, sizeof(sa_str));
    BREAK_ON_EPID_ERROR(res);
    {
      FfElement const* points[4];
      BigNumStr const* exponents[4];
      points[0] = ctx->e12;
      points[1] = ctx->e22;
      points[2] = ctx->e2w;
      points[3] = ctx->eg12;
      exponents[0] = &sf_str;
      exponents[1] = &sb_str;
      exponents[2] = &sa_str;
      exponents[3] = &c_str;
      res = FfMultiExp(GT, points, exponents, COUNT_OF(points), t2);
      BREAK_ON_EPID_ERROR(res);
    }
    //   m. The verifier compute R2 = GT.mul(R2, t2).
    res = FfMul(GT, R2, t2, R2);
    BREAK_ON_EPID_ERROR(res);
    //   n. The verifier compute t3 = Fp.hash(p || g1 || g2 || h1 ||
    //      h2 || w || B || K || T || R1 || R2).
    //   o. The verifier verifies c = Fp.hash(t3 || m).
    res = SetCalculatedCommitValues(&sig->B, &sig->K, &sig->T, R1, G1, R2, GT,
                                    &commit_values);
    BREAK_ON_EPID_ERROR(res);
    res = CalculateCommitmentHash(&commit_values, Fp, ctx->hash_alg, msg,
                                  msg_len, c_hash);
    BREAK_ON_EPID_ERROR(res);

    res = FfIsEqual(Fp, c, c_hash, &cmp_result);
    BREAK_ON_EPID_ERROR(res);
    if (cmp_result != true) {
      // p. If any of the above verifications fails, the verifier
      //    aborts and outputs 1.
      res = kEpidSigInvalid;
      break;
    }

    res = kEpidNoErr;
  } while (0);

  DeleteEcPoint(&B);
  DeleteEcPoint(&K);
  DeleteEcPoint(&T);
  DeleteEcPoint(&R1);
  DeleteEcPoint(&t4);

  DeleteEcPoint(&t1);

  DeleteFfElement(&R2);
  DeleteFfElement(&t2);

  DeleteFfElement(&c);
  DeleteFfElement(&sx);
  DeleteFfElement(&sf);
  DeleteFfElement(&sa);
  DeleteFfElement(&sb);
  DeleteFfElement(&nc);
  DeleteFfElement(&nsx);
  DeleteFfElement(&c_hash);

  return (res);
}
