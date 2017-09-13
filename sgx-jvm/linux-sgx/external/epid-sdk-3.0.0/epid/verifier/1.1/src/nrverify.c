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
* \brief Epid11NrVerify implementation.
*/
#include "ext/ipp/include/ippcp.h"
#include "epid/common/src/memory.h"
#include "epid/verifier/1.1/api.h"
#include "epid/verifier/1.1/src/context.h"
#include "epid/common/src/endian_convert.h"
#include "epid/common/math/hash.h"
/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }
/// Count of elements in array
#define COUNT_OF(A) (sizeof(A) / sizeof((A)[0]))
#pragma pack(1)
/// Storage for values to create commitment in NrVerify algorithm
typedef struct Epid11NrVerifyCommitValues {
  BigNumStr p_tick;        //!< A large prime (256-bit)
  Epid11G3ElemStr g3;      //!< Generator of G3 (512-bit)
  Epid11G3ElemStr B;       //!< (element of G3): part of basic signature Sigma0
  Epid11G3ElemStr K;       //!< (element of G3): part of basic signature Sigma0
  Epid11G3ElemStr B_tick;  //!< (element of G3): one entry in SigRL
  Epid11G3ElemStr K_tick;  //!< (element of G3): one entry in SigRL
  Epid11G3ElemStr T;       //!< element of G3
  Epid11G3ElemStr R1;      //!< element of G3
  Epid11G3ElemStr R2;      //!< element of G3
  uint32_t msg_len;        //!< length of the message
  uint8_t msg[1];          //!< message
} Epid11NrVerifyCommitValues;
#pragma pack()

EpidStatus Epid11NrVerify(Epid11VerifierCtx const* ctx,
                          Epid11BasicSignature const* sig, void const* msg,
                          size_t msg_len, Epid11SigRlEntry const* sigrl_entry,
                          Epid11NrProof const* proof) {
  size_t const cv_header_len =
      sizeof(Epid11NrVerifyCommitValues) - sizeof(uint8_t);
  Epid11NrVerifyCommitValues* commit_values = NULL;
  size_t const commit_len = sizeof(Epid11NrVerifyCommitValues) + msg_len - 1;
  EpidStatus res = kEpidErr;
  // Epid11 G3 elements
  EcPoint* T = NULL;
  EcPoint* R1 = NULL;
  EcPoint* R2 = NULL;

  EcPoint* K = NULL;
  EcPoint* B = NULL;
  EcPoint* K_tick = NULL;
  EcPoint* B_tick = NULL;

  // Big integers
  BigNum* smu = NULL;
  BigNum* snu = NULL;
  BigNum* nc_tick_bn = NULL;
  Sha256Digest commit_hash;

  if (!ctx || !sig || !proof || !sigrl_entry) {
    return kEpidBadArgErr;
  }
  if (!msg && (0 != msg_len)) {
    return kEpidBadArgErr;
  }
  if (msg_len > (UINT_MAX - cv_header_len)) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid11_params) {
    return kEpidBadArgErr;
  }
  do {
    bool cmp_result = false;
    // handy shorthands:
    EcGroup* G3 = ctx->epid11_params->G3;
    BigNum* p_tick_bn = ctx->epid11_params->p_tick;

    if (!G3 || !p_tick_bn) {
      res = kEpidBadArgErr;
      BREAK_ON_EPID_ERROR(res);
    }

    commit_values = SAFE_ALLOC(commit_len);
    if (commit_values == NULL) {
      res = kEpidMemAllocErr;
      break;
    }
    // 1. We use the following variables T, R1, R2 (elements of G3), and c, smu,
    // snu, nc (big integers).
    res = NewEcPoint(G3, &T);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &R1);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &R2);
    BREAK_ON_EPID_ERROR(res);

    res = NewEcPoint(G3, &K);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &B);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &K_tick);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G3, &B_tick);
    BREAK_ON_EPID_ERROR(res);

    res = NewBigNum(sizeof(proof->smu), &smu);
    BREAK_ON_EPID_ERROR(res);
    res = NewBigNum(sizeof(proof->smu), &snu);
    BREAK_ON_EPID_ERROR(res);

    res = NewBigNum(sizeof(FpElemStr), &nc_tick_bn);
    BREAK_ON_EPID_ERROR(res);

    // 2. The verifier verifies that G3.inGroup(T) = true.
    res = ReadEcPoint(G3, &(proof->T), sizeof(proof->T), T);
    if (kEpidNoErr != res) {
      res = kEpidBadArgErr;
      break;
    }

    // 3. The verifier verifies that G3.isIdentity(T) = false.
    res = EcIsIdentity(G3, T, &(cmp_result));
    BREAK_ON_EPID_ERROR(res);
    if (cmp_result) {
      res = kEpidBadArgErr;
      break;
    }

    // 4. The verifier verifies that smu, snu in [0, p'-1].
    res = WriteBigNum(ctx->epid11_params->p_tick, sizeof(commit_values->p_tick),
                      &commit_values->p_tick);
    BREAK_ON_EPID_ERROR(res);
    if (memcmp(&proof->smu, &commit_values->p_tick, sizeof(FpElemStr)) >= 0 ||
        memcmp(&proof->snu, &commit_values->p_tick, sizeof(FpElemStr)) >= 0) {
      res = kEpidBadArgErr;
      break;
    }
    // 5. The verifier computes nc = (- c) mod p'.
    res = ReadBigNum(&(proof->c), sizeof(proof->c), nc_tick_bn);
    BREAK_ON_EPID_ERROR(res);
    res = BigNumMod(nc_tick_bn, p_tick_bn, nc_tick_bn);
    BREAK_ON_EPID_ERROR(res);
    // (-c) mod p'  ==  p' - (c mod p')
    res = BigNumSub(p_tick_bn, nc_tick_bn, nc_tick_bn);
    BREAK_ON_EPID_ERROR(res);

    // 6. The verifier computes R1 = G3.multiExp(K, smu, B, snu).
    res = ReadEcPoint(G3, &(sig->K), sizeof(sig->K), K);
    if (kEpidNoErr != res) {
      res = kEpidBadArgErr;
      break;
    }
    res = ReadEcPoint(G3, &(sig->B), sizeof(sig->B), B);
    if (kEpidNoErr != res) {
      res = kEpidBadArgErr;
      break;
    }
    res = ReadBigNum(&(proof->smu), sizeof(proof->smu), smu);
    BREAK_ON_EPID_ERROR(res);
    res = ReadBigNum(&(proof->snu), sizeof(proof->snu), snu);
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[2];
      BigNum const* exponents[2];
      points[0] = K;
      points[1] = B;
      exponents[0] = smu;
      exponents[1] = snu;
      res = EcMultiExpBn(G3, points, exponents, COUNT_OF(points), R1);
      BREAK_ON_EPID_ERROR(res);
    }
    // 7. The verifier computes R2 = G3.multiExp(K', smu, B', snu, T, nc).
    res = ReadEcPoint(G3, &(sigrl_entry->k), sizeof(sigrl_entry->k), K_tick);
    if (kEpidNoErr != res) {
      res = kEpidBadArgErr;
      break;
    }
    res = ReadEcPoint(G3, &(sigrl_entry->b), sizeof(sigrl_entry->b), B_tick);
    if (kEpidNoErr != res) {
      res = kEpidBadArgErr;
      break;
    }
    {
      EcPoint const* points[3];
      BigNum const* exponents[3];
      points[0] = K_tick;
      points[1] = B_tick;
      points[2] = T;
      exponents[0] = smu;
      exponents[1] = snu;
      exponents[2] = nc_tick_bn;
      res = EcMultiExpBn(G3, points, exponents, COUNT_OF(points), R2);
      BREAK_ON_EPID_ERROR(res);
    }
    // 8. The verifier verifies c = Hash(p' || g3 || B || K || B' || K' || T ||
    // R1 || R2 || mSize || m).
    if (msg) {
      // Memory copy is used to copy a message of variable length
      if (0 != memcpy_S(&commit_values->msg[0], msg_len, msg, msg_len)) {
        res = kEpidBadArgErr;
        break;
      }
    }
    commit_values->g3 = ctx->commit_values.g3;
    commit_values->B = sig->B;
    commit_values->K = sig->K;
    commit_values->B_tick = sigrl_entry->b;
    commit_values->K_tick = sigrl_entry->k;
    commit_values->T = proof->T;
    commit_values->msg_len = ntohl(msg_len);
    res = WriteEcPoint(G3, R1, &commit_values->R1, sizeof(commit_values->R1));
    BREAK_ON_EPID_ERROR(res);
    res = WriteEcPoint(G3, R2, &commit_values->R2, sizeof(commit_values->R2));
    BREAK_ON_EPID_ERROR(res);
    res = Sha256MessageDigest(commit_values, commit_len, &commit_hash);
    if (0 != memcmp(&proof->c, &commit_hash, sizeof(proof->c))) {
      res = kEpidBadArgErr;
      break;
    }
  } while (0);
  SAFE_FREE(commit_values);
  DeleteEcPoint(&T);
  DeleteEcPoint(&R1);
  DeleteEcPoint(&R2);

  DeleteEcPoint(&K);
  DeleteEcPoint(&B);
  DeleteEcPoint(&K_tick);
  DeleteEcPoint(&B_tick);

  DeleteBigNum(&smu);
  DeleteBigNum(&snu);

  DeleteBigNum(&nc_tick_bn);

  return res;
}
