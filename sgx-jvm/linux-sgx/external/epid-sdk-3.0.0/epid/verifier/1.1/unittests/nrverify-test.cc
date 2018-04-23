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
* \brief Epid11NrVerify unit tests.
*/

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/1.1/api.h"
#include "epid/verifier/1.1/src/context.h"
#include "epid/common/1.1/types.h"
}

#include "epid/verifier/1.1/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/1.1/verifier_wrapper-testhelper.h"

namespace {

/////////////////////////////////////////////////////////////////////////
// Simple Errors

TEST_F(Epid11VerifierTest, NrVerifyFailsGivenNullParameters) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  EXPECT_EQ(kEpidBadArgErr,
            Epid11NrVerify(nullptr, &epid_signature->sigma0, this->kMsg0.data(),
                           this->kMsg0.size(), &sig_rl->bk[0],
                           &epid_signature->sigma[0]));

  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11NrVerify(verifier, nullptr, this->kMsg0.data(), this->kMsg0.size(),
                     &sig_rl->bk[0], &epid_signature->sigma[0]));

  EXPECT_EQ(kEpidBadArgErr,
            Epid11NrVerify(verifier, &epid_signature->sigma0, nullptr,
                           this->kMsg0.size(), &sig_rl->bk[0],
                           &epid_signature->sigma[0]));

  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11NrVerify(verifier, &epid_signature->sigma0, this->kMsg0.data(),
                     this->kMsg0.size(), nullptr, &epid_signature->sigma[0]));

  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11NrVerify(verifier, &epid_signature->sigma0, this->kMsg0.data(),
                     this->kMsg0.size(), &sig_rl->bk[0], nullptr));
}

/////////////////////////////////////////////////////////////////////
// Reject
TEST_F(Epid11VerifierTest, NrVerifyRejectsTotalMsgSizeOutOfRangeOfInt) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  // Since before hashing some other data will be concatenated to commit
  // message, passing msg with size==UINT_MAX is causes out of range for
  // this concatenated msg
  Epid11NrProof nr_proof = epid_signature->sigma[0];
  EXPECT_EQ(kEpidBadArgErr, Epid11NrVerify(verifier, &epid_signature->sigma0,
                                           this->kMsg0.data(), 0xffffffff,
                                           &sig_rl->bk[0], &nr_proof));
#if (SIZE_MAX >= 0x100000001)  // When size_t value allowed to be 0x100000001
  EXPECT_EQ(kEpidBadArgErr, Epid11NrVerify(verifier, &epid_signature->sigma0,
                                           this->kMsg0.data(), 0x100000001,
                                           &sig_rl->bk[0], &nr_proof));
#endif
}
TEST_F(Epid11VerifierTest, NrVerifyRejectsSigWithTNotInG3) {
  // 4.2.2 step 2 - The verifier verifies that G3.inGroup(T) = true.
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  Epid11NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.T.x.data.data[0]++;
  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11NrVerify(verifier, &epid_signature->sigma0, this->kMsg0.data(),
                     this->kMsg0.size(), &sig_rl->bk[0], &nr_proof));
}

TEST_F(Epid11VerifierTest, NrVerifyRejectsSigWithTIdentityOfG3) {
  // 4.2.2 step 3 - The verifier verifies that G3.isIdentity(T) = false.
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  Epid11NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.T = this->kG3IdentityStr;
  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11NrVerify(verifier, &epid_signature->sigma0, this->kMsg0.data(),
                     this->kMsg0.size(), &sig_rl->bk[0], &nr_proof));
}

TEST_F(Epid11VerifierTest, NrVerifyRejectsSigWithSmuNotInRange) {
  // 4.2.2 step 4 - The verifier verifies that smu, snu in [0, p'-1].
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  Epid11NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.smu.data = this->kParamsStr.p.data;
  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11NrVerify(verifier, &epid_signature->sigma0, this->kMsg0.data(),
                     this->kMsg0.size(), &sig_rl->bk[0], &nr_proof));
}

TEST_F(Epid11VerifierTest, NrVerifyRejectsSigWithSnuNotInRange) {
  // 4.2.2 step 4 - The verifier verifies that smu, snu in [0, p'-1].
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  Epid11NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.snu.data = this->kParamsStr.p.data;
  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11NrVerify(verifier, &epid_signature->sigma0, this->kMsg0.data(),
                     this->kMsg0.size(), &sig_rl->bk[0], &nr_proof));
}

//   4.2.2 step 5 - The verifier computes nc = (- c) mod p'.
// This Step is not testable

//   4.2.2 step 6 - The verifier computes R1 = G3.multiExp(K, smu, B, snu).
// This Step is not testable

//   4.2.2 step 7 - The verifier computes R2 = G3.multiExp(K', smu, B', snu,
//                  T, nc).
// This Step is not testable

TEST_F(Epid11VerifierTest, NrVerifyRejectsSigWithInvalidCommitment) {
  // 4.2.2 step 8 - The verifier verifies c = Hash(p' || g3 || B || K || B' ||
  //                K' || T || R1 || R2 || mSize || m).
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  std::vector<uint8_t> test_msg = this->kMsg0;
  test_msg[0]++;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11NrVerify(verifier, &epid_signature->sigma0, test_msg.data(),
                           test_msg.size(), &sig_rl->bk[0],
                           &epid_signature->sigma[0]));
}

TEST_F(Epid11VerifierTest, NrVerifyRejectsSigWithMismatchCommitmentSize) {
  // 4.2.2 step 8 - The verifier verifies c = Hash(p' || g3 || B || K || B' ||
  //                K' || T || R1 || R2 || mSize || m).
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  std::vector<uint8_t> test_msg = this->kMsg0;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11NrVerify(verifier, &epid_signature->sigma0, test_msg.data(),
                           test_msg.size() - 1, &sig_rl->bk[0],
                           &epid_signature->sigma[0]));
}
/////////////////////////////////////////////////////////////////////
// Accept
//   4.2.2 step 9 - If all the above verifications succeed, the verifier
//                  outputs true. If any of the above verifications fails,
//                  the verifier aborts and outputs false

TEST_F(Epid11VerifierTest, NrVerifyAcceptsSigWithRandomBaseName) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11Signature const* epid_signature =
      reinterpret_cast<Epid11Signature const*>(
          this->kSigGrpXMember0Sha256RandbaseMsg0N2One.data());
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  EXPECT_EQ(kEpidSigValid,
            Epid11NrVerify(verifier, &epid_signature->sigma0,
                           this->kMsg0.data(), this->kMsg0.size(),
                           &sig_rl->bk[0], &epid_signature->sigma[0]));
}

}  // namespace
