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
 * \brief Member context implementation.
 */

#include <string.h>

#include "epid/member/api.h"
#include "epid/member/src/context.h"
#include "epid/member/src/privkey.h"
#include "epid/common/src/memory.h"

/// Perform pre-computation and store in context
static EpidStatus DoPrecomputation(MemberCtx* ctx);

/// Read Member precomp
static EpidStatus ReadPrecomputation(MemberPrecomp const* precomp_str,
                                     MemberCtx* ctx);

EpidStatus EpidMemberCreate(GroupPubKey const* pub_key, PrivKey const* priv_key,
                            MemberPrecomp const* precomp, BitSupplier rnd_func,
                            void* rnd_param, MemberCtx** ctx) {
  EpidStatus result = kEpidErr;
  MemberCtx* member_ctx = NULL;

  if (!pub_key || !priv_key || !rnd_func || !ctx) {
    return kEpidBadArgErr;
  }

  // The member verifies that gid in public key and in private key
  // match. If mismatch, abort and return operation failed.
  if (memcmp(&pub_key->gid, &priv_key->gid, sizeof(GroupId))) {
    return kEpidBadArgErr;
  }

  // Allocate memory for VerifierCtx
  member_ctx = SAFE_ALLOC(sizeof(MemberCtx));
  if (!member_ctx) {
    return kEpidMemAllocErr;
  }

  do {
    // set the default hash algorithm to sha512
    member_ctx->hash_alg = kSha512;

    // Internal representation of Epid2Params
    result = CreateEpid2Params(&member_ctx->epid2_params);
    if (kEpidNoErr != result) {
      break;
    }
    // Internal representation of Group Pub Key
    result =
        CreateGroupPubKey(pub_key, member_ctx->epid2_params->G1,
                          member_ctx->epid2_params->G2, &member_ctx->pub_key);
    if (kEpidNoErr != result) {
      break;
    }
    // Internal representation of Member Priv Key
    result = CreatePrivKey(priv_key, member_ctx->epid2_params->G1,
                           member_ctx->epid2_params->Fp, &member_ctx->priv_key);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate member_ctx->e12
    result = NewFfElement(member_ctx->epid2_params->GT, &member_ctx->e12);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate member_ctx->e22
    result = NewFfElement(member_ctx->epid2_params->GT, &member_ctx->e22);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate member_ctx->e2w
    result = NewFfElement(member_ctx->epid2_params->GT, &member_ctx->e2w);
    if (kEpidNoErr != result) {
      break;
    }
    // Allocate member_ctx->ea2
    result = NewFfElement(member_ctx->epid2_params->GT, &member_ctx->ea2);
    if (kEpidNoErr != result) {
      break;
    }
    // precomputation
    if (precomp != NULL) {
      result = ReadPrecomputation(precomp, member_ctx);
    } else {
      result = DoPrecomputation(member_ctx);
    }
    if (kEpidNoErr != result) {
      break;
    }
    result = SetKeySpecificCommitValues(pub_key, &member_ctx->commit_values);
    if (kEpidNoErr != result) {
      break;
    }

    member_ctx->rnd_func = rnd_func;
    member_ctx->rnd_param = rnd_param;
    member_ctx->allowed_basenames = NULL;

    if (!CreateStack(sizeof(PreComputedSignature), &member_ctx->presigs)) {
      result = kEpidMemAllocErr;
      break;
    }

    *ctx = member_ctx;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    DeleteFfElement(&member_ctx->ea2);
    DeleteFfElement(&member_ctx->e2w);
    DeleteFfElement(&member_ctx->e22);
    DeleteFfElement(&member_ctx->e12);
    DeleteEpid2Params(&member_ctx->epid2_params);
    DeleteGroupPubKey(&member_ctx->pub_key);
    DeletePrivKey(&member_ctx->priv_key);
    DeleteStack(&member_ctx->presigs);
    SAFE_FREE(member_ctx);
  }

  return (result);
}

void EpidMemberDelete(MemberCtx** ctx) {
  if (ctx && *ctx) {
    DeleteGroupPubKey(&(*ctx)->pub_key);
    DeleteFfElement(&(*ctx)->e12);
    DeleteFfElement(&(*ctx)->e22);
    DeleteFfElement(&(*ctx)->e2w);
    DeleteFfElement(&(*ctx)->ea2);
    DeleteEpid2Params(&(*ctx)->epid2_params);
    DeletePrivKey(&(*ctx)->priv_key);
    DeleteBasenames(&(*ctx)->allowed_basenames);
    DeleteStack(&(*ctx)->presigs);
    SAFE_FREE(*ctx);
  }
}

EpidStatus EpidMemberWritePrecomp(MemberCtx const* ctx,
                                  MemberPrecomp* precomp) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;   // an element in GT
  FfElement* e22 = NULL;   // an element in GT
  FfElement* e2w = NULL;   // an element in GT
  FfElement* ea2 = NULL;   // an element in GT
  FiniteField* GT = NULL;  // Finite field GT(Fq12 )
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (!precomp) {
    return kEpidBadArgErr;
  }
  if (!ctx->e12 || !ctx->e22 || !ctx->e2w || !ctx->ea2 || !ctx->epid2_params ||
      !(ctx->epid2_params->GT)) {
    return kEpidBadArgErr;
  }
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  ea2 = ctx->ea2;
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
  result = WriteFfElement(GT, ea2, &(precomp->ea2), sizeof(precomp->ea2));
  if (kEpidNoErr != result) {
    return result;
  }
  return result;
}

EpidStatus EpidMemberSetHashAlg(MemberCtx* ctx, HashAlg hash_alg) {
  if (!ctx) return kEpidBadArgErr;
  if (kSha256 != hash_alg && kSha384 != hash_alg && kSha512 != hash_alg)
    return kEpidBadArgErr;
  ctx->hash_alg = hash_alg;
  return kEpidNoErr;
}

EpidStatus EpidRegisterBaseName(MemberCtx* ctx, void const* basename,
                                size_t basename_len) {
  EpidStatus result = kEpidErr;
  if (basename_len == 0) {
    return kEpidBadArgErr;
  }
  if (!ctx || !basename) {
    return kEpidBadArgErr;
  }

  if (ContainsBasename(ctx->allowed_basenames, basename, basename_len)) {
    return kEpidDuplicateErr;
  }

  result = AddBasename(&ctx->allowed_basenames, basename, basename_len);

  return result;
}

void DeleteBasenames(AllowedBasename** rootnode) {
  if (rootnode && *rootnode) {
    AllowedBasename* currentnode = *rootnode;
    while (currentnode) {
      AllowedBasename* deletenode = currentnode;
      currentnode = currentnode->next;
      SAFE_FREE(deletenode);
    }
    *rootnode = NULL;
  }
}

EpidStatus AddBasename(AllowedBasename** rootnode, void const* basename,
                       size_t length) {
  EpidStatus result = kEpidErr;
  AllowedBasename* newnode = NULL;
  AllowedBasename* currentnode = NULL;
  if (length > (SIZE_MAX - sizeof(AllowedBasename)) + 1) {
    return kEpidBadArgErr;
  }
  if (!basename) {
    return kEpidBadArgErr;
  }

  newnode = SAFE_ALLOC(sizeof(AllowedBasename) + (length - 1));
  if (!newnode) {
    return kEpidMemAllocErr;
  }
  newnode->next = NULL;
  newnode->length = length;
  // Memory copy is used to copy a flexible array
  if (0 != memcpy_S(newnode->name, length, basename, length)) {
    SAFE_FREE(newnode);
    return kEpidBadArgErr;
  }
  if (*rootnode == NULL) {
    *rootnode = newnode;
    return kEpidNoErr;
  }
  currentnode = *rootnode;
  while (currentnode->next != NULL) {
    currentnode = currentnode->next;
  }
  currentnode->next = newnode;
  result = kEpidNoErr;

  return result;
}

bool ContainsBasename(AllowedBasename const* rootnode, void const* basename,
                      size_t length) {
  if (length != 0) {
    while (rootnode != NULL) {
      if (rootnode->length == length) {
        if (!memcmp(rootnode->name, basename, length)) {
          return true;
        }
      }
      rootnode = rootnode->next;
    }
  }
  return false;
}

EpidStatus EpidAddPreSigs(MemberCtx* ctx, size_t number_presigs,
                          PreComputedSignature* presigs) {
  PreComputedSignature* new_presigs;
  if (!ctx) return kEpidBadArgErr;
  if (!ctx->presigs) return kEpidBadArgErr;

  if (0 == number_presigs) return kEpidNoErr;
  if (number_presigs > SIZE_MAX / sizeof(PreComputedSignature))
    return kEpidBadArgErr;  // integer overflow

  new_presigs =
      (PreComputedSignature*)StackPushN(ctx->presigs, number_presigs, presigs);
  if (!new_presigs) return kEpidMemAllocErr;

  if (presigs) {
    memset(presigs, 0, number_presigs * sizeof(PreComputedSignature));
  } else {
    size_t i;
    for (i = 0; i < number_presigs; i++) {
      EpidStatus sts = EpidComputePreSig(ctx, &new_presigs[i]);
      if (kEpidNoErr != sts) {
        // roll back pre-computed-signature pool
        StackPopN(ctx->presigs, number_presigs, 0);
        return sts;
      }
    }
  }
  return kEpidNoErr;
}

size_t EpidGetNumPreSigs(MemberCtx const* ctx) {
  return (ctx && ctx->presigs) ? StackGetSize(ctx->presigs) : (size_t)0;
}

EpidStatus EpidWritePreSigs(MemberCtx* ctx, PreComputedSignature* presigs,
                            size_t number_presigs) {
  if (!ctx || (!presigs && (0 != number_presigs))) return kEpidBadArgErr;
  if (!ctx->presigs) return kEpidBadArgErr;

  if (0 == number_presigs) return kEpidNoErr;

  return StackPopN(ctx->presigs, number_presigs, presigs) ? kEpidNoErr
                                                          : kEpidBadArgErr;
}

static EpidStatus DoPrecomputation(MemberCtx* ctx) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;
  FfElement* e22 = NULL;
  FfElement* e2w = NULL;
  FfElement* ea2 = NULL;
  Epid2Params_* params = NULL;
  GroupPubKey_* pub_key = NULL;
  PairingState* ps_ctx = NULL;
  if (!ctx) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->epid2_params->GT ||
      !ctx->epid2_params->pairing_state || !ctx->pub_key || !ctx->priv_key ||
      !ctx->e12 || !ctx->e22 || !ctx->e2w || !ctx->ea2) {
    return kEpidBadArgErr;
  }
  pub_key = ctx->pub_key;
  params = ctx->epid2_params;
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  ea2 = ctx->ea2;
  ps_ctx = params->pairing_state;
  // do precomputation
  // 1. The member computes e12 = pairing(h1, g2).
  result = Pairing(ps_ctx, e12, pub_key->h1, params->g2);
  if (kEpidNoErr != result) {
    return result;
  }
  // 2.  The member computes e22 = pairing(h2, g2).
  result = Pairing(ps_ctx, e22, pub_key->h2, params->g2);
  if (kEpidNoErr != result) {
    return result;
  }
  // 3.  The member computes e2w = pairing(h2, w).
  result = Pairing(ps_ctx, e2w, pub_key->h2, pub_key->w);
  if (kEpidNoErr != result) {
    return result;
  }
  // 4.  The member computes ea2 = pairing(A, g2).
  result = Pairing(ps_ctx, ea2, ctx->priv_key->A, params->g2);
  if (kEpidNoErr != result) {
    return result;
  }
  return kEpidNoErr;
}

static EpidStatus ReadPrecomputation(MemberPrecomp const* precomp_str,
                                     MemberCtx* ctx) {
  EpidStatus result = kEpidErr;
  FfElement* e12 = NULL;
  FfElement* e22 = NULL;
  FfElement* e2w = NULL;
  FfElement* ea2 = NULL;
  FiniteField* GT = NULL;
  Epid2Params_* params = NULL;
  if (!ctx || !precomp_str) {
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->epid2_params->GT || !ctx->e12 || !ctx->e22 ||
      !ctx->e2w || !ctx->ea2) {
    return kEpidBadArgErr;
  }
  params = ctx->epid2_params;
  GT = params->GT;
  e12 = ctx->e12;
  e22 = ctx->e22;
  e2w = ctx->e2w;
  ea2 = ctx->ea2;

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
  result = ReadFfElement(GT, &precomp_str->ea2, sizeof(precomp_str->ea2), ea2);
  if (kEpidNoErr != result) {
    return result;
  }
  return kEpidNoErr;
}
