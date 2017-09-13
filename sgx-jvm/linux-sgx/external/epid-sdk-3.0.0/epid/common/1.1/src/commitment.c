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
#include <stdio.h>
#include <limits.h>
#include "epid/common/1.1/src/commitment.h"
#include "epid/common/src/memory.h"
#include "epid/common/math/bignum.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/src/endian_convert.h"

EpidStatus SetKeySpecificEpid11CommitValues(Epid11GroupPubKey const* pub_key,
                                            Epid11CommitValues* values) {
  static const Epid11Params params = {
#include "epid/common/1.1/src/epid11params_tate.inc"
  };

  if (!pub_key || !values) return kEpidBadArgErr;

  values->p = params.p;
  values->g1 = params.g1;
  values->g2 = params.g2;
  values->g3 = params.g3;
  values->h1 = pub_key->h1;
  values->h2 = pub_key->h2;
  values->w = pub_key->w;

  return kEpidNoErr;
}

EpidStatus SetCalculatedEpid11CommitValues(
    Epid11G3ElemStr const* B, Epid11G3ElemStr const* K,
    Epid11G1ElemStr const* T1, Epid11G1ElemStr const* T2, EcPoint const* R1,
    EcPoint const* R2, EcPoint const* R3, FfElement const* R4, EcGroup* G1,
    EcGroup* G3, FiniteField* GT, Epid11CommitValues* values) {
  EpidStatus result;
  if (!B || !K || !T1 || !T2 || !R1 || !R2 || !R3 || !R4 || !G1 || !G3 || !GT ||
      !values) {
    return kEpidBadArgErr;
  }

  values->B = *B;
  values->K = *K;
  values->T1 = *T1;
  values->T2 = *T2;

  result = WriteEcPoint(G1, R1, &values->R1, sizeof(values->R1));
  if (kEpidNoErr != result) return result;
  result = WriteEcPoint(G1, R2, &values->R2, sizeof(values->R2));
  if (kEpidNoErr != result) return result;
  result = WriteEcPoint(G3, R3, &values->R3, sizeof(values->R3));
  if (kEpidNoErr != result) return result;
  result = WriteFfElement(GT, R4, &values->R4, sizeof(values->R4));
  if (kEpidNoErr != result) return result;

  return kEpidNoErr;
}

EpidStatus CalculateEpid11CommitmentHash(Epid11CommitValues const* values,
                                         void const* msg, uint32_t msg_len,
                                         OctStr80 const* nd, Sha256Digest* c) {
  EpidStatus result;

#pragma pack(1)
  struct {
    Sha256Digest t4;
    OctStr80 nd;
    uint32_t msg_len;
    uint8_t msg[1];
  }* t4mconcat_buf = NULL;
#pragma pack()
  size_t max_msg_len =
      SIZE_MAX - (sizeof(*t4mconcat_buf) - sizeof(t4mconcat_buf->msg));
  size_t t4mconcat_size =
      sizeof(*t4mconcat_buf) - sizeof(t4mconcat_buf->msg) + msg_len;

  if (!values || !nd || !c) return kEpidBadArgErr;
  if (!msg && (0 != msg_len)) {
    // if message is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (max_msg_len < (size_t)msg_len) {
    return kEpidBadArgErr;
  }

  do {
    // compute c = H(t4 || nd || msg_len || msg).
    t4mconcat_buf = SAFE_ALLOC(t4mconcat_size);
    if (!t4mconcat_buf) {
      result = kEpidMemAllocErr;
      break;
    }
    // Calculate c = Hash(t4 || nd || mSize || m) where t4 is Hash(p || g1 || g2
    //    || g3 || h1 || h2 || w || B || K || T1 || T2 || R1 || R2 || R3 || R4).
    result = Sha256MessageDigest(values, sizeof(*values), &t4mconcat_buf->t4);
    if (kEpidNoErr != result) break;
    t4mconcat_buf->nd = *nd;
    t4mconcat_buf->msg_len = ntohl(msg_len);
    // place variable length msg into t4mconcat_buf
    if (msg) {
      if (0 != memcpy_S(&t4mconcat_buf->msg[0],
                        t4mconcat_size - sizeof(*t4mconcat_buf) +
                            sizeof(t4mconcat_buf->msg),
                        msg, msg_len)) {
        result = kEpidBadArgErr;
        break;
      }
    }
    result = Sha256MessageDigest(t4mconcat_buf, t4mconcat_size, c);
    if (kEpidNoErr != result) break;

    result = kEpidNoErr;
  } while (0);

  SAFE_FREE(t4mconcat_buf);

  return result;
}
