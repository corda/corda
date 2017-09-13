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
 * \brief EcdsaSignBuffer unit tests.
 */

#include <cstdint>
#include <cstring>
#include <vector>

#include "gtest/gtest.h"

extern "C" {
#include "epid/common/math/ecdsa.h"
}

#include "epid/common-testhelper/prng-testhelper.h"

bool operator==(EcdsaSignature const& lhs, EcdsaSignature const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}

namespace {
/// Fill message buffer
/*!

 Fill a message buffer

 \param[in] buf
 pointer to buffer to be filled
 \param[in] buf_len
 size of buffer in bytes

 \returns ::EpidStatus
*/
static EpidStatus FillMessage(uint8_t* buf, size_t buf_len) {
  if (!buf) return kEpidBadArgErr;
  if (buf_len <= 0) return kEpidBadArgErr;
  for (size_t n = 0; n < buf_len; n++) {
    buf[n] = (uint8_t)n;
  }
  return kEpidNoErr;
}
class EcdsaSignBufferTest : public ::testing::Test {
 public:
  /// Signer's static private key (ECDSA-256 RFC 4754 Test Vector)
  static const EcdsaPrivateKey kPrivkey0;
  /// Signer's static public key (ECDSA-256 RFC 4754 Test Vector)
  static const EcdsaPublicKey kPubkey0;
  /// Signer's ephemeral private key (ECDSA-256 RFC 4754 Test Vector)
  static const EcdsaPrivateKey kEphPrivkey0;
  /// Message (ECDSA-256 RFC 4754 Test Vector)
  static const std::vector<uint8_t> kMsg0;
  /// Signature of msg0 with privkey0 and kEphPrivkey0
  static const EcdsaSignature kSig_msg0_key0;
  /// Signature of empty msg with privkey0 and kEphPrivkey0
  static const EcdsaSignature kSig_emptymsg_key0;
  /// Signature of 1M msg with privkey0 and kEphPrivkey0
  static const EcdsaSignature kSig_1Mmsg_key0;
};

const EcdsaPrivateKey EcdsaSignBufferTest::kPrivkey0 = {
    0xDC, 0x51, 0xD3, 0x86, 0x6A, 0x15, 0xBA, 0xCD, 0xE3, 0x3D, 0x96,
    0xF9, 0x92, 0xFC, 0xA9, 0x9D, 0xA7, 0xE6, 0xEF, 0x09, 0x34, 0xE7,
    0x09, 0x75, 0x59, 0xC2, 0x7F, 0x16, 0x14, 0xC8, 0x8A, 0x7F};
const EcdsaPublicKey EcdsaSignBufferTest::kPubkey0 = {
    0x24, 0x42, 0xA5, 0xCC, 0x0E, 0xCD, 0x01, 0x5F, 0xA3, 0xCA, 0x31,
    0xDC, 0x8E, 0x2B, 0xBC, 0x70, 0xBF, 0x42, 0xD6, 0x0C, 0xBC, 0xA2,
    0x00, 0x85, 0xE0, 0x82, 0x2C, 0xB0, 0x42, 0x35, 0xE9, 0x70, 0x6F,
    0xC9, 0x8B, 0xD7, 0xE5, 0x02, 0x11, 0xA4, 0xA2, 0x71, 0x02, 0xFA,
    0x35, 0x49, 0xDF, 0x79, 0xEB, 0xCB, 0x4B, 0xF2, 0x46, 0xB8, 0x09,
    0x45, 0xCD, 0xDF, 0xE7, 0xD5, 0x09, 0xBB, 0xFD, 0x7D};
const EcdsaPrivateKey EcdsaSignBufferTest::kEphPrivkey0 = {
    0x9E, 0x56, 0xF5, 0x09, 0x19, 0x67, 0x84, 0xD9, 0x63, 0xD1, 0xC0,
    0xA4, 0x01, 0x51, 0x0E, 0xE7, 0xAD, 0xA3, 0xDC, 0xC5, 0xDE, 0xE0,
    0x4B, 0x15, 0x4B, 0xF6, 0x1A, 0xF1, 0xD5, 0xA6, 0xDE, 0xCE};
/*

Ephemeral public key expected to be generated for kEphPrivkey0:
gkx: ephemeral public key:
CB28E099 9B9C7715 FD0A80D8 E47A7707 9716CBBF 917DD72E 97566EA1 C066957C
gky: ephemeral public key:
2B57C023 5FB74897 68D058FF 4911C20F DBE71E36 99D91339 AFBB903E E17255DC
*/

const std::vector<uint8_t> EcdsaSignBufferTest::kMsg0 = {'a', 'b', 'c'};

const EcdsaSignature EcdsaSignBufferTest::kSig_msg0_key0 = {
    0xCB, 0x28, 0xE0, 0x99, 0x9B, 0x9C, 0x77, 0x15, 0xFD, 0x0A, 0x80,
    0xD8, 0xE4, 0x7A, 0x77, 0x07, 0x97, 0x16, 0xCB, 0xBF, 0x91, 0x7D,
    0xD7, 0x2E, 0x97, 0x56, 0x6E, 0xA1, 0xC0, 0x66, 0x95, 0x7C, 0x86,
    0xFA, 0x3B, 0xB4, 0xE2, 0x6C, 0xAD, 0x5B, 0xF9, 0x0B, 0x7F, 0x81,
    0x89, 0x92, 0x56, 0xCE, 0x75, 0x94, 0xBB, 0x1E, 0xA0, 0xC8, 0x92,
    0x12, 0x74, 0x8B, 0xFF, 0x3B, 0x3D, 0x5B, 0x03, 0x15,
};
const EcdsaSignature EcdsaSignBufferTest::kSig_emptymsg_key0 = {
    0xCB, 0x28, 0xE0, 0x99, 0x9B, 0x9C, 0x77, 0x15, 0xFD, 0x0A, 0x80,
    0xD8, 0xE4, 0x7A, 0x77, 0x07, 0x97, 0x16, 0xCB, 0xBF, 0x91, 0x7D,
    0xD7, 0x2E, 0x97, 0x56, 0x6E, 0xA1, 0xC0, 0x66, 0x95, 0x7C, 0x8c,
    0x09, 0x5c, 0xec, 0xd5, 0xcf, 0xec, 0x1e, 0xa5, 0xb6, 0xa6, 0x44,
    0x1e, 0x12, 0x3d, 0x30, 0xff, 0x97, 0xdd, 0x4b, 0x44, 0xc1, 0x70,
    0x7c, 0x95, 0x9d, 0x7f, 0x46, 0x86, 0x73, 0x55, 0xae,
};
const EcdsaSignature EcdsaSignBufferTest::kSig_1Mmsg_key0 = {
    0xCB, 0x28, 0xE0, 0x99, 0x9B, 0x9C, 0x77, 0x15, 0xFD, 0x0A, 0x80,
    0xD8, 0xE4, 0x7A, 0x77, 0x07, 0x97, 0x16, 0xCB, 0xBF, 0x91, 0x7D,
    0xD7, 0x2E, 0x97, 0x56, 0x6E, 0xA1, 0xC0, 0x66, 0x95, 0x7C, 0xf9,
    0xa5, 0x3a, 0xbf, 0x22, 0xe7, 0xf3, 0x97, 0x5a, 0x8c, 0xce, 0xb8,
    0xca, 0x7b, 0xae, 0x9d, 0xd8, 0x7f, 0x43, 0xa9, 0xef, 0x40, 0x78,
    0x56, 0x37, 0xcc, 0xb2, 0xda, 0x1e, 0x04, 0x31, 0x03,
};

static int __STDCALL constant_32byte_endianswap_prng(unsigned int* random_data,
                                                     int num_bits,
                                                     void* user_data) {
  if (256 != num_bits) return -1;
  for (int i = 0; i < 32; i++) {
    ((uint8_t*)random_data)[i] = ((uint8_t*)user_data)[31 - i];
  }
  return 0;
}

static int __STDCALL contextless_kEphPrivkey0_prng(unsigned int* random_data,
                                                   int num_bits,
                                                   void* user_data) {
  (void)user_data;
  return constant_32byte_endianswap_prng(
      random_data, num_bits,
      (void*)(EcdsaSignBufferTest::kEphPrivkey0.data.data));
}

TEST_F(EcdsaSignBufferTest, FailsGivenNullPtr) {
  uint8_t msg[1];
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  EcdsaSignature signature;

  EXPECT_EQ(kEpidBadArgErr,
            EcdsaSignBuffer(nullptr, sizeof(msg), &this->kPrivkey0, rnd_func,
                            rnd_param, &signature));
  EXPECT_EQ(kEpidBadArgErr, EcdsaSignBuffer(msg, sizeof(msg), nullptr, rnd_func,
                                            rnd_param, &signature));
  EXPECT_EQ(kEpidBadArgErr, EcdsaSignBuffer(msg, sizeof(msg), &this->kPrivkey0,
                                            nullptr, rnd_param, &signature));
  EXPECT_EQ(kEpidBadArgErr, EcdsaSignBuffer(msg, sizeof(msg), &this->kPrivkey0,
                                            rnd_func, rnd_param, nullptr));
}

TEST_F(EcdsaSignBufferTest, SignsEmptyMessage) {
  uint8_t msg[1];
  EcdsaSignature signature;

  EXPECT_EQ(
      kEpidNoErr,
      EcdsaSignBuffer(msg, 0, &this->kPrivkey0, constant_32byte_endianswap_prng,
                      (void*)&(this->kEphPrivkey0), &signature));
  EXPECT_EQ(this->kSig_emptymsg_key0, signature);
  EXPECT_EQ(kEpidNoErr,
            EcdsaSignBuffer(nullptr, 0, &this->kPrivkey0,
                            constant_32byte_endianswap_prng,
                            (void*)&(this->kEphPrivkey0), &signature));
  EXPECT_EQ(this->kSig_emptymsg_key0, signature);
}

TEST_F(EcdsaSignBufferTest, WorksGivenNoRndParam) {
  EcdsaSignature signature;

  EXPECT_EQ(
      kEpidNoErr,
      EcdsaSignBuffer(this->kMsg0.data(), this->kMsg0.size(), &this->kPrivkey0,
                      contextless_kEphPrivkey0_prng, nullptr, &signature));
  EXPECT_EQ(this->kSig_msg0_key0, signature);
}

TEST_F(EcdsaSignBufferTest, SignsShortMessage) {
  EcdsaSignature signature;

  EXPECT_EQ(kEpidNoErr,
            EcdsaSignBuffer(this->kMsg0.data(), this->kMsg0.size(),
                            &this->kPrivkey0, constant_32byte_endianswap_prng,
                            (void*)&(this->kEphPrivkey0), &signature));
  EXPECT_EQ(this->kSig_msg0_key0, signature);
}

TEST_F(EcdsaSignBufferTest, SignsLongMessage) {
  std::vector<uint8_t> msg_1mb(0x100000);
  FillMessage(msg_1mb.data(), msg_1mb.size());

  EcdsaSignature signature;

  EXPECT_EQ(kEpidNoErr,
            EcdsaSignBuffer(msg_1mb.data(), msg_1mb.size(), &this->kPrivkey0,
                            constant_32byte_endianswap_prng,
                            (void*)&(this->kEphPrivkey0), &signature));
  EXPECT_EQ(this->kSig_1Mmsg_key0, signature);
}

TEST_F(EcdsaSignBufferTest, FailsGivenInvalidPrivateKey) {
  uint8_t msg[1] = {0x00};
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  EcdsaSignature signature;

  EcdsaPrivateKey invalid_prikey;
  memset(&invalid_prikey, 0xff, sizeof(invalid_prikey));

  EXPECT_EQ(kEpidBadArgErr, EcdsaSignBuffer(msg, sizeof(msg), &invalid_prikey,
                                            rnd_func, rnd_param, &signature));
}

}  // namespace
