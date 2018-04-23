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
* \brief Epid11CheckPrivRlEntry implementation.
*/

#include "epid/verifier/1.1/api.h"
#include "epid/verifier/1.1/src/context.h"
EpidStatus Epid11CheckPrivRlEntry(Epid11VerifierCtx const* ctx,
                                  Epid11BasicSignature const* sig,
                                  FpElemStr const* f) {
  EpidStatus result = kEpidErr;
  EcPoint* b = NULL;
  EcPoint* k = NULL;
  EcPoint* t5 = NULL;
  EcGroup* G3 = NULL;
  if (!ctx || !sig || !f) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid11_params || !ctx->epid11_params->G3) {
    return kEpidBadArgErr;
  }
  do {
    // Section 4.1.2 Step 31. The verifier computes t5 = G3.exp(B, f)
    // and verifies that G3.isEqual(t5, K) = false
    bool compare_result = false;
    G3 = ctx->epid11_params->G3;
    result = NewEcPoint(G3, &b);
    if (kEpidNoErr != result) {
      result = kEpidMathErr;
      break;
    }
    result = NewEcPoint(G3, &k);
    if (kEpidNoErr != result) {
      result = kEpidMathErr;
      break;
    }
    result = NewEcPoint(G3, &t5);
    if (kEpidNoErr != result) {
      result = kEpidMathErr;
      break;
    }
    result = ReadEcPoint(G3, &sig->B, sizeof(sig->B), b);
    if (kEpidNoErr != result) {
      result = kEpidMathErr;
      break;
    }
    result = ReadEcPoint(G3, &sig->K, sizeof(sig->K), k);
    if (kEpidNoErr != result) {
      result = kEpidMathErr;
      break;
    }
    result = EcExp(G3, b, (BigNumStr const*)f, t5);
    if (kEpidNoErr != result) {
      result = kEpidMathErr;
      break;
    }
    result = EcIsEqual(G3, t5, k, &compare_result);
    if (kEpidNoErr != result) {
      result = kEpidMathErr;
      break;
    }
    // if t5 == k, sig revoked in PrivRl
    if (compare_result) {
      result = kEpidSigRevokedInPrivRl;
    } else {
      result = kEpidNoErr;
    }
  } while (0);

  DeleteEcPoint(&t5);
  DeleteEcPoint(&k);
  DeleteEcPoint(&b);
  return result;
}
