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
 * \brief Sign unit tests.
 */
#include <vector>
#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
#include "epid/member/src/context.h"
#include "epid/verifier/api.h"
}

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/prng-testhelper.h"
#include "epid/member/unittests/member-testhelper.h"
#include "epid/common-testhelper/verifier_wrapper-testhelper.h"
namespace {

/// Count of elements in array
#define COUNT_OF(A) (sizeof(A) / sizeof((A)[0]))

/////////////////////////////////////////////////////////////////////////
// Simple error cases

TEST_F(EpidMemberTest, SignFailsGivenNullParameters) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kGroupPublicKey.gid;
  std::vector<uint8_t> sig(EpidGetSigSize(&srl));
  EXPECT_EQ(kEpidBadArgErr, EpidSign(nullptr, msg.data(), msg.size(),
                                     bsn.data(), bsn.size(), &srl, sizeof(srl),
                                     (EpidSignature*)sig.data(), sig.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidSign(member, msg.data(), msg.size(), bsn.data(), bsn.size(),
                     &srl, sizeof(srl), nullptr, sig.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidSign(member, nullptr, msg.size(), bsn.data(), bsn.size(), &srl,
                     sizeof(srl), (EpidSignature*)sig.data(), sig.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidSign(member, msg.data(), msg.size(), nullptr, bsn.size(), &srl,
                     sizeof(srl), (EpidSignature*)sig.data(), sig.size()));
}

TEST_F(EpidMemberTest, SignFailsGivenWrongSigLen) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kGroupPublicKey.gid;

  // signature buffer one byte less than needed
  std::vector<uint8_t> sig_small(EpidGetSigSize(&srl) - 1);
  EXPECT_EQ(kEpidBadArgErr,
            EpidSign(member, msg.data(), msg.size(), bsn.data(), bsn.size(),
                     &srl, sizeof(srl), (EpidSignature*)sig_small.data(),
                     sig_small.size()));

  // signature buffer is one byte - a less than allowed for EpidSignature
  std::vector<uint8_t> sig_one(1);
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidSign(member, msg.data(), msg.size(), bsn.data(), bsn.size(), &srl,
               sizeof(srl), (EpidSignature*)sig_one.data(), sig_one.size()));
}

TEST_F(EpidMemberTest, SignFailsGivenWrongSigRlLen) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kGroupPublicKey.gid;

  std::vector<uint8_t> sig(EpidGetSigSize(&srl));
  std::vector<uint8_t> srl_reduced(1);
  EXPECT_EQ(kEpidBadArgErr,
            EpidSign(member, msg.data(), msg.size(), bsn.data(), bsn.size(),
                     (SigRl*)srl_reduced.data(), srl_reduced.size(),
                     (EpidSignature*)sig.data(), sig.size()));
}

TEST_F(EpidMemberTest, SignFailsGivenUnregisteredBasename) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& bsn1 = this->kBsn1;
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kGroupPublicKey.gid;
  std::vector<uint8_t> sig(EpidGetSigSize(&srl));
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidSign(member, msg.data(), msg.size(), bsn1.data(), bsn1.size(), &srl,
               sizeof(srl), (EpidSignature*)sig.data(), sig.size()));
}

/////////////////////////////////////////////////////////////////////////
// Anonymity

TEST_F(EpidMemberTest, SignaturesOfSameMessageAreDifferent) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig1(EpidGetSigSize(nullptr));
  std::vector<uint8_t> sig2(EpidGetSigSize(nullptr));
  // without signature based revocation list
  EXPECT_EQ(kEpidNoErr,
            EpidSign(member, msg.data(), msg.size(), nullptr, 0, nullptr, 0,
                     (EpidSignature*)sig1.data(), sig1.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidSign(member, msg.data(), msg.size(), nullptr, 0, nullptr, 0,
                     (EpidSignature*)sig2.data(), sig2.size()));
  EXPECT_TRUE(sig1.size() == sig2.size() &&
              0 != memcmp(sig1.data(), sig2.data(), sig1.size()));
  // with signature based revocation list
  uint8_t sig_rl_data_n2_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x01,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x01,
      // one bk
      0x9c, 0xa5, 0xe5, 0xae, 0x5f, 0xae, 0x51, 0x59, 0x33, 0x35, 0x27, 0xd,
      0x8, 0xb1, 0xbe, 0x5d, 0x69, 0x50, 0x84, 0xc5, 0xfe, 0xe2, 0x87, 0xea,
      0x2e, 0xef, 0xfa, 0xee, 0x67, 0xf2, 0xd8, 0x28, 0x56, 0x43, 0xc6, 0x94,
      0x67, 0xa6, 0x72, 0xf6, 0x41, 0x15, 0x4, 0x58, 0x42, 0x16, 0x88, 0x57,
      0x9d, 0xc7, 0x71, 0xd1, 0xc, 0x84, 0x13, 0xa, 0x90, 0x23, 0x18, 0x8, 0xad,
      0x7d, 0xfe, 0xf5, 0xc8, 0xae, 0xfc, 0x51, 0x40, 0xa7, 0xd1, 0x28, 0xc2,
      0x89, 0xb2, 0x6b, 0x4e, 0xb4, 0xc1, 0x55, 0x87, 0x98, 0xbd, 0x72, 0xf9,
      0xcf, 0xd, 0x40, 0x15, 0xee, 0x32, 0xc, 0xf3, 0x56, 0xc5, 0xc, 0x61, 0x9d,
      0x4f, 0x7a, 0xb5, 0x2b, 0x16, 0xa9, 0xa3, 0x97, 0x38, 0xe2, 0xdd, 0x3a,
      0x33, 0xad, 0xf6, 0x7b, 0x68, 0x8b, 0x68, 0xcf, 0xa3, 0xd3, 0x98, 0x37,
      0xce, 0xec, 0xd1, 0xa8, 0xc, 0x8b};
  SigRl* srl1 = reinterpret_cast<SigRl*>(sig_rl_data_n2_one);
  size_t srl1_size = sizeof(sig_rl_data_n2_one);
  std::vector<uint8_t> sig3(EpidGetSigSize(srl1));
  std::vector<uint8_t> sig4(EpidGetSigSize(srl1));
  EXPECT_EQ(kEpidNoErr,
            EpidSign(member, msg.data(), msg.size(), nullptr, 0, srl1,
                     srl1_size, (EpidSignature*)sig3.data(), sig3.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidSign(member, msg.data(), msg.size(), nullptr, 0, srl1,
                     srl1_size, (EpidSignature*)sig4.data(), sig4.size()));
  EXPECT_TRUE(sig3.size() == sig4.size() &&
              0 != memcmp(sig3.data(), sig4.data(), sig3.size()));
}
TEST_F(EpidMemberTest, SignaturesOfSameMessageWithSameBasenameAreDifferent) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  std::vector<uint8_t> sig1(EpidGetSigSize(nullptr));
  std::vector<uint8_t> sig2(EpidGetSigSize(nullptr));
  // without signature based revocation list
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidSign(member, msg.data(), msg.size(), bsn.data(), bsn.size(),
                     nullptr, 0, (EpidSignature*)sig1.data(), sig1.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidSign(member, msg.data(), msg.size(), bsn.data(), bsn.size(),
                     nullptr, 0, (EpidSignature*)sig2.data(), sig2.size()));
  EXPECT_TRUE(sig1.size() == sig2.size() &&
              0 != memcmp(sig1.data(), sig2.data(), sig1.size()));

  // with signature based revocation list
  uint8_t sig_rl_data_n2_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x01,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x01,
      // one bk
      0x9c, 0xa5, 0xe5, 0xae, 0x5f, 0xae, 0x51, 0x59, 0x33, 0x35, 0x27, 0xd,
      0x8, 0xb1, 0xbe, 0x5d, 0x69, 0x50, 0x84, 0xc5, 0xfe, 0xe2, 0x87, 0xea,
      0x2e, 0xef, 0xfa, 0xee, 0x67, 0xf2, 0xd8, 0x28, 0x56, 0x43, 0xc6, 0x94,
      0x67, 0xa6, 0x72, 0xf6, 0x41, 0x15, 0x4, 0x58, 0x42, 0x16, 0x88, 0x57,
      0x9d, 0xc7, 0x71, 0xd1, 0xc, 0x84, 0x13, 0xa, 0x90, 0x23, 0x18, 0x8, 0xad,
      0x7d, 0xfe, 0xf5, 0xc8, 0xae, 0xfc, 0x51, 0x40, 0xa7, 0xd1, 0x28, 0xc2,
      0x89, 0xb2, 0x6b, 0x4e, 0xb4, 0xc1, 0x55, 0x87, 0x98, 0xbd, 0x72, 0xf9,
      0xcf, 0xd, 0x40, 0x15, 0xee, 0x32, 0xc, 0xf3, 0x56, 0xc5, 0xc, 0x61, 0x9d,
      0x4f, 0x7a, 0xb5, 0x2b, 0x16, 0xa9, 0xa3, 0x97, 0x38, 0xe2, 0xdd, 0x3a,
      0x33, 0xad, 0xf6, 0x7b, 0x68, 0x8b, 0x68, 0xcf, 0xa3, 0xd3, 0x98, 0x37,
      0xce, 0xec, 0xd1, 0xa8, 0xc, 0x8b};
  SigRl* srl1 = reinterpret_cast<SigRl*>(sig_rl_data_n2_one);
  size_t srl1_size = sizeof(sig_rl_data_n2_one);
  std::vector<uint8_t> sig3(EpidGetSigSize(srl1));
  std::vector<uint8_t> sig4(EpidGetSigSize(srl1));
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), bsn.data(),
                                 bsn.size(), srl1, srl1_size,
                                 (EpidSignature*)sig3.data(), sig3.size()));
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), bsn.data(),
                                 bsn.size(), srl1, srl1_size,
                                 (EpidSignature*)sig4.data(), sig4.size()));
  EXPECT_TRUE(sig3.size() == sig4.size() &&
              0 != memcmp(sig3.data(), sig4.data(), sig3.size()));
}

/////////////////////////////////////////////////////////////////////////
// Variable basename

TEST_F(EpidMemberTest, SignsMessageUsingRandomBaseNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignsMessageUsingRandomBaseWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageUsingBasenameNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), bsn.data(),
                                 bsn.size(), nullptr, 0, sig, sig_len));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageUsingBasenameWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), bsn.data(),
                                 bsn.size(), srl, srl_size, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsUsingRandomBaseWithRegisteredBasenamesNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignsUsingRandomBaseWithRegisteredBasenamesWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsUsingRandomBaseWithoutRegisteredBasenamesNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest,
       SignsUsingRandomBaseWithoutRegisteredBasenamesWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////////
// Variable sigRL

TEST_F(EpidMemberTest, SignFailsGivenInvalidSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;

  // sign fail with mismatch gid
  uint8_t sig_rl_data_n2_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x02,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x01,
      // one bk
      0x9c, 0xa5, 0xe5, 0xae, 0x5f, 0xae, 0x51, 0x59, 0x33, 0x35, 0x27, 0xd,
      0x8, 0xb1, 0xbe, 0x5d, 0x69, 0x50, 0x84, 0xc5, 0xfe, 0xe2, 0x87, 0xea,
      0x2e, 0xef, 0xfa, 0xee, 0x67, 0xf2, 0xd8, 0x28, 0x56, 0x43, 0xc6, 0x94,
      0x67, 0xa6, 0x72, 0xf6, 0x41, 0x15, 0x4, 0x58, 0x42, 0x16, 0x88, 0x57,
      0x9d, 0xc7, 0x71, 0xd1, 0xc, 0x84, 0x13, 0xa, 0x90, 0x23, 0x18, 0x8, 0xad,
      0x7d, 0xfe, 0xf5, 0xc8, 0xae, 0xfc, 0x51, 0x40, 0xa7, 0xd1, 0x28, 0xc2,
      0x89, 0xb2, 0x6b, 0x4e, 0xb4, 0xc1, 0x55, 0x87, 0x98, 0xbd, 0x72, 0xf9,
      0xcf, 0xd, 0x40, 0x15, 0xee, 0x32, 0xc, 0xf3, 0x56, 0xc5, 0xc, 0x61, 0x9d,
      0x4f, 0x7a, 0xb5, 0x2b, 0x16, 0xa9, 0xa3, 0x97, 0x38, 0xe2, 0xdd, 0x3a,
      0x33, 0xad, 0xf6, 0x7b, 0x68, 0x8b, 0x68, 0xcf, 0xa3, 0xd3, 0x98, 0x37,
      0xce, 0xec, 0xd1, 0xa8, 0xc, 0x8b};
  SigRl* srl = reinterpret_cast<SigRl*>(sig_rl_data_n2_one);
  size_t srl_size = sizeof(sig_rl_data_n2_one);
  size_t sig_len = EpidGetSigSize(srl);
  std::vector<uint8_t> newsig(sig_len);
  EpidSignature* sig = (EpidSignature*)newsig.data();

  EXPECT_EQ(kEpidBadArgErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                     srl, srl_size, sig, sig_len));

  // sign fail given invalid sigrl size
  uint8_t sig_rl_data_n_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x01,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x00,
      // one bk
      0x9c, 0xa5, 0xe5, 0xae, 0x5f, 0xae, 0x51, 0x59, 0x33, 0x35, 0x27, 0xd,
      0x8, 0xb1, 0xbe, 0x5d, 0x69, 0x50, 0x84, 0xc5, 0xfe, 0xe2, 0x87, 0xea,
      0x2e, 0xef, 0xfa, 0xee, 0x67, 0xf2, 0xd8, 0x28, 0x56, 0x43, 0xc6, 0x94,
      0x67, 0xa6, 0x72, 0xf6, 0x41, 0x15, 0x4, 0x58, 0x42, 0x16, 0x88, 0x57,
      0x9d, 0xc7, 0x71, 0xd1, 0xc, 0x84, 0x13, 0xa, 0x90, 0x23, 0x18, 0x8, 0xad,
      0x7d, 0xfe, 0xf5, 0xc8, 0xae, 0xfc, 0x51, 0x40, 0xa7, 0xd1, 0x28, 0xc2,
      0x89, 0xb2, 0x6b, 0x4e, 0xb4, 0xc1, 0x55, 0x87, 0x98, 0xbd, 0x72, 0xf9,
      0xcf, 0xd, 0x40, 0x15, 0xee, 0x32, 0xc, 0xf3, 0x56, 0xc5, 0xc, 0x61, 0x9d,
      0x4f, 0x7a, 0xb5, 0x2b, 0x16, 0xa9, 0xa3, 0x97, 0x38, 0xe2, 0xdd, 0x3a,
      0x33, 0xad, 0xf6, 0x7b, 0x68, 0x8b, 0x68, 0xcf, 0xa3, 0xd3, 0x98, 0x37,
      0xce, 0xec, 0xd1, 0xa8, 0xc, 0x8b};
  SigRl* srl1 = reinterpret_cast<SigRl*>(sig_rl_data_n_one);
  size_t srl1_size = sizeof(sig_rl_data_n_one);
  size_t sig_len1 = EpidGetSigSize(srl1);
  std::vector<uint8_t> newsig1(sig_len1);
  EpidSignature* sig1 = (EpidSignature*)newsig1.data();

  EXPECT_EQ(kEpidBadArgErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                     srl1, srl1_size, sig1, sig_len1));
}

TEST_F(EpidMemberTest, SignsMessageGivenNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;

  size_t sig_len = EpidGetSigSize(nullptr);
  std::vector<uint8_t> newsig(sig_len);
  EpidSignature* sig = (EpidSignature*)newsig.data();

  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignsMessageGivenNoSigRlUsingIKGFData) {
  GroupPubKey grp_public_key = *reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  PrivKey mbr_private_key =
      *reinterpret_cast<const PrivKey*>(this->kMemberPrivateKeyDataIkgf.data());
  Prng my_prng;
  auto& msg = this->kMsg0;

  size_t sig_len = EpidGetSigSize(nullptr);
  std::vector<uint8_t> newsig(sig_len);
  // using ikgf keys
  MemberCtxObj member(grp_public_key, mbr_private_key, &Prng::Generate,
                      &my_prng);
  EpidSignature* sig = (EpidSignature*)newsig.data();
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify signature
  VerifierCtxObj ctx(grp_public_key);
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageGivenSigRlWithNoEntries) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;

  uint8_t sig_rl_data_n2_zero[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x01,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x00,
      // not bk's
  };
  SigRl const* srl = reinterpret_cast<SigRl const*>(sig_rl_data_n2_zero);
  size_t srl_size = sizeof(sig_rl_data_n2_zero);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageGivenSigRlWithNoEntriesUsingIkgfData) {
  GroupPubKey grp_public_key = *reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  PrivKey mbr_private_key =
      *reinterpret_cast<const PrivKey*>(this->kMemberPrivateKeyDataIkgf.data());
  Prng my_prng;
  auto& msg = this->kMsg0;
  // using ikgf keys
  MemberCtxObj member_ikgf(grp_public_key, mbr_private_key, &Prng::Generate,
                           &my_prng);
  uint8_t sig_rl_data_n2_one_gid0[] = {
#include "epid/common-testhelper/testdata/ikgf/groupa/sigrl_empty.inc"
  };
  SigRl* srl_ikgf = reinterpret_cast<SigRl*>(sig_rl_data_n2_one_gid0);
  size_t srl_size = sizeof(sig_rl_data_n2_one_gid0);
  std::vector<uint8_t> sig_data_ikgf(EpidGetSigSize(srl_ikgf));
  EpidSignature* sig_ikgf =
      reinterpret_cast<EpidSignature*>(sig_data_ikgf.data());
  size_t sig_len = sig_data_ikgf.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member_ikgf, msg.data(), msg.size(), nullptr,
                                 0, srl_ikgf, srl_size, sig_ikgf, sig_len));
  VerifierCtxObj ctx_ikgf(grp_public_key);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx_ikgf, srl_ikgf, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx_ikgf, sig_ikgf, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageGivenSigRlWithEntries) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  uint8_t sig_rl_data_n2_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x01,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x02,
      // one bk
      0x9c, 0xa5, 0xe5, 0xae, 0x5f, 0xae, 0x51, 0x59, 0x33, 0x35, 0x27, 0xd,
      0x8, 0xb1, 0xbe, 0x5d, 0x69, 0x50, 0x84, 0xc5, 0xfe, 0xe2, 0x87, 0xea,
      0x2e, 0xef, 0xfa, 0xee, 0x67, 0xf2, 0xd8, 0x28, 0x56, 0x43, 0xc6, 0x94,
      0x67, 0xa6, 0x72, 0xf6, 0x41, 0x15, 0x4, 0x58, 0x42, 0x16, 0x88, 0x57,
      0x9d, 0xc7, 0x71, 0xd1, 0xc, 0x84, 0x13, 0xa, 0x90, 0x23, 0x18, 0x8, 0xad,
      0x7d, 0xfe, 0xf5, 0xc8, 0xae, 0xfc, 0x51, 0x40, 0xa7, 0xd1, 0x28, 0xc2,
      0x89, 0xb2, 0x6b, 0x4e, 0xb4, 0xc1, 0x55, 0x87, 0x98, 0xbd, 0x72, 0xf9,
      0xcf, 0xd, 0x40, 0x15, 0xee, 0x32, 0xc, 0xf3, 0x56, 0xc5, 0xc, 0x61, 0x9d,
      0x4f, 0x7a, 0xb5, 0x2b, 0x16, 0xa9, 0xa3, 0x97, 0x38, 0xe2, 0xdd, 0x3a,
      0x33, 0xad, 0xf6, 0x7b, 0x68, 0x8b, 0x68, 0xcf, 0xa3, 0xd3, 0x98, 0x37,
      0xce, 0xec, 0xd1, 0xa8, 0xc, 0x8b,

      0x71, 0x8a, 0xb5, 0x1, 0x7f, 0x7c, 0x92, 0x9a, 0xa2, 0xc9, 0x81, 0x10,
      0xfe, 0xbf, 0xc, 0x53, 0xa4, 0x43, 0xaf, 0x31, 0x74, 0x12, 0x25, 0x60,
      0x3e, 0xc0, 0x21, 0xe6, 0x63, 0x9a, 0xd2, 0x67, 0x2d, 0xb5, 0xd5, 0x82,
      0xc4, 0x49, 0x29, 0x51, 0x42, 0x8f, 0xe0, 0xe, 0xd1, 0x73, 0x27, 0xf5,
      0x77, 0x16, 0x4, 0x40, 0x8a, 0x0, 0xe, 0x3a, 0x5d, 0x37, 0x42, 0xd3, 0x8,
      0x40, 0xbd, 0x69, 0xf7, 0x5f, 0x74, 0x21, 0x50, 0xf4, 0xce, 0xfe, 0xd9,
      0xdd, 0x97, 0x6c, 0xa8, 0xa5, 0x60, 0x6b, 0xf8, 0x1b, 0xba, 0x2, 0xb2,
      0xca, 0x5, 0x44, 0x9b, 0xb1, 0x5e, 0x3a, 0xa4, 0x35, 0x7a, 0x51, 0xfa,
      0xcf, 0xa4, 0x4, 0xe9, 0xf3, 0xbf, 0x38, 0xd4, 0x24, 0x9, 0x52, 0xf3,
      0x58, 0x3d, 0x9d, 0x4b, 0xb3, 0x37, 0x4b, 0xec, 0x87, 0xe1, 0x64, 0x60,
      0x3c, 0xb6, 0xf7, 0x7b, 0xff, 0x40, 0x11};
  SigRl* srl = reinterpret_cast<SigRl*>(sig_rl_data_n2_one);
  size_t srl_size = sizeof(sig_rl_data_n2_one);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageGivenSigRlWithEntriesUsingIKGFData) {
  GroupPubKey grp_public_key = *reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  PrivKey mbr_private_key =
      *reinterpret_cast<const PrivKey*>(this->kMemberPrivateKeyDataIkgf.data());
  Prng my_prng;
  auto& msg = this->kMsg0;
  // using ikgf keys
  MemberCtxObj member_ikgf(grp_public_key, mbr_private_key, &Prng::Generate,
                           &my_prng);
  uint8_t sig_rl_data_n2_one_gid0[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x02,
      // one bk
      0x9c, 0xa5, 0xe5, 0xae, 0x5f, 0xae, 0x51, 0x59, 0x33, 0x35, 0x27, 0xd,
      0x8, 0xb1, 0xbe, 0x5d, 0x69, 0x50, 0x84, 0xc5, 0xfe, 0xe2, 0x87, 0xea,
      0x2e, 0xef, 0xfa, 0xee, 0x67, 0xf2, 0xd8, 0x28, 0x56, 0x43, 0xc6, 0x94,
      0x67, 0xa6, 0x72, 0xf6, 0x41, 0x15, 0x4, 0x58, 0x42, 0x16, 0x88, 0x57,
      0x9d, 0xc7, 0x71, 0xd1, 0xc, 0x84, 0x13, 0xa, 0x90, 0x23, 0x18, 0x8, 0xad,
      0x7d, 0xfe, 0xf5, 0xc8, 0xae, 0xfc, 0x51, 0x40, 0xa7, 0xd1, 0x28, 0xc2,
      0x89, 0xb2, 0x6b, 0x4e, 0xb4, 0xc1, 0x55, 0x87, 0x98, 0xbd, 0x72, 0xf9,
      0xcf, 0xd, 0x40, 0x15, 0xee, 0x32, 0xc, 0xf3, 0x56, 0xc5, 0xc, 0x61, 0x9d,
      0x4f, 0x7a, 0xb5, 0x2b, 0x16, 0xa9, 0xa3, 0x97, 0x38, 0xe2, 0xdd, 0x3a,
      0x33, 0xad, 0xf6, 0x7b, 0x68, 0x8b, 0x68, 0xcf, 0xa3, 0xd3, 0x98, 0x37,
      0xce, 0xec, 0xd1, 0xa8, 0xc, 0x8b,

      0x71, 0x8a, 0xb5, 0x1, 0x7f, 0x7c, 0x92, 0x9a, 0xa2, 0xc9, 0x81, 0x10,
      0xfe, 0xbf, 0xc, 0x53, 0xa4, 0x43, 0xaf, 0x31, 0x74, 0x12, 0x25, 0x60,
      0x3e, 0xc0, 0x21, 0xe6, 0x63, 0x9a, 0xd2, 0x67, 0x2d, 0xb5, 0xd5, 0x82,
      0xc4, 0x49, 0x29, 0x51, 0x42, 0x8f, 0xe0, 0xe, 0xd1, 0x73, 0x27, 0xf5,
      0x77, 0x16, 0x4, 0x40, 0x8a, 0x0, 0xe, 0x3a, 0x5d, 0x37, 0x42, 0xd3, 0x8,
      0x40, 0xbd, 0x69, 0xf7, 0x5f, 0x74, 0x21, 0x50, 0xf4, 0xce, 0xfe, 0xd9,
      0xdd, 0x97, 0x6c, 0xa8, 0xa5, 0x60, 0x6b, 0xf8, 0x1b, 0xba, 0x2, 0xb2,
      0xca, 0x5, 0x44, 0x9b, 0xb1, 0x5e, 0x3a, 0xa4, 0x35, 0x7a, 0x51, 0xfa,
      0xcf, 0xa4, 0x4, 0xe9, 0xf3, 0xbf, 0x38, 0xd4, 0x24, 0x9, 0x52, 0xf3,
      0x58, 0x3d, 0x9d, 0x4b, 0xb3, 0x37, 0x4b, 0xec, 0x87, 0xe1, 0x64, 0x60,
      0x3c, 0xb6, 0xf7, 0x7b, 0xff, 0x40, 0x11};
  SigRl* srl_ikgf = reinterpret_cast<SigRl*>(sig_rl_data_n2_one_gid0);
  size_t srl_size = sizeof(sig_rl_data_n2_one_gid0);
  std::vector<uint8_t> sig_data_ikgf(EpidGetSigSize(srl_ikgf));
  EpidSignature* sig_ikgf =
      reinterpret_cast<EpidSignature*>(sig_data_ikgf.data());
  size_t sig_len = sig_data_ikgf.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member_ikgf, msg.data(), msg.size(), nullptr,
                                 0, srl_ikgf, srl_size, sig_ikgf, sig_len));
  VerifierCtxObj ctx_ikgf(grp_public_key);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx_ikgf, srl_ikgf, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx_ikgf, sig_ikgf, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignMessageReportsIfMemberRevoked) {
  // note: a complete sig + nr proof should still be returned!!
  const GroupPubKey pub_key = {
#include "epid/common-testhelper/testdata/grp_x/pubkey.inc"
  };
  const PrivKey priv_key = {
#include "epid/common-testhelper/testdata/grp_x/member0/mprivkey.inc"
  };
  Prng my_prng;
  MemberCtxObj member(pub_key, priv_key, this->kMemberPrecomp, &Prng::Generate,
                      &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  const std::vector<uint8_t> kGrpXSigRlMember0Sha256Bsn0Msg0FirstEntry = {
#include "epid/common-testhelper/testdata/grp_x/sigrl_member0_sig_sha256_bsn0_msg0_revoked_middle_entry.inc"
  };
  auto srl = reinterpret_cast<SigRl const*>(
      kGrpXSigRlMember0Sha256Bsn0Msg0FirstEntry.data());
  size_t srl_size = kGrpXSigRlMember0Sha256Bsn0Msg0FirstEntry.size();

  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidBadArgErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                     srl, srl_size, sig, sig_len));

  VerifierCtxObj ctx(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));

  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignMessageReportsIfMemberRevokedUsingIKGFData) {
  // note: a complete sig + nr proof should still be returned!!
  GroupPubKey grp_public_key = *reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  const PrivKey member_private_key_revoked_by_sig = {
#include "epid/common-testhelper/testdata/ikgf/groupa/sigrevokedmember0/mprivkey.inc"
  };
  Prng my_prng;
  MemberCtxObj member(grp_public_key, member_private_key_revoked_by_sig,
                      &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  const std::vector<uint8_t> sig_Rl = {
#include "epid/common-testhelper/testdata/ikgf/groupa/sigrl.inc"
  };
  auto srl = reinterpret_cast<SigRl const*>(sig_Rl.data());
  size_t srl_size = sig_Rl.size();

  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidSigRevokedInSigRl,
            EpidSign(member, msg.data(), msg.size(), nullptr, 0, srl, srl_size,
                     sig, sig_len));

  VerifierCtxObj ctx(grp_public_key);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));

  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}
/////////////////////////////////////////////////////////////////////////
// Variable hash alg

TEST_F(EpidMemberTest, SignsMessageUsingSha256HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha256));
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha256));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageUsingSha384HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha384));
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha384));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageUsingSha512HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha512));
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha512));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, DISABLED_SignsMessageUsingSha512256HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha512_256));
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha512_256));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////////
// Variable precomputed signatures

TEST_F(EpidMemberTest, SignConsumesPrecomputedSignaturesNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 3, nullptr));
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  EXPECT_EQ((size_t)2, EpidGetNumPreSigs(member));
}

TEST_F(EpidMemberTest, SignConsumesPrecomputedSignaturesWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 3, nullptr));
  auto& msg = this->kMsg0;
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  EXPECT_EQ((size_t)2, EpidGetNumPreSigs(member));
}

TEST_F(EpidMemberTest, SignsMessageWithPrecomputedSignaturesNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, nullptr));
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageWithPrecomputedSignaturesWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, nullptr));
  auto& msg = this->kMsg0;
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageWithoutPrecomputedSignaturesNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  // test sign without precomputed signatures
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignsMessageWithoutPrecomputedSignaturesWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  // test sign without precomputed signatures
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()));
}

TEST_F(EpidMemberTest, SignFailsOnBadPrecomputedSignaturesNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  PreComputedSignature precompsig;
  precompsig = this->kPrecomputedSignatures[0];
  precompsig.B.x.data.data[0]++;
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, &precompsig));
  auto& msg = this->kMsg0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidBadArgErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                     nullptr, 0, sig, sig_len));
}

TEST_F(EpidMemberTest, SignFailsOnBadPrecomputedSignaturesWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  PreComputedSignature precompsig;
  precompsig = this->kPrecomputedSignatures[0];
  precompsig.B.x.data.data[0]++;
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, &precompsig));
  auto& msg = this->kMsg0;
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidBadArgErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                     srl, srl_size, sig, sig_len));
}

/////////////////////////////////////////////////////////////////////////
// Variable messages

TEST_F(EpidMemberTest, SignsEmptyMessageNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), 0, bsn.data(), bsn.size(),
                                 nullptr, 0, sig, sig_len));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid, EpidVerify(ctx, sig, sig_len, msg.data(), 0));
}

TEST_F(EpidMemberTest, SignsEmptyMessageWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), 0, nullptr, 0, srl,
                                 srl_size, sig, sig_len));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  EXPECT_EQ(kEpidSigValid, EpidVerify(ctx, sig, sig_len, msg.data(), 0));
}

TEST_F(EpidMemberTest, SignsShortMessageNoSigRl) {
  // check: 1, 13, 128, 256, 512, 1021, 1024 bytes
  // 13 and 1021 are primes
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  VerifierCtxObj ctx(this->kGroupPublicKey);
  size_t lengths[] = {1,   13,   128, 256,
                      512, 1021, 1024};  // have desired lengths to loop over
  std::vector<uint8_t> msg(
      lengths[COUNT_OF(lengths) - 1]);  // allocate message for max size
  for (size_t n = 0; n < msg.size(); n++) {
    msg[n] = (uint8_t)n;
  }
  for (auto length : lengths) {
    EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), length, nullptr, 0,
                                   nullptr, 0, sig, sig_len))
        << "EpidSign for message_len: " << length << " failed";
    EXPECT_EQ(kEpidSigValid, EpidVerify(ctx, sig, sig_len, msg.data(), length))
        << "EpidVerify for message_len: " << length << " failed";
  }
}

TEST_F(EpidMemberTest, SignsShortMessageWithSigRl) {
  // check: 1, 13, 128, 256, 512, 1021, 1024 bytes
  // 13 and 1021 are primes
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  size_t message_len = 0;
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  size_t lengths[] = {1,   13,   128, 256,
                      512, 1021, 1024};  // have desired lengths to loop over
  std::vector<uint8_t> msg(
      lengths[COUNT_OF(lengths) - 1]);  // allocate message for max size
  for (size_t n = 0; n < msg.size(); n++) {
    msg.at(n) = (uint8_t)n;
  }
  for (auto length : lengths) {
    EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), length, nullptr, 0, srl,
                                   srl_size, sig, sig_len))
        << "EpidSign for message_len: " << message_len << " failed";
    EXPECT_EQ(kEpidSigValid, EpidVerify(ctx, sig, sig_len, msg.data(), length))
        << "EpidVerify for message_len: " << message_len << " failed";
  }
}

TEST_F(EpidMemberTest, SignsLongMessageNoSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  std::vector<uint8_t> sig_data(EpidGetSigSize(nullptr));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  VerifierCtxObj ctx(this->kGroupPublicKey);
  std::vector<uint8_t> msg(1000000);  // allocate message for max size
  for (size_t n = 0; n < msg.size(); n++) {
    msg.at(n) = (uint8_t)n;
  }
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 nullptr, 0, sig, sig_len))
      << "EpidSign for message_len: " << 1000000 << " failed";
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()))
      << "EpidVerify for message_len: " << 1000000 << " failed";
}

TEST_F(EpidMemberTest, SignsLongMessageWithSigRl) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  SigRl const* srl =
      reinterpret_cast<SigRl const*>(this->kSigRl5EntryData.data());
  size_t srl_size = this->kSigRl5EntryData.size() * sizeof(uint8_t);
  std::vector<uint8_t> sig_data(EpidGetSigSize(srl));
  EpidSignature* sig = reinterpret_cast<EpidSignature*>(sig_data.data());
  size_t sig_len = sig_data.size() * sizeof(uint8_t);
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(ctx, srl, srl_size));
  std::vector<uint8_t> msg(1000000);  // allocate message for max size
  for (size_t n = 0; n < msg.size(); n++) {
    msg.at(n) = (uint8_t)n;
  }
  EXPECT_EQ(kEpidNoErr, EpidSign(member, msg.data(), msg.size(), nullptr, 0,
                                 srl, srl_size, sig, sig_len))
      << "EpidSign for message_len: " << 1000000 << " failed";
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(ctx, sig, sig_len, msg.data(), msg.size()))
      << "EpidVerify for message_len: " << 1000000 << " failed";
}

}  // namespace
