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
 * \brief Epid11AreSigsLinkable unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/1.1/api.h"
}

#include "epid/verifier/1.1/unittests/verifier-testhelper.h"

namespace {

TEST_F(Epid11VerifierTest, AreSigsLinkedReturnsFalseGivenNullParameters) {
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  EXPECT_FALSE(Epid11AreSigsLinked(nullptr, nullptr));
  EXPECT_FALSE(
      Epid11AreSigsLinked((Epid11BasicSignature const*)sig.data(), nullptr));
  EXPECT_FALSE(
      Epid11AreSigsLinked(nullptr, (Epid11BasicSignature const*)sig.data()));
}

TEST_F(Epid11VerifierTest, SigsBySameMemberWithRandomBaseAreNotLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256RandbaseMsg0;
  auto& sig2 = this->kSigGrpXMember0Sha256RandbaseMsg1;
  EXPECT_FALSE(Epid11AreSigsLinked((Epid11BasicSignature const*)sig1.data(),
                                   (Epid11BasicSignature const*)sig2.data()));
}

TEST_F(Epid11VerifierTest, SigsBySameMemberWithSameBasenameAreLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& sig2 = this->kSigGrpXMember0Sha256Bsn0Msg1;
  EXPECT_TRUE(Epid11AreSigsLinked((Epid11BasicSignature const*)sig1.data(),
                                  (Epid11BasicSignature const*)sig2.data()));
}

TEST_F(Epid11VerifierTest,
       SigsBySameMemberWithDifferentBasenameAreNotLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& sig2 = this->kSigGrpXMember0Sha256Bsn1Msg0;
  EXPECT_FALSE(Epid11AreSigsLinked((Epid11BasicSignature const*)sig1.data(),
                                   (Epid11BasicSignature const*)sig2.data()));
}

TEST_F(Epid11VerifierTest,
       SigsByDifferentMembersWithSameBasenameAreNotLinkable) {
  auto& sig1 = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& sig2 = this->kSigGrpXMember1Sha256Bsn0Msg0;
  EXPECT_FALSE(Epid11AreSigsLinked((Epid11BasicSignature const*)sig1.data(),
                                   (Epid11BasicSignature const*)sig2.data()));
}

}  // namespace
