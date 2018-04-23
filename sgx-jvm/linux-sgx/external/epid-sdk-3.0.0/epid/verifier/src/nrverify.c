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
 * \brief NrVerfy implementation.
 */

#include "epid/common/src/memory.h"
#include "epid/verifier/api.h"
#include "epid/verifier/src/context.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

#pragma pack(1)
/// Storage for values to create commitment in NrVerify algorithm
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

EpidStatus EpidNrVerify(VerifierCtx const* ctx, BasicSignature const* sig,
                        void const* msg, size_t msg_len,
                        SigRlEntry const* sigrl_entry, NrProof const* proof) {
  size_t const cv_header_len = sizeof(NrVerifyCommitValues) - sizeof(uint8_t);
  EpidStatus sts = kEpidErr;
  NrVerifyCommitValues* commit_values = NULL;
  size_t const commit_len = sizeof(*commit_values) + msg_len - 1;
  EcPoint* t_pt = NULL;
  EcPoint* k_pt = NULL;
  EcPoint* b_pt = NULL;
  EcPoint* kp_pt = NULL;
  EcPoint* bp_pt = NULL;
  EcPoint* r1_pt = NULL;
  EcPoint* r2_pt = NULL;
  FfElement* c_el = NULL;
  FfElement* nc_el = NULL;
  FfElement* smu_el = NULL;
  FfElement* snu_el = NULL;
  FfElement* commit_hash = NULL;
  if (!ctx || !sig || !proof || !sigrl_entry) {
    return kEpidBadArgErr;
  }
  if (!msg && (0 != msg_len)) {
    return kEpidBadArgErr;
  }
  if (msg_len > (SIZE_MAX - cv_header_len)) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->epid2_params->G1 || !ctx->epid2_params->Fp) {
    return kEpidBadArgErr;
  }
  do {
    EcGroup* G1 = ctx->epid2_params->G1;
    FiniteField* Fp = ctx->epid2_params->Fp;
    G1ElemStr const* b = &sig->B;
    G1ElemStr const* k = &sig->K;
    G1ElemStr const* bp = &sigrl_entry->b;
    G1ElemStr const* kp = &sigrl_entry->k;
    EcPoint const* r1p[2];
    FpElemStr const* r1b[2];
    EcPoint const* r2p[3];
    FpElemStr const* r2b[3];
    FpElemStr nc_str;
    bool t_is_identity;
    bool c_is_equal;

    commit_values = SAFE_ALLOC(commit_len);
    if (commit_values == NULL) {
      sts = kEpidMemAllocErr;
      break;
    }

    // allocate local memory
    sts = NewEcPoint(G1, &t_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(G1, &k_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(G1, &b_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(G1, &kp_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(G1, &bp_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(G1, &r1_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(G1, &r2_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(Fp, &c_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(Fp, &nc_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(Fp, &smu_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(Fp, &snu_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(Fp, &commit_hash);
    BREAK_ON_EPID_ERROR(sts);

    // 1. The verifier verifies that G1.inGroup(T) = true.
    sts = ReadEcPoint(G1, &proof->T, sizeof(proof->T), t_pt);
    if (kEpidNoErr != sts) {
      sts = kEpidBadArgErr;
      break;
    }

    // 2. The verifier verifies that G1.isIdentity(T) = false.
    sts = EcIsIdentity(G1, t_pt, &t_is_identity);
    BREAK_ON_EPID_ERROR(sts);
    if (t_is_identity) {
      sts = kEpidBadArgErr;
      break;
    }

    // 3. The verifier verifies that c, smu, snu in [0, p-1].
    sts = ReadFfElement(Fp, &proof->c, sizeof(proof->c), c_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = ReadFfElement(Fp, &proof->smu, sizeof(proof->smu), smu_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = ReadFfElement(Fp, &proof->snu, sizeof(proof->snu), snu_el);
    BREAK_ON_EPID_ERROR(sts);

    // 4. The verifier computes nc = (- c) mod p.
    sts = FfNeg(Fp, c_el, nc_el);
    BREAK_ON_EPID_ERROR(sts);

    sts = WriteFfElement(Fp, nc_el, &nc_str, sizeof(nc_str));
    BREAK_ON_EPID_ERROR(sts);

    // 5. The verifier computes R1 = G1.multiExp(K, smu, B, snu).
    sts = ReadEcPoint(G1, k, sizeof(*k), k_pt);
    if (kEpidNoErr != sts) {
      sts = kEpidBadArgErr;
      break;
    }
    sts = ReadEcPoint(G1, b, sizeof(*b), b_pt);
    if (kEpidNoErr != sts) {
      sts = kEpidBadArgErr;
      break;
    }
    r1p[0] = k_pt;
    r1p[1] = b_pt;
    r1b[0] = &proof->smu;
    r1b[1] = &proof->snu;
    sts = EcMultiExp(G1, r1p, (const BigNumStr**)r1b, 2, r1_pt);
    BREAK_ON_EPID_ERROR(sts);

    // 6. The verifier computes R2 = G1.multiExp(K', smu, B', snu, T, nc).
    sts = ReadEcPoint(G1, kp, sizeof(*kp), kp_pt);
    if (kEpidNoErr != sts) {
      sts = kEpidBadArgErr;
      break;
    }
    sts = ReadEcPoint(G1, bp, sizeof(*bp), bp_pt);
    if (kEpidNoErr != sts) {
      sts = kEpidBadArgErr;
      break;
    }
    r2p[0] = kp_pt;
    r2p[1] = bp_pt;
    r2p[2] = t_pt;
    r2b[0] = &proof->smu;
    r2b[1] = &proof->snu;
    r2b[2] = &nc_str;
    sts = EcMultiExp(G1, r2p, (const BigNumStr**)r2b, 3, r2_pt);
    BREAK_ON_EPID_ERROR(sts);

    // 7. The verifier verifies c = Fp.hash(p || g1 || B || K ||
    //    B' || K' || T || R1 || R2 || m).
    //    Refer to Section 7.1 for hash operation over a prime field.

    // commit_values is allocated such that there are msg_len bytes available
    // starting at commit_values->msg
    if (msg) {
      // Memory copy is used to copy a message of variable length
      if (0 != memcpy_S(&commit_values->msg[0], msg_len, msg, msg_len)) {
        sts = kEpidBadArgErr;
        break;
      }
    }
    commit_values->p = ctx->commit_values.p;
    commit_values->g1 = ctx->commit_values.g1;
    commit_values->b = sig->B;
    commit_values->k = sig->K;
    commit_values->bp = sigrl_entry->b;
    commit_values->kp = sigrl_entry->k;
    commit_values->t = proof->T;
    sts =
        WriteEcPoint(G1, r1_pt, &commit_values->r1, sizeof(commit_values->r1));
    BREAK_ON_EPID_ERROR(sts);
    sts =
        WriteEcPoint(G1, r2_pt, &commit_values->r2, sizeof(commit_values->r2));
    BREAK_ON_EPID_ERROR(sts);
    sts = FfHash(Fp, commit_values, commit_len, ctx->hash_alg, commit_hash);
    BREAK_ON_EPID_ERROR(sts);
    sts = FfIsEqual(Fp, c_el, commit_hash, &c_is_equal);
    BREAK_ON_EPID_ERROR(sts);
    if (!c_is_equal) {
      sts = kEpidBadArgErr;
      break;
    }
    sts = kEpidNoErr;
  } while (0);
  SAFE_FREE(commit_values);
  DeleteFfElement(&commit_hash);
  DeleteFfElement(&snu_el);
  DeleteFfElement(&smu_el);
  DeleteFfElement(&nc_el);
  DeleteFfElement(&c_el);
  DeleteEcPoint(&r2_pt);
  DeleteEcPoint(&r1_pt);
  DeleteEcPoint(&bp_pt);
  DeleteEcPoint(&kp_pt);
  DeleteEcPoint(&b_pt);
  DeleteEcPoint(&k_pt);
  DeleteEcPoint(&t_pt);
  return sts;
}
