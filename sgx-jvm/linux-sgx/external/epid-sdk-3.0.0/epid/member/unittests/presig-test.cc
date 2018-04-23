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
 * \brief ComputePreSig unit tests.
 */
#include <cstring>
#include <limits>
#include <algorithm>
#include <vector>

#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
}

#include "epid/member/unittests/member-testhelper.h"
#include "epid/common-testhelper/prng-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"

/// Count of elements in array
#define COUNT_OF(A) (sizeof(A) / sizeof((A)[0]))

bool operator==(PreComputedSignature const& lhs,
                PreComputedSignature const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}

bool operator!=(PreComputedSignature const& lhs,
                PreComputedSignature const& rhs) {
  return !(lhs == rhs);
}

namespace {

///////////////////////////////////////////////////////////////////////
// EpidAddPreSigs
TEST_F(EpidMemberTest, AddPreSigsFailsGivenNullPointer) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature presig = this->kPrecomputedSignatures[0];

  EXPECT_EQ(kEpidBadArgErr, EpidAddPreSigs(nullptr, 1, &presig));
}

TEST_F(EpidMemberTest, AddPreSigsFailsGivenHugeNumberOfPreSigs) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature presig = this->kPrecomputedSignatures[0];

  // number_presigs = 0x80..01 of size equal to sizeof(size_t)
  EXPECT_EQ(kEpidBadArgErr,
            EpidAddPreSigs(member, (SIZE_MAX >> 1) + 2, &presig));
}

TEST_F(EpidMemberTest,
       AddPreSigsComputesSpecifiedNumberOfPresigsIfInputPresigsNull) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  ASSERT_EQ(kEpidNoErr, EpidAddPreSigs(member, 2, nullptr));
  ASSERT_EQ(kEpidNoErr, EpidAddPreSigs(member, 1, nullptr));
  // request to generate 0 pre-computed signatures do nothing
  ASSERT_EQ(kEpidNoErr, EpidAddPreSigs(member, 0, nullptr));
  EXPECT_EQ((size_t)3, EpidGetNumPreSigs(member));
}

TEST_F(EpidMemberTest, AddPreSigsClearsInputPresigBuffer) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  // For a test purposes allocate an array of precomputed signatures with
  // all elements initialized to the same precomputed signature.
  // Warning: Do not use precomputed signatures twice in production code!
  std::vector<PreComputedSignature> presigs(2, this->kPrecomputedSignatures[0]);

  ASSERT_EQ(kEpidNoErr, EpidAddPreSigs(member, presigs.size(), presigs.data()));
  EXPECT_TRUE(std::all_of((uint8_t*)presigs.data(),
                          (uint8_t*)(presigs.data() + presigs.size()),
                          [](uint8_t a) { return 0 == a; }));
}

TEST_F(EpidMemberTest, AddPreSigsAddsCorrectNumberOfPresigsGivenValidInput) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  // For a test purposes allocate an arrays of precomputed signatures with
  // all elements initialized to the same precomputed signature.
  // Warning: Do not use precomputed signatures twice in production code!
  std::vector<PreComputedSignature> presigs1(2,
                                             this->kPrecomputedSignatures[0]);
  std::vector<PreComputedSignature> presigs2 = presigs1;

  // add
  ASSERT_EQ(kEpidNoErr,
            EpidAddPreSigs(member, presigs1.size(), presigs1.data()));
  // extend
  ASSERT_EQ(kEpidNoErr,
            EpidAddPreSigs(member, presigs2.size(), presigs2.data()));
  // add empty pre-computed signatures array does not affect internal pool
  ASSERT_EQ(kEpidNoErr, EpidAddPreSigs(member, 0, presigs2.data()));
  EXPECT_EQ(presigs1.size() + presigs2.size(), EpidGetNumPreSigs(member));
}

///////////////////////////////////////////////////////////////////////
// EpidGetNumPreSigs
TEST_F(EpidMemberTest, GetNumPreSigsReturnsZeroGivenNullptr) {
  EXPECT_EQ((size_t)0, EpidGetNumPreSigs(nullptr));
}

TEST_F(EpidMemberTest, NumPreSigsForNewleyCreatedContextIsZero) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  EXPECT_EQ((size_t)0, EpidGetNumPreSigs(member));
}

TEST_F(EpidMemberTest, GetNumPreSigsReturnsNumberOfAddedPresigs) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  // For a test purposes allocate an array of precomputed signatures with
  // all elements initialized to the same precomputed signature.
  // Warning: Do not use precomputed signatures twice in production code!
  std::vector<PreComputedSignature> presigs(5, this->kPrecomputedSignatures[0]);

  THROW_ON_EPIDERR(EpidAddPreSigs(member, presigs.size(), presigs.data()));
  EXPECT_EQ(presigs.size(), EpidGetNumPreSigs(member));
}
///////////////////////////////////////////////////////////////////////
// EpidWritePreSigs
TEST_F(EpidMemberTest, WritePreSigsFailsGivenNullPointer) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  PreComputedSignature presig;

  EXPECT_EQ(kEpidBadArgErr, EpidWritePreSigs(nullptr, &presig, 0));
}

TEST_F(EpidMemberTest, WritePreSigsFailsGivenWrongNumberOfPresigs) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature presig = this->kPrecomputedSignatures[0];

  // add one pre-computed signature
  THROW_ON_EPIDERR(EpidAddPreSigs(member, 1, &presig));
  // export more pre-computed signatures than available
  EXPECT_EQ(kEpidBadArgErr, EpidWritePreSigs(member, &presig, 2));
}

TEST_F(EpidMemberTest, WritePreSigsClearsPresigsOnSuccess) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  std::vector<PreComputedSignature> presigs(
      COUNT_OF(this->kPrecomputedSignatures));
  presigs.assign(std::begin(this->kPrecomputedSignatures),
                 std::end(this->kPrecomputedSignatures));

  THROW_ON_EPIDERR(EpidAddPreSigs(member, presigs.size(), presigs.data()));

  // can export some but not all
  EXPECT_EQ(kEpidNoErr, EpidWritePreSigs(member, presigs.data(), 1));
  EXPECT_EQ(presigs.size() - 1, EpidGetNumPreSigs(member));
  // can export all the rest
  EXPECT_EQ(kEpidNoErr,
            EpidWritePreSigs(member, presigs.data() + 1, presigs.size() - 1));
  // check that all exported
  EXPECT_EQ((size_t)0, EpidGetNumPreSigs(member));
  // check that both write operations export (and leave) correct values.
  EXPECT_EQ(presigs.end(), std::unique(presigs.begin(), presigs.end()));
}

TEST_F(EpidMemberTest, CanWriteAddedPresigs) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature presig0 = this->kPrecomputedSignatures[0];
  PreComputedSignature presig1 = this->kPrecomputedSignatures[1];
  PreComputedSignature presigs[2] = {presig0, presig1};

  THROW_ON_EPIDERR(EpidAddPreSigs(member, COUNT_OF(presigs), presigs));

  EXPECT_EQ(kEpidNoErr, EpidWritePreSigs(member, presigs, COUNT_OF(presigs)));
  // compare ignoring order
  EXPECT_TRUE((presig0 == presigs[0] && presig1 == presigs[1]) ||
              (presig0 == presigs[1] && presig1 == presigs[0]));
}

TEST_F(EpidMemberTest, CanWriteGeneratedPresigs) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature zero_buffer;
  memset(&zero_buffer, 0, sizeof(zero_buffer));
  PreComputedSignature presigs[2] = {zero_buffer, zero_buffer};

  THROW_ON_EPIDERR(EpidAddPreSigs(member, COUNT_OF(presigs), nullptr));

  EXPECT_EQ(kEpidNoErr, EpidWritePreSigs(member, presigs, COUNT_OF(presigs)));
  // check pre-computed signature were written
  EXPECT_NE(zero_buffer, presigs[0]);
  EXPECT_NE(zero_buffer, presigs[1]);
}

TEST_F(EpidMemberTest, WritePreSigsCanWriteZeroPresigs) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature presig;

  EXPECT_EQ(kEpidNoErr, EpidWritePreSigs(member, &presig, 0));
}

}  // namespace
