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
#include "epid/verifier/src/context.h"
#include "epid/common/src/epid2params.h"
#include "epid/common/src/memory.h"
#include "epid/common/math/pairing.h"
#include "epid/common/src/endian_convert.h"
#include "epid/common/src/sigrlvalid.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }
/// create Verifier precomp of the VerifierCtx
static EpidStatus DoPrecomputation(VerifierCtx* ctx);

/// Read Verifier precomp
static EpidStatus ReadPrecomputation(VerifierPrecomp const* precomp_str,
                                     VerifierCtx* ctx);

/// Internal function to prove if group based revocation list is valid
static bool IsGroupRlValid(GroupRl const* group_rl, size_t grp_rl_size) {
  const size_t kMinGroupRlSize = sizeof(GroupRl) - sizeof(GroupId);
  size_t input_grp_rl_size = 0;

  if (!group_rl || grp_rl_size < kMinGroupRlSize) {
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

  if (!gid || !priv_rl || kMinPrivRlSize > priv_rl_size) {
    return false;
  }
  if (ntohl(priv_rl->n1) >
      (SIZE_MAX - kMinPrivRlSize) / sizeof(priv_rl->f[0])) {
    return false;
  }
  // sanity check of input PrivRl size
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
    verifier_ctx->was_verifier_rl_updated = false;
    verifier_ctx->basename_hash = NULL;
    verifier_ctx->basename = NULL;
    verifier_ctx->basename_len = 0;
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
    SAFE_FREE((*ctx)->verifier_rl);
    DeleteEcPoint(&(*ctx)->basename_hash);
    SAFE_FREE((*ctx)->basename);
    (*ctx)->basename_len = 0;
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
      !ctx->epid2_params->GT || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  eg12 = ctx->eg12;
  GT = ctx->epid2_params->GT;
  precomp->gid = ctx->pub_key->gid;
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
  if (!ctx || !priv_rl || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (!IsPrivRlValid(&ctx->pub_key->gid, priv_rl, priv_rl_size)) {
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
  ctx->priv_rl = priv_rl;
  return kEpidNoErr;
}

EpidStatus EpidVerifierSetSigRl(VerifierCtx* ctx, SigRl const* sig_rl,
                                size_t sig_rl_size) {
  if (!ctx || !sig_rl || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (!IsSigRlValid(&ctx->pub_key->gid, sig_rl, sig_rl_size)) {
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
  ctx->sig_rl = sig_rl;

  return kEpidNoErr;
}

EpidStatus EpidVerifierSetGroupRl(VerifierCtx* ctx, GroupRl const* grp_rl,
                                  size_t grp_rl_size) {
  if (!ctx || !grp_rl || !ctx->pub_key) {
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
  VerifierRl* verifier_rl = NULL;
  EpidStatus res = kEpidErr;
  EcPoint* B = NULL;
  bool cmp_result = false;
  EcGroup* G1 = NULL;
  if (!ctx || !ver_rl || !ctx->pub_key || !ctx->epid2_params ||
      !ctx->epid2_params->G1) {
    return kEpidBadArgErr;
  }
  if (!IsVerifierRlValid(&ctx->pub_key->gid, ver_rl, ver_rl_size)) {
    return kEpidBadArgErr;
  }
  // if random basename
  if (!ctx->basename_hash) {
    return kEpidInconsistentBasenameSetErr;
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
  do {
    G1 = ctx->epid2_params->G1;
    res = NewEcPoint(G1, &B);
    BREAK_ON_EPID_ERROR(res);
    res = ReadEcPoint(G1, &(ver_rl->B), sizeof(ver_rl->B), B);
    BREAK_ON_EPID_ERROR(res);
    // verify B = G1.hash(bsn)
    res = EcIsEqual(G1, ctx->basename_hash, B, &cmp_result);
    BREAK_ON_EPID_ERROR(res);
    if (true != cmp_result) {
      res = kEpidBadArgErr;
      break;
    }
    verifier_rl = SAFE_ALLOC(ver_rl_size);
    if (!verifier_rl) {
      res = kEpidMemAllocErr;
      break;
    }
    if (0 != memcpy_S(verifier_rl, ver_rl_size, ver_rl, ver_rl_size)) {
      res = kEpidBadArgErr;
      break;
    }
    res = kEpidNoErr;
  } while (0);
  DeleteEcPoint(&B);
  SAFE_FREE(ctx->verifier_rl);
  if (kEpidNoErr == res) {
    ctx->verifier_rl = verifier_rl;
    ctx->was_verifier_rl_updated = false;
  }
  return res;
}

size_t EpidGetVerifierRlSize(VerifierCtx const* ctx) {
  size_t empty_size = 0;
  if (!ctx || !ctx->basename_hash) return 0;
  empty_size = sizeof(VerifierRl) - sizeof(((VerifierRl*)0)->K[0]);
  if (!ctx->verifier_rl) return empty_size;
  return empty_size +
         ntohl(ctx->verifier_rl->n4) * sizeof(ctx->verifier_rl->K[0]);
}

EpidStatus EpidWriteVerifierRl(VerifierCtx const* ctx, VerifierRl* ver_rl,
                               size_t ver_rl_size) {
  EpidStatus res = kEpidErr;
  size_t real_ver_rl_size = 0;
  if (!ctx || !ver_rl || !ctx->pub_key || !ctx->epid2_params ||
      !ctx->epid2_params->G1) {
    return kEpidBadArgErr;
  }
  real_ver_rl_size = EpidGetVerifierRlSize(ctx);
  if (real_ver_rl_size == 0) {
    return kEpidErr;
  }
  if (real_ver_rl_size != ver_rl_size) {
    return kEpidBadArgErr;
  }
  if (ctx->verifier_rl) {
    // serialize
    if (0 !=
        memcpy_S(ver_rl, ver_rl_size, ctx->verifier_rl, real_ver_rl_size)) {
      return kEpidBadArgErr;
    }
    // update rl version if it has changed
    if (ctx->was_verifier_rl_updated) {
      uint32_t prior_rl_version = ntohl(ver_rl->version);
      *((uint32_t*)(&ver_rl->version)) = htonl(prior_rl_version + 1);
      ((VerifierCtx*)ctx)->was_verifier_rl_updated = false;
    }
  } else {
    // write empty rl
    res = WriteEcPoint(ctx->epid2_params->G1, ctx->basename_hash, &(ver_rl->B),
                       sizeof(ver_rl->B));
    if (kEpidNoErr != res) {
      return res;
    }
    ver_rl->gid = ctx->pub_key->gid;
    memset(&ver_rl->version, 0, sizeof(ver_rl->version));
    memset(&ver_rl->n4, 0, sizeof(ver_rl->n4));
  }
  return kEpidNoErr;
}

EpidStatus EpidBlacklistSig(VerifierCtx* ctx, EpidSignature const* sig,
                            size_t sig_len, void const* msg, size_t msg_len) {
  EpidStatus result = kEpidErr;
  VerifierRl* ver_rl = NULL;
  if (!ctx || !sig || (!msg && msg_len > 0) || !ctx->epid2_params ||
      !ctx->epid2_params->G1) {
    return kEpidBadArgErr;
  }
  if (sig_len < sizeof(EpidSignature) - sizeof(((EpidSignature*)0)->sigma[0])) {
    return kEpidBadArgErr;
  }
  if (!ctx->basename_hash) {
    return kEpidInconsistentBasenameSetErr;
  }

  do {
    EcGroup* G1 = ctx->epid2_params->G1;
    uint32_t n4 = 0;
    result = EpidVerify(ctx, sig, sig_len, msg, msg_len);
    BREAK_ON_EPID_ERROR(result);

    if (!ctx->verifier_rl) {
      ver_rl = SAFE_ALLOC(sizeof(VerifierRl));
      if (!ver_rl) {
        result = kEpidMemAllocErr;
        break;
      }
      // write empty rl
      ver_rl->gid = ctx->pub_key->gid;
      result =
          WriteEcPoint(G1, ctx->basename_hash, &(ver_rl->B), sizeof(ver_rl->B));
      BREAK_ON_EPID_ERROR(result);
    } else {
      uint32_t prior_rl_version = ntohl(ctx->verifier_rl->version);
      n4 = ntohl(ctx->verifier_rl->n4);

      if (prior_rl_version == UINT32_MAX || n4 == UINT32_MAX) {
        result = kEpidBadArgErr;
        break;
      }
      ver_rl =
          SAFE_REALLOC(ctx->verifier_rl, EpidGetVerifierRlSize(ctx) +
                                             sizeof(((VerifierRl*)0)->K[0]));
      if (!ver_rl) {
        result = kEpidMemAllocErr;
        break;
      }
    }

    ctx->was_verifier_rl_updated = true;
    ++n4;
    ver_rl->K[n4 - 1] = sig->sigma0.K;

    *((uint32_t*)(&ver_rl->n4)) = htonl(n4);
    ctx->verifier_rl = ver_rl;
    result = kEpidNoErr;
  } while (0);
  if (kEpidNoErr != result) SAFE_FREE(ver_rl);
  return result;
}

EpidStatus EpidVerifierSetHashAlg(VerifierCtx* ctx, HashAlg hash_alg) {
  EpidStatus result = kEpidErr;
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (kSha256 != hash_alg && kSha384 != hash_alg && kSha512 != hash_alg)
    return kEpidBadArgErr;

  if (ctx->hash_alg != hash_alg) {
    HashAlg previous_hash_alg = ctx->hash_alg;
    ctx->hash_alg = hash_alg;

    result = EpidVerifierSetBasename(ctx, ctx->basename, ctx->basename_len);
    if (kEpidNoErr != result) {
      ctx->hash_alg = previous_hash_alg;
      return result;
    }
  }
  result = kEpidNoErr;
  return result;
}

EpidStatus EpidVerifierSetBasename(VerifierCtx* ctx, void const* basename,
                                   size_t basename_len) {
  EpidStatus result = kEpidErr;
  EcPoint* basename_hash = NULL;
  uint8_t* basename_buffer = NULL;

  if (!ctx || !ctx->epid2_params || !ctx->epid2_params->G1) {
    return kEpidBadArgErr;
  }
  if (!basename && basename_len > 0) {
    return kEpidBadArgErr;
  }

  if (!basename) {
    DeleteEcPoint(&ctx->basename_hash);
    ctx->basename_hash = NULL;
    ctx->was_verifier_rl_updated = false;
    SAFE_FREE(ctx->basename);
    ctx->basename_len = 0;
    return kEpidNoErr;
  }

  do {
    size_t i = 0;
    EcGroup* G1 = ctx->epid2_params->G1;
    result = NewEcPoint(G1, &basename_hash);
    if (kEpidNoErr != result) {
      break;
    }

    result = EcHash(G1, basename, basename_len, ctx->hash_alg, basename_hash);
    if (kEpidNoErr != result) {
      break;
    }

    if (basename_len > 0) {
      basename_buffer = SAFE_ALLOC(basename_len);
      if (!basename_buffer) {
        result = kEpidMemAllocErr;
        break;
      }
    }

    SAFE_FREE(ctx->verifier_rl);

    DeleteEcPoint(&ctx->basename_hash);
    ctx->basename_hash = basename_hash;
    SAFE_FREE(ctx->basename);
    ctx->basename = basename_buffer;
    ctx->basename_len = basename_len;
    for (i = 0; i < basename_len; i++) {
      ctx->basename[i] = ((uint8_t*)basename)[i];
    }
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    DeleteEcPoint(&basename_hash);
    SAFE_FREE(basename_buffer);
  }
  return result;
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
      !ctx->e2w || !ctx->eg12 || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (0 !=
      memcmp(&precomp_str->gid, &ctx->pub_key->gid, sizeof(precomp_str->gid))) {
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
