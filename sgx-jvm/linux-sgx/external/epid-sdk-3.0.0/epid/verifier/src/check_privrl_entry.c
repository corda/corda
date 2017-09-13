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
 * \brief EpidCheckPrivRlEntry implementation.
 */

#include "epid/verifier/api.h"
#include "epid/verifier/src/context.h"
EpidStatus EpidCheckPrivRlEntry(VerifierCtx const* ctx,
                                BasicSignature const* sig, FpElemStr const* f) {
  EpidStatus result = kEpidErr;
  EcPoint* b = NULL;
  EcPoint* k = NULL;
  EcPoint* t4 = NULL;
  EcGroup* G1 = NULL;
  if (!ctx || !sig || !f) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->epid2_params->G1) {
    return kEpidBadArgErr;
  }
  do {
    // Section 4.1.2 Step 4.b For i = 0, ... , n1-1, the verifier computes t4
    // =G1.exp(B, f[i]) and verifies that G1.isEqual(t4, K) = false.
    bool compare_result = false;
    G1 = ctx->epid2_params->G1;
    result = NewEcPoint(G1, &b);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewEcPoint(G1, &k);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewEcPoint(G1, &t4);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadEcPoint(G1, &sig->B, sizeof(sig->B), b);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadEcPoint(G1, &sig->K, sizeof(sig->K), k);
    if (kEpidNoErr != result) {
      break;
    }
    result = EcExp(G1, b, (BigNumStr const*)f, t4);
    if (kEpidNoErr != result) {
      break;
    }
    result = EcIsEqual(G1, t4, k, &compare_result);
    if (kEpidNoErr != result) {
      break;
    }
    // if t4 == k, sig revoked in PrivRl
    if (compare_result) {
      result = kEpidSigRevokedInPrivRl;
    } else {
      result = kEpidNoErr;
    }
  } while (0);

  DeleteEcPoint(&t4);
  DeleteEcPoint(&k);
  DeleteEcPoint(&b);
  return result;
}
