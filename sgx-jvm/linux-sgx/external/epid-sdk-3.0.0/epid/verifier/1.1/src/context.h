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
#ifndef EPID_VERIFIER_1_1_SRC_CONTEXT_H_
#define EPID_VERIFIER_1_1_SRC_CONTEXT_H_
/*!
 * \file
 * \brief Intel(R) EPID 1.1 Verifier context interface.
 */
#include "epid/common/1.1/src/grouppubkey.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/1.1/src/epid11params.h"
#include "epid/common/1.1/src/commitment.h"

/// Verifier context definition
struct Epid11VerifierCtx {
  Epid11GroupPubKey_* pub_key;  ///< Group public key
  /// Verifier pre-computation
  FfElement* e12;  ///< an element in GT
  FfElement* e22;  ///< an element in GT
  FfElement* e2w;  ///< an element in GT
  /// Revocation lists
  Epid11PrivRl const* priv_rl;    ///< Private key based RL - not owned
  Epid11SigRl const* sig_rl;      ///< Signature based RL - not owned
  Epid11GroupRl const* group_rl;  ///< Group RL - not owned

  Epid11Params_* epid11_params;      ///< Intel(R) EPID 1.1 params
  Epid11CommitValues commit_values;  ///< Hashed values to create commitment
  uint8_t* basename;                 ///< Basename to use - NULL for random base
  size_t basename_len;               ///< Number of bytes in basename
  EcPoint* basename_hash;            ///< Epid11EcHash of the basename
};

#endif  // EPID_VERIFIER_1_1_SRC_CONTEXT_H_
