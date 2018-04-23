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
 * \brief Test fixture class for Epid11Verifier.
 */
#ifndef EPID_VERIFIER_1_1_UNITTESTS_VERIFIER_TESTHELPER_H_
#define EPID_VERIFIER_1_1_UNITTESTS_VERIFIER_TESTHELPER_H_

#include <vector>

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/1.1/api.h"
}

/// Test fixture class for Epid11Verifier
class Epid11VerifierTest : public ::testing::Test {
 public:
  /// Serialized identity element in G3
  static const Epid11G3ElemStr kG3IdentityStr;
  /// test public key
  static const Epid11GroupPubKey kPubKeyStr;
  /// the message "test message"
  static const std::vector<uint8_t> kMsg0;
  /// the basename "basename1"
  static const std::vector<uint8_t> kBsn0;
  /// the privrl of group X
  static const std::vector<uint8_t> kGrpXPrivRl;
  /// a single entry privrl for group X
  static const std::vector<uint8_t> kGrpXPrivRlSingleEntry;
  /// verifier pre-computation data associated with pub_key_str
  static const Epid11VerifierPrecomp kVerifierPrecompStr;
  /// Intel(R) EPID 1.1 parameters
  static const Epid11Params kParamsStr;
  /// signature of msg0 by member0 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn0Msg0;
  /// signature of msg0 by member0 of groupX with Sha256 bsn0 with one NrProof
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn0Msg0SingleEntry;
  /// signature of msg0 by member0 of groupX with Sha256 bsn0 with three NrProof
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn0Msg0ThreeEntry;
  /// signature of msg1 by member0 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn0Msg1;
  /// signature of msg0 by member0 of groupX with Sha256 bsn1
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn1Msg0;
  /// signature of msg0 by member0 of groupX with Sha256 rnd base
  static const std::vector<uint8_t> kSigGrpXMember0Sha256RandbaseMsg0;
  /// signature of msg0 by member0 of groupX with Sha256 rnd base with n2==1
  static const std::vector<uint8_t> kSigGrpXMember0Sha256RandbaseMsg0N2One;
  /// signature of msg1 by member0 of groupX with Sha256 rnd base
  static const std::vector<uint8_t> kSigGrpXMember0Sha256RandbaseMsg1;
  /// signature of msg0 by member1 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXMember1Sha256Bsn0Msg0;
  /// signature of msg0 by priv revoked member 0 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;
  /// signature of msg0 by priv revoked member 1 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXRevokedPrivKey001Sha256Bsn0Msg0;
  /// signature of msg0 by priv revoked member 2 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXRevokedPrivKey002Sha256Bsn0Msg0;

  /// group based rl test data (empty rl)
  static const std::vector<uint8_t> kGroupRlEmptyBuf;
  /// group based rl test data (v=3, n=3, 3 revoked gid)
  static const std::vector<uint8_t> kGroupRl3GidBuf;

  /// a group revocation list with single group revoked
  static const std::vector<uint8_t> kGrpRlRevokedGrpXSingleEntry;
  /// a group revocation list with multiple entries
  static const std::vector<uint8_t> kGrpRlRevokedGrpXFirstEntry;
  /// a group revocation list with multiple entries
  static const std::vector<uint8_t> kGrpRlRevokedGrpXMiddleEntry;
  /// a group revocation list with multiple entries
  static const std::vector<uint8_t> kGrpRlRevokedGrpXLastEntry;

  /// signature based revocation list
  static const std::vector<uint8_t> kSigRl;
  /// signature based revocation list (empty rl)
  static const std::vector<uint8_t> kEmptySigRl;

  /// setup called before each TEST_F starts
  virtual void SetUp() {}
  /// teardown called after each TEST_F finishes
  virtual void TearDown() {}

  /// value "1" represented as an octstr constant
  /*!
  this value is used frequently to set 32 bit fields. describing as a constant
  here
  to reduce replication in code.
  */
  static const OctStr32 kOctStr32_1;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Bsn0Msg0SingleEntry;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Bsn0Msg0FirstEntry;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Bsn0Msg0MiddleEntry;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Bsn0Msg0LastEntry;
};

#endif  // EPID_VERIFIER_1_1_UNITTESTS_VERIFIER_TESTHELPER_H_
