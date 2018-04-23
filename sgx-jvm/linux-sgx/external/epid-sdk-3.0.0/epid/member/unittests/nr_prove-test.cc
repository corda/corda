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
 * \brief NrProve unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
#include "epid/verifier/api.h"
}

#include "epid/member/unittests/member-testhelper.h"
#include "epid/common-testhelper/prng-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/verifier_wrapper-testhelper.h"

namespace {

TEST_F(EpidMemberTest, NrProveFailsGivenNullParameters) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature const* basic_sig =
      &reinterpret_cast<EpidSignature const*>(
           this->kGrp01Member0SigTest1Sha256.data())
           ->sigma0;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  EXPECT_EQ(kEpidBadArgErr, EpidNrProve(nullptr, msg.data(), msg.size(),
                                        basic_sig, &sig_rl->bk[0], &proof));
  EXPECT_EQ(kEpidBadArgErr, EpidNrProve(member, nullptr, msg.size(), basic_sig,
                                        &sig_rl->bk[0], &proof));
  EXPECT_EQ(kEpidBadArgErr, EpidNrProve(member, msg.data(), msg.size(), nullptr,
                                        &sig_rl->bk[0], &proof));
  EXPECT_EQ(kEpidBadArgErr, EpidNrProve(member, msg.data(), msg.size(),
                                        basic_sig, nullptr, &proof));
  EXPECT_EQ(kEpidBadArgErr, EpidNrProve(member, msg.data(), msg.size(),
                                        basic_sig, &sig_rl->bk[0], nullptr));
}

TEST_F(EpidMemberTest, NrProveFailsGivenInvalidSigRlEntry) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature const* basic_sig =
      &reinterpret_cast<EpidSignature const*>(
           this->kGrp01Member0SigTest1Sha256.data())
           ->sigma0;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  SigRlEntry sig_rl_enty_invalid_k = sig_rl->bk[0];
  sig_rl_enty_invalid_k.k.x.data.data[31]++;  // make it not in EC group
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrProve(member, msg.data(), msg.size(), basic_sig,
                        &sig_rl_enty_invalid_k, &proof));

  SigRlEntry sig_rl_enty_invalid_b = sig_rl->bk[0];
  sig_rl_enty_invalid_b.b.x.data.data[31]++;  // make it not in EC group
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrProve(member, msg.data(), msg.size(), basic_sig,
                        &sig_rl_enty_invalid_b, &proof));
}

TEST_F(EpidMemberTest, NrProveFailsGivenInvalidBasicSig) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature const* basic_sig =
      &reinterpret_cast<EpidSignature const*>(
           this->kGrp01Member0SigTest1Sha256.data())
           ->sigma0;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  // invalid basic sig in this case is invalid B or K value only!!
  BasicSignature basic_sig_invalid_B = *basic_sig;
  basic_sig_invalid_B.B.x.data.data[31]++;  // make it not in EC group;
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrProve(member, msg.data(), msg.size(), &basic_sig_invalid_B,
                        &sig_rl->bk[0], &proof));

  BasicSignature basic_sig_invalid_K = *basic_sig;
  basic_sig_invalid_K.K.x.data.data[31]++;  // make it not in EC group
  EXPECT_EQ(kEpidBadArgErr,
            EpidNrProve(member, msg.data(), msg.size(), &basic_sig_invalid_K,
                        &sig_rl->bk[0], &proof));
}

TEST_F(EpidMemberTest, GeneratesNrProofForEmptyMessage) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature basic_sig;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha256));
  ASSERT_EQ(kEpidNoErr,
            EpidSignBasic(member, nullptr, 0, nullptr, 0, &basic_sig));
  EXPECT_EQ(kEpidNoErr, EpidNrProve(member, nullptr, 0, &basic_sig,
                                    &sig_rl->bk[0], &proof));

  // Check proof by doing an NrVerify
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha256));
  EXPECT_EQ(kEpidNoErr,
            EpidNrVerify(ctx, &basic_sig, nullptr, 0, &sig_rl->bk[0], &proof));
}

TEST_F(EpidMemberTest, GeneratesNrProofUsingDefaultHashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature basic_sig;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  EXPECT_EQ(kEpidNoErr, EpidNrProve(member, msg.data(), msg.size(), &basic_sig,
                                    &sig_rl->bk[0], &proof));

  // Check proof by doing an NrVerify
  VerifierCtxObj ctx(this->kGroupPublicKey);

  EXPECT_EQ(kEpidNoErr, EpidNrVerify(ctx, &basic_sig, msg.data(), msg.size(),
                                     &sig_rl->bk[0], &proof));
}

TEST_F(EpidMemberTest, GeneratesNrProofUsingDefaultHashAlgUsingIKGFData) {
  Prng my_prng;
  GroupPubKey grp_public_key = *reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  PrivKey mbr_private_key =
      *reinterpret_cast<const PrivKey*>(this->kMemberPrivateKeyDataIkgf.data());
  const std::vector<uint8_t> sigrl_bin = {
#include "epid/common-testhelper/testdata/ikgf/groupa/sigrl.inc"
  };

  MemberCtxObj member(grp_public_key, mbr_private_key, &Prng::Generate,
                      &my_prng);

  BasicSignature basic_sig;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(sigrl_bin.data());

  NrProof proof;

  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  EXPECT_EQ(kEpidNoErr, EpidNrProve(member, msg.data(), msg.size(), &basic_sig,
                                    &sig_rl->bk[0], &proof));

  // Check proof by doing an NrVerify
  VerifierCtxObj ctx(grp_public_key);

  EXPECT_EQ(kEpidNoErr, EpidNrVerify(ctx, &basic_sig, msg.data(), msg.size(),
                                     &sig_rl->bk[0], &proof));
}

TEST_F(EpidMemberTest, GeneratesNrProofUsingSha256HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature basic_sig;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha256));
  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  EXPECT_EQ(kEpidNoErr, EpidNrProve(member, msg.data(), msg.size(), &basic_sig,
                                    &sig_rl->bk[0], &proof));

  // Check proof by doing an NrVerify
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha256));
  EXPECT_EQ(kEpidNoErr, EpidNrVerify(ctx, &basic_sig, msg.data(), msg.size(),
                                     &sig_rl->bk[0], &proof));
}

TEST_F(EpidMemberTest, GeneratesNrProofUsingSha384HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature basic_sig;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha384));
  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  EXPECT_EQ(kEpidNoErr, EpidNrProve(member, msg.data(), msg.size(), &basic_sig,
                                    &sig_rl->bk[0], &proof));

  // Check proof by doing an NrVerify
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha384));
  EXPECT_EQ(kEpidNoErr, EpidNrVerify(ctx, &basic_sig, msg.data(), msg.size(),
                                     &sig_rl->bk[0], &proof));
}

TEST_F(EpidMemberTest, GeneratesNrProofUsingSha512HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature basic_sig;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha512));
  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  EXPECT_EQ(kEpidNoErr, EpidNrProve(member, msg.data(), msg.size(), &basic_sig,
                                    &sig_rl->bk[0], &proof));

  // Check proof by doing an NrVerify
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha512));
  EXPECT_EQ(kEpidNoErr, EpidNrVerify(ctx, &basic_sig, msg.data(), msg.size(),
                                     &sig_rl->bk[0], &proof));
}

TEST_F(EpidMemberTest, DISABLED_GeneratesNrProofUsingSha512256HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  BasicSignature basic_sig;
  auto msg = this->kTest1Msg;
  SigRl const* sig_rl = reinterpret_cast<const SigRl*>(this->kSigRlData.data());

  NrProof proof;

  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha512_256));
  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  EXPECT_EQ(kEpidNoErr, EpidNrProve(member, msg.data(), msg.size(), &basic_sig,
                                    &sig_rl->bk[0], &proof));

  // Check proof by doing an NrVerify
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha512_256));
  EXPECT_EQ(kEpidNoErr, EpidNrVerify(ctx, &basic_sig, msg.data(), msg.size(),
                                     &sig_rl->bk[0], &proof));
}

}  // namespace
