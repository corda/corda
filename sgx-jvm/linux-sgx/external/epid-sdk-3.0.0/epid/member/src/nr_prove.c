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
 * \brief EpidNrProve implementation.
 */
#include "epid/common/src/memory.h"
#include "epid/member/api.h"
#include "epid/member/src/context.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// Count of elements in array
#define COUNT_OF(A) (sizeof(A) / sizeof((A)[0]))

#pragma pack(1)
/// Storage for values to create commitment in NrProve algorithm
typedef struct NrVerifyCommitValues {
  BigNumStr p;     //!< A large prime (256-bit)
  G1ElemStr g1;    //!< Generator of G1 (512-bit)
  G1ElemStr b;     //!< (element of G1): part of basic signature Sigma0
  G1ElemStr k;     //!< (element of G1): part of basic signature Sigma0
  G1ElemStr bp;    //!< (element of G1): one entry in SigRL
  G1ElemStr kp;    //!< (element of G1): one entry in SigRL
  G1ElemStr t;     //!< element of G1
  G1ElemStr r1;    //!< element of G1
  G1ElemStr r2;    //!< element of G1
  uint8_t msg[1];  //!< message
} NrVerifyCommitValues;
#pragma pack()

EpidStatus EpidNrProve(MemberCtx const* ctx, void const* msg, size_t msg_len,
                       BasicSignature const* sig, SigRlEntry const* sigrl_entry,
                       NrProof* proof) {
  EpidStatus res = kEpidErr;
  NrVerifyCommitValues* commit_values = NULL;
  size_t const commit_len = sizeof(*commit_values) - 1 + msg_len;
  EcPoint* T = NULL;
  EcPoint* R1 = NULL;
  EcPoint* R2 = NULL;
  FfElement* mu = NULL;
  FfElement* nu = NULL;
  FfElement* rmu = NULL;
  FfElement* rnu = NULL;
  FfElement* c = NULL;
  FfElement* smu = NULL;
  FfElement* snu = NULL;
  EcPoint* B = NULL;
  EcPoint* K = NULL;
  EcPoint* rlB = NULL;
  EcPoint* rlK = NULL;
  FfElement const* f = NULL;
  if (!ctx || (0 != msg_len && !msg) || !sig || !sigrl_entry || !proof)
    return kEpidBadArgErr;
  if (msg_len > ((SIZE_MAX - sizeof(*commit_values)) + 1))
    return kEpidBadArgErr;
  if (!ctx->epid2_params || !ctx->priv_key) return kEpidBadArgErr;

  do {
    bool is_identity = false;
    BigNumStr mu_str = {0};
    BigNumStr nu_str = {0};
    BigNumStr rmu_str = {0};
    BigNumStr rnu_str = {0};
    BitSupplier rnd_func = ctx->rnd_func;
    void* rnd_param = ctx->rnd_param;
    FiniteField* Fp = ctx->epid2_params->Fp;
    EcGroup* G1 = ctx->epid2_params->G1;
    static const BigNumStr one = {
        {{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}}};

    // Check required parameters
    if (!ctx->priv_key->f || !rnd_func || !Fp || !G1) return kEpidBadArgErr;

    f = ctx->priv_key->f;

    commit_values = SAFE_ALLOC(commit_len);
    if (!commit_values) {
      res = kEpidMemAllocErr;
      break;
    }

    // The following variables T, R1, R2 (elements of G1), and mu, nu,
    // rmu, rnu, c, smu, snu (256-bit integers) are used.
    res = NewEcPoint(G1, &T);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &R1);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &R2);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &mu);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &nu);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &rmu);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &rnu);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &c);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &smu);
    BREAK_ON_EPID_ERROR(res);
    res = NewFfElement(Fp, &snu);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &B);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &K);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &rlB);
    BREAK_ON_EPID_ERROR(res);
    res = NewEcPoint(G1, &rlK);
    BREAK_ON_EPID_ERROR(res);

    res = ReadEcPoint(G1, (const uint8_t*)&(sig->B), sizeof(sig->B), B);
    BREAK_ON_EPID_ERROR(res);
    res = ReadEcPoint(G1, (const uint8_t*)&(sig->K), sizeof(sig->K), K);
    BREAK_ON_EPID_ERROR(res);
    res = ReadEcPoint(G1, (const uint8_t*)&(sigrl_entry->b),
                      sizeof(sigrl_entry->b), rlB);
    BREAK_ON_EPID_ERROR(res);
    res = ReadEcPoint(G1, (const uint8_t*)&(sigrl_entry->k),
                      sizeof(sigrl_entry->k), rlK);
    BREAK_ON_EPID_ERROR(res);

    // 1.  The member chooses random mu from [1, p-1].
    res = FfGetRandom(Fp, &one, rnd_func, rnd_param, mu);
    BREAK_ON_EPID_ERROR(res);
    // 2.  The member computes nu = (- f * mu) mod p.
    res = FfMul(Fp, mu, f, nu);
    BREAK_ON_EPID_ERROR(res);
    res = FfNeg(Fp, nu, nu);
    BREAK_ON_EPID_ERROR(res);
    // 3.  The member computes T = G1.sscmMultiExp(K', mu, B', nu).
    res = WriteFfElement(Fp, mu, (uint8_t*)&mu_str, sizeof(mu_str));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, nu, (uint8_t*)&nu_str, sizeof(nu_str));
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[2];
      BigNumStr const* exponents[2];
      points[0] = rlK;
      points[1] = rlB;
      exponents[0] = &mu_str;
      exponents[1] = &nu_str;
      res = EcSscmMultiExp(G1, points, exponents, COUNT_OF(points), T);
      BREAK_ON_EPID_ERROR(res);
    }
    // 4.  The member chooses rmu, rnu randomly from [1, p-1].
    res = FfGetRandom(Fp, &one, rnd_func, rnd_param, rmu);
    BREAK_ON_EPID_ERROR(res);
    res = FfGetRandom(Fp, &one, rnd_func, rnd_param, rnu);
    BREAK_ON_EPID_ERROR(res);
    // 5.  The member computes R1 = G1.sscmMultiExp(K, rmu, B, rnu).
    res = WriteFfElement(Fp, rmu, (uint8_t*)&rmu_str, sizeof(rmu_str));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, rnu, (uint8_t*)&rnu_str, sizeof(rnu_str));
    BREAK_ON_EPID_ERROR(res);
    {
      EcPoint const* points[2];
      BigNumStr const* exponents[2];
      points[0] = K;
      points[1] = B;
      exponents[0] = &rmu_str;
      exponents[1] = &rnu_str;
      res = EcSscmMultiExp(G1, points, exponents, COUNT_OF(points), R1);
      BREAK_ON_EPID_ERROR(res);
    }
    // 6.  The member computes R2 = G1.sscmMultiExp(K', rmu, B', rnu).
    {
      EcPoint const* points[2];
      BigNumStr const* exponents[2];
      points[0] = rlK;
      points[1] = rlB;
      exponents[0] = &rmu_str;
      exponents[1] = &rnu_str;
      res = EcSscmMultiExp(G1, points, exponents, COUNT_OF(points), R2);
      BREAK_ON_EPID_ERROR(res);
    }
    // 7.  The member computes c = Fp.hash(p || g1 || B || K || B' ||
    //     K' || T || R1 || R2 || m). Refer to Section 7.1 for hash
    //     operation over a prime field.

    // commit_values is allocated such that there are msg_len bytes available
    // starting at commit_values->msg
    if (msg) {
      // Memory copy is used to copy a message of variable length
      if (0 != memcpy_S(&commit_values->msg[0], msg_len, msg, msg_len)) {
        res = kEpidBadArgErr;
        break;
      }
    }
    commit_values->p = ctx->commit_values.p;
    commit_values->g1 = ctx->commit_values.g1;
    commit_values->b = sig->B;
    commit_values->k = sig->K;
    commit_values->bp = sigrl_entry->b;
    commit_values->kp = sigrl_entry->k;
    res = WriteEcPoint(G1, T, (uint8_t*)&commit_values->t,
                       sizeof(commit_values->t));
    BREAK_ON_EPID_ERROR(res);
    res = WriteEcPoint(G1, R1, (uint8_t*)&commit_values->r1,
                       sizeof(commit_values->r1));
    BREAK_ON_EPID_ERROR(res);
    res = WriteEcPoint(G1, R2, (uint8_t*)&commit_values->r2,
                       sizeof(commit_values->r2));
    BREAK_ON_EPID_ERROR(res);
    res = FfHash(Fp, (uint8_t*)commit_values, commit_len, ctx->hash_alg, c);
    BREAK_ON_EPID_ERROR(res);

    // 8.  The member computes smu = (rmu + c * mu) mod p.
    res = FfMul(Fp, c, mu, smu);
    BREAK_ON_EPID_ERROR(res);
    res = FfAdd(Fp, rmu, smu, smu);
    BREAK_ON_EPID_ERROR(res);
    // 9.  The member computes snu = (rnu + c * nu) mod p.
    res = FfMul(Fp, c, nu, snu);
    BREAK_ON_EPID_ERROR(res);
    res = FfAdd(Fp, rnu, snu, snu);
    BREAK_ON_EPID_ERROR(res);
    // 10. The member outputs sigma = (T, c, smu, snu), a non-revoked
    //     proof. If G1.is_identity(T) = true, the member also outputs
    //     "failed".

    proof->T = commit_values->t;
    res = WriteFfElement(Fp, c, (uint8_t*)&proof->c, sizeof(proof->c));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, smu, (uint8_t*)&proof->smu, sizeof(proof->smu));
    BREAK_ON_EPID_ERROR(res);
    res = WriteFfElement(Fp, snu, (uint8_t*)&proof->snu, sizeof(proof->snu));
    BREAK_ON_EPID_ERROR(res);

    res = EcIsIdentity(G1, T, &is_identity);
    BREAK_ON_EPID_ERROR(res);
    if (is_identity) {
      res = kEpidSigRevokedInSigRl;
      BREAK_ON_EPID_ERROR(res);
    }
    res = kEpidNoErr;
  } while (0);

  f = NULL;
  SAFE_FREE(commit_values)
  DeleteEcPoint(&T);
  DeleteEcPoint(&R1);
  DeleteEcPoint(&R2);
  DeleteFfElement(&mu);
  DeleteFfElement(&nu);
  DeleteFfElement(&rmu);
  DeleteFfElement(&rnu);
  DeleteFfElement(&c);
  DeleteFfElement(&smu);
  DeleteFfElement(&snu);
  DeleteEcPoint(&B);
  DeleteEcPoint(&K);
  DeleteEcPoint(&rlB);
  DeleteEcPoint(&rlK);

  return res;
}
