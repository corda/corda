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
 * \brief AreSigsLinkable unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/api.h"
}

#include "epid/verifier/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/verifier_wrapper-testhelper.h"

namespace {

TEST_F(EpidVerifierTest, AreSigsLinkedReturnsFalseGivenNullParameters) {
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  EXPECT_FALSE(EpidAreSigsLinked(nullptr, nullptr));
  EXPECT_FALSE(EpidAreSigsLinked((BasicSignature const*)sig.data(), nullptr));
  EXPECT_FALSE(EpidAreSigsLinked(nullptr, (BasicSignature const*)sig.data()));
}

TEST_F(EpidVerifierTest, SigsBySameMemberWithRandomBaseAreNotLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256RandbaseMsg0;
  auto& sig2 = this->kSigGrpXMember0Sha256RandbaseMsg1;
  EXPECT_FALSE(EpidAreSigsLinked((BasicSignature const*)sig1.data(),
                                 (BasicSignature const*)sig2.data()));
}

TEST_F(EpidVerifierTest, SigsBySameMemberWithSameBasenameAreLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& sig2 = this->kSigGrpXMember0Sha256Bsn0Msg1;
  EXPECT_TRUE(EpidAreSigsLinked((BasicSignature const*)sig1.data(),
                                (BasicSignature const*)sig2.data()));
}

TEST_F(EpidVerifierTest, SigsBySameMemberWithDifferentBasenameAreNotLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& sig2 = this->kSigGrpXMember0Sha256Bsn1Msg0;
  EXPECT_FALSE(EpidAreSigsLinked((BasicSignature const*)sig1.data(),
                                 (BasicSignature const*)sig2.data()));
}

TEST_F(EpidVerifierTest, SigsByDifferentMembersWithSameBasenameAreNotLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& sig2 = this->kSigGrpXMember1Sha256Bsn0Msg0;
  EXPECT_FALSE(EpidAreSigsLinked((BasicSignature const*)sig1.data(),
                                 (BasicSignature const*)sig2.data()));
}

}  // namespace
