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
 * \brief Verifier C++ wrapper implementation.
 */
#include <cstdio>
#include <string>
#include <stdexcept>

#include "epid/common-testhelper/verifier_wrapper-testhelper.h"

VerifierCtxObj::VerifierCtxObj(GroupPubKey const& pub_key) : ctx_(nullptr) {
  auto sts = EpidVerifierCreate(&pub_key, nullptr, &ctx_);
  if (kEpidNoErr != sts) {
    printf("%s(%d): %s\n", __FILE__, __LINE__, "test defect:");
    throw std::logic_error(std::string("Failed to call: ") +
                           "EpidVerifierCreate()");
  }
}

VerifierCtxObj::VerifierCtxObj(GroupPubKey const& pub_key,
                               VerifierPrecomp const& precomp)
    : ctx_(nullptr) {
  auto sts = EpidVerifierCreate(&pub_key, &precomp, &ctx_);
  if (kEpidNoErr != sts) {
    printf("%s(%d): %s\n", __FILE__, __LINE__, "test defect:");
    throw std::logic_error(std::string("Failed to call: ") +
                           "EpidVerifierCreate()");
  }
}

VerifierCtxObj::~VerifierCtxObj() { EpidVerifierDelete(&ctx_); }

VerifierCtx* VerifierCtxObj::ctx() const { return ctx_; }

VerifierCtxObj::operator VerifierCtx*() const { return ctx_; }

VerifierCtxObj::operator const VerifierCtx*() const { return ctx_; }
