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
 * \brief Verify implementation.
 */
#include <string.h>
#include "epid/verifier/api.h"
#include "epid/verifier/src/context.h"
#include "epid/common/src/endian_convert.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

static size_t EpidGetSignatureRlCount(EpidSignature const* sig) {
  if (!sig)
    return 0;
  else
    return ntohl(sig->n2);
}

static size_t EpidGetGroupRlCount(GroupRl const* rl) {
  if (!rl)
    return 0;
  else
    return ntohl(rl->n3);
}

static size_t EpidGetPrivRlCount(PrivRl const* rl) {
  if (!rl)
    return 0;
  else
    return ntohl(rl->n1);
}

static size_t EpidGetSigRlCount(SigRl const* rl) {
  if (!rl)
    return 0;
  else
    return ntohl(rl->n2);
}

static size_t EpidGetVerifierRlCount(VerifierRl const* rl) {
  if (!rl)
    return 0;
  else
    return ntohl(rl->n4);
}

// implements section 4.1.2 "Verify algorithm" from Intel(R) EPID 2.0 Spec
EpidStatus EpidVerify(VerifierCtx const* ctx, EpidSignature const* sig,
                      size_t sig_len, void const* msg, size_t msg_len) {
  // Step 1. Setup
  size_t const sig_header_len = (sizeof(EpidSignature) - sizeof(NrProof));
  EpidStatus sts = kEpidErr;
  size_t rl_count = 0;
  size_t i;
  if (!ctx || !sig) {
    return kEpidBadArgErr;
  }
  if (!msg && (0 != msg_len)) {
    // if message is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (sig_len < sig_header_len) {
    return kEpidBadArgErr;
  }
  rl_count = EpidGetSignatureRlCount(sig);
  if (rl_count > (SIZE_MAX - sig_header_len) / sizeof(sig->sigma[0]) ||
      (rl_count * sizeof(sig->sigma[0])) + sig_header_len != sig_len) {
    return kEpidBadArgErr;
  }
  // Step 2. The verifier verifies the basic signature Sigma0 as follows:
  sts = EpidVerifyBasicSig(ctx, &sig->sigma0, msg, msg_len);
  if (sts != kEpidNoErr) {
    // p. If any of the above verifications fails, the verifier aborts and
    // outputs 1
    return kEpidSigInvalid;
  }

  // Step 3. If GroupRL is provided,
  if (ctx->group_rl) {
    // a. The verifier verifies that gid does not match any entry in GroupRL.
    size_t grouprl_count = EpidGetGroupRlCount(ctx->group_rl);
    for (i = 0; i < grouprl_count; ++i) {
      if (0 == memcmp(&ctx->pub_key->gid, &ctx->group_rl->gid[i],
                      sizeof(ctx->pub_key->gid))) {
        // b. If gid matches an entry in GroupRL, aborts and returns 2.
        return kEpidSigRevokedInGroupRl;
      }
    }
  }

  // Step  4. If PrivRL is provided,
  if (ctx->priv_rl) {
    size_t privrl_count = EpidGetPrivRlCount(ctx->priv_rl);
    // a. The verifier verifies that gid in the public key and in PrivRL match.
    // If mismatch, abort and return "operation failed".
    if (0 != memcmp(&ctx->pub_key->gid, &ctx->priv_rl->gid,
                    sizeof(ctx->pub_key->gid))) {
      return kEpidBadArgErr;
    }
    // b. For i = 0, ..., n1-1, the verifier computes t4 =G1.exp(B, f[i]) and
    // verifies that G1.isEqual(t4, K) = false. A faster private-key revocation
    // check algorithm is provided in Section 4.5.
    for (i = 0; i < privrl_count; ++i) {
      sts = EpidCheckPrivRlEntry(ctx, &sig->sigma0, &ctx->priv_rl->f[i]);
      if (sts != kEpidNoErr) {
        // c. If the above step fails, the verifier aborts and output 3.
        return kEpidSigRevokedInPrivRl;
      }
    }
  }

  // Step 5. If SigRL is provided,
  if (ctx->sig_rl) {
    size_t sigrl_count = EpidGetSigRlCount(ctx->sig_rl);
    // a. The verifier verifies that gid in the public key and in SigRL match.
    // If mismatch, abort and return "operation failed".
    if (0 != memcmp(&ctx->pub_key->gid, &ctx->sig_rl->gid,
                    sizeof(ctx->pub_key->gid))) {
      return kEpidBadArgErr;
    }

    // b. The verifier verifies that RLver in Sigma and in SigRL match. If
    // mismatch, abort and output "operation failed".
    if (0 != memcmp(&ctx->sig_rl->version, &sig->rl_ver,
                    sizeof(ctx->sig_rl->version))) {
      return kEpidBadArgErr;
    }

    // c. The verifier verifies that n2 in Sigma and in SigRL match. If
    // mismatch, abort and output "operation failed".
    if (sigrl_count != rl_count) {
      return kEpidBadArgErr;
    }

    // d. For i = 0, ..., n2-1, the verifier verifies nrVerify(B, K, B[i],
    // K[i], Sigma[i]) = true. The details of nrVerify() will be given in the
    // next subsection.
    for (i = 0; i < sigrl_count; ++i) {
      sts = EpidNrVerify(ctx, &sig->sigma0, msg, msg_len, &ctx->sig_rl->bk[i],
                         &sig->sigma[i]);
      if (sts != kEpidNoErr) {
        // e. If the above step fails, the verifier aborts and output 4.
        return kEpidSigRevokedInSigRl;
      }
    }
  }

  // Step 6. If VerifierRL is provided,
  if (ctx->verifier_rl) {
    // a. The verifier verifies that gid in the public key and in VerifierRL
    // match. If mismatch, abort and return "operation failed".
    if (0 != memcmp(&ctx->pub_key->gid, &ctx->verifier_rl->gid,
                    sizeof(ctx->pub_key->gid))) {
      return kEpidBadArgErr;
    }

    // b. The verifier verifies that B in the signature and in VerifierRL
    // match. If mismatch, go to step 7.
    if (0 ==
        memcmp(&ctx->verifier_rl->B, &sig->sigma0.B, sizeof(sig->sigma0.B))) {
      size_t verifierrl_count = EpidGetVerifierRlCount(ctx->verifier_rl);
      // c. For i = 0, ..., n4-1, the verifier verifies that K != K[i].
      for (i = 0; i < verifierrl_count; ++i) {
        if (0 == memcmp(&ctx->verifier_rl->K[i], &sig->sigma0.K,
                        sizeof(sig->sigma0.K))) {
          // d. If the above step fails, the verifier aborts and output 5.
          return kEpidSigRevokedInVerifierRl;
        }
      }
    }
  }

  // Step 7. If all the above verifications succeed, the verifier outputs 0.
  return kEpidSigValid;
}
