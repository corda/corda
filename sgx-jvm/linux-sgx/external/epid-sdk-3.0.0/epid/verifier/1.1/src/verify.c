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
* \brief Epid11Verify implementation.
*/

#include <string.h>
#include "epid/verifier/1.1/api.h"
#include "epid/verifier/1.1/src/context.h"
#include "epid/common/src/endian_convert.h"

static size_t Epid11GetSignatureRlCount(Epid11Signature const* sig) {
  return (!sig) ? 0 : ntohl(sig->n2);
}

static size_t Epid11GetGroupRlCount(Epid11GroupRl const* rl) {
  return (!rl) ? 0 : ntohl(rl->n3);
}

static size_t Epid11GetSigRlCount(Epid11SigRl const* rl) {
  return (!rl) ? 0 : ntohl(rl->n2);
}

static size_t Epid11GetPrivRlCount(Epid11PrivRl const* rl) {
  return (!rl) ? 0 : ntohl(rl->n1);
}

/// Check PrivRL status of a signature for one PrivRl entry
/*!
computes t5 =G3.exp(B, f[i]) and verifies that G3.isEqual(t5, K) = false.

 \param[in] ctx
 The verifier context.
 \param[in] sig
 The basic signature.
 \param[in] f_str
 priv_rl entry to check.

 \returns ::EpidStatus

 \retval ::kEpidNoErr
 Signature was not revoked
 \retval ::kEpidSigRevokedInPrivRl
 Signature revoked in PrivRl
*/
EpidStatus Epid11PrVerify(Epid11VerifierCtx const* ctx,
                          Epid11BasicSignature const* sig, BigNumStr* f_str) {
  EpidStatus sts = kEpidErr;
  EcPoint* B = NULL;
  EcPoint* K = NULL;
  EcPoint* t5 = NULL;
  FfElement* f = NULL;
  EcGroup* G3 = ctx->epid11_params->G3;
  FiniteField* Fp = ctx->epid11_params->Fp;
  bool eq = false;
  do {
    sts = NewEcPoint(G3, &B);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    sts = NewEcPoint(G3, &K);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    sts = NewEcPoint(G3, &t5);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    sts = NewFfElement(Fp, &f);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    sts = ReadEcPoint(G3, &sig->B, sizeof(sig->B), B);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    sts = ReadEcPoint(G3, &sig->K, sizeof(sig->K), K);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    sts = EcExp(G3, B, f_str, t5);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    sts = EcIsEqual(G3, t5, K, &eq);
    if (kEpidNoErr != sts) {
      sts = kEpidMathErr;
      break;
    }
    if (eq) {
      sts = kEpidSigRevokedInPrivRl;
    } else {
      sts = kEpidNoErr;
    }
  } while (0);
  DeleteFfElement(&f);
  DeleteEcPoint(&t5);
  DeleteEcPoint(&K);
  DeleteEcPoint(&B);
  return sts;
}

EpidStatus Epid11Verify(Epid11VerifierCtx const* ctx,
                        Epid11Signature const* sig, size_t sig_len,
                        void const* msg, size_t msg_len) {
  // Step 1. Setup
  size_t const sig_header_len =
      (sizeof(Epid11Signature) - sizeof(Epid11NrProof));
  EpidStatus sts = kEpidErr;
  size_t rl_count = 0;
  size_t i;
  if (!sig || !ctx || !ctx->epid11_params || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (!msg && (0 != msg_len)) {
    // if message is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (sig_len < sig_header_len) {
    return kEpidBadArgErr;
  }
  rl_count = Epid11GetSignatureRlCount(sig);
  if (rl_count > (SIZE_MAX - sig_header_len) / sizeof(sig->sigma[0]) ||
      (rl_count * sizeof(sig->sigma[0])) + sig_header_len != sig_len) {
    return kEpidBadArgErr;
  }
  // Check if signature has NrPoofs but SigRl is not set
  if (0 < rl_count && !ctx->sig_rl) {
    return kEpidBadArgErr;
  }

  // Step 3. The verifier verifies gid in the public key, PRIV-RL, and
  // SIG-RL (if provided) and the verifier pre-computation blob all match.
  if (ctx->priv_rl) {
    if (0 != memcmp(&ctx->pub_key->gid, &ctx->priv_rl->gid,
                    sizeof(ctx->pub_key->gid))) {
      return kEpidBadArgErr;
    }
  }

  if (ctx->sig_rl) {
    if (0 != memcmp(&ctx->pub_key->gid, &ctx->sig_rl->gid,
                    sizeof(ctx->pub_key->gid))) {
      return kEpidBadArgErr;
    }
  }
  // Verification of gid value in precomputation blob
  // and public key is done in ReadPrecomp

  // Step 4. The verifier verifies the signatures of PRIV-RL,
  // SIG-RL (if provided), and Group-RL (if provided) using IVK.
  // Data is already verified.

  // Step 5. If GroupRL is provided as input,...
  if (ctx->group_rl) {
    // ...the verifier verifies that gid has not been revoked, i.e.,
    // gid does not match any entry in Group-RL.
    size_t grouprl_count = Epid11GetGroupRlCount(ctx->group_rl);
    for (i = 0; i < grouprl_count; ++i) {
      if (0 == memcmp(&ctx->pub_key->gid, &ctx->group_rl->gid[i],
                      sizeof(ctx->pub_key->gid))) {
        return kEpidSigRevokedInGroupRl;
      }
    }
  }

  // Step 6. If SIG-RL is provided as input,...
  if (ctx->sig_rl) {
    size_t sigrl_count = Epid11GetSigRlCount(ctx->sig_rl);

    // ...the verifier verifies that RLver and n2
    // values in s match with the values in SIG-RL....
    if (0 != memcmp(&ctx->sig_rl->version, &sig->rl_ver,
                    sizeof(ctx->sig_rl->version))) {
      return kEpidBadArgErr;
    }

    if (sigrl_count != rl_count) {
      return kEpidBadArgErr;
    }
  }

  // Step 7-30. The verifier verifies the basic signature.
  sts = Epid11VerifyBasicSig(ctx, &sig->sigma0, msg, msg_len);
  if (sts != kEpidNoErr) {
    return kEpidSigInvalid;
  }

  // Step 31. For i = 0, ..., n1-1, the verifier computes t5 =G3.exp(B, f[i])
  // and verifies that G3.isEqual(t5, K) = false.
  if (ctx->priv_rl) {
    size_t privrl_count = Epid11GetPrivRlCount(ctx->priv_rl);
    for (i = 0; i < privrl_count; ++i) {
      sts = Epid11PrVerify(ctx, &sig->sigma0, (BigNumStr*)&ctx->priv_rl->f[i]);
      if (sts != kEpidNoErr) {
        return kEpidSigRevokedInPrivRl;
      }
    }
  }

  // Step 32. For i = 0, ..., n2-1, the verifier verifies nrVerify(B, K, B[i],
  // K[i], Sigma[i]) = true. The details of nrVerify() will be given in the
  // next subsection.
  if (ctx->sig_rl) {
    size_t sigrl_count = Epid11GetSigRlCount(ctx->sig_rl);

    for (i = 0; i < sigrl_count; ++i) {
      sts = Epid11NrVerify(ctx, &sig->sigma0, msg, msg_len, &ctx->sig_rl->bk[i],
                           &sig->sigma[i]);
      if (sts != kEpidNoErr) {
        return kEpidSigRevokedInSigRl;
      }
    }
  }

  // Step 33. If all the above verifications succeed, the verifier outputs true.
  return kEpidSigValid;
}
