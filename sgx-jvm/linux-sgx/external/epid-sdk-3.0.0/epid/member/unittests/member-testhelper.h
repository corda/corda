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
 * \brief Member C++ wrapper interface.
 */
#ifndef EPID_MEMBER_UNITTESTS_MEMBER_TESTHELPER_H_
#define EPID_MEMBER_UNITTESTS_MEMBER_TESTHELPER_H_

#include <vector>

#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
}

/// C++ Wrapper to manage memory for MemberCtx via RAII
class MemberCtxObj {
 public:
  /// Create a MemberCtx
  explicit MemberCtxObj(GroupPubKey const& pub_key, PrivKey const& priv_key,
                        BitSupplier rnd_func, void* rnd_param);
  /// Create a MemberCtx given precomputation blob
  MemberCtxObj(GroupPubKey const& pub_key, PrivKey const& priv_key,
               MemberPrecomp const& precomp, BitSupplier rnd_func,
               void* rnd_param);

  // This class instances are not meant to be copied.
  // Explicitly delete copy constructor and assignment operator.
  MemberCtxObj(const MemberCtxObj&) = delete;
  MemberCtxObj& operator=(const MemberCtxObj&) = delete;

  /// Destroy the MemberCtx
  ~MemberCtxObj();
  /// get a pointer to the stored MemberCtx
  MemberCtx* ctx() const;
  /// cast operator to get the pointer to the stored MemberCtx
  operator MemberCtx*() const;
  /// const cast operator to get the pointer to the stored MemberCtx
  operator const MemberCtx*() const;

 private:
  /// The stored MemberCtx
  MemberCtx* ctx_;
};

/// Test fixture class for EpidMember
class EpidMemberTest : public ::testing::Test {
 public:
  /// test data
  static const GroupPubKey kGroupPublicKey;
  /// test data
  static const PrivKey kMemberPrivateKey;
  /// test data
  static const std::vector<uint8_t> kGroupPublicKeyDataIkgf;
  /// test data
  static const std::vector<uint8_t> kMemberPrivateKeyDataIkgf;
  /// test data
  static const MemberPrecomp kMemberPrecomp;
  /// test data
  static const PreComputedSignature kPrecomputedSignatures[2];
  /// test data
  static const std::vector<uint8_t> kGrp01Member0SigTest1Sha256;
  /// test data
  static const std::vector<uint8_t> kGrp01Member0SigTest1Sha384;
  /// test data
  static const std::vector<uint8_t> kGrp01Member0SigTest1Sha512;
  /// test data
  static const std::vector<uint8_t> kTest1Msg;
  /// signature based revocation list with 50 entries
  static std::vector<uint8_t> kSigRlData;
  /// signature based revocation list with 5 entries
  static std::vector<uint8_t> kSigRl5EntryData;
  /// a message
  static const std::vector<uint8_t> kMsg0;
  /// a message
  static const std::vector<uint8_t> kMsg1;
  /// a basename
  static const std::vector<uint8_t> kBsn0;
  /// a basename
  static const std::vector<uint8_t> kBsn1;

  /// a group key in group X
  static const GroupPubKey kGrpXKey;
  /// a compressed private key in group X
  static const CompressedPrivKey kGrpXMember9CompressedKey;
  /// a private key in group X
  static const PrivKey kGrpXMember9PrivKey;

  /// a group key in group Y
  static const GroupPubKey kGrpYKey;
  /// a compressed private key in group Y
  static const CompressedPrivKey kGrpYMember9CompressedKey;

  /// setup called before each TEST_F starts
  virtual void SetUp() {}
  /// teardown called after each TEST_F finishes
  virtual void TearDown() {}
};

#endif  // EPID_MEMBER_UNITTESTS_MEMBER_TESTHELPER_H_
