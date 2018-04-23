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
 * \brief Epid11VerifyBasicSig unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/1.1/api.h"
}

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/verifier/1.1/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/1.1/verifier_wrapper-testhelper.h"

namespace {

TEST_F(Epid11VerifierTest, VerifyBasicSigFailsGivenNullPtr) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto const& sig =
      (Epid11Signature const*)this->kSigGrpXMember0Sha256RandbaseMsg0.data();
  const Epid11BasicSignature basic_sig = sig->sigma0;
  auto& msg = this->kMsg0;

  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifyBasicSig(nullptr, &basic_sig, msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifyBasicSig(verifier, nullptr, msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifyBasicSig(verifier, &basic_sig, nullptr, msg.size()));
}

TEST_F(Epid11VerifierTest,
       VerifyBasicSigCanVerifyValidSignatureWithSHA256AsDefault) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto const& sig =
      (Epid11Signature const*)this->kSigGrpXMember0Sha256RandbaseMsg0.data();
  const Epid11BasicSignature basic_sig = sig->sigma0;
  auto& msg = this->kMsg0;

  EXPECT_EQ(kEpidNoErr,
            Epid11VerifyBasicSig(verifier, &basic_sig, msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest,
       VerifyBasicSigDetectsInvalidSignatureGivenMatchingMessage) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto const& sig =
      (Epid11Signature const*)this->kSigGrpXMember0Sha256RandbaseMsg0.data();
  const Epid11BasicSignature basic_sig = sig->sigma0;
  auto& msg = this->kMsg0;
  Epid11BasicSignature corrupted_basic_sig = basic_sig;
  corrupted_basic_sig.B.x.data.data[0]++;
  EXPECT_NE(kEpidNoErr, Epid11VerifyBasicSig(verifier, &corrupted_basic_sig,
                                             msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest,
       VerifyBasicSigDetectsInvalidSignatureGivenMessageMismatch) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto const& sig =
      (Epid11Signature const*)this->kSigGrpXMember0Sha256RandbaseMsg0.data();
  const Epid11BasicSignature basic_sig = sig->sigma0;
  auto msg = this->kMsg0;
  msg[0]++;  // change message for signature verification to fail
  EXPECT_EQ(kEpidSigInvalid,
            Epid11VerifyBasicSig(verifier, &basic_sig, msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyBasicSigCanVerifyWithBasename) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto const& sig =
      (Epid11Signature const*)this->kSigGrpXMember0Sha256Bsn0Msg0.data();
  const Epid11BasicSignature basic_sig = sig->sigma0;
  auto& msg = this->kMsg0;
  auto& basename = this->kBsn0;
  THROW_ON_EPIDERR(
      Epid11VerifierSetBasename(verifier, basename.data(), basename.size()));
  EXPECT_EQ(kEpidNoErr,
            Epid11VerifyBasicSig(verifier, &basic_sig, msg.data(), msg.size()));
}

}  // namespace
