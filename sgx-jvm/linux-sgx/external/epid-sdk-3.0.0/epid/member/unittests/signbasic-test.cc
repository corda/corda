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
 * \brief SignBasic unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
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
TEST_F(EpidMemberTest, SignBasicFailsGivenNullParameters) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  BasicSignature basic_sig;
  EXPECT_EQ(kEpidBadArgErr, EpidSignBasic(nullptr, msg.data(), msg.size(),
                                          bsn.data(), bsn.size(), &basic_sig));
  EXPECT_EQ(kEpidBadArgErr, EpidSignBasic(member, msg.data(), msg.size(),
                                          bsn.data(), bsn.size(), nullptr));
  EXPECT_EQ(kEpidBadArgErr, EpidSignBasic(member, nullptr, msg.size(),
                                          bsn.data(), bsn.size(), &basic_sig));
  EXPECT_EQ(kEpidBadArgErr, EpidSignBasic(member, msg.data(), msg.size(),
                                          nullptr, bsn.size(), &basic_sig));
}
TEST_F(EpidMemberTest, SignBasicFailsForBasenameWithoutRegisteredBasenames) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  BasicSignature basic_sig;
  EXPECT_EQ(kEpidBadArgErr, EpidSignBasic(member, msg.data(), msg.size(),
                                          bsn.data(), bsn.size(), &basic_sig));
}
TEST_F(EpidMemberTest, SignBasicFailsIfGivenUnregisteredBasename) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn0 = this->kBsn0;
  auto& bsn1 = this->kBsn1;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn0.data(), bsn0.size()));
  BasicSignature basic_sig;
  EXPECT_EQ(kEpidBadArgErr,
            EpidSignBasic(member, msg.data(), msg.size(), bsn1.data(),
                          bsn1.size(), &basic_sig));
}
/////////////////////////////////////////////////////////////////////////
// Anonymity
TEST_F(EpidMemberTest, BasicSignaturesOfSameMessageAreDifferent) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  BasicSignature basic_sig1;
  BasicSignature basic_sig2;
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig1));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig2));
  EXPECT_NE(0, memcmp(&basic_sig1, &basic_sig2, sizeof(BasicSignature)));
}
TEST_F(EpidMemberTest,
       BasicSignaturesOfSameMessageWithSameBasenameAreDifferent) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  BasicSignature basic_sig1;
  BasicSignature basic_sig2;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig1));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig2));
  EXPECT_NE(0, memcmp(&basic_sig1, &basic_sig2, sizeof(BasicSignature)));
}
/////////////////////////////////////////////////////////////////////////
// Variable basename
TEST_F(EpidMemberTest, SignBasicSucceedsUsingRandomBase) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  BasicSignature basic_sig;
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignBasicSucceedsUsingBasename) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  BasicSignature basic_sig;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignBasicSucceedsUsingBasenameUsingIKGFData) {
  Prng my_prng;
  GroupPubKey grp_public_key = *reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  PrivKey mbr_private_key =
      *reinterpret_cast<const PrivKey*>(this->kMemberPrivateKeyDataIkgf.data());
  MemberCtxObj member(grp_public_key, mbr_private_key, &Prng::Generate,
                      &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  BasicSignature basic_sig;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(grp_public_key);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest,
       SignBasicSucceedsUsingRandomBaseWithRegisteredBasenames) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  BasicSignature basic_sig;
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest,
       SignBasicSucceedsUsingRandomBaseWithoutRegisteredBasenames) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  BasicSignature basic_sig;
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                      0, &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
/////////////////////////////////////////////////////////////////////////
// Variable hash alg
TEST_F(EpidMemberTest, SignBasicSucceedsUsingSha256HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  BasicSignature basic_sig;
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha256));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha256));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignBasicSucceedsUsingSha384HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  BasicSignature basic_sig;
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha384));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha384));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignBasicSucceedsUsingSha512HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  BasicSignature basic_sig;
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha512));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha512));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, DISABLED_SignBasicSucceedsUsingSha512256HashAlg) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  BasicSignature basic_sig;
  THROW_ON_EPIDERR(EpidMemberSetHashAlg(member, kSha512_256));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha512_256));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
/////////////////////////////////////////////////////////////////////////
TEST_F(EpidMemberTest, SignBasicFailsForInvalidMemberPrecomp) {
  Prng my_prng;
  MemberPrecomp mbr_precomp = this->kMemberPrecomp;
  mbr_precomp.e12.x[0].data.data[0]++;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      mbr_precomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  BasicSignature basic_sig;
  auto& bsn = this->kBsn0;
  EXPECT_EQ(kEpidBadArgErr, EpidSignBasic(member, msg.data(), msg.size(),
                                          bsn.data(), bsn.size(), &basic_sig));
}
// Variable precomputed signatures
TEST_F(EpidMemberTest, SignBasicFailsForInvalidPrecomputedSignature) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  PreComputedSignature precompsig = this->kPrecomputedSignatures[0];
  precompsig.B.x.data.data[0]++;
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, &precompsig));
  auto& msg = this->kMsg0;
  BasicSignature basic_sig;
  auto& bsn = this->kBsn0;
  EXPECT_EQ(kEpidBadArgErr, EpidSignBasic(member, msg.data(), msg.size(),
                                          bsn.data(), bsn.size(), &basic_sig));
}
TEST_F(EpidMemberTest, SignBasicConsumesPrecomputedSignatures) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 3, nullptr));
  auto& msg = this->kMsg0;
  BasicSignature basic_sig;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  // use 1 precomputed signature
  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  EXPECT_EQ((size_t)2, EpidGetNumPreSigs(member));
}
TEST_F(EpidMemberTest, SignBasicSucceedsWithPrecomputedSignatures) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, nullptr));
  auto& msg = this->kMsg0;
  BasicSignature basic_sig;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
TEST_F(EpidMemberTest, SignBasicSucceedsWithoutPrecomputedSignatures) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, nullptr));
  auto& msg = this->kMsg0;
  BasicSignature basic_sig;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  ASSERT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // test sign without precomputed signatures
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(),
                                      bsn.data(), bsn.size(), &basic_sig));
  // verify basic signature
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()));
}
/////////////////////////////////////////////////////////////////////////
// Variable messages
TEST_F(EpidMemberTest, SignBasicSucceedsGivenEmptyMessage) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidRegisterBaseName(member, bsn.data(), bsn.size()));
  BasicSignature basic_sig;
  EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), 0, bsn.data(),
                                      bsn.size(), &basic_sig));
  VerifierCtxObj ctx(this->kGroupPublicKey);
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid, EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), 0));
}
TEST_F(EpidMemberTest, SignBasicSucceedsWithShortMessage) {
  // check: 1, 13, 128, 256, 512, 1021, 1024 bytes
  // 13 and 1021 are primes
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  BasicSignature basic_sig;
  VerifierCtxObj ctx(this->kGroupPublicKey);
  size_t lengths[] = {1,   13,   128, 256,
                      512, 1021, 1024};  // have desired lengths to loop over
  std::vector<uint8_t> msg(
      lengths[COUNT_OF(lengths) - 1]);  // allocate message for max size
  for (size_t n = 0; n < msg.size(); n++) {
    msg.at(n) = (uint8_t)n;
  }
  for (auto length : lengths) {
    EXPECT_EQ(kEpidNoErr,
              EpidSignBasic(member, msg.data(), length, nullptr, 0, &basic_sig))
        << "EpidSignBasic for message_len: " << length << " failed";
    EXPECT_EQ(kEpidNoErr,
              EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), length))
        << "EpidVerifyBasicSig for message_len: " << length << " failed";
  }
}
TEST_F(EpidMemberTest, SignBasicSucceedsWithLongMessage) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  BasicSignature basic_sig;
  VerifierCtxObj ctx(this->kGroupPublicKey);
  {                                     // 1000000
    std::vector<uint8_t> msg(1000000);  // allocate message for max size
    for (size_t n = 0; n < msg.size(); n++) {
      msg.at(n) = (uint8_t)n;
    }
    EXPECT_EQ(kEpidNoErr, EpidSignBasic(member, msg.data(), msg.size(), nullptr,
                                        0, &basic_sig))
        << "EpidSignBasic for message_len: " << 1000000 << " failed";
    EXPECT_EQ(kEpidNoErr,
              EpidVerifyBasicSig(ctx, &basic_sig, msg.data(), msg.size()))
        << "EpidVerifyBasicSig for message_len: " << 1000000 << " failed";
  }
}

}  // namespace
