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
 * \brief Epid11VerifyBasicSig implementation.
 */
#include <stdio.h>
#include <string.h>
#include "epid/verifier/1.1/api.h"
#include "epid/verifier/1.1/src/context.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/src/memory.h"
/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// Count of elements in array
#define COUNT_OF(A) (sizeof(A) / sizeof((A)[0]))

/// Convert bit size into 32-bit words
#ifndef BITS2BYTES
#define BITS2BYTES(n) ((((n) + 7) / 8))
#endif

/// The EPID11 "sf" value must never be larger than 2**593
#define EPID11_SF_MAX_SIZE_BITS (593)

EpidStatus Epid11VerifyBasicSig(Epid11VerifierCtx const* ctx,
                                Epid11BasicSignature const* sig,
                                void const* msg, size_t msg_len) {
  EpidStatus res = kEpidNoErr;

  // Epid11 G1 elements
  EcPoint* T1 = NULL;
  EcPoint* T2 = NULL;
  EcPoint* R1 = NULL;
  EcPoint* R2 = NULL;
  EcPoint* t1 = NULL;
  EcPoint* t2 = NULL;

  // Epid11 GT elements
  FfElement* R4 = NULL;
  FfElement* t3 = NULL;

  // Epid11 G3 elements
  EcPoint* B = NULL;
  EcPoint* K = NULL;
  EcPoint* R3 = NULL;
  EcPoint* t5 = NULL;

  BigNum* c_bn = NULL;
  BigNum* sa_bn = NULL;
  BigNum* sb_bn = NULL;
  BigNum* nc_bn = NULL;
  BigNum* salpha_bn = NULL;
  BigNum* sbeta_bn = NULL;
  BigNum* nsx_bn = NULL;
  BigNum* sf_bn = NULL;
  BigNum* sf_tick_bn = NULL;
  BigNum* nc_tick_bn = NULL;
  BigNum* syalpha_bn = NULL;

  Sha256Digest c_hash = {0};

  if (!ctx || !sig) return kEpidBadArgErr;
  if (!msg && (0 != msg_len)) {
    // if message is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (msg_len > UINT_MAX) return kEpidBadArgErr;
  if (!ctx->epid11_params || !ctx->pub_key) return kEpidBadArgErr;

  do {
    bool cmp_result = false;
    BigNumStr nc_str = {0};
    // handy shorthands:
    EcGroup* G1 = ctx->epid11_params->G1;
    EcGroup* G3 = ctx->epid11_params->G3;
    FiniteField* GT = ctx->epid11_params->GT;
    BigNum* p_bn = ctx->epid11_params->p;
    BigNum* p_tick_bn = ctx->epid11_params->p_tick;
    EcPoint* g1 = ctx->epid11_params->g1;
    EcPoint* g2 = ctx->epid11_params->g2;
    EcPoint* w = ctx->pub_key->w;
    Epid11CommitValues commit_values = ctx->commit_values;
    EcPoint* basename_hash = ctx->basename_hash;

    if (!G1 || !G3 || !GT || !p_bn || !p_tick_bn || !g1 || !g2 || !w) {
      res = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(res);
    }

    // 1. We use the following variables T1, T2, R1, R2, t1,
    //    t2 (elements of G1), R4, t3 (elements of GT), B, K, R3,
    //    t5 (elements of G3), c, sx, sy, sa, sb, salpha, sbeta,
    //    nc, nc_tick, nsx, syalpha, t4 (256-bit big integers),
    //    nd (80-bit big integer), and sf (600-bit big integer).
    res = NewEcPoint(G1, &T1);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &T2);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &R1);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &R2);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &t1);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &t2);
    BREAK_ON_EPID_ERROR(res);

    res = NewFfElement(GT, &R4);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(GT, &t3);
    BREAK_ON_EPID_ERROR(res);

    res = NewEcPoint(G3, &B);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &K);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &R3);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &t5);
    BREAK_ON_EPID_ERROR(res);

    res = NewBigNum(sizeof(FpElemStr), &c_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr), &sa_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr), &sb_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr), &nc_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr), &salpha_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr), &sbeta_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr), &nsx_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(OctStr600), &sf_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(OctStr600), &sf_tick_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr), &nc_tick_bn);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(FpElemStr) * 2, &syalpha_bn);
    BREAK_ON_EPID_ERROR(res);

    // Steps 2-6 done in Epid11Create

    // 8. If bsnSize = 0, the verifier verifies G3.inGroup(B) = true.
    res = ReadEcPoint(G3, &(sig->B), sizeof(sig->B), B);
    if (kEpidNoErr != res) {
      if (ctx->basename_len == 0 && kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }

    // 7. The verifier verifies that G3.isIdentity(B) is false
    res = EcIsIdentity(G3, B, &cmp_result);
    BREAK_ON_EPID_ERROR(res);
    if (cmp_result != false) {
      res = kEpidSigInvalid;
      break;
    }

    // 9. If bsnSize > 0, the verifier verifies B = G3.hash(bsn).
    if (basename_hash) {
      res = EcIsEqual(G3, basename_hash, B, &cmp_result);
      BREAK_ON_EPID_ERROR(res);
      if (cmp_result != true) {
        res = kEpidSigInvalid;
        break;
      }
    }
    // 10. The verifier verifies G3.inGroup(K) = true.
    res = ReadEcPoint(G3, &(sig->K), sizeof(sig->K), K);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }

    // 11. The verifier verifies G1.inGroup(T1) = true.
    res = ReadEcPoint(G1, &(sig->T1), sizeof(sig->T1), T1);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }

    // 12. The verifier verifies G1.inGroup(T2) = true.
    res = ReadEcPoint(G1, &(sig->T2), sizeof(sig->T2), T2);
    if (kEpidNoErr != res) {
      if (kEpidBadArgErr == res) {
        res = kEpidSigInvalid;
      }
      break;
    }

    // 13. The verifier verifies sx, sy, sa, sb, salpha, sbeta in [0, p-1].
    if (memcmp(&sig->sx, &ctx->commit_values.p, sizeof(FpElemStr)) >= 0 ||
        memcmp(&sig->sy, &ctx->commit_values.p, sizeof(FpElemStr)) >= 0 ||
        memcmp(&sig->sa, &ctx->commit_values.p, sizeof(FpElemStr)) >= 0 ||
        memcmp(&sig->sb, &ctx->commit_values.p, sizeof(FpElemStr)) >= 0 ||
        memcmp(&sig->salpha, &ctx->commit_values.p, sizeof(FpElemStr)) >= 0 ||
        memcmp(&sig->sbeta, &ctx->commit_values.p, sizeof(FpElemStr)) >= 0) {
      res = kEpidSigInvalid;
      break;
    }

    // 14. The verifier verifies that sf is an (at-most) 593-bit unsigned
    //     integer, in other words, sf < 2**593.

    if (EPID11_SF_MAX_SIZE_BITS <=
        OctStrBitSize(sig->sf.data, sizeof(sig->sf.data))) {
      res = kEpidSigInvalid;
      break;
    }

    // 15. The verifier computes nc = (-c) mod p.
    res = ReadBigNum(&(sig->c), sizeof(sig->c), c_bn);
    BREAK_ON_EPID_ERROR(res);
    res = BigNumMod(c_bn, p_bn, nc_bn);
    BREAK_ON_EPID_ERROR(res);
    // (-c) mod p  ==  p - (c mod p)
    res = BigNumSub(p_bn, nc_bn, nc_bn);
    BREAK_ON_EPID_ERROR(res);

    // 16. The verifier computes nc_tick = (-c) mod p_tick.
    res = BigNumMod(c_bn, p_tick_bn, nc_tick_bn);
    BREAK_ON_EPID_ERROR(res);
    res = BigNumSub(p_tick_bn, nc_tick_bn, nc_tick_bn);
    BREAK_ON_EPID_ERROR(res);

    // 17. The verifier computes nsx = (-sx) mod p.
    res = ReadBigNum(&(sig->sx), sizeof(sig->sx), nsx_bn);
    BREAK_ON_EPID_ERROR(res);
    res = BigNumSub(p_bn, nsx_bn, nsx_bn);
    BREAK_ON_EPID_ERROR(res);

    // 18. The verifier computes syalpha = (sy + salpha) mod p.
    res = ReadBigNum(&(sig->salpha), sizeof(sig->salpha), salpha_bn);
    BREAK_ON_EPID_ERROR(res);
    res = ReadBigNum(&(sig->sy), sizeof(sig->sy), syalpha_bn);
    BREAK_ON_EPID_ERROR(res);
    res = BigNumAdd(salpha_bn, syalpha_bn, syalpha_bn);
    BREAK_ON_EPID_ERROR(res);
    res = BigNumMod(syalpha_bn, p_bn, syalpha_bn);
    BREAK_ON_EPID_ERROR(res);

    // 19. The verifier computes R1 = G1.multiexp(h1, sa, h2, sb, T2, nc).
    res = ReadBigNum(&sig->sa, sizeof(sig->sa), sa_bn);
    BREAK_ON_EPID_ERROR(res);
    res = ReadBigNum(&sig->sb, sizeof(sig->sb), sb_bn);
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[3];
      BigNum const* exponents[3];
      points[0] = ctx->pub_key->h1;
      points[1] = ctx->pub_key->h2;
      points[2] = T2;
      exponents[0] = sa_bn;
      exponents[1] = sb_bn;
      exponents[2] = nc_bn;
      res = EcMultiExpBn(G1, points, exponents, COUNT_OF(points), R1);
      BREAK_ON_EPID_ERROR(res);
    }
    // 20. The verifier computes
    //     R2 = G1.multiexp(h1, salpha, h2, sbeta, T2, nsx).
    res = ReadBigNum(&sig->sbeta, sizeof(sig->sbeta), sbeta_bn);
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[3];
      BigNum const* exponents[3];
      points[0] = ctx->pub_key->h1;
      points[1] = ctx->pub_key->h2;
      points[2] = T2;
      exponents[0] = salpha_bn;
      exponents[1] = sbeta_bn;
      exponents[2] = nsx_bn;
      res = EcMultiExpBn(G1, points, exponents, COUNT_OF(points), R2);
      BREAK_ON_EPID_ERROR(res);
    }
    // 21. The verifier computes R3 = G3.multiexp(B, sf, K, nc_tick).
    res = ReadBigNum(&sig->sf, sizeof(sig->sf), sf_tick_bn);
    BREAK_ON_EPID_ERROR(res);
    // G3.exp(B, sf) = G3(B, sf mod G3.order)
    res = BigNumMod(sf_tick_bn, p_tick_bn, sf_tick_bn);
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[2];
      BigNum const* exponents[2];
      points[0] = B;
      points[1] = K;
      exponents[0] = sf_tick_bn;
      exponents[1] = nc_tick_bn;
      res = EcMultiExpBn(G3, points, exponents, COUNT_OF(points), R3);
      BREAK_ON_EPID_ERROR(res);
    }

    // 22. The verifier computes t1 = G1.multiexp(T1, nsx, g1, c).
    res = BigNumMod(c_bn, p_bn, c_bn);
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[2];
      BigNum const* exponents[2];
      points[0] = T1;
      points[1] = g1;
      exponents[0] = nsx_bn;
      exponents[1] = c_bn;
      res = EcMultiExpBn(G1, points, exponents, COUNT_OF(points), t1);
      BREAK_ON_EPID_ERROR(res);
    }
    // 23. The verifier computes t2 = G1.exp(T1, nc).
    res = WriteBigNum(nc_bn, sizeof(nc_str), &nc_str);
    BREAK_ON_EPID_ERROR(res);
    res = EcExp(G1, T1, &nc_str, t2);
    BREAK_ON_EPID_ERROR(res);
    // 24. The verifier computes R4 = pairing(t1, g2).
    res = Epid11Pairing(ctx->epid11_params->pairing_state, t1, g2, R4);
    BREAK_ON_EPID_ERROR(res);
    // 25. The verifier computes t3 = pairing(t2, w).
    res = Epid11Pairing(ctx->epid11_params->pairing_state, t2, w, t3);
    BREAK_ON_EPID_ERROR(res);
    // 26. The verifier computes R4 = GT.mul(R4, t3).
    res = FfMul(GT, R4, t3, R4);
    BREAK_ON_EPID_ERROR(res);
    // 27. The verifier compute
    //     t3 = GT.multiexp(e12, sf, e22, syalpha, e2w, sa).
    res = ReadBigNum(&sig->sf, sizeof(sig->sf), sf_bn);
    BREAK_ON_EPID_ERROR(res);
    {
      FfElement const* points[3];
      BigNum const* exponents[3];
      points[0] = ctx->e12;
      points[1] = ctx->e22;
      points[2] = ctx->e2w;
      exponents[0] = sf_bn;
      exponents[1] = syalpha_bn;
      exponents[2] = sa_bn;
      res = FfMultiExpBn(GT, points, exponents, COUNT_OF(points), t3);
      BREAK_ON_EPID_ERROR(res);
    }
    // 28. The verifier compute R4 = GT.mul(R4, t3).
    res = FfMul(GT, R4, t3, R4);
    BREAK_ON_EPID_ERROR(res);
    // 29. The verifier compute t4 = Hash(p || g1 || g2 || g3 || h1 || h2 || w
    //     || B || K || T1 || T2 || R1 || R2 || R3 || R4).
    // 30. The verifier verifies c = H(t4 || nd || mSize || m).
    res = SetCalculatedEpid11CommitValues(&sig->B, &sig->K, &sig->T1, &sig->T2,
                                          R1, R2, R3, R4, G1, G3, GT,
                                          &commit_values);
    BREAK_ON_EPID_ERROR(res);
    res = CalculateEpid11CommitmentHash(&commit_values, msg, (uint32_t)msg_len,
                                        &sig->nd, &c_hash);
    BREAK_ON_EPID_ERROR(res);
    if (0 != memcmp(&sig->c, &c_hash, sizeof(sig->c))) {
      res = kEpidSigInvalid;
      break;
    }
    res = kEpidNoErr;
  } while (0);
  EpidZeroMemory(&c_hash, sizeof(c_hash));

  DeleteEcPoint(&T1);
  DeleteEcPoint(&T2);
  DeleteEcPoint(&R1);
  DeleteEcPoint(&R2);
  DeleteEcPoint(&t1);
  DeleteEcPoint(&t2);

  DeleteFfElement(&R4);
  DeleteFfElement(&t3);

  DeleteEcPoint(&B);
  DeleteEcPoint(&K);
  DeleteEcPoint(&R3);
  DeleteEcPoint(&t5);

  DeleteBigNum(&c_bn);
  DeleteBigNum(&sa_bn);
  DeleteBigNum(&sb_bn);
  DeleteBigNum(&nc_bn);
  DeleteBigNum(&salpha_bn);
  DeleteBigNum(&sbeta_bn);
  DeleteBigNum(&nsx_bn);
  DeleteBigNum(&sf_bn);
  DeleteBigNum(&sf_tick_bn);
  DeleteBigNum(&nc_tick_bn);
  DeleteBigNum(&syalpha_bn);

  return (res);
}
