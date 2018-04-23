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
 * \brief Test fixture class for EpidVerifier.
 */
#ifndef EPID_VERIFIER_UNITTESTS_VERIFIER_TESTHELPER_H_
#define EPID_VERIFIER_UNITTESTS_VERIFIER_TESTHELPER_H_

#include <vector>

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/api.h"
}

/// Test fixture class for EpidVerifier
class EpidVerifierTest : public ::testing::Test {
 public:
  /// Serialized identity element in G1
  static const G1ElemStr kG1IdentityStr;
  /// test public key
  static const GroupPubKey kPubKeyStr;
  /// test public key from Ikgf
  static const GroupPubKey kPubKeyIkgfStr;
  /// test public key of revoked group from Ikgf
  static const GroupPubKey kPubKeyRevGroupIkgfStr;
  /// verifier pre-computation data associated with pub_key_str
  static const VerifierPrecomp kVerifierPrecompStr;
  /// verifier pre-computation data associated with pub_key_str from Ikgf
  static const VerifierPrecomp kVerifierPrecompIkgfStr;
  /// Intel(R) EPID 2.0 parameters
  static const Epid2Params kParamsStr;
  /// public key in Grp01
  static const GroupPubKey kGrp01Key;
  /// private key based revocation list in Grp01
  static const std::vector<uint8_t> kGrp01PrivRl;
  /// signature based revocation list in Grp01
  static const std::vector<uint8_t> kGrp01SigRl;
  /// signature based revocation list from Ikgf
  static const std::vector<uint8_t> kSigRlIkgf;
  /// empty signature based revocation list from Ikgf
  static const std::vector<uint8_t> kEmptySigRlIkgf;
  /// number of SigRl entries for Grp01
  static const uint32_t kGrp01SigRlN2 = 50;
  /// verifier revocation list in Grp01 with one entry
  static const std::vector<uint8_t> kGrp01VerRlOneEntry;
  /// verifier revocation list in Grp01
  static const std::vector<uint8_t> kGrp01VerRl;
  /// empty verifier revocation in Grp01
  static const std::vector<uint8_t> kEmptyGrp01VerRl;
  /// C string with a message "test message"
  static const std::vector<uint8_t> kTest0;
  /// the message "test1"
  static const std::vector<uint8_t> kTest1;
  /// the basename "basename"
  static const std::vector<uint8_t> kBasename;
  /// the basename "basename1"
  static const std::vector<uint8_t> kBasename1;
  /// Signature of Test0 with RandomBase by Grp01 Member0 using Sha256
  static const std::vector<uint8_t> kSigGrp01Member0Sha256RandombaseTest0;
  /// Signature of Test with RandomBase, Member0 using Sha256 from Ikgf
  static const std::vector<uint8_t> kSigMember0Sha256RandombaseMsg0Ikgf;
  /// Signature of Test1 with RandomBase by Grp01 Member0 using Sha384
  static const std::vector<uint8_t> kSigGrp01Member0Sha384RandombaseTest0;
  /// Signature of Test1 with RandomBase by Grp01 Member0 using Sha512
  static const std::vector<uint8_t> kSigGrp01Member0Sha512RandombaseTest0;
  /// Signature of Test1 with RandomBase by Grp01 Member0 using Sha512_256
  static const std::vector<uint8_t> kSigGrp01Member0Sha512256RandombaseTest1;
  /// Sig of Test1 with RandomBase by Grp01(no SigRl)  Member0 using Sha256
  static const std::vector<uint8_t>
      kSigGrp01Member0Sha256RandombaseTest1NoSigRl;
  /// Sig of Test1 with Basename1 by Grp01(no SigRl) Member0 using Sha256
  static const std::vector<uint8_t> kSigGrp01Member0Sha256Basename1Test1NoSigRl;
  /// Sig of Test1 with Basename1 by Member0 using Sha256 from Ikgf
  static const std::vector<uint8_t> kSigSha256Basename1Test1NoSigRlIkgf;
  /// Sig of Test1 with RandomBase by Grp01(no SigRl) Member0 using Sha384
  static const std::vector<uint8_t>
      kSigGrp01Member0Sha384RandombaseTest1NoSigRl;
  /// Sig of Test1 with RandomBase by Grp01(no SigRl) Member0 using Sha512
  static const std::vector<uint8_t>
      kSigGrp01Member0Sha512RandombaseTest1NoSigRl;
  /// group based rl test data (empty rl)
  static const std::vector<uint8_t> kGroupRlEmptyBuf;
  /// group based rl test data (v=3, n=3, 3 revoked gid)
  static const std::vector<uint8_t> kGroupRl3GidBuf;
  /// group based rl test data (v=3, n=0, 3 revoked gid)
  static const std::vector<uint8_t> kGroupRl3GidN0Buf;
  /// group based rl test data (v=3, n=2, 3 revoked gid)
  static const std::vector<uint8_t> kGroupRl3GidN2Buf;
  /// group based rl test data (v=3, n=4, 3 revoked gid)
  static const std::vector<uint8_t> kGroupRl3GidN4Buf;
  /// a message
  static const std::vector<uint8_t> kMsg0;
  /// a message
  static const std::vector<uint8_t> kMsg1;
  /// a basename
  static const std::vector<uint8_t> kBsn0;
  /// a basename
  static const std::vector<uint8_t> kBsn1;

  /// a group revocation list
  static const std::vector<uint8_t> kGrpRl;
  /// a group revocation list from Ikgf
  static const std::vector<uint8_t> kGrpRlIkgf;
  /// a group revocation list with single group revoked
  static const std::vector<uint8_t> kGrpRlRevokedGrpXOnlyEntry;
  /// a group revocation list with multiple entries
  static const std::vector<uint8_t> kGrpRlRevokedGrpXFirstEntry;
  /// a group revocation list with multiple entries
  static const std::vector<uint8_t> kGrpRlRevokedGrpXMiddleEntry;
  /// a group revocation list with multiple entries
  static const std::vector<uint8_t> kGrpRlRevokedGrpXLastEntry;
  /// private key based revocation list from Ikgf
  static const std::vector<uint8_t> kPrivRlIkgf;
  /// empty private key based revocation list from Ikgf
  static const std::vector<uint8_t> kEmptyPrivRlIkgf;

  /// a group key in group X
  static const GroupPubKey kGrpXKey;
  /// the privrl of group X
  static const std::vector<uint8_t> kGrpXPrivRl;

  /// the privrl of group X with single entry PrivKey000 revoked
  static const std::vector<uint8_t> kGrpXPrivRlRevokedPrivKey000OnlyEntry;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRl;
  /// a verifierrl of group X with bsn0 and SHA256 for some verifier
  static const std::vector<uint8_t> kGrpXBsn0Sha256VerRl;
  /// a verifierrl of group X with bsn0 and SHA384 for some verifier
  static const std::vector<uint8_t> kGrpXBsn0Sha384VerRl;
  /// a verifierrl of group X with bsn0 and SHA512 for some verifier
  static const std::vector<uint8_t> kGrpXBsn0Sha512VerRl;
  /// a verifierrl of group X with bsn0 and SHA512/256 for some verifier
  static const std::vector<uint8_t> kGrpXBsn0Sha512256VerRl;
  /// a verifierrl of group X with bsn0 for some verifier with single entry
  static const std::vector<uint8_t> kGrpXBsn0VerRlSingleEntry;
  /// a verifierrl of group X with bsn1 for some verifier
  static const std::vector<uint8_t> kGrpXBsn1VerRl;
  /// a verifierrl of group X with bsn1 for some verifier with 0-2 revoked
  static const std::vector<uint8_t> kGrpXBsn1VerRl_012;

  /// the sigrl of group X corrputed
  static const std::vector<uint8_t> kGrpXSigRlVersion2;

  /// a group key in group Y
  static const GroupPubKey kGrpYKey;
  /// the privrl of group Y
  static const std::vector<uint8_t> kGrpYPrivRl;
  /// the sigrl of group Y
  static const std::vector<uint8_t> kGrpYSigRl;
  /// a verifierrl of group Y for some verifier
  static const std::vector<uint8_t> kGrpYVerRl;

  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Sha256Bsn0Msg0OnlyEntry;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Sha256Bsn0Msg0FirstEntry;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Sha256Bsn0Msg0MiddleEntry;
  /// the sigrl of group X
  static const std::vector<uint8_t> kGrpXSigRlMember0Sha256Bsn0Msg0LastEntry;

  /// signature of msg0 by member0 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn0Msg0;
  /// signature of msg0 by member0 with Sha256 bsn0 from Ikgf
  static const std::vector<uint8_t> kSigMember0Sha256Bsn0Msg0Ikgf;
  /// signature of msg0 by member0 with Sha256 bsn0 from Ikgf with empty SigRl
  static const std::vector<uint8_t> kSigMember0Sha256Bsn0Msg0EmptySigRlIkgf;
  /// signature of msg0 by member0 with Sha256 bsn0 from Ikgf without SigRl
  static const std::vector<uint8_t> kSigMember0Sha256Bsn0Msg0NoSigRlIkgf;
  /// signature of msg0 by member0 from SigRl first entry with Sha256 bsn0 from
  /// Ikgf
  static const std::vector<uint8_t> kSigRevSigMember0Sha256Bsn0Msg0Ikgf;
  /// signature of msg0 by member0 from revoked Group with Sha256 bsn0 from Ikgf
  static const std::vector<uint8_t> kRevGroupSigMember0Sha256Bsn0Msg0Ikgf;
  /// signature of msg0 by member0 of groupX with Sha256 bsn0 single entry sigrl
  static const std::vector<uint8_t>
      kSigGrpXMember0Sha256Bsn0Msg0SingleEntrySigRl;
  /// signature of msg0 by member0 of groupX with Sha256 bsn0 with revoked key
  /// 000
  static const std::vector<uint8_t> kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;
  /// signature of msg0 by member0 with Sha256 bsn0 with revoked key from Ikgf
  static const std::vector<uint8_t> kSigRevokedPrivKeySha256Bsn0Msg0Ikgf;
  /// signature of msg0 by member0 of groupX with Sha256 bsn0 with revoked key
  /// 001
  static const std::vector<uint8_t> kSigGrpXRevokedPrivKey001Sha256Bsn0Msg0;
  /// signature of msg0 by member0 of groupX with Sha256 bsn0 with revoked key
  /// 002
  static const std::vector<uint8_t> kSigGrpXRevokedPrivKey002Sha256Bsn0Msg0;
  /// signature of msg1 by member0 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn0Msg1;
  /// signature of msg0 by member0 of groupX with Sha256 bsn1
  static const std::vector<uint8_t> kSigGrpXMember0Sha256Bsn1Msg0;
  /// signature of msg0 by member0 of groupX with Sha256 rnd base
  static const std::vector<uint8_t> kSigGrpXMember0Sha256RandbaseMsg0;
  /// signature of msg0 by member0 of groupA with Sha256 rnd base
  static const std::vector<uint8_t> kSigMember0Sha256RandbaseMsg0Ikgf;
  /// signature of msg1 by member0 of groupX with Sha256 rnd base
  static const std::vector<uint8_t> kSigGrpXMember0Sha256RandbaseMsg1;
  /// signature of msg0 by member0 of groupX with Sha384 bsn0
  static const std::vector<uint8_t> kSigGrpXMember0Sha384Bsn0Msg0;
  /// signature of msg0 by member0 of groupX with Sha384 rnd base
  static const std::vector<uint8_t> kSigGrpXMember0Sha384RandbaseMsg0;
  /// signature of msg0 by member0 of groupX with Sha512 bsn0
  static const std::vector<uint8_t> kSigGrpXMember0Sha512Bsn0Msg0;
  /// signature of msg0 by member0 of groupX with Sha512 rnd base
  static const std::vector<uint8_t> kSigGrpXMember0Sha512RandbaseMsg0;
  /// signature of msg0 by member0 of groupX with Sha512256 bsn0
  static const std::vector<uint8_t> kSigGrpXMember0Sha512256Bsn0Msg0;
  /// signature of msg0 by member0 of groupX with Sha512256 rnd base
  static const std::vector<uint8_t> kSigGrpXMember0Sha512256RandbaseMsg0;

  /// signature of msg0 by verrevokedmember0 of groupX Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXVerRevokedMember0Sha256Bsn0Msg0;
  /// signature of msg0 by verrevokedmember1 of groupX Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXVerRevokedMember1Sha256Bsn0Msg0;
  /// signature of msg0 by verrevokedmember2 of groupX Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXVerRevokedMember2Sha256Bsn0Msg0;
  /// signature of msg0 by verrevokedmember3 of groupX Sha256 bsn1
  static const std::vector<uint8_t> kSigGrpXVerRevokedMember3Sha256Bsn1Msg0;
  /// signature of msg0 by member1 of groupX with Sha256 bsn0
  static const std::vector<uint8_t> kSigGrpXMember1Sha256Bsn0Msg0;

  /////////////////////////////////////////////////////////////////////
  // EpidVerify Signature Based Revocation List Reject
  /// GroupPubKey to be used for EpidVerify Signature Based Revocation List
  /// Reject tests
  static const GroupPubKey kPubKeySigRlVerify;
  /// SigRl with 1 entry
  static const std::vector<uint8_t> kSigRlSingleEntry;
  /// SigRl with 1 entry
  static const std::vector<uint8_t> kSigRlFiveEntries;
  /// First entry in sigrl_five_entries
  static const EpidSignature kSignatureSigrlFirst;
  /// Middle entry in sigrl_five_entries
  static const EpidSignature kSignatureSigrlMiddle;
  /// Last entry in sigrl_five_entries
  static const EpidSignature kSignatureSigrlLast;

  /// setup called before each TEST_F starts
  virtual void SetUp() {}
  /// teardown called after each TEST_F finishes
  virtual void TearDown() {}

  /// value "1" represented as an octstr constant
  /*!
  this value is used frequently to set 32 bit fields. describing as a constant
  here to reduce replication in code.
  */
  static const OctStr32 kOctStr32_1;
};

#endif  // EPID_VERIFIER_UNITTESTS_VERIFIER_TESTHELPER_H_
