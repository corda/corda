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
 * \brief EcPoint C++ wrapper unit tests.
 */

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/ecpoint_wrapper-testhelper.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/bignum.h"
}

namespace {

// Use Test Fixture for SetUp and TearDown
class EcPointObjTest : public ::testing::Test {
 public:
  static EcGroupObj group;
  static const G1ElemStr group_str_1;
  static const G1ElemStr group_str_2;
};

const G1ElemStr EcPointObjTest::group_str_1 = {
    {{{0x12, 0xA6, 0x5B, 0xD6, 0x91, 0x8D, 0x50, 0xA7, 0x66, 0xEB, 0x7D, 0x52,
       0xE3, 0x40, 0x17, 0x60, 0x7F, 0xDF, 0x6C, 0xA1, 0x2C, 0x1A, 0x37, 0xE0,
       0x92, 0xC0, 0xF7, 0xB9, 0x76, 0xAB, 0xB1, 0x8A}}},
    {{{0x78, 0x65, 0x28, 0xCB, 0xAF, 0x07, 0x52, 0x50, 0x55, 0x7A, 0x5F, 0x30,
       0x0A, 0xC0, 0xB4, 0x6B, 0xEA, 0x6F, 0xE2, 0xF6, 0x6D, 0x96, 0xF7, 0xCD,
       0xC8, 0xD3, 0x12, 0x7F, 0x1F, 0x3A, 0x8B, 0x42}}}};

const G1ElemStr EcPointObjTest::group_str_2 = {
    {{{0xE6, 0x65, 0x23, 0x9B, 0xD4, 0x07, 0x16, 0x83, 0x38, 0x23, 0xB2, 0x67,
       0x57, 0xEB, 0x0F, 0x23, 0x3A, 0xF4, 0x8E, 0xDA, 0x71, 0x5E, 0xD9, 0x98,
       0x63, 0x98, 0x2B, 0xBC, 0x78, 0xD1, 0x94, 0xF2}}},
    {{{0x63, 0xB0, 0xAD, 0xB8, 0x2C, 0xE8, 0x14, 0xFD, 0xA2, 0x39, 0x0E, 0x66,
       0xB7, 0xD0, 0x6A, 0xAB, 0xEE, 0xFA, 0x2E, 0x24, 0x9B, 0xB5, 0x14, 0x35,
       0xFE, 0xB6, 0xB0, 0xFF, 0xFD, 0x5F, 0x73, 0x19}}}};

EcGroupObj EcPointObjTest::group;

TEST_F(EcPointObjTest, ObjDefaultConstructedIsNotNull) {
  EcPointObj point(&group);
  EXPECT_NE(nullptr, (EcPoint*)point);
}

TEST_F(EcPointObjTest, AssignmentDoesNotCopyPointer) {
  EcPointObj point1(&group, group_str_1);
  EcPointObj point2(&group, group_str_2);
  EXPECT_NE((EcPoint*)point1, (EcPoint*)point2);
  point1 = point2;
  EXPECT_NE((EcPoint*)point1, (EcPoint*)point2);
}

TEST_F(EcPointObjTest, CopyConstructorDoesNotCopyPointer) {
  EcPointObj point1(&group, group_str_1);
  EcPointObj point2(point1);
  EXPECT_NE((EcPoint*)point1, (EcPoint*)point2);
}

TEST_F(EcPointObjTest, CanCastConstToConstPointer) {
  EcPointObj const point(&group);
  EcPoint const* point_ptr = point;
  (void)point_ptr;
}

TEST_F(EcPointObjTest, CanGetConstPointerFromConst) {
  EcPointObj const point(&group);
  EcPoint const* point_ptr = point.getc();
  (void)point_ptr;
}

/*
The following tests are expected to result in
compile time errors (by design)
*/
/*
TEST_F(EcPointObjTest, CannotCastConstToNonConstPointer) {
  EcPointObj const point(&group);
  EcPoint * point_ptr = point;
  (void) point_ptr;
}

TEST_F(EcPointObjTest, CannotGetNonConstPointerFromConst) {
  EcPointObj const point(&group);
  EcPoint * point_ptr = point.get();
  (void) point_ptr;
}
*/

TEST_F(EcPointObjTest, CanCastNonConstToConstPointer) {
  EcPointObj point(&group);
  EcPoint const* point_ptr = point;
  (void)point_ptr;
}

TEST_F(EcPointObjTest, CanGetConstPointerFromNonConst) {
  EcPointObj point(&group);
  EcPoint const* point_ptr = point.getc();
  (void)point_ptr;
}

TEST_F(EcPointObjTest, CanCastNonConstToNonConstPointer) {
  EcPointObj point(&group);
  EcPoint* point_ptr = point;
  (void)point_ptr;
}

TEST_F(EcPointObjTest, CanGetNonConstPointerFromNonConst) {
  EcPointObj point(&group);
  EcPoint* point_ptr = point.get();
  (void)point_ptr;
}

}  // namespace
