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
#ifndef EPID_VERIFIER_SRC_CONTEXT_H_
#define EPID_VERIFIER_SRC_CONTEXT_H_
/*!
 * \file
 * \brief Verifier context interface.
 */
#include "epid/common/src/grouppubkey.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/src/epid2params.h"
#include "epid/common/src/commitment.h"

/// Verifier context definition
struct VerifierCtx {
  GroupPubKey_* pub_key;    ///< group public key
  FfElement* e12;           ///< an element in GT
  FfElement* e22;           ///< an element in GT
  FfElement* e2w;           ///< an element in GT
  FfElement* eg12;          ///< an element in GT
  PrivRl const* priv_rl;    ///< Private key based revocation list - not owned
  SigRl const* sig_rl;      ///< Signature based revocation list - not owned
  GroupRl const* group_rl;  ///< Group revocation list - not owned
  VerifierRl* verifier_rl;  ///< Verifier revocation list
  bool was_verifier_rl_updated;  ///< Indicates if blacklist was updated
  Epid2Params_* epid2_params;    ///< Intel(R) EPID 2.0 params
  CommitValues commit_values;  ///< Values that are hashed to create commitment
  HashAlg hash_alg;            ///< Hash algorithm to use
  EcPoint* basename_hash;      ///< EcHash of the basename (NULL = random base)
  uint8_t* basename;           ///< Basename to use
  size_t basename_len;         ///< Number of bytes in basename
};
#endif  // EPID_VERIFIER_SRC_CONTEXT_H_
