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
 * \brief Verifier C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_VERIFIER_WRAPPER_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_VERIFIER_WRAPPER_TESTHELPER_H_

extern "C" {
#include "epid/verifier/api.h"
}

/// C++ Wrapper to manage memory for VerifierCtx via RAII
class VerifierCtxObj {
 public:
  /// Create a VerifierCtx
  explicit VerifierCtxObj(GroupPubKey const& pub_key);
  /// Create a VerifierCtx given precomputation blob
  VerifierCtxObj(GroupPubKey const& pub_key, VerifierPrecomp const& precomp);

  // This class instances are not meant to be copied.
  // Explicitly delete copy constructor and assignment operator.
  VerifierCtxObj(const VerifierCtxObj&) = delete;
  VerifierCtxObj& operator=(const VerifierCtxObj&) = delete;

  /// Destroy the VerifierCtx
  ~VerifierCtxObj();
  /// get a pointer to the stored VerifierCtx
  VerifierCtx* ctx() const;
  /// cast operator to get the pointer to the stored VerifierCtx
  operator VerifierCtx*() const;
  /// const cast operator to get the pointer to the stored VerifierCtx
  operator const VerifierCtx*() const;

 private:
  /// The stored VerifierCtx
  VerifierCtx* ctx_;
};

#endif  // EPID_COMMON_TESTHELPER_VERIFIER_WRAPPER_TESTHELPER_H_
