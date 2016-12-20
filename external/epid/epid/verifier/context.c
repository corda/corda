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
 * \brief Verifier context implementation.
 */
#include <string.h>
#include "epid/verifier/api.h"
#include "epid/verifier/context.h"
#include "epid/common/epid2params.h"
#include "epid/common/memory.h"
#include "epid/common/math/pairing.h"
#include "epid/common/endian_convert.h"
#include "epid/common/sigrlvalid.h"

/// create Verifier precomp of the VerifierCtx
static EpidStatus DoPrecomputation(VerifierCtx* ctx);

/// Read Verifier precomp
static EpidStatus ReadPrecomputation(VerifierPrecomp const* precomp_str,
                                     VerifierCtx* ctx);

/// Internal function to prove if group based revocation list is valid
static bool IsGroupRlValid(GroupRl const* group_rl, size_t grp_rl_size) {
  const size_t kMinGroupRlSize = sizeof(GroupRl) - sizeof(GroupId);
  size_t input_grp_rl_size = 0;

  if (!group_rl) {
    return false;
  }
  if (grp_rl_size < kMinGroupRlSize) {
    return false;
  }
  if (ntohl(group_rl->n3) > (SIZE_MAX - kMinGroupRlSize) / sizeof(GroupId)) {
    return false;
  }
  input_grp_rl_size = kMinGroupRlSize + (ntohl(group_rl->n3) * sizeof(GroupId));
  if (input_grp_rl_size != grp_rl_size) {
    return false;
  }
  return true;
}

/// Internal function to verify if private key based revocation list is valid
static bool IsPrivRlValid(GroupId const* gid, PrivRl const* priv_rl,
                          size_t priv_rl_size) {
  const size_t kMinPrivRlSize = sizeof(PrivRl) - sizeof(FpElemStr);
  size_t input_priv_rl_size = 0;

  if (!gid || !priv_rl) {
    return false;
  }
  if (kMinPrivRlSize > priv_rl_size) {
    return false;
  }
  if (ntohl(priv_rl->n1) >
      (SIZE_MAX - kMinPrivRlSize) / sizeof(priv_rl->f[0])) {
    return false;
  }
  // sanity check of intput PrivRl size
  input_priv_rl_size =
      kMinPrivRlSize + ntohl(priv_rl->n1) * sizeof(priv_rl->f[0]);
  if (input_priv_rl_size != priv_rl_size) {
    return false;
  }
  // verify that gid given and gid in PrivRl match
  if (0 != memcmp(gid, &priv_rl->gid, sizeof(*gid))) {
    return false;
  }
  return true;
}

/// Internal function to verify if verifier revocation list is valid
static bool IsVerifierRlValid(GroupId const* gid, VerifierRl const* ver_rl,
                              size_t ver_rl_size) {
  const size_t kMinVerifierRlSize = sizeof(VerifierRl) - sizeof(G1ElemStr);
  size_t expected_verifier_rl_size = 0;

  if (!gid || !ver_rl || kMinVerifierRlSize > ver_rl_size) {
    return false;
  }
  if (ntohl(ver_rl->n4) >
      (SIZE_MAX - kMinVerifierRlSize) / sizeof(ver_rl->K[0])) {
    return false;
  }
  // sanity check of input VerifierRl size
  expected_verifier_rl_size =
      kMinVerifierRlSize + ntohl(ver_rl->n4) * sizeof(ver_rl->K[0]);
  if (expected_verifier_rl_size != ver_rl_size) {
    return false;
  }

  // verify that gid in public key and gid in SigRl match
  if (0 != memcmp(gid, &ver_rl->gid, sizeof(*gid))) {
    return false;
  }

  return true;
}

EpidStatus EpidVerifierCreate(GroupPubKey const* pubkey,
                              VerifierPrecomp const* precomp,
                              VerifierCtx** ctx) {
  EpidStatus result = kEpidErr;
  VerifierCtx* verifier_ctx = NULL;
  if (!pubkey || !ctx) {
    return kEpidBadArgErr;
  }
  do {
    // Allocate memory for VerifierCtx
    verifier_ctx = SAFE_ALLOC(sizeof(VerifierCtx));
    if (!verifier_ctx) {
      result = kEpidMemAllocErr;
      break;
    }

    // set SHA512 as the default hash algorithm
    verifier_ctx->hash_alg = kSha512;

    // Internal representation of Epid2Params
    result = CreateEpid2Params(&verifier_ctx->epid2_params);
    if (kEpidNoErr != result) {
      break;
    }
    // Internal representation of Group Pub Key
    result = CreateGroupPubKey(pubkey, verifier_ctx->epid2_params->G1,
                               verifier_ctx->epid2_params->G2,
                               &verifier_ctx->pub_key);
    if (kEpidNoErr != result) {
      break;
    }
    // Store group public key strings for later use
    result = SetKeySpecificCommitValues(pubkey, &verifier_ctx->commit_values);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate verifier_ctx->e12
    result = NewFfElement(verifier_ctx->epid2_params->GT, &verifier_ctx->e12);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate verifier_ctx->e22
    result = NewFfElement(verifier_ctx->epid2_params->GT, &verifier_ctx->e22);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate verifier_ctx->e2w
    result = NewFfElement(verifier_ctx->epid2_params->GT, &verifier_ctx->e2w);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate verifier_ctx->eg12
    result = NewFfElement(verifier_ctx->epid2_params->GT, &verifier_ctx->eg12);
    if (kEpidNoErr != result) {
      break;
    }
    // precomputation
    if (precomp != NULL) {
      result = ReadPrecomputation(precomp, verifier_ctx);
    } else {
      result = DoPrecomputation(verifier_ctx);
    }
    if (kEpidNoErr != result) {
      break;
    }
    verifier_ctx->sig_rl = NULL;
    verifier_ctx->group_rl = NULL;
    verifier_ctx->priv_rl = NULL;
    verifier_ctx->verifier_rl = NULL;
    *ctx = verifier_ctx;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result && verifier_ctx) {
    DeleteFfElement(&verifier_ctx->eg12);
    DeleteFfElement(&verifier_ctx->e2w);
    DeleteFfElement(&verifier_ctx->e22);
    DeleteFfElement(&verifier_ctx->e12);
    DeleteEpid2Params(&verifier_ctx->epid2_params);
    DeleteGroupPubKey(&verifier_ctx->pub_key);
    SAFE_FREE(verifier_ctx);
  }
  return result;
}

void EpidVerifierDelete(VerifierCtx** ctx) {
  if (ctx && *ctx) {
    DeleteFfElement(&(*ctx)->eg12);
    DeleteFfElement(&(*ctx)->e2w);
    DeleteFfElement(&(*ctx)->e22);
    DeleteFfElement(&(*ctx)->e12);
    DeleteGroupPubKey(&(*ctx)->pub_key);
    DeleteEpid2Params(&(*ctx)->epid2_params);
    (*ctx)->priv_rl = NULL;
    (*ctx)->sig_rl = NULL;
    (*ctx)->group_rl = NULL;
    (*ctx)->verifier_rl = NULL;
    SAFE_FREE(*ctx);
  }
}

EpidStatus EpidVerifierWritePrecomp(VerifierCtx const* ctx,
                                    VerifierPrecomp* precomp) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;   // an element in GT
  FfElement* e22 = NULL;   // an element in GT
  FfElement* e2w = NULL;   // an element in GT
  FfElement* eg12 = NULL;  // an element in GT
  FiniteField* GT = NULL;  // Finite field GT(Fq12 )
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (!precomp) {
    return kEpidBadArgErr;
  }
  if (!ctx->e12 || !ctx->e22 || !ctx->e2w || !ctx->eg12 || !ctx->epid2_params ||
      !(ctx->epid2_params->GT)) {
    return kEpidBadArgErr;
  }
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  eg12 = ctx->eg12;
  GT = ctx->epid2_params->GT;
  result = WriteFfElement(GT, e12, &(precomp->e12), sizeof(precomp->e12));
  if (kEpidNoErr != result) {
    return result;
  }
  result = WriteFfElement(GT, e22, &(precomp->e22), sizeof(precomp->e22));
  if (kEpidNoErr != result) {
    return result;
  }
  result = WriteFfElement(GT, e2w, &(precomp->e2w), sizeof(precomp->e2w));
  if (kEpidNoErr != result) {
    return result;
  }
  result = WriteFfElement(GT, eg12, &(precomp->eg12), sizeof(precomp->eg12));
  if (kEpidNoErr != result) {
    return result;
  }
  return result;
}

EpidStatus EpidVerifierSetPrivRl(VerifierCtx* ctx, PrivRl const* priv_rl,
                                 size_t priv_rl_size) {
  const size_t kMinPrivRlSize = sizeof(PrivRl) - sizeof(FpElemStr);
  if (!ctx || !priv_rl) {
    return kEpidBadArgErr;
  }
  if (kMinPrivRlSize > priv_rl_size) {
    return kEpidBadArgErr;
  }
  if (!ctx->pub_key) {
    return kEpidBadArgErr;
  }
  // Do not set an older version of priv rl
  if (ctx->priv_rl) {
    unsigned int current_ver = 0;
    unsigned int incoming_ver = 0;
    current_ver = ntohl(ctx->priv_rl->version);
    incoming_ver = ntohl(priv_rl->version);
    if (current_ver >= incoming_ver) {
      return kEpidBadArgErr;
    }
  }
  if (!IsPrivRlValid(&ctx->pub_key->gid, priv_rl, priv_rl_size)) {
    return kEpidBadArgErr;
  }
  ctx->priv_rl = priv_rl;
  return kEpidNoErr;
}

EpidStatus EpidVerifierSetSigRl(VerifierCtx* ctx, SigRl const* sig_rl,
                                size_t sig_rl_size) {
  const size_t kMinSigRlSize = sizeof(SigRl) - sizeof(SigRlEntry);
  if (!ctx || !sig_rl) {
    return kEpidBadArgErr;
  }
  if (kMinSigRlSize > sig_rl_size) {
    return kEpidBadArgErr;
  }
  if (!ctx->pub_key) {
    return kEpidBadArgErr;
  }
  // Do not set an older version of sig rl
  if (ctx->sig_rl) {
    unsigned int current_ver = 0;
    unsigned int incoming_ver = 0;
    current_ver = ntohl(ctx->sig_rl->version);
    incoming_ver = ntohl(sig_rl->version);
    if (current_ver >= incoming_ver) {
      return kEpidBadArgErr;
    }
  }
  if (!IsSigRlValid(&ctx->pub_key->gid, sig_rl, sig_rl_size)) {
    return kEpidBadArgErr;
  }
  ctx->sig_rl = sig_rl;

  return kEpidNoErr;
}

EpidStatus EpidVerifierSetGroupRl(VerifierCtx* ctx, GroupRl const* grp_rl,
                                  size_t grp_rl_size) {
  if (!ctx || !grp_rl) {
    return kEpidBadArgErr;
  }
  if (!ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (!IsGroupRlValid(grp_rl, grp_rl_size)) {
    return kEpidBadArgErr;
  }
  // Do not set an older version of group rl
  if (ctx->group_rl) {
    unsigned int current_ver = 0;
    unsigned int incoming_ver = 0;
    current_ver = ntohl(ctx->group_rl->version);
    incoming_ver = ntohl(grp_rl->version);
    if (current_ver >= incoming_ver) {
      return kEpidBadArgErr;
    }
  }
  ctx->group_rl = grp_rl;

  return kEpidNoErr;
}

EpidStatus EpidVerifierSetVerifierRl(VerifierCtx* ctx, VerifierRl const* ver_rl,
                                     size_t ver_rl_size) {
  if (!ctx || !ver_rl) {
    return kEpidBadArgErr;
  }
  if (!ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (!IsVerifierRlValid(&ctx->pub_key->gid, ver_rl, ver_rl_size)) {
    return kEpidBadArgErr;
  }
  // Do not set an older version of verifier rl
  if (ctx->verifier_rl) {
    unsigned int current_ver = 0;
    unsigned int incoming_ver = 0;
    current_ver = ntohl(ctx->verifier_rl->version);
    incoming_ver = ntohl(ver_rl->version);
    if (current_ver >= incoming_ver) {
      return kEpidBadArgErr;
    }
  }

  ctx->verifier_rl = ver_rl;

  return kEpidNoErr;
}

EpidStatus EpidVerifierSetHashAlg(VerifierCtx* ctx, HashAlg hash_alg) {
  if (!ctx) return kEpidBadArgErr;
  if (kSha256 != hash_alg && kSha384 != hash_alg && kSha512 != hash_alg)
    return kEpidBadArgErr;
  ctx->hash_alg = hash_alg;
  return kEpidNoErr;
}

static EpidStatus DoPrecomputation(VerifierCtx* ctx) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;
  FfElement* e22 = NULL;
  FfElement* e2w = NULL;
  FfElement* eg12 = NULL;
  Epid2Params_* params = NULL;
  GroupPubKey_* pub_key = NULL;
  PairingState* ps_ctx = NULL;
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->epid2_params->GT ||
      !ctx->epid2_params->pairing_state || !ctx->pub_key || !ctx->e12 ||
      !ctx->e22 || !ctx->e2w || !ctx->eg12) {
    return kEpidBadArgErr;
  }
  pub_key = ctx->pub_key;
  params = ctx->epid2_params;
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  eg12 = ctx->eg12;
  ps_ctx = params->pairing_state;
  // do precomputation
  // 1. The verifier computes e12 = pairing(h1, g2).
  result = Pairing(ps_ctx, e12, pub_key->h1, params->g2);
  if (kEpidNoErr != result) {
    return result;
  }
  // 2. The verifier computes e22 = pairing(h2, g2).
  result = Pairing(ps_ctx, e22, pub_key->h2, params->g2);
  if (kEpidNoErr != result) {
    return result;
  }
  // 3. The verifier computes e2w = pairing(h2, w).
  result = Pairing(ps_ctx, e2w, pub_key->h2, pub_key->w);
  if (kEpidNoErr != result) {
    return result;
  }
  // 4. The verifier computes eg12 = pairing(g1, g2).
  result = Pairing(ps_ctx, eg12, params->g1, params->g2);
  if (kEpidNoErr != result) {
    return result;
  }
  return kEpidNoErr;
}
static EpidStatus ReadPrecomputation(VerifierPrecomp const* precomp_str,
                                     VerifierCtx* ctx) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;
  FfElement* e22 = NULL;
  FfElement* e2w = NULL;
  FfElement* eg12 = NULL;
  FiniteField* GT = NULL;
  Epid2Params_* params = NULL;
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->epid2_params->GT || !ctx->e12 || !ctx->e22 ||
      !ctx->e2w || !ctx->eg12) {
    return kEpidBadArgErr;
  }
  params = ctx->epid2_params;
  GT = params->GT;
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  eg12 = ctx->eg12;

  result = ReadFfElement(GT, &precomp_str->e12, sizeof(precomp_str->e12), e12);
  if (kEpidNoErr != result) {
    return result;
  }
  result = ReadFfElement(GT, &precomp_str->e22, sizeof(precomp_str->e22), e22);
  if (kEpidNoErr != result) {
    return result;
  }
  result = ReadFfElement(GT, &precomp_str->e2w, sizeof(precomp_str->e2w), e2w);
  if (kEpidNoErr != result) {
    return result;
  }
  result =
      ReadFfElement(GT, &precomp_str->eg12, sizeof(precomp_str->eg12), eg12);
  if (kEpidNoErr != result) {
    return result;
  }
  return kEpidNoErr;
}
