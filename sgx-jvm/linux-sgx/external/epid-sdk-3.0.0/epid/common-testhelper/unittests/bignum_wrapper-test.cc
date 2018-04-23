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
 * \brief Bignum C++ wrapper unit tests.
 */

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/bignum_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/bignum.h"
#include "epid/common/src/memory.h"
}

namespace {

// Use Test Fixture for SetUp and TearDown
class BigNumObjTest : public ::testing::Test {
 public:
  static const BigNumStr str_0;
  static const std::vector<unsigned char> vec_0;
};

const BigNumStr BigNumObjTest::str_0 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

const std::vector<unsigned char> BigNumObjTest::vec_0 = {0, 0, 0, 0,
                                                         0, 0, 0, 0};

TEST_F(BigNumObjTest, ObjDefaultConstructedIsNotNull) {
  BigNumObj bn;
  EXPECT_NE(nullptr, (BigNum*)bn);
}

TEST_F(BigNumObjTest, ObjConstructedWithSizeIsNotNull) {
  BigNumObj bn1(1);
  EXPECT_NE(nullptr, (BigNum*)bn1);
  BigNumObj bn32(32);
  EXPECT_NE(nullptr, (BigNum*)bn32);
}

TEST_F(BigNumObjTest, AssignmentDoesNotCopyPointer) {
  BigNumObj bn1;
  BigNumObj bn2;
  EXPECT_NE((BigNum*)bn1, (BigNum*)bn2);
  bn1 = bn2;
  EXPECT_NE((BigNum*)bn1, (BigNum*)bn2);
}

TEST_F(BigNumObjTest, CopyConstructorDoesNotCopyPointer) {
  BigNumObj bn1;
  BigNumObj bn2(bn1);
  EXPECT_NE((BigNum*)bn1, (BigNum*)bn2);
}

TEST_F(BigNumObjTest, ConstructorDoesNotThrow) {
  BigNumObj bn1;
  BigNumObj bn2(32);
  BigNumObj bn3(32, this->str_0);
  BigNumObj bn4(32, this->vec_0);
  BigNumObj bn5(this->str_0);
  BigNumObj bn6(this->vec_0);

  EXPECT_NE((BigNum*)bn1, (BigNum*)bn2);
  EXPECT_NE((BigNum*)bn1, (BigNum*)bn3);
  EXPECT_NE((BigNum*)bn1, (BigNum*)bn4);
  EXPECT_NE((BigNum*)bn1, (BigNum*)bn5);
  EXPECT_NE((BigNum*)bn1, (BigNum*)bn6);

  EXPECT_NE((BigNum*)bn2, (BigNum*)bn1);
  EXPECT_NE((BigNum*)bn2, (BigNum*)bn3);
  EXPECT_NE((BigNum*)bn2, (BigNum*)bn4);
  EXPECT_NE((BigNum*)bn2, (BigNum*)bn5);
  EXPECT_NE((BigNum*)bn2, (BigNum*)bn6);

  EXPECT_NE((BigNum*)bn3, (BigNum*)bn1);
  EXPECT_NE((BigNum*)bn3, (BigNum*)bn2);
  EXPECT_NE((BigNum*)bn3, (BigNum*)bn4);
  EXPECT_NE((BigNum*)bn3, (BigNum*)bn5);
  EXPECT_NE((BigNum*)bn3, (BigNum*)bn6);

  EXPECT_NE((BigNum*)bn4, (BigNum*)bn1);
  EXPECT_NE((BigNum*)bn4, (BigNum*)bn2);
  EXPECT_NE((BigNum*)bn4, (BigNum*)bn3);
  EXPECT_NE((BigNum*)bn4, (BigNum*)bn5);
  EXPECT_NE((BigNum*)bn4, (BigNum*)bn6);

  EXPECT_NE((BigNum*)bn5, (BigNum*)bn1);
  EXPECT_NE((BigNum*)bn5, (BigNum*)bn2);
  EXPECT_NE((BigNum*)bn5, (BigNum*)bn3);
  EXPECT_NE((BigNum*)bn5, (BigNum*)bn4);
  EXPECT_NE((BigNum*)bn5, (BigNum*)bn6);

  EXPECT_NE((BigNum*)bn6, (BigNum*)bn1);
  EXPECT_NE((BigNum*)bn6, (BigNum*)bn2);
  EXPECT_NE((BigNum*)bn6, (BigNum*)bn3);
  EXPECT_NE((BigNum*)bn6, (BigNum*)bn4);
  EXPECT_NE((BigNum*)bn6, (BigNum*)bn5);
}

TEST_F(BigNumObjTest, CanCastConstToConstPointer) {
  BigNumObj const bn;
  BigNum const* bn_ptr = bn;
  (void)bn_ptr;
}

TEST_F(BigNumObjTest, CanGetConstPointerFromConst) {
  BigNumObj const bn;
  BigNum const* bn_ptr = bn.getc();
  (void)bn_ptr;
}

/*
The following tests are expected to result in
compile time errors (by design)
*/
/*
TEST_F(BigNumObjTest, CannotCastConstToNonConstPointer) {
  BigNumObj const bn;
  BigNum * bn_ptr = bn;
  (void) bn_ptr;
}

TEST_F(BigNumObjTest, CannotGetNonConstPointerFromConst) {
  BigNumObj const bn;
  BigNum * bn_ptr = bn.get();
  (void) bn_ptr;
}
*/

TEST_F(BigNumObjTest, CanCastNonConstToConstPointer) {
  BigNumObj bn;
  BigNum const* bn_ptr = bn;
  (void)bn_ptr;
}

TEST_F(BigNumObjTest, CanGetConstPointerFromNonConst) {
  BigNumObj bn;
  BigNum const* bn_ptr = bn.getc();
  (void)bn_ptr;
}

TEST_F(BigNumObjTest, CanCastNonConstToNonConstPointer) {
  BigNumObj bn;
  BigNum* bn_ptr = bn;
  (void)bn_ptr;
}

TEST_F(BigNumObjTest, CanGetNonConstPointerFromNonConst) {
  BigNumObj bn;
  BigNum* bn_ptr = bn.get();
  (void)bn_ptr;
}

}  // namespace
