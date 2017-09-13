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
* \brief Intel EPID 1.1 Verifier context implementation.
*/

#include "epid/verifier/1.1/api.h"
#include "epid/verifier/1.1/src/context.h"
#include "epid/common/src/memory.h"
#include "epid/common/src/endian_convert.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
                                 \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// create Verifier precomp of the Epid11VerifierCtx
static EpidStatus DoPrecomputation(Epid11VerifierCtx* ctx);

/// Read Verifier precomp
static EpidStatus ReadPrecomputation(Epid11VerifierPrecomp const* precomp_str,
                                     Epid11VerifierCtx* ctx);

/// Internal function to prove if group based revocation list is valid
static bool Epid11IsGroupRlValid(Epid11GroupRl const* group_rl,
                                 size_t grp_rl_size) {
  const size_t kMinGroupRlSize = sizeof(Epid11GroupRl) - sizeof(Epid11GroupId);
  size_t input_grp_rl_size = 0;

  if (!group_rl) {
    return false;
  }
  if (grp_rl_size < kMinGroupRlSize) {
    return false;
  }
  if (ntohl(group_rl->n3) >
      (SIZE_MAX - kMinGroupRlSize) / sizeof(Epid11GroupId)) {
    return false;
  }
  input_grp_rl_size =
      kMinGroupRlSize + (ntohl(group_rl->n3) * sizeof(Epid11GroupId));
  if (input_grp_rl_size != grp_rl_size) {
    return false;
  }
  return true;
}
/// Internal function to prove if signature based revocation list is valid
bool Epid11IsSigRlValid(Epid11GroupId const* gid, Epid11SigRl const* sig_rl,
                        size_t sig_rl_size) {
  const size_t kMinSigRlSize = sizeof(Epid11SigRl) - sizeof(Epid11SigRlEntry);
  size_t input_sig_rl_size = 0;
  if (!gid || !sig_rl || kMinSigRlSize > sig_rl_size) {
    return false;
  }
  if (ntohl(sig_rl->n2) > (SIZE_MAX - kMinSigRlSize) / sizeof(sig_rl->bk[0])) {
    return false;
  }
  // sanity check of intput SigRl size
  input_sig_rl_size = kMinSigRlSize + ntohl(sig_rl->n2) * sizeof(sig_rl->bk[0]);
  if (input_sig_rl_size != sig_rl_size) {
    return false;
  }
  // verify that gid given and gid in SigRl match
  if (0 != memcmp(gid, &sig_rl->gid, sizeof(*gid))) {
    return false;
  }
  return true;
}
/// Internal function to verify if Intel(R) EPID 1.1 private key based
/// revocation list is valid
static bool IsEpid11PrivRlValid(Epid11GroupId const* gid,
                                Epid11PrivRl const* priv_rl,
                                size_t priv_rl_size) {
  const size_t kMinPrivRlSize = sizeof(Epid11PrivRl) - sizeof(FpElemStr);
  size_t input_priv_rl_size = 0;

  if (!gid || !priv_rl || kMinPrivRlSize > priv_rl_size) {
    return false;
  }
  if (ntohl(priv_rl->n1) >
      (SIZE_MAX - kMinPrivRlSize) / sizeof(priv_rl->f[0])) {
    return false;
  }
  // sanity check of input Epid11PrivRl size
  input_priv_rl_size =
      kMinPrivRlSize + ntohl(priv_rl->n1) * sizeof(priv_rl->f[0]);
  if (input_priv_rl_size != priv_rl_size) {
    return false;
  }
  // verify that gid given and gid in Epid11PrivRl match
  if (0 != memcmp(gid, &priv_rl->gid, sizeof(*gid))) {
    return false;
  }
  return true;
}

EpidStatus Epid11VerifierCreate(Epid11GroupPubKey const* pub_key,
                                Epid11VerifierPrecomp const* precomp,
                                Epid11VerifierCtx** ctx) {
  EpidStatus result = kEpidErr;
  Epid11VerifierCtx* verifier_ctx = NULL;
  if (!pub_key || !ctx) {
    return kEpidBadArgErr;
  }
  do {
    // Allocate memory for VerifierCtx
    verifier_ctx = SAFE_ALLOC(sizeof(Epid11VerifierCtx));
    if (!verifier_ctx) {
      result = kEpidMemAllocErr;
      break;
    }

    // Internal representation of Epid11Params
    result = CreateEpid11Params(&verifier_ctx->epid11_params);
    BREAK_ON_EPID_ERROR(result);
    // Internal representation of Group Pub Key
    result = CreateEpid11GroupPubKey(pub_key, verifier_ctx->epid11_params->G1,
                                     verifier_ctx->epid11_params->G2,
                                     &verifier_ctx->pub_key);
    BREAK_ON_EPID_ERROR(result);
    // Store group public key strings for later use
    result =
        SetKeySpecificEpid11CommitValues(pub_key, &verifier_ctx->commit_values);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate verifier_ctx->e12
    result = NewFfElement(verifier_ctx->epid11_params->GT, &verifier_ctx->e12);
    BREAK_ON_EPID_ERROR(result);
    // Allocate verifier_ctx->e22
    result = NewFfElement(verifier_ctx->epid11_params->GT, &verifier_ctx->e22);
    BREAK_ON_EPID_ERROR(result);
    // Allocate verifier_ctx->e2w
    result = NewFfElement(verifier_ctx->epid11_params->GT, &verifier_ctx->e2w);
    BREAK_ON_EPID_ERROR(result);
    // precomputation
    if (precomp != NULL) {
      result = ReadPrecomputation(precomp, verifier_ctx);
    } else {
      result = DoPrecomputation(verifier_ctx);
    }
    BREAK_ON_EPID_ERROR(result);
    verifier_ctx->sig_rl = NULL;
    verifier_ctx->group_rl = NULL;
    verifier_ctx->priv_rl = NULL;
    *ctx = verifier_ctx;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result && verifier_ctx) {
    DeleteFfElement(&verifier_ctx->e2w);
    DeleteFfElement(&verifier_ctx->e22);
    DeleteFfElement(&verifier_ctx->e12);
    DeleteEpid11GroupPubKey(&verifier_ctx->pub_key);
    DeleteEpid11Params(&verifier_ctx->epid11_params);
    SAFE_FREE(verifier_ctx);
  }
  return result;
}

void Epid11VerifierDelete(Epid11VerifierCtx** ctx) {
  if (ctx && *ctx) {
    DeleteFfElement(&(*ctx)->e2w);
    DeleteFfElement(&(*ctx)->e22);
    DeleteFfElement(&(*ctx)->e12);
    DeleteEpid11GroupPubKey(&(*ctx)->pub_key);
    DeleteEpid11Params(&(*ctx)->epid11_params);
    (*ctx)->priv_rl = NULL;
    (*ctx)->sig_rl = NULL;
    (*ctx)->group_rl = NULL;
    DeleteEcPoint(&(*ctx)->basename_hash);
    SAFE_FREE((*ctx)->basename);
    (*ctx)->basename_len = 0;
    SAFE_FREE(*ctx);
  }
}

EpidStatus Epid11VerifierWritePrecomp(Epid11VerifierCtx const* ctx,
                                      Epid11VerifierPrecomp* precomp) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;   // an element in GT
  FfElement* e22 = NULL;   // an element in GT
  FfElement* e2w = NULL;   // an element in GT
  FiniteField* GT = NULL;  // Finite field GT(Fq6)
  if (!ctx || !ctx->e12 || !ctx->e22 || !ctx->e2w || !ctx->epid11_params ||
      !(ctx->epid11_params->GT) || !ctx->pub_key || !precomp) {
    return kEpidBadArgErr;
  }
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  GT = ctx->epid11_params->GT;

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
  return result;
}

EpidStatus Epid11VerifierSetPrivRl(Epid11VerifierCtx* ctx,
                                   Epid11PrivRl const* priv_rl,
                                   size_t priv_rl_size) {
  if (!ctx || !priv_rl || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (!IsEpid11PrivRlValid(&ctx->pub_key->gid, priv_rl, priv_rl_size)) {
    return kEpidBadArgErr;
  }
  // Do not set an older version of Epid11PrivRl
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

EpidStatus Epid11VerifierSetSigRl(Epid11VerifierCtx* ctx,
                                  Epid11SigRl const* sig_rl,
                                  size_t sig_rl_size) {
  if (!ctx || !sig_rl || !ctx->pub_key) {
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
  if (!Epid11IsSigRlValid(&ctx->pub_key->gid, sig_rl, sig_rl_size)) {
    return kEpidBadArgErr;
  }
  ctx->sig_rl = sig_rl;

  return kEpidNoErr;
}

EpidStatus Epid11VerifierSetGroupRl(Epid11VerifierCtx* ctx,
                                    Epid11GroupRl const* grp_rl,
                                    size_t grp_rl_size) {
  if (!ctx || !grp_rl || !ctx->pub_key) {
    return kEpidBadArgErr;
  }
  if (!Epid11IsGroupRlValid(grp_rl, grp_rl_size)) {
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

EpidStatus Epid11VerifierSetBasename(Epid11VerifierCtx* ctx,
                                     void const* basename,
                                     size_t basename_len) {
  EpidStatus result = kEpidErr;
  EcPoint* basename_hash = NULL;
  uint8_t* basename_buffer = NULL;

  if (!ctx || !ctx->epid11_params || !ctx->epid11_params->G3) {
    return kEpidBadArgErr;
  }
  if (!basename && basename_len > 0) {
    return kEpidBadArgErr;
  }

  if (!basename) {
    ctx->basename_len = 0;
    DeleteEcPoint(&ctx->basename_hash);
    SAFE_FREE(ctx->basename);
    return kEpidNoErr;
  }

  do {
    EcGroup* G3 = ctx->epid11_params->G3;
    result = NewEcPoint(G3, &basename_hash);
    if (kEpidNoErr != result) {
      break;
    }

    result = Epid11EcHash(G3, basename, basename_len, basename_hash);
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

    ctx->basename_len = basename_len;

    if (basename_len > 0) {
      // memcpy is used to copy variable length basename
      if (0 != memcpy_S(basename_buffer, ctx->basename_len, basename,
                        basename_len)) {
        result = kEpidErr;
        break;
      }
    }
    DeleteEcPoint(&ctx->basename_hash);
    SAFE_FREE(ctx->basename);
    ctx->basename = basename_buffer;
    ctx->basename_hash = basename_hash;

    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    DeleteEcPoint(&basename_hash);
    SAFE_FREE(basename_buffer);
  }
  return result;
}

static EpidStatus DoPrecomputation(Epid11VerifierCtx* ctx) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;
  FfElement* e22 = NULL;
  FfElement* e2w = NULL;
  Epid11Params_* params = NULL;
  Epid11GroupPubKey_* pub_key = NULL;
  Epid11PairingState* ps_ctx = NULL;
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid11_params || !ctx->epid11_params->GT ||
      !ctx->epid11_params->pairing_state || !ctx->pub_key || !ctx->e12 ||
      !ctx->e22 || !ctx->e2w) {
    return kEpidBadArgErr;
  }
  pub_key = ctx->pub_key;
  params = ctx->epid11_params;
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  ps_ctx = params->pairing_state;
  // do precomputation
  // 1. The verifier computes e12 = pairing(h1, g2).
  result = Epid11Pairing(ps_ctx, pub_key->h1, params->g2, e12);
  if (kEpidNoErr != result) {
    return result;
  }
  // 2. The verifier computes e22 = pairing(h2, g2).
  result = Epid11Pairing(ps_ctx, pub_key->h2, params->g2, e22);
  if (kEpidNoErr != result) {
    return result;
  }
  // 3. The verifier computes e2w = pairing(h2, w).
  result = Epid11Pairing(ps_ctx, pub_key->h2, pub_key->w, e2w);
  if (kEpidNoErr != result) {
    return result;
  }
  return kEpidNoErr;
}
static EpidStatus ReadPrecomputation(Epid11VerifierPrecomp const* precomp_str,
                                     Epid11VerifierCtx* ctx) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;
  FfElement* e22 = NULL;
  FfElement* e2w = NULL;
  FiniteField* GT = NULL;
  Epid11Params_* params = NULL;
  unsigned int current_gid = 0;
  unsigned int incoming_gid = 0;
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid11_params || !ctx->epid11_params->GT || !ctx->e12 ||
      !ctx->e22 || !ctx->e2w) {
    return kEpidBadArgErr;
  }

  if (!ctx->pub_key || !precomp_str) return kEpidBadArgErr;

  current_gid = ntohl(ctx->pub_key->gid);
  incoming_gid = ntohl(precomp_str->gid);

  if (current_gid != incoming_gid) {
    return kEpidBadArgErr;
  }

  params = ctx->epid11_params;
  GT = params->GT;
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;

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
  return kEpidNoErr;
}
