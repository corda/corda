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
 * \brief VerifierCreate unit tests.
 */

#include <vector>
#include <cstring>

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/api.h"
#include "epid/verifier/context.h"
}

#include "epid/verifier/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/verifier_wrapper-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"
bool operator==(VerifierPrecomp const& lhs, VerifierPrecomp const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}
namespace {
//////////////////////////////////////////////////////////////////////////
// EpidVerifierCreate Tests
TEST_F(EpidVerifierTest, CreateFailsGivenNullPointer) {
  VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&this->pub_key_str, &this->verifier_precomp_str,
                               nullptr));
  EpidVerifierDelete(&ctx);
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(nullptr, &this->verifier_precomp_str, &ctx));
  EpidVerifierDelete(&ctx);
}
TEST_F(EpidVerifierTest, CreateSucceedsGivenNullPrecomp) {
  VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidNoErr, EpidVerifierCreate(&this->pub_key_str, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
}
TEST_F(EpidVerifierTest, CreateSucceedsGivenNullPrecompUsingIkgfData) {
  VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierCreate(&this->pub_key_ikgf_str, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
}
TEST_F(EpidVerifierTest, CreateFailsGivenInvalidPubkey) {
  VerifierCtx* ctx = nullptr;
  GroupPubKey pubkey_with_bad_h1 = this->pub_key_str;
  pubkey_with_bad_h1.h1.x.data.data[31]++;  // munge h1 so not in G1
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&pubkey_with_bad_h1, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
  GroupPubKey pubkey_with_bad_h2 = this->pub_key_str;
  pubkey_with_bad_h2.h2.x.data.data[31]++;  // munge h2 so not in G1
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&pubkey_with_bad_h2, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
  GroupPubKey pubkey_with_bad_w = this->pub_key_str;
  pubkey_with_bad_w.w.x[0].data.data[31]++;  // munge w so not in G2
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&pubkey_with_bad_w, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierDelete Tests
TEST_F(EpidVerifierTest, DeleteNullsVerifierCtx) {
  VerifierCtx* ctx = nullptr;
  EpidVerifierCreate(&this->pub_key_str, nullptr, &ctx);
  EpidVerifierDelete(&ctx);
  EXPECT_EQ(nullptr, ctx);
}
TEST_F(EpidVerifierTest, DeleteWorksGivenNullVerifierCtx) {
  EpidVerifierDelete(nullptr);
  VerifierCtx* ctx = nullptr;
  EpidVerifierDelete(&ctx);
}

//////////////////////////////////////////////////////////////////////////
// EpidVerifierWritePrecomp
TEST_F(EpidVerifierTest, WritePrecompFailsGivenNullPointer) {
  VerifierPrecomp precomp;
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierWritePrecomp(nullptr, &precomp));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierWritePrecomp(ctx, nullptr));
}
TEST_F(EpidVerifierTest, WritePrecompSucceedGivenValidArgument) {
  VerifierPrecomp precomp;
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidNoErr, EpidVerifierWritePrecomp(ctx, &precomp));
  VerifierPrecomp expected_precomp = this->verifier_precomp_str;
  EXPECT_EQ(expected_precomp, precomp);

  VerifierCtxObj verifier2(this->pub_key_str);
  VerifierCtx* ctx2 = verifier2;
  EXPECT_EQ(kEpidNoErr, EpidVerifierWritePrecomp(ctx2, &precomp));
  EXPECT_EQ(expected_precomp, precomp);
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetPrivRl
TEST_F(EpidVerifierTest, SetPrivRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(nullptr, &prl, sizeof(prl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetPrivRl(verifier, nullptr, sizeof(prl)));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenZeroSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(verifier, &prl, 0));
}

// Size parameter must be at least big enough for n1 == 0 case
TEST_F(EpidVerifierTest, SetPrivRlFailsGivenTooSmallSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetPrivRl(verifier, &prl, (sizeof(prl) - sizeof(prl.f)) - 1));
  prl.n1 = this->octstr32_1;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetPrivRl(verifier, &prl, (sizeof(prl) - sizeof(prl.f)) - 1));
}

// Size parameter must be cross-checked with n1 value in priv_rl
TEST_F(EpidVerifierTest, SetPrivRlFailsGivenN1TooBigForSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  prl.n1 = this->octstr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenN1TooSmallForSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(EpidVerifierTest, SetPrivRlPassesGivenDefaultPrivRl) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

TEST_F(EpidVerifierTest, SetPrivRlPassesGivenDefaultPrivRlUsingIkgfData) {
  VerifierCtxObj verifier(this->pub_key_ikgf_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_ikgf_str.gid;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

TEST_F(EpidVerifierTest, SetPrivRlPassesGivenPrivRlWithSingleElement) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  prl.n1 = this->octstr32_1;
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenBadGroupId) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  prl.gid.data[0] = ~prl.gid.data[0];
  prl.n1 = this->octstr32_1;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->pub_key_str.gid;
  prl.version = this->octstr32_1;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  prl.version = octstr32_0;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetSigRl
TEST_F(EpidVerifierTest, SetSigRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetSigRl(nullptr, &srl, sizeof(SigRl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, nullptr, sizeof(SigRl)));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenZeroSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetSigRl(verifier, &srl, 0));
}

// Size parameter must be at least big enough for n2 == 0 case
TEST_F(EpidVerifierTest, SetSigRlFailsGivenTooSmallSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->pub_key_str.gid;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetSigRl(verifier, &srl, (sizeof(srl) - sizeof(srl.bk)) - 1));
  srl.n2 = this->octstr32_1;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetSigRl(verifier, &srl, (sizeof(srl) - sizeof(srl.bk)) - 1));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenN2TooBigForSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->pub_key_str.gid;
  srl.n2 = this->octstr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenN2TooSmallForSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetSigRl(verifier, &srl, sizeof(srl)));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenDefaultSigRl) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierCtx* ctx = verifier;
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  size_t sig_rl_size = this->kGrp01SigRl.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(ctx, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenDefaultSigRlUsingIkgfData) {
  VerifierCtxObj verifier(this->pub_key_ikgf_str,
                          this->verifier_precomp_ikgf_str);
  VerifierCtx* ctx = verifier;
  SigRl const* sig_rl = reinterpret_cast<SigRl const*>(this->kSigRlIkgf.data());
  size_t sig_rl_size = this->kSigRlIkgf.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(ctx, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenSigRlWithNoElements) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);

  uint8_t sig_rl_data_n2_zero[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x00,
      // not bk's
  };
  SigRl* sig_rl = reinterpret_cast<SigRl*>(sig_rl_data_n2_zero);
  size_t sig_rl_size = sizeof(sig_rl_data_n2_zero);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(verifier, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenSigRlWithOneElement) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);

  uint8_t sig_rl_data_n2_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
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
  SigRl* sig_rl = reinterpret_cast<SigRl*>(sig_rl_data_n2_one);
  size_t sig_rl_size = sizeof(sig_rl_data_n2_one);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(verifier, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenBadGroupId) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->pub_key_str.gid;
  srl.gid.data[0] = ~srl.gid.data[0];
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->pub_key_str.gid;
  srl.version = this->octstr32_1;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  srl.version = octstr32_0;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
}

//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetGroupRl
TEST_F(EpidVerifierTest, SetGroupRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl grl = {{0}, {0}, {0}};
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetGroupRl(nullptr, &grl, sizeof(grl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, nullptr, sizeof(grl)));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenSizeZero) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl grl = {{0}, {0}, {0}};
  size_t grl_size = 0;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetGroupRl(verifier, &grl, grl_size));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenSizeTooSmall) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl grl = {{0}, {0}, {0}};
  size_t grl_size = sizeof(grl) - sizeof(grl.gid[0]);
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, &grl, grl_size - 1));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenSizeTooLarge) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl grl = {{0}, {0}, {0}};
  size_t grl_size = sizeof(grl) - sizeof(grl.gid[0]);
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, &grl, grl_size + 1));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenN3ZeroAndGroupRLSizeTooBig) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl* group_rl = (GroupRl*)this->group_rl_3gid_n0_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, group_rl,
                                   this->group_rl_3gid_n0_buf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenN3TooSmall) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl* group_rl = (GroupRl*)this->group_rl_3gid_n2_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, group_rl,
                                   this->group_rl_3gid_n2_buf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenN3TooLarge) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl* group_rl = (GroupRl*)this->group_rl_3gid_n4_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, group_rl,
                                   this->group_rl_3gid_n4_buf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlSucceedsGivenEmptyRL) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl* empty_grl = (GroupRl*)this->group_rl_empty_buf.data();
  size_t grl_size = this->group_rl_empty_buf.size();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, empty_grl, grl_size));
}
TEST_F(EpidVerifierTest, SetGroupRlSucceedsGivenDefaultGroupRLUsingIkgfData) {
  VerifierCtxObj verifier(this->pub_key_ikgf_str,
                          this->verifier_precomp_ikgf_str);
  GroupRl* empty_grl = (GroupRl*)this->group_rl_empty_buf.data();
  size_t grl_size = this->group_rl_empty_buf.size();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, empty_grl, grl_size));
}
TEST_F(EpidVerifierTest, SetGroupRlSucceedsGivenRLWith3gid) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl* group_rl = (GroupRl*)this->group_rl_3gid_buf.data();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, group_rl,
                                               this->group_rl_3gid_buf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  GroupRl* group_rl = (GroupRl*)this->group_rl_3gid_buf.data();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, group_rl,
                                               this->group_rl_3gid_buf.size()));
  GroupRl* empty_grl = (GroupRl*)this->group_rl_empty_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, empty_grl,
                                   this->group_rl_empty_buf.size()));
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetVerifierRl
TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(nullptr, &ver_rl, sizeof(ver_rl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, nullptr, sizeof(ver_rl)));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenSizeZero) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetVerifierRl(verifier, &ver_rl, 0));
}

// Size parameter must be at least equal to minimum value for n4 == 0 case
TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenSizeTooSmall) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(
                verifier, &ver_rl, sizeof(ver_rl) - sizeof(ver_rl.K[0]) - 1));
  ver_rl.n4 = this->octstr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(
                verifier, &ver_rl, sizeof(ver_rl) - sizeof(ver_rl.K[0]) - 1));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenN4TooBigForSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->pub_key_str.gid;
  ver_rl.n4 = this->octstr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenN4TooSmallForSize) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->pub_key_str.gid;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl, sizeof(ver_rl)));
}

TEST_F(EpidVerifierTest, SetVerifierRlWorksGivenDefaultVerifierRl) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl const* ver_rl_ptr =
      reinterpret_cast<VerifierRl const*>(this->kGrp01VerRl.data());
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetVerifierRl(
                            verifier, ver_rl_ptr,
                            this->kGrp01VerRl.size() * sizeof(uint8_t)));
}

TEST_F(EpidVerifierTest, SetVerifierRlWorksGivenVerifierRlWithNoElements) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  uint8_t ver_rl_data_n4_zero[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
      // B
      0x41, 0x63, 0xfd, 0x06, 0xb8, 0xb1, 0xa6, 0x32, 0xa5, 0xe3, 0xeb, 0xc4,
      0x40, 0x11, 0x37, 0xc0, 0x62, 0x0d, 0xe1, 0xca, 0xe9, 0x79, 0xad, 0xff,
      0x1d, 0x13, 0xb3, 0xda, 0xa0, 0x10, 0x8a, 0xa8, 0x30, 0x72, 0xa4, 0xe8,
      0x27, 0xb5, 0xad, 0xdb, 0xac, 0x89, 0xd8, 0x37, 0x79, 0xd9, 0x8c, 0xd0,
      0xb3, 0xef, 0x94, 0x17, 0x4f, 0x05, 0x53, 0x4c, 0x4d, 0xf0, 0x77, 0xf7,
      0xb6, 0xaf, 0xb8, 0xfa,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n4
      0x00, 0x00, 0x00, 0x00,
      // no k's
  };

  VerifierRl* ver_rl_ptr = reinterpret_cast<VerifierRl*>(ver_rl_data_n4_zero);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetVerifierRl(verifier, ver_rl_ptr,
                                                  sizeof(ver_rl_data_n4_zero)));
}

TEST_F(EpidVerifierTest, SetVerifierRlWorksGivenVerifierRlWithOneElement) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  uint8_t ver_rl_data_n4_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
      // B
      0x41, 0x63, 0xfd, 0x06, 0xb8, 0xb1, 0xa6, 0x32, 0xa5, 0xe3, 0xeb, 0xc4,
      0x40, 0x11, 0x37, 0xc0, 0x62, 0x0d, 0xe1, 0xca, 0xe9, 0x79, 0xad, 0xff,
      0x1d, 0x13, 0xb3, 0xda, 0xa0, 0x10, 0x8a, 0xa8, 0x30, 0x72, 0xa4, 0xe8,
      0x27, 0xb5, 0xad, 0xdb, 0xac, 0x89, 0xd8, 0x37, 0x79, 0xd9, 0x8c, 0xd0,
      0xb3, 0xef, 0x94, 0x17, 0x4f, 0x05, 0x53, 0x4c, 0x4d, 0xf0, 0x77, 0xf7,
      0xb6, 0xaf, 0xb8, 0xfa,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n4
      0x00, 0x00, 0x00, 0x01,
      // k's
      0xdc, 0x41, 0x24, 0xe7, 0xb8, 0xf2, 0x6d, 0xc4, 0x01, 0xf9, 0x5d, 0xf8,
      0xd9, 0x23, 0x32, 0x29, 0x0a, 0xe1, 0xf6, 0xdc, 0xa1, 0xef, 0x52, 0xf7,
      0x3a, 0x3c, 0xe6, 0x7e, 0x3d, 0x0e, 0xe8, 0x86, 0xa9, 0x58, 0xf4, 0xfe,
      0xfa, 0x8b, 0xe4, 0x1c, 0xad, 0x58, 0x5b, 0x1c, 0xc7, 0x54, 0xee, 0x7e,
      0xe7, 0x12, 0x6a, 0x4b, 0x01, 0x63, 0xb4, 0xdb, 0x6e, 0xe7, 0x7a, 0xe9,
      0x62, 0xa5, 0xb4, 0xe3,
  };
  VerifierRl* ver_rl_ptr = reinterpret_cast<VerifierRl*>(ver_rl_data_n4_one);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetVerifierRl(verifier, ver_rl_ptr,
                                                  sizeof(ver_rl_data_n4_one)));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenBadGroupId) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->pub_key_str.gid;
  ver_rl.gid.data[0] = ~ver_rl.gid.data[0];
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->pub_key_str.gid;
  ver_rl.version = this->octstr32_1;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  ver_rl.version = octstr32_0;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
}

//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetHashAlg
TEST_F(EpidVerifierTest, SetHashAlgFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(nullptr, kSha256));
}
TEST_F(EpidVerifierTest, SetHashAlgCanSetValidAlgoritm) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha256));
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha384));
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha512));
  // DE2089 - SHA-512/256 Hash Alg is not supported by EpidMemberSetHashAlg
  // EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha512_256));
}
TEST_F(EpidVerifierTest, SetHashAlgCanFailForNonSupportedAlgoritms) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, kSha3_256));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, kSha3_384));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, kSha3_512));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, (HashAlg)-1));
}
TEST_F(EpidVerifierTest, DefaultHashAlgIsSha512) {
  VerifierCtxObj verifier(this->pub_key_str, this->verifier_precomp_str);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(kSha512, ctx->hash_alg);
}
}  // namespace
