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
 * \brief EcGroup C++ wrapper unit tests.
 */

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/bignum_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"
#include "epid/common-testhelper/ecpoint_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/bignum.h"
#include "epid/common/types.h"
}

namespace {

// Use Test Fixture for SetUp and TearDown
class EcGroupObjTest : public ::testing::Test {
 public:
  static const BigNumStr q_str;
  static const FqElemStr b_str;
  static const BigNumStr p_str;
  static const BigNumStr h1;
  static const G1ElemStr g1_str;
};
const BigNumStr EcGroupObjTest::q_str = {
    {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
      0xEE, 0x71, 0xA4, 0x9F, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x98, 0x0A, 0x82,
      0xD3, 0x29, 0x2D, 0xDB, 0xAE, 0xD3, 0x30, 0x13}}};
const FqElemStr EcGroupObjTest::b_str = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03}}};
const BigNumStr EcGroupObjTest::p_str = {
    {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
      0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99, 0x92, 0x1A,
      0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D}}};
const BigNumStr EcGroupObjTest::h1 = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}};
const G1ElemStr EcGroupObjTest::g1_str = {
    {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}},
    {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02}}}};

TEST_F(EcGroupObjTest, ObjDefaultConstructedIsNotNull) {
  EcGroupObj group;
  EXPECT_NE(nullptr, (EcGroup*)group);
}

TEST_F(EcGroupObjTest, AssignmentCopiesPointer) {
  EcGroupObj group1;
  EcGroupObj group2;
  EXPECT_NE((EcGroup*)group1, (EcGroup*)group2);
  group1 = group2;
  EXPECT_EQ((EcGroup*)group1, (EcGroup*)group2);
}

TEST_F(EcGroupObjTest, CopyConstructorCopiesPointer) {
  EcGroupObj group1;
  EcGroupObj group2(group1);
  EXPECT_EQ((EcGroup*)group1, (EcGroup*)group2);
}

TEST_F(EcGroupObjTest, ConstructorDoesNotThrow) {
  EcGroupObj group1;
  FiniteFieldObj fq(this->q_str);
  EcGroupObj group2(&fq, FfElementObj(&fq), FfElementObj(&fq, this->b_str),
                    FfElementObj(&fq, this->g1_str.x),
                    FfElementObj(&fq, this->g1_str.y), BigNumObj(this->p_str),
                    BigNumObj(this->h1));
}

TEST_F(EcGroupObjTest, CanCastConstToConstPointer) {
  EcGroupObj const group;
  EcGroup const* group_ptr = group;
  (void)group_ptr;
}

TEST_F(EcGroupObjTest, CanGetConstPointerFromConst) {
  EcGroupObj const group;
  EcGroup const* group_ptr = group.getc();
  (void)group_ptr;
}

/*
The following tests are expected to result in
compile time errors (by design)
*/
/*
TEST_F(EcGroupObjTest, CannotCastConstToNonConstPointer) {
  EcGroupObj const group;
  EcGroup * group_ptr = group;
  (void) group_ptr;
}

TEST_F(EcGroupObjTest, CannotGetNonConstPointerFromConst) {
  EcGroupObj const group;
  EcGroup * group_ptr = group.get();
  (void) group_ptr;
}
*/

TEST_F(EcGroupObjTest, CanCastNonConstToConstPointer) {
  EcGroupObj group;
  EcGroup const* group_ptr = group;
  (void)group_ptr;
}

TEST_F(EcGroupObjTest, CanGetConstPointerFromNonConst) {
  EcGroupObj group;
  EcGroup const* group_ptr = group.getc();
  (void)group_ptr;
}

TEST_F(EcGroupObjTest, CanCastNonConstToNonConstPointer) {
  EcGroupObj group;
  EcGroup* group_ptr = group;
  (void)group_ptr;
}

TEST_F(EcGroupObjTest, CanGetNonConstPointerFromNonConst) {
  EcGroupObj group;
  EcGroup* group_ptr = group.get();
  (void)group_ptr;
}

}  // namespace
