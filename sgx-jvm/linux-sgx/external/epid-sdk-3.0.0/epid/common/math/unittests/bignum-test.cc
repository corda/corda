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
 * \brief BigNum unit tests.
 */

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/bignum_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/bignum.h"
}

namespace {

// Use Test Fixture for SetUp and TearDown
class BigNumTest : public ::testing::Test {
 public:
  static const BigNumStr str_0;
  static const BigNumStr str_1;
  static const BigNumStr str_2;
  static const BigNumStr str_big;
  static const BigNumStr str_2big;
  static const BigNumStr str_large_m1;
  static const BigNumStr str_large;
  static const BigNumStr str_large_p1;
  static const BigNumStr str_32byte_high_bit_set;
  static const BigNumStr str_32byte_high;
  static const std::vector<unsigned char> vec_33byte_low;
  virtual void SetUp() {}

  virtual void TearDown() {}

  ::testing::AssertionResult CompareBigNumStr(const BigNumStr* expected,
                                              const BigNumStr* actual);

  ::testing::AssertionResult CompareBigNum(const BigNum* expected,
                                           const BigNum* actual);
};

const BigNumStr BigNumTest::str_0{
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

const BigNumStr BigNumTest::str_1{
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
const BigNumStr BigNumTest::str_2{
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02};
const BigNumStr BigNumTest::str_big{
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
const BigNumStr BigNumTest::str_2big{
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
const BigNumStr BigNumTest::str_large_m1{
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2,
    0x5E, 0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99,
    0x92, 0x1A, 0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0C};

/// Intel(R) EPID 2.0 parameter p
const BigNumStr BigNumTest::str_large{
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2,
    0x5E, 0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99,
    0x92, 0x1A, 0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D};
const BigNumStr BigNumTest::str_large_p1{
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2,
    0x5E, 0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99,
    0x92, 0x1A, 0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0E};
const BigNumStr BigNumTest::str_32byte_high{
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
const BigNumStr BigNumTest::str_32byte_high_bit_set{
    0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
const std::vector<unsigned char> BigNumTest::vec_33byte_low{
    0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

::testing::AssertionResult BigNumTest::CompareBigNumStr(
    const BigNumStr* expected, const BigNumStr* actual) {
  int size = sizeof(BigNumStr);
  unsigned char* expected_str = (unsigned char*)expected;
  unsigned char* actual_str = (unsigned char*)actual;
  for (int i = 0; i < size; ++i) {
    if (expected_str[i] != actual_str[i]) {
      return ::testing::AssertionFailure()
             << "Mismatch at " << i << " : Expected " << std::hex
             << expected_str[i] << " Found " << std::hex << actual_str[i];
    }
  }

  return ::testing::AssertionSuccess();
}

::testing::AssertionResult BigNumTest::CompareBigNum(const BigNum* expected_bn,
                                                     const BigNum* actual_bn) {
  size_t size = 0;
  std::vector<unsigned char> expected_str;
  std::vector<unsigned char> actual_str;
  // Use an extra huge size so we have plenty of room to check
  // overflow tests.  This assumes no tests try to create a number
  // bigger than 64 bytes.
  size = sizeof(BigNumStr) * 2;
  expected_str.resize(size, 0);
  actual_str.resize(size, 0);

  THROW_ON_EPIDERR(WriteBigNum(expected_bn, size, &expected_str[0]));
  THROW_ON_EPIDERR(WriteBigNum(actual_bn, size, &actual_str[0]));

  for (size_t i = 0; i < size; ++i) {
    if (expected_str[i] != actual_str[i]) {
      return ::testing::AssertionFailure() << "Numbers do not match";
    }
  }

  return ::testing::AssertionSuccess();
}

///////////////////////////////////////////////////////////////////////
// Create / Destroy

TEST_F(BigNumTest, NewCanCreate256BitBigNum) {
  BigNum* bn = nullptr;
  EXPECT_EQ(kEpidNoErr, NewBigNum(32, &bn));
  DeleteBigNum(&bn);
}

TEST_F(BigNumTest, NewFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr, NewBigNum(sizeof(BigNumStr), NULL));
}

TEST_F(BigNumTest, NewFailsGivenSizeZero) {
  BigNum* bn = nullptr;
  EXPECT_EQ(kEpidBadArgErr, NewBigNum(0, &bn));
  DeleteBigNum(&bn);
}

TEST_F(BigNumTest, DeleteBigNumNullsPointer) {
  BigNum* bn = nullptr;
  THROW_ON_EPIDERR(NewBigNum(sizeof(BigNumStr), &bn));
  DeleteBigNum(&bn);
  EXPECT_EQ(nullptr, bn);
}

TEST_F(BigNumTest, DeleteWorksGivenNullPointer) {
  BigNum* bn = nullptr;
  DeleteBigNum(nullptr);
  EXPECT_EQ(nullptr, bn);
}

///////////////////////////////////////////////////////////////////////
// Serialization

TEST_F(BigNumTest, ReadFailsGivenNullPtr) {
  BigNum* bn = nullptr;
  THROW_ON_EPIDERR(NewBigNum(sizeof(BigNumStr), &bn));
  EXPECT_EQ(kEpidBadArgErr, ReadBigNum(NULL, sizeof(BigNumStr), bn));
  EXPECT_EQ(kEpidBadArgErr,
            ReadBigNum(&this->str_large, sizeof(BigNumStr), NULL));
  DeleteBigNum(&bn);
}

TEST_F(BigNumTest, ReadFailsGivenInvalidBufferSize) {
  BigNumObj bn(32);
  EXPECT_EQ(kEpidBadArgErr, ReadBigNum(&this->str_0, 0, bn));
  EXPECT_EQ(kEpidBadArgErr,
            ReadBigNum(&this->str_0, std::numeric_limits<size_t>::max(), bn));
#if (SIZE_MAX >= 0x100000001)  // When size_t value allowed to be 0x100000001
  EXPECT_EQ(kEpidBadArgErr, ReadBigNum(&this->str_0, 0x100000001, bn));
#endif
}

TEST_F(BigNumTest, ReadFailsGivenTooBigBuffer) {
  BigNum* bn = nullptr;
  THROW_ON_EPIDERR(NewBigNum(sizeof(BigNumStr), &bn));
  EXPECT_NE(kEpidNoErr, ReadBigNum(&this->vec_33byte_low[0],
                                   this->vec_33byte_low.size(), bn));
  DeleteBigNum(&bn);
}

TEST_F(BigNumTest, WriteFailsGivenNullPtr) {
  BigNum* bn = nullptr;
  BigNumStr str = {0};
  THROW_ON_EPIDERR(NewBigNum(sizeof(BigNumStr), &bn));
  EXPECT_EQ(kEpidBadArgErr, WriteBigNum(NULL, sizeof(str), &str));
  EXPECT_EQ(kEpidBadArgErr, WriteBigNum(bn, 0, NULL));
  DeleteBigNum(&bn);
}

TEST_F(BigNumTest, WriteFailsGivenTooSmallBuffer) {
  BigNumStr str;
  BigNumObj bn(this->vec_33byte_low);
  EXPECT_NE(kEpidNoErr, WriteBigNum(bn, sizeof(str), &str));
}

TEST_F(BigNumTest, ReadCanDeSerializeBigNumStrZero) {
  BigNumObj bn_ref;
  BigNumObj bn;
  EXPECT_EQ(kEpidNoErr, ReadBigNum(&this->str_0, sizeof(this->str_0), bn));
  // No way to check this yet
}

TEST_F(BigNumTest, ReadCanDeSerializeBigNum) {
  BigNumObj bn;
  EXPECT_EQ(kEpidNoErr,
            ReadBigNum(&this->str_large, sizeof(this->str_large), bn));
  // No way to check this yet
}

TEST_F(BigNumTest, WriteCanSerializeBigNumZero) {
  BigNumObj bn;  // defaults to 0
  BigNumStr str;
  EXPECT_EQ(kEpidNoErr, WriteBigNum(bn, sizeof(str), &str));
  EXPECT_TRUE(CompareBigNumStr(&str, &this->str_0));
}

TEST_F(BigNumTest, DeSerializeFollowedBySerializeHasSameValue) {
  BigNumStr str;
  BigNumObj bn;
  EXPECT_EQ(kEpidNoErr,
            ReadBigNum(&this->str_large, sizeof(this->str_large), bn));
  EXPECT_EQ(kEpidNoErr, WriteBigNum(bn, sizeof(str), &str));
  EXPECT_TRUE(CompareBigNumStr(&this->str_large, &str));
}

///////////////////////////////////////////////////////////////////////
// Addition

TEST_F(BigNumTest, AddBadArgumentsFail) {
  BigNumObj bn;
  EXPECT_NE(kEpidNoErr, BigNumAdd(nullptr, nullptr, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumAdd(bn, nullptr, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumAdd(nullptr, bn, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumAdd(nullptr, nullptr, bn));
  EXPECT_NE(kEpidNoErr, BigNumAdd(bn, bn, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumAdd(nullptr, bn, bn));
  EXPECT_NE(kEpidNoErr, BigNumAdd(bn, nullptr, bn));
}

TEST_F(BigNumTest, AddZeroIsIdentity) {
  BigNumObj bn;
  BigNumObj bn_0(this->str_0);
  BigNumObj bn_large(this->str_large);
  EXPECT_EQ(kEpidNoErr, BigNumAdd(bn_large, bn_0, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_large));
}

TEST_F(BigNumTest, AddOneIncrements) {
  BigNumObj bn;
  BigNumObj bn_1(this->str_1);
  BigNumObj bn_large(this->str_large);
  BigNumObj bn_large_p1(this->str_large_p1);
  EXPECT_EQ(kEpidNoErr, BigNumAdd(bn_large, bn_1, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_large_p1));
}

TEST_F(BigNumTest, AddOneTo32ByteInTo32BytesFails) {
  BigNumObj bn(32);
  BigNumObj bn_1(this->str_1);
  BigNumObj bn_32high(this->str_32byte_high);
  EXPECT_NE(kEpidNoErr, BigNumAdd(bn_32high, bn_1, bn));
}

TEST_F(BigNumTest, AddOneTo32ByteInTo33BytesIncrements) {
  BigNumObj bn(33);
  BigNumObj bn_1(this->str_1);
  BigNumObj bn_32high(this->str_32byte_high);
  BigNumObj bn_33low(this->vec_33byte_low);
  EXPECT_EQ(kEpidNoErr, BigNumAdd(bn_32high, bn_1, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_33low));
}

///////////////////////////////////////////////////////////////////////
// Subtraction

TEST_F(BigNumTest, SubBadArgumentsFail) {
  BigNumObj bn;
  EXPECT_NE(kEpidNoErr, BigNumSub(nullptr, nullptr, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumSub(bn, nullptr, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumSub(nullptr, bn, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumSub(nullptr, nullptr, bn));
  EXPECT_NE(kEpidNoErr, BigNumSub(bn, bn, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumSub(nullptr, bn, bn));
  EXPECT_NE(kEpidNoErr, BigNumSub(bn, nullptr, bn));
}

TEST_F(BigNumTest, SubOneFromZeroFails) {
  BigNumObj bn;
  BigNumObj bn_0(this->str_0);
  BigNumObj bn_1(this->str_1);
  EXPECT_EQ(kEpidUnderflowErr, BigNumSub(bn_0, bn_1, bn));
}

TEST_F(BigNumTest, SubZeroIsIdentity) {
  BigNumObj bn;
  BigNumObj bn_0(this->str_0);
  BigNumObj bn_large(this->str_large);
  EXPECT_EQ(kEpidNoErr, BigNumSub(bn_large, bn_0, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_large));
}

TEST_F(BigNumTest, SubOneDecrements) {
  BigNumObj bn;
  BigNumObj bn_1(this->str_1);
  BigNumObj bn_large(this->str_large);
  BigNumObj bn_large_m1(this->str_large_m1);
  EXPECT_EQ(kEpidNoErr, BigNumSub(bn_large, bn_1, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_large_m1));
}

///////////////////////////////////////////////////////////////////////
// Multiplication

TEST_F(BigNumTest, MulBadArgumentsFail) {
  BigNumObj bn;
  EXPECT_NE(kEpidNoErr, BigNumMul(nullptr, nullptr, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumMul(bn, nullptr, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumMul(nullptr, bn, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumMul(nullptr, nullptr, bn));
  EXPECT_NE(kEpidNoErr, BigNumMul(bn, bn, nullptr));
  EXPECT_NE(kEpidNoErr, BigNumMul(nullptr, bn, bn));
  EXPECT_NE(kEpidNoErr, BigNumMul(bn, nullptr, bn));
}

TEST_F(BigNumTest, MulOneIsIdentity) {
  BigNumObj bn;
  BigNumObj bn_1(this->str_1);
  BigNumObj bn_large(this->str_large);
  EXPECT_EQ(kEpidNoErr, BigNumMul(bn_large, bn_1, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_large));
}

TEST_F(BigNumTest, MulTwoIsDouble) {
  BigNumObj bn;
  BigNumObj bn_2(this->str_2);
  BigNumObj bn_big(this->str_big);
  BigNumObj bn_2big(this->str_2big);
  EXPECT_EQ(kEpidNoErr, BigNumMul(bn_big, bn_2, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_2big));
}

TEST_F(BigNumTest, MulZeroIsZero) {
  BigNumObj bn;
  BigNumObj bn_0(this->str_0);
  BigNumObj bn_large(this->str_large);
  EXPECT_EQ(kEpidNoErr, BigNumMul(bn_large, bn_0, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_0));
}

TEST_F(BigNumTest, MulReportsErrorGivenOverflow) {
  BigNumObj bn(32);
  BigNumObj bn_2(this->str_2);
  BigNumObj bn_high_bit_set(this->str_32byte_high_bit_set);
  EXPECT_EQ(kEpidBadArgErr, BigNumMul(bn_high_bit_set, bn_2, bn));
}

TEST_F(BigNumTest, MulWorksWith264BitValue) {
  BigNumObj bn(33);
  BigNumObj bn_2(this->str_2);
  BigNumObj bn_high_bit_set(this->str_32byte_high_bit_set);
  BigNumObj bn_33low(this->vec_33byte_low);
  EXPECT_EQ(kEpidNoErr, BigNumMul(bn_high_bit_set, bn_2, bn));
  EXPECT_TRUE(CompareBigNum(bn, bn_33low));
}

///////////////////////////////////////////////////////////////////////
// Division

TEST_F(BigNumTest, DivFailsGivenNullPointer) {
  BigNumObj a, b, q, r;
  EXPECT_EQ(kEpidBadArgErr, BigNumDiv(nullptr, b, q, r));
  EXPECT_EQ(kEpidBadArgErr, BigNumDiv(a, nullptr, q, r));
  EXPECT_EQ(kEpidBadArgErr, BigNumDiv(a, b, nullptr, r));
  EXPECT_EQ(kEpidBadArgErr, BigNumDiv(a, b, q, nullptr));
}

TEST_F(BigNumTest, DivFailsGivenDivByZero) {
  BigNumObj a;
  BigNumObj zero(this->str_0);
  BigNumObj q, r;
  EXPECT_EQ(kEpidBadArgErr, BigNumDiv(a, zero, q, r));
}

TEST_F(BigNumTest, DivToOneKeepsOriginal) {
  BigNumObj a(this->str_large);
  BigNumObj zero(this->str_0);
  BigNumObj one(this->str_1);
  BigNumObj q, r;
  EXPECT_EQ(kEpidNoErr, BigNumDiv(a, one, q, r));
  EXPECT_TRUE(CompareBigNum(a, q));
  EXPECT_TRUE(CompareBigNum(zero, r));
}

TEST_F(BigNumTest, DivToItselfIsIdentity) {
  BigNumObj a(this->str_large);
  BigNumObj zero(this->str_0);
  BigNumObj one(this->str_1);
  BigNumObj q, r;
  EXPECT_EQ(kEpidNoErr, BigNumDiv(a, a, q, r));
  EXPECT_TRUE(CompareBigNum(one, q));
  EXPECT_TRUE(CompareBigNum(zero, r));
}

TEST_F(BigNumTest, DivOneByTwoIsZero) {
  BigNumObj zero(this->str_0);
  BigNumObj one(this->str_1);
  BigNumObj two(this->str_2);
  BigNumObj q, r;
  EXPECT_EQ(kEpidNoErr, BigNumDiv(one, two, q, r));
  EXPECT_TRUE(CompareBigNum(zero, q));
  EXPECT_TRUE(CompareBigNum(one, r));
}

///////////////////////////////////////////////////////////////////////
// IsEven

TEST_F(BigNumTest, IsEvenFailsGivenNullPointer) {
  BigNumObj zero(this->str_0);
  bool r;
  EXPECT_EQ(kEpidBadArgErr, BigNumIsEven(nullptr, &r));
  EXPECT_EQ(kEpidBadArgErr, BigNumIsEven(zero, nullptr));
}

TEST_F(BigNumTest, IsEvenPassesEvenNumbers) {
  BigNumObj zero(this->str_0);
  BigNumObj two(this->str_2);
  BigNumObj big(this->str_big);
  bool r;
  EXPECT_EQ(kEpidNoErr, BigNumMul(big, two, big));
  EXPECT_EQ(kEpidNoErr, BigNumIsEven(zero, &r));
  EXPECT_EQ(kEpidNoErr, BigNumIsEven(two, &r));
  EXPECT_EQ(kEpidNoErr, BigNumIsEven(big, &r));
}

TEST_F(BigNumTest, IsEvenFailsOddNumbers) {
  BigNumObj zero(this->str_0);
  BigNumObj one(this->str_1);
  BigNumObj two(this->str_2);
  BigNumObj big(this->str_big);
  bool r;
  EXPECT_EQ(kEpidNoErr, BigNumMul(big, two, big));
  EXPECT_EQ(kEpidNoErr, BigNumAdd(big, one, big));
  EXPECT_EQ(kEpidNoErr, BigNumIsEven(one, &r));
  EXPECT_EQ(kEpidNoErr, BigNumIsEven(big, &r));
}

///////////////////////////////////////////////////////////////////////
// IsZero
TEST_F(BigNumTest, IsZeroFailsGivenNullPointer) {
  BigNumObj zero(this->str_0);
  bool r;
  EXPECT_EQ(kEpidBadArgErr, BigNumIsZero(nullptr, &r));
  EXPECT_EQ(kEpidBadArgErr, BigNumIsZero(zero, nullptr));
}

TEST_F(BigNumTest, IsZeroPassesZero) {
  BigNumObj zero(this->str_0);
  bool r;
  EXPECT_EQ(kEpidNoErr, BigNumIsZero(zero, &r));
}

TEST_F(BigNumTest, IsZeroFailsNonZero) {
  BigNumObj one(this->str_1);
  BigNumObj two(this->str_2);
  BigNumObj big(this->str_big);
  bool r;
  EXPECT_EQ(kEpidNoErr, BigNumIsZero(one, &r));
  EXPECT_EQ(kEpidNoErr, BigNumIsZero(two, &r));
  EXPECT_EQ(kEpidNoErr, BigNumIsZero(big, &r));
}

///////////////////////////////////////////////////////////////////////
// Pow2N
TEST_F(BigNumTest, Pow2NFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr, BigNumPow2N(1, nullptr));
}

TEST_F(BigNumTest, Pow2NZeroGivesOne) {
  BigNumObj r;
  BigNumObj one(this->str_1);
  EXPECT_EQ(kEpidNoErr, BigNumPow2N(0, r));
  EXPECT_TRUE(CompareBigNum(one, r));
}

TEST_F(BigNumTest, Pow2NOneGivesTwo) {
  BigNumObj r;
  BigNumObj two(this->str_2);
  EXPECT_EQ(kEpidNoErr, BigNumPow2N(1, r));
  EXPECT_TRUE(CompareBigNum(two, r));
}

TEST_F(BigNumTest, Pow2NGivesPow2n) {
  unsigned int n = 2;
  BigNumObj r;
  BigNumObj two(this->str_2);
  BigNumObj expect;
  EXPECT_EQ(kEpidNoErr, BigNumMul(two, two, expect));
  for (n = 2; n < 4; n++) {
    EXPECT_EQ(kEpidNoErr, BigNumPow2N(n, r));
    EXPECT_TRUE(CompareBigNum(expect, r));
    EXPECT_EQ(kEpidNoErr, BigNumMul(expect, two, expect));
    n++;
  }
}

}  // namespace
