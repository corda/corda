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
 * \brief Commitment hash implementation.
 */
#include <limits.h>
#include "epid/common/src/commitment.h"
#include "epid/common/src/memory.h"

EpidStatus SetKeySpecificCommitValues(GroupPubKey const* pub_key,
                                      CommitValues* values) {
  static const Epid2Params params = {
#include "epid/common/src/epid2params_ate.inc"
  };

  if (!pub_key || !values) return kEpidBadArgErr;

  values->p = params.p;
  values->g1 = params.g1;
  values->g2 = params.g2;
  values->h1 = pub_key->h1;
  values->h2 = pub_key->h2;
  values->w = pub_key->w;

  return kEpidNoErr;
}

EpidStatus SetCalculatedCommitValues(G1ElemStr const* B, G1ElemStr const* K,
                                     G1ElemStr const* T, EcPoint const* R1,
                                     EcGroup* G1, FfElement const* R2,
                                     FiniteField* GT, CommitValues* values) {
  EpidStatus sts;

  if (!B || !K || !T || !R1 || !G1 || !R2 || !GT || !values) {
    return kEpidBadArgErr;
  }

  values->B = *B;
  values->K = *K;
  values->T = *T;

  sts = WriteEcPoint(G1, R1, &values->R1, sizeof(values->R1));
  if (kEpidNoErr != sts) return sts;
  sts = WriteFfElement(GT, R2, &values->R2, sizeof(values->R2));
  if (kEpidNoErr != sts) return sts;

  return kEpidNoErr;
}

EpidStatus CalculateCommitmentHash(CommitValues const* values, FiniteField* Fp,
                                   HashAlg hash_alg, void const* msg,
                                   size_t msg_len, FfElement* c) {
  EpidStatus sts;

  FfElement* t3 = NULL;
  size_t t3mconcat_size = sizeof(FpElemStr) + msg_len;
  uint8_t* t3mconcat_buf = NULL;

  if (!values || !Fp || !c) return kEpidBadArgErr;
  if (!msg && (0 != msg_len)) {
    // if message is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (SIZE_MAX - sizeof(FpElemStr) < msg_len) {
    return kEpidBadArgErr;
  }

  do {
    sts = NewFfElement(Fp, &t3);
    if (kEpidNoErr != sts) break;

    // compute t3 = Fp.hash(p || g1 || g2 || h1 ||
    //  h2 || w || B || K || T || R1 || R2).
    sts = FfHash(Fp, values, sizeof(*values), hash_alg, t3);
    if (kEpidNoErr != sts) break;

    //   compute c = Fp.hash(t3 || m).
    t3mconcat_buf = SAFE_ALLOC(t3mconcat_size);
    if (!t3mconcat_buf) {
      sts = kEpidMemAllocErr;
      break;
    }

    // get t3 into buffer
    sts = WriteFfElement(Fp, t3, t3mconcat_buf, sizeof(FpElemStr));
    if (kEpidNoErr != sts) break;
    // get m into buffer
    if (msg) {
      // Memory copy is used to copy a message of variable length
      if (0 != memcpy_S(t3mconcat_buf + sizeof(FpElemStr),
                        t3mconcat_size - sizeof(FpElemStr), msg, msg_len)) {
        sts = kEpidBadArgErr;
        break;
      }
    }

    sts = FfHash(Fp, t3mconcat_buf, t3mconcat_size, hash_alg, c);
    if (kEpidNoErr != sts) break;

    sts = kEpidNoErr;
  } while (0);

  SAFE_FREE(t3mconcat_buf);
  DeleteFfElement(&t3);

  return sts;
}
