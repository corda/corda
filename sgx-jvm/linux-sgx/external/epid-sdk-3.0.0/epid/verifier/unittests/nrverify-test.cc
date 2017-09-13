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
 * \brief NrVerify unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/api.h"
}

#include "epid/verifier/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/verifier_wrapper-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"

namespace {

/////////////////////////////////////////////////////////////////////////
// Simple Errors

TEST_F(EpidVerifierTest, NrVerifyFailsGivenNullParameters) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(nullptr, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0],
                         &epid_signature->sigma[0]));

  EXPECT_EQ(kEpidBadArgErr, EpidNrVerify(verifier, nullptr, this->kTest0.data(),
                                         this->kTest0.size(), &sig_rl->bk[0],
                                         &epid_signature->sigma[0]));

  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature->sigma0, nullptr,
                         this->kTest0.size(), &sig_rl->bk[0],
                         &epid_signature->sigma[0]));

  EXPECT_EQ(
      kEpidBadArgErr,
      EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                   this->kTest0.size(), nullptr, &epid_signature->sigma[0]));

  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0], nullptr));
}

/////////////////////////////////////////////////////////////////////
// Reject

TEST_F(EpidVerifierTest, NrVerifyRejectsSigWithTNotInG1) {
  // * 4.2.2 step 1 - The verifier verifies that G1.inGroup(T) = true.
  // result must be kEpidBadArgErr
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.T.x.data.data[0]++;
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0], &nr_proof));
}

TEST_F(EpidVerifierTest, NrVerifyRejectsSigWithTIdentityOfG1) {
  // * 4.2.2 step 2 - The verifier verifies that G1.isIdentity(T) = false.
  // result must be kEpidBadArgErr
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.T = this->kG1IdentityStr;
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0], &nr_proof));
}

TEST_F(EpidVerifierTest, NrVerifyRejectsSigWithCNotInRange) {
  // * 4.2.2 step 3 - The verifier verifies that c, smu, snu in [0, p-1].
  // result must be kEpidBadArgErr
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.c.data = this->kParamsStr.p.data;
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0], &nr_proof));
}

TEST_F(EpidVerifierTest, NrVerifyRejectsSigWithSmuNotInRange) {
  // * 4.2.2 step 3 - The verifier verifies that c, smu, snu in [0, p-1].
  // result must be kEpidBadArgErr
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.smu.data = this->kParamsStr.p.data;
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0], &nr_proof));
}

TEST_F(EpidVerifierTest, NrVerifyRejectsSigWithSnuNotInRange) {
  // * 4.2.2 step 3 - The verifier verifies that c, smu, snu in [0, p-1].
  // result must be kEpidBadArgErr
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  NrProof nr_proof = epid_signature->sigma[0];
  nr_proof.snu.data = this->kParamsStr.p.data;
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0], &nr_proof));
}

//   4.2.2 step 4 - The verifier computes nc = (- c) mod p.
// This Step is not testable

//   4.2.2 step 5 - The verifier computes R1 = G1.multiExp(K, smu, B, snu).
// This Step is not testable

//   4.2.2 step 6 - The verifier computes R2 = G1.multiExp(K', smu, B', snu,
//                  T, nc).
// This Step is not testable

TEST_F(EpidVerifierTest, NrVerifyRejectsSigWithInvalidCommitment) {
  // * 4.2.2 step 7 - The verifier verifies c = Fp.hash(p || g1 || B || K ||
  //                  B' || K' || T || R1 || R2 || m).
  //                  Refer to Section 7.1 for hash operation over a
  //                  prime field.
  // result must be kEpidBadArgErr
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  std::vector<uint8_t> test_msg = this->kTest0;
  test_msg[0]++;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidNrVerify(verifier, &epid_signature->sigma0, test_msg.data(),
                   test_msg.size(), &sig_rl->bk[0], &epid_signature->sigma[0]));
}

TEST_F(EpidVerifierTest, NrVerifyRejectsSigWithValidCommitmentDiffHashAlg) {
  // * 4.2.2 step 7 - The verifier verifies c = Fp.hash(p || g1 || B || K ||
  //                  B' || K' || T || R1 || R2 || m).
  //                  Refer to Section 7.1 for hash operation over a
  //                  prime field.
  // result must be kEpidBadArgErr
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature_sha256 =
      reinterpret_cast<EpidSignature const*>(
          this->kSigGrp01Member0Sha256RandombaseTest0.data());
  EpidSignature const* epid_signature_sha384 =
      reinterpret_cast<EpidSignature const*>(
          this->kSigGrp01Member0Sha384RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha384));
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature_sha256->sigma0,
                         this->kTest0.data(), this->kTest0.size(),
                         &sig_rl->bk[0], &epid_signature_sha256->sigma[0]));
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512));
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrVerify(verifier, &epid_signature_sha384->sigma0,
                         this->kTest0.data(), this->kTest0.size(),
                         &sig_rl->bk[0], &epid_signature_sha384->sigma[0]));
}

/////////////////////////////////////////////////////////////////////
// Accept
//   4.2.2 step 8 - If all the above verifications succeed, the verifier
//                  outputs true. If any of the above verifications fails,
//                  the verifier aborts and outputs false

TEST_F(EpidVerifierTest, NrVerifyAcceptsSigWithRandomBaseNameSha256) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha256RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  EXPECT_EQ(kEpidSigValid,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0],
                         &epid_signature->sigma[0]));
}

TEST_F(EpidVerifierTest,
       NrVerifyAcceptsSigWithRandomBaseNameSha256UsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyIkgfStr);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigMember0Sha256RandombaseMsg0Ikgf.data());
  SigRl const* sig_rl = reinterpret_cast<SigRl const*>(this->kSigRlIkgf.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  EXPECT_EQ(kEpidSigValid,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kMsg0.data(),
                         this->kMsg0.size(), &sig_rl->bk[2],
                         &epid_signature->sigma[2]));
}

TEST_F(EpidVerifierTest, NrVerifyAcceptsSigWithRandomBaseNameSha384) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha384RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha384));
  EXPECT_EQ(kEpidSigValid,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0],
                         &epid_signature->sigma[0]));
}

TEST_F(EpidVerifierTest, NrVerifyAcceptsSigWithRandomBaseNameSha512) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha512RandombaseTest0.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512));
  EXPECT_EQ(kEpidSigValid,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest0.data(),
                         this->kTest0.size(), &sig_rl->bk[0],
                         &epid_signature->sigma[0]));
}

TEST_F(EpidVerifierTest,
       DISABLED_NrVerifyAcceptsSigWithRandomBaseNameSha512256) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidSignature const* epid_signature = reinterpret_cast<EpidSignature const*>(
      this->kSigGrp01Member0Sha512256RandombaseTest1.data());
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512_256));
  EXPECT_EQ(kEpidSigValid,
            EpidNrVerify(verifier, &epid_signature->sigma0, this->kTest1.data(),
                         this->kTest1.size(), &sig_rl->bk[0],
                         &epid_signature->sigma[0]));
}

}  // namespace
