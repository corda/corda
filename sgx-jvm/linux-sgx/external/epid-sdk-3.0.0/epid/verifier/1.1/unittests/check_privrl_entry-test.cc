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
 * \brief Epid11CheckPrivRlEntry unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/1.1/api.h"
}

#include "epid/verifier/1.1/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/1.1/verifier_wrapper-testhelper.h"

namespace {

TEST_F(Epid11VerifierTest, CheckPrivRlEntryFailsGivenNullPtr) {
  // check ctx, sig, f for NULL
  auto& pub_key = this->kPubKeyStr;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  FpElemStr fp_str = ((Epid11PrivRl const*)priv_rl.data())->f[0];
  Epid11BasicSignature basic_signature =
      ((Epid11Signature const*)sig.data())->sigma0;

  EXPECT_EQ(kEpidBadArgErr,
            Epid11CheckPrivRlEntry(nullptr, &basic_signature, &fp_str));
  EXPECT_EQ(kEpidBadArgErr, Epid11CheckPrivRlEntry(verifier, nullptr, &fp_str));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11CheckPrivRlEntry(verifier, &basic_signature, nullptr));
}

TEST_F(Epid11VerifierTest, CheckPrivRlEntryFailsGivenRevokedPrivKey) {
  // test a revoked priv key
  // check ctx, sig, f for NULL
  auto& pub_key = this->kPubKeyStr;
  auto& priv_rl = this->kGrpXPrivRl;
  // signed using revoked key
  auto& sig = this->kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  FpElemStr fp_str = ((Epid11PrivRl const*)priv_rl.data())->f[0];
  Epid11BasicSignature basic_signature =
      ((Epid11Signature const*)sig.data())->sigma0;

  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            Epid11CheckPrivRlEntry(verifier, &basic_signature, &fp_str));
}

TEST_F(Epid11VerifierTest, CheckPrivRlEntrySucceedsGivenUnRevokedPrivKey) {
  // test a non revoked priv key
  auto& pub_key = this->kPubKeyStr;
  auto& priv_rl = this->kGrpXPrivRl;
  // signed using un revoked key
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  FpElemStr fp_str = ((Epid11PrivRl const*)priv_rl.data())->f[0];
  Epid11BasicSignature basic_signature =
      ((Epid11Signature const*)sig.data())->sigma0;

  EXPECT_EQ(kEpidNoErr,
            Epid11CheckPrivRlEntry(verifier, &basic_signature, &fp_str));
}
}  // namespace
