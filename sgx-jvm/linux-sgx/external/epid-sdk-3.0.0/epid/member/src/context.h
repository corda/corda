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
#ifndef EPID_MEMBER_SRC_CONTEXT_H_
#define EPID_MEMBER_SRC_CONTEXT_H_
/*!
 * \file
 * \brief Member context interface.
 */

#include <stddef.h>
#include "epid/member/api.h"
#include "epid/common/errors.h"
#include "epid/common/src/epid2params.h"
#include "epid/common/src/grouppubkey.h"
#include "epid/common/src/stack.h"
#include "epid/common/src/commitment.h"
#include "epid/member/src/privkey.h"

/// Internal implementation of base name
typedef struct AllowedBasename {
  struct AllowedBasename* next;  ///< pointer to the next base name
  size_t length;                 ///< size of base name
  uint8_t name[1];               ///< base name (flexible array)
} AllowedBasename;

/// Member context definition
struct MemberCtx {
  GroupPubKey_* pub_key;       ///< group public key
  FfElement* e12;              ///< an element in GT
  FfElement* e22;              ///< an element in GT
  FfElement* e2w;              ///< an element in GT
  FfElement* ea2;              ///< an element in GT
  Epid2Params_* epid2_params;  ///< Intel(R) EPID 2.0 params
  PrivKey_* priv_key;          ///< Member private key

  BitSupplier rnd_func;  ///< Pseudo random number generation function
  void* rnd_param;       ///< Pointer to user context for rnd_func
  HashAlg hash_alg;      ///< Hash algorithm to use
  AllowedBasename* allowed_basenames;  ///< Base name list
  Stack* presigs;                      ///< Pre-computed signatures pool
  CommitValues commit_values;  ///< Values that are hashed to create commitment
};

/// Delete base name list
void DeleteBasenames(AllowedBasename** rootnode);

/// Add new base name to list
EpidStatus AddBasename(AllowedBasename** rootnode, void const* basename,
                       size_t length);

/// Check if given base name exist in the list
bool ContainsBasename(AllowedBasename const* rootnode, void const* basename,
                      size_t length);

/// Performs Pre-computation that can be used to speed up signing
/*!
 \warning
 Do not re-use the same pre-computed signature to generate more than
 one signature. If a pre-computed signature is used for computing
 two signatures, an attacker could learn the Intel(R) EPID private key.

 \param[in] ctx
 The member context.
 \param[out] precompsig
 The pre-computed signature.

 \returns ::EpidStatus
 */
EpidStatus EpidComputePreSig(MemberCtx const* ctx,
                             PreComputedSignature* precompsig);

#endif  // EPID_MEMBER_SRC_CONTEXT_H_
