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
 * \brief EcGroup unit tests.
 */

#include <cstring>
#include <memory>
#include <string>
#include <stdexcept>
#include <vector>

#include "gtest/gtest.h"

extern "C" {
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"
}
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/prng-testhelper.h"
#include "epid/common-testhelper/bignum_wrapper-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"
#include "epid/common-testhelper/ecpoint_wrapper-testhelper.h"

/// compares G1ElemStr values
bool operator==(G1ElemStr const& lhs, G1ElemStr const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}

/// compares G2ElemStr values
bool operator==(G2ElemStr const& lhs, G2ElemStr const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}

namespace {

class EFq2Params {
 public:
  FiniteFieldObj fq2;
  FfElementObj a;
  FfElementObj b;
  FfElementObj x;
  FfElementObj y;
  BigNumObj order;
  BigNumObj cofactor;

  explicit EFq2Params(FiniteFieldObj* fq) {
    // Intel(R) EPID 2.0 parameters for EC(Fq2)
    static const FqElemStr param_beta = {
        {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2,
          0x5E, 0xEE, 0x71, 0xA4, 0x9F, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x98,
          0x0A, 0x82, 0xD3, 0x29, 0x2D, 0xDB, 0xAE, 0xD3, 0x30, 0x12}}};
    static const G2ElemStr param_g2 = {
        {{{{0xE2, 0x01, 0x71, 0xC5, 0x4A, 0xA3, 0xDA, 0x05, 0x21, 0x67, 0x04,
            0x13, 0x74, 0x3C, 0xCF, 0x22, 0xD2, 0x5D, 0x52, 0x68, 0x3D, 0x32,
            0x47, 0x0E, 0xF6, 0x02, 0x13, 0x43, 0xBF, 0x28, 0x23, 0x94}}},
         {{{0x59, 0x2D, 0x1E, 0xF6, 0x53, 0xA8, 0x5A, 0x80, 0x46, 0xCC, 0xDC,
            0x25, 0x4F, 0xBB, 0x56, 0x56, 0x43, 0x43, 0x3B, 0xF6, 0x28, 0x96,
            0x53, 0xE2, 0x7D, 0xF7, 0xB2, 0x12, 0xBA, 0xA1, 0x89, 0xBE}}}},
        {{{{0xAE, 0x60, 0xA4, 0xE7, 0x51, 0xFF, 0xD3, 0x50, 0xC6, 0x21, 0xE7,
            0x03, 0x31, 0x28, 0x26, 0xBD, 0x55, 0xE8, 0xB5, 0x9A, 0x4D, 0x91,
            0x68, 0x38, 0x41, 0x4D, 0xB8, 0x22, 0xDD, 0x23, 0x35, 0xAE}}},
         {{{0x1A, 0xB4, 0x42, 0xF9, 0x89, 0xAF, 0xE5, 0xAD, 0xF8, 0x02, 0x74,
            0xF8, 0x76, 0x45, 0xE2, 0x53, 0x2C, 0xDC, 0x61, 0x81, 0x90, 0x93,
            0xD6, 0x13, 0x2C, 0x90, 0xFE, 0x89, 0x51, 0xB9, 0x24, 0x21}}}}};
    static const Fq2ElemStr param_xi0xi1 = {
        {{{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02}}},
         {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}}}};
    static const FqElemStr param_b = {
        {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03}};

    // Setup Fq2 with parameters (q, beta)
    // Fq^2 = Fq[u] / (u^2 - beta)
    FfElementObj neg_beta(fq);
    THROW_ON_EPIDERR(FfNeg(*fq, FfElementObj(fq, param_beta), neg_beta));
    fq2 = FiniteFieldObj(*fq, neg_beta, 2);

    // set x to (g2.x[0], g2.x[1]) and y to (g2.y[0], g2.y[1])
    x = FfElementObj(&fq2, &param_g2.x, sizeof(param_g2.x));
    y = FfElementObj(&fq2, &param_g2.y, sizeof(param_g2.y));

    // set a to identity, NewFfElement does it by default
    a = FfElementObj(&fq2);

    // set b to inv(xi)*param_b, where xi is (xi0, xi1) element in Fq2
    FfElementObj neg_xi(&fq2);
    THROW_ON_EPIDERR(FfInv(fq2, FfElementObj(&fq2, param_xi0xi1), neg_xi));
    b = FfElementObj(&fq2);
    THROW_ON_EPIDERR(FfMul(fq2, neg_xi.get(), FfElementObj(fq, param_b), b));

    // set h = 2q - p, aka cofactor
    std::vector<uint8_t> cofactor_str(
        {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff,
         0xff, 0xfc, 0xf0, 0xcd, 0x46, 0xe5, 0xf2, 0x5e, 0xee, 0x71, 0xa4, 0xa0,
         0x0c, 0xdc, 0x65, 0xfb, 0x12, 0x96, 0x82, 0xea, 0xb0, 0x25, 0x08, 0x4a,
         0x8c, 0x9b, 0x10, 0x19});
    cofactor = BigNumObj(cofactor_str);

    // set n = p * h, AKA order
    std::vector<uint8_t> order_str(
        {0xff, 0xff, 0xff, 0xff, 0xff, 0xf9, 0xe1, 0x9a, 0x8d, 0xcb, 0xe4, 0xc7,
         0x38, 0xfa, 0x9b, 0x98, 0x4d, 0x1c, 0x12, 0x9f, 0x64, 0x97, 0xe8, 0x54,
         0xa3, 0x0a, 0x81, 0xac, 0x42, 0xf9, 0x39, 0x16, 0xa7, 0x70, 0x21, 0xdc,
         0xfb, 0xb6, 0xe7, 0x7e, 0x1f, 0x5b, 0x55, 0xcc, 0x4e, 0x84, 0xcd, 0x19,
         0x4f, 0x49, 0x20, 0x94, 0xb5, 0xd8, 0x12, 0xa0, 0x2e, 0x7f, 0x40, 0x13,
         0xb2, 0xfa, 0xa1, 0x45});
    order = BigNumObj(order_str);
  }

  virtual ~EFq2Params() {}

 private:
  // This class is not meant to be copied or assigned
  EFq2Params(const EFq2Params&);
  EFq2Params& operator=(const EFq2Params&);
};

class EcGroupTest : public ::testing::Test {
 public:
  static const G1ElemStr g1_str;
  static const G2ElemStr g2_str;

  static const FqElemStr a1;
  static const FqElemStr b1;
  static const BigNumStr h1;
  static const BigNumStr p;
  static const BigNumStr q;

  static const G1ElemStr efq_a_str;
  static const G1ElemStr efq_b_str;
  static const BigNumStr x_str;
  static const BigNumStr y_str;
  static const G1ElemStr efq_mul_ab_str;
  static const G1ElemStr efq_exp_ax_str;
  static const G1ElemStr efq_multiexp_abxy_str;
  static const G1ElemStr efq_inv_a_str;
  static const G1ElemStr efq_identity_str;
  static const G1ElemStr efq_r_sha256_str;
  static const G1ElemStr efq_r_sha384_str;
  static const G1ElemStr efq_r_sha512_str;
  static const uint8_t sha_msg[];

  static const G2ElemStr efq2_a_str;
  static const G2ElemStr efq2_b_str;
  static const G2ElemStr efq2_mul_ab_str;
  static const G2ElemStr efq2_exp_ax_str;
  static const G2ElemStr efq2_multiexp_abxy_str;
  static const G2ElemStr efq2_inv_a_str;
  static const G2ElemStr efq2_identity_str;

  // Epid 1.1 hash of message "aad"
  static const Epid11G3ElemStr kAadHash;
  // Epid 1.1 hash of message "bsn0"
  static const Epid11G3ElemStr kBsn0Hash;
  // Epid 1.1 hash of message "test"
  static const Epid11G3ElemStr kTestHash;
  // Epid 1.1 hash of message "aac"
  static const Epid11G3ElemStr kAacHash;

  virtual void SetUp() {
    Epid11Params epid11_params_str = {
#include "epid/common/1.1/src/epid11params_tate.inc"
    };

    fq = FiniteFieldObj(q);
    fq_a = FfElementObj(&fq, a1);
    fq_b = FfElementObj(&fq, b1);
    g1_x = FfElementObj(&fq, g1_str.x);
    g1_y = FfElementObj(&fq, g1_str.y);

    bn_p = BigNumObj(p);
    bn_h = BigNumObj(h1);

    efq = EcGroupObj(&fq, fq_a, fq_b, g1_x, g1_y, bn_p, bn_h);

    efq_a = EcPointObj(&efq, efq_a_str);
    efq_b = EcPointObj(&efq, efq_b_str);
    efq_r = EcPointObj(&efq);
    efq_identity = EcPointObj(&efq, efq_identity_str);

    efq2_par.reset(new EFq2Params(&fq));

    efq2 = EcGroupObj(&efq2_par->fq2, efq2_par->a, efq2_par->b, efq2_par->x,
                      efq2_par->y, efq2_par->order, efq2_par->cofactor);

    efq2_a = EcPointObj(&efq2, efq2_a_str);
    efq2_b = EcPointObj(&efq2, efq2_b_str);
    efq2_r = EcPointObj(&efq2);
    efq2_identity = EcPointObj(&efq2, efq_identity_str);

    epid11_Fq_tick = FiniteFieldObj(epid11_params_str.q_tick);
    epid11_a_tick = FfElementObj(&epid11_Fq_tick, epid11_params_str.a_tick);
    epid11_b_tick = FfElementObj(&epid11_Fq_tick, epid11_params_str.b_tick);
    epid11_g3_x = FfElementObj(&epid11_Fq_tick, epid11_params_str.g3.x);
    epid11_g3_y = FfElementObj(&epid11_Fq_tick, epid11_params_str.g3.y);
    epid11_p_tick = BigNumObj(epid11_params_str.p_tick);
    BigNumStr h_tick_str = {0};
    ((OctStr32*)
         h_tick_str.data.data)[sizeof(BigNumStr) / sizeof(OctStr32) - 1] =
        epid11_params_str.h_tick;
    epid11_h_tick = BigNumObj(h_tick_str);

    epid11_G3 =
        EcGroupObj(&epid11_Fq_tick, epid11_a_tick, epid11_b_tick, epid11_g3_x,
                   epid11_g3_y, epid11_p_tick, epid11_h_tick);
    epid11_G3_r = EcPointObj(&epid11_G3);
  }

  FiniteFieldObj fq;
  FfElementObj fq_a;
  FfElementObj fq_b;
  FfElementObj g1_x;
  FfElementObj g1_y;

  BigNumObj bn_p;
  BigNumObj bn_h;

  EcGroupObj efq;
  EcPointObj efq_a;
  EcPointObj efq_b;
  EcPointObj efq_r;
  EcPointObj efq_identity;

  std::unique_ptr<EFq2Params> efq2_par;
  EcGroupObj efq2;
  EcPointObj efq2_a;
  EcPointObj efq2_b;
  EcPointObj efq2_r;
  EcPointObj efq2_identity;

  FiniteFieldObj epid11_Fq_tick;
  FfElementObj epid11_a_tick;
  FfElementObj epid11_b_tick;
  FfElementObj epid11_g3_x;
  FfElementObj epid11_g3_y;
  BigNumObj epid11_p_tick;
  BigNumObj epid11_h_tick;

  EcGroupObj epid11_G3;
  EcPointObj epid11_G3_r;
};

const G1ElemStr EcGroupTest::g1_str = {
    {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}},
    {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02}}}};
const G2ElemStr EcGroupTest::g2_str = {
    {{{{0xE2, 0x01, 0x71, 0xC5, 0x4A, 0xA3, 0xDA, 0x05, 0x21, 0x67, 0x04, 0x13,
        0x74, 0x3C, 0xCF, 0x22, 0xD2, 0x5D, 0x52, 0x68, 0x3D, 0x32, 0x47, 0x0E,
        0xF6, 0x02, 0x13, 0x43, 0xBF, 0x28, 0x23, 0x94}}},
     {{{0x59, 0x2D, 0x1E, 0xF6, 0x53, 0xA8, 0x5A, 0x80, 0x46, 0xCC, 0xDC, 0x25,
        0x4F, 0xBB, 0x56, 0x56, 0x43, 0x43, 0x3B, 0xF6, 0x28, 0x96, 0x53, 0xE2,
        0x7D, 0xF7, 0xB2, 0x12, 0xBA, 0xA1, 0x89, 0xBE}}}},
    {{{{0xAE, 0x60, 0xA4, 0xE7, 0x51, 0xFF, 0xD3, 0x50, 0xC6, 0x21, 0xE7, 0x03,
        0x31, 0x28, 0x26, 0xBD, 0x55, 0xE8, 0xB5, 0x9A, 0x4D, 0x91, 0x68, 0x38,
        0x41, 0x4D, 0xB8, 0x22, 0xDD, 0x23, 0x35, 0xAE}}},
     {{{0x1A, 0xB4, 0x42, 0xF9, 0x89, 0xAF, 0xE5, 0xAD, 0xF8, 0x02, 0x74, 0xF8,
        0x76, 0x45, 0xE2, 0x53, 0x2C, 0xDC, 0x61, 0x81, 0x90, 0x93, 0xD6, 0x13,
        0x2C, 0x90, 0xFE, 0x89, 0x51, 0xB9, 0x24, 0x21}}}}};

const FqElemStr EcGroupTest::a1 = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}};
const FqElemStr EcGroupTest::b1 = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03}}};
const BigNumStr EcGroupTest::h1 = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}};
const BigNumStr EcGroupTest::p = {
    {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
      0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99, 0x92, 0x1A,
      0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D}}};
const BigNumStr EcGroupTest::q = {
    {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
      0xEE, 0x71, 0xA4, 0x9F, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x98, 0x0A, 0x82,
      0xD3, 0x29, 0x2D, 0xDB, 0xAE, 0xD3, 0x30, 0x13}}};

const G1ElemStr EcGroupTest::efq_a_str = {
    {{{0x12, 0xA6, 0x5B, 0xD6, 0x91, 0x8D, 0x50, 0xA7, 0x66, 0xEB, 0x7D, 0x52,
       0xE3, 0x40, 0x17, 0x60, 0x7F, 0xDF, 0x6C, 0xA1, 0x2C, 0x1A, 0x37, 0xE0,
       0x92, 0xC0, 0xF7, 0xB9, 0x76, 0xAB, 0xB1, 0x8A}}},
    {{{0x78, 0x65, 0x28, 0xCB, 0xAF, 0x07, 0x52, 0x50, 0x55, 0x7A, 0x5F, 0x30,
       0x0A, 0xC0, 0xB4, 0x6B, 0xEA, 0x6F, 0xE2, 0xF6, 0x6D, 0x96, 0xF7, 0xCD,
       0xC8, 0xD3, 0x12, 0x7F, 0x1F, 0x3A, 0x8B, 0x42}}}};

const G1ElemStr EcGroupTest::efq_b_str = {
    {{{0xE6, 0x65, 0x23, 0x9B, 0xD4, 0x07, 0x16, 0x83, 0x38, 0x23, 0xB2, 0x67,
       0x57, 0xEB, 0x0F, 0x23, 0x3A, 0xF4, 0x8E, 0xDA, 0x71, 0x5E, 0xD9, 0x98,
       0x63, 0x98, 0x2B, 0xBC, 0x78, 0xD1, 0x94, 0xF2}}},
    {{{0x63, 0xB0, 0xAD, 0xB8, 0x2C, 0xE8, 0x14, 0xFD, 0xA2, 0x39, 0x0E, 0x66,
       0xB7, 0xD0, 0x6A, 0xAB, 0xEE, 0xFA, 0x2E, 0x24, 0x9B, 0xB5, 0x14, 0x35,
       0xFE, 0xB6, 0xB0, 0xFF, 0xFD, 0x5F, 0x73, 0x19}}}};

const BigNumStr EcGroupTest::x_str = {
    {{0xFF, 0xFB, 0x3E, 0x5D, 0xFF, 0x9A, 0xFF, 0x02, 0x00, 0xFF, 0xFF, 0xFF,
      0xF2, 0xE1, 0x85, 0x81, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x81,
      0xFF, 0xFD, 0xFF, 0xEB, 0xFF, 0x29, 0xA7, 0xFF}}};

const BigNumStr EcGroupTest::y_str = {
    {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B, 0x08, 0xFF, 0xFF,
      0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8, 0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF,
      0xF2, 0x6A, 0xFF, 0xFF, 0xEA, 0x31, 0xFF, 0xFF}}};

const G1ElemStr EcGroupTest::efq_mul_ab_str = {
    {{{0x30, 0xF8, 0x33, 0xB7, 0x1C, 0x85, 0x94, 0x6D, 0x6F, 0x3C, 0x97, 0x77,
       0x81, 0xA5, 0xC2, 0x98, 0x93, 0x5C, 0x8C, 0xC1, 0xFF, 0x35, 0x9E, 0x68,
       0xF6, 0x4D, 0x18, 0xDD, 0x65, 0xA9, 0xC0, 0x60}}},
    {{{0x89, 0xE5, 0x08, 0x2D, 0xD1, 0xD8, 0xC7, 0xBF, 0xDE, 0x16, 0x24, 0xA7,
       0x2F, 0xF1, 0x48, 0x00, 0x26, 0xAF, 0x89, 0xEA, 0xC9, 0x94, 0x78, 0xFF,
       0x2A, 0xB0, 0x20, 0xED, 0x33, 0x0C, 0x4E, 0x88}}}};

const G1ElemStr EcGroupTest::efq_exp_ax_str = {
    {{{0x44, 0x45, 0xFA, 0x16, 0x23, 0x66, 0x26, 0x9D, 0x44, 0xB9, 0x43, 0xAB,
       0x87, 0xE3, 0x56, 0xCA, 0x9C, 0x89, 0x44, 0x8E, 0xE8, 0x19, 0x29, 0x4D,
       0x4D, 0x59, 0x7D, 0xBE, 0x46, 0x3F, 0x55, 0x0D}}},
    {{{0x98, 0x09, 0xCF, 0x43, 0x46, 0x75, 0xB8, 0x71, 0xFF, 0x37, 0xBA, 0xA0,
       0x63, 0xE2, 0xAC, 0x09, 0x38, 0x10, 0x70, 0xAC, 0x15, 0x52, 0x28, 0xF4,
       0x77, 0x68, 0x32, 0x7B, 0x6E, 0xFB, 0xC1, 0x43}}}};

const G1ElemStr EcGroupTest::efq_multiexp_abxy_str = {
    {{{0x63, 0x4A, 0xD4, 0xC1, 0x6B, 0x90, 0x67, 0xA2, 0x0B, 0xE2, 0xB3, 0xE9,
       0x95, 0x3F, 0x82, 0x7E, 0x21, 0xBF, 0x9F, 0xCD, 0xA0, 0x16, 0x56, 0x6B,
       0x31, 0x66, 0x68, 0xBB, 0x25, 0xF8, 0xBD, 0xF3}}},
    {{{0xBD, 0x5F, 0xF8, 0x48, 0xD4, 0xBF, 0x35, 0x2D, 0xDC, 0xD1, 0x78, 0x74,
       0xFF, 0xB1, 0x47, 0xD5, 0x6B, 0x21, 0xE5, 0x15, 0x01, 0xA8, 0xDC, 0x8B,
       0x3C, 0x9D, 0x96, 0xC7, 0xC6, 0xB0, 0x05, 0x20}}}};

const G1ElemStr EcGroupTest::efq_inv_a_str = {
    {{{0x12, 0xA6, 0x5B, 0xD6, 0x91, 0x8D, 0x50, 0xA7, 0x66, 0xEB, 0x7D, 0x52,
       0xE3, 0x40, 0x17, 0x60, 0x7F, 0xDF, 0x6C, 0xA1, 0x2C, 0x1A, 0x37, 0xE0,
       0x92, 0xC0, 0xF7, 0xB9, 0x76, 0xAB, 0xB1, 0x8A}}},
    {{{0x87, 0x9A, 0xD7, 0x34, 0x50, 0xF5, 0x9E, 0x7C, 0xF1, 0x6B, 0x93, 0x2E,
       0xE3, 0xB0, 0xF0, 0x33, 0x22, 0x6C, 0x83, 0x04, 0xA5, 0x01, 0x12, 0xB5,
       0x0A, 0x56, 0x1B, 0x5C, 0x8F, 0x98, 0xA4, 0xD1}}}};

const G1ElemStr EcGroupTest::efq_identity_str = {
    {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}},
    {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}}};

const uint8_t EcGroupTest::sha_msg[] = {'a', 'b', 'c'};

const G1ElemStr EcGroupTest::efq_r_sha256_str = {
    {{{0x2E, 0xBB, 0x50, 0x4D, 0x88, 0xFF, 0x25, 0x62, 0xF3, 0x71, 0x65, 0x81,
       0xAD, 0xBE, 0x83, 0x6E, 0x54, 0xF5, 0xA6, 0x2A, 0x70, 0xE6, 0x18, 0x6B,
       0xD5, 0x4A, 0x10, 0x3C, 0x80, 0x08, 0x95, 0x3D}}},
    {{{0x8A, 0x43, 0xA1, 0x04, 0xB1, 0x3F, 0x3C, 0xB4, 0xBD, 0x67, 0x38, 0xB1,
       0x07, 0xF0, 0x7A, 0x32, 0x7E, 0xCD, 0xF0, 0x2E, 0x62, 0x3E, 0x2C, 0x1F,
       0x48, 0xAA, 0x0D, 0x6C, 0xDC, 0x48, 0xF9, 0xF7}}}};

const G1ElemStr EcGroupTest::efq_r_sha384_str = {
    {{{0xE1, 0xC8, 0x28, 0xB1, 0x9A, 0xDF, 0x5D, 0x4B, 0xC4, 0x25, 0x90, 0xFB,
       0x38, 0x20, 0xD4, 0x8B, 0x30, 0x8F, 0x95, 0x76, 0xC3, 0x7F, 0x9D, 0xAD,
       0x94, 0xC4, 0x31, 0x80, 0xD7, 0xDF, 0xD5, 0xFE}}},
    {{{0x0E, 0x86, 0x11, 0x90, 0xAF, 0xEF, 0xEB, 0x79, 0x4B, 0x3E, 0x80, 0x92,
       0x94, 0x3B, 0x2F, 0x5E, 0x72, 0x21, 0xEF, 0xF8, 0xBC, 0xE3, 0x48, 0xA9,
       0xD0, 0x31, 0x19, 0xAC, 0xD1, 0xD7, 0x49, 0x87}}}};

const G1ElemStr EcGroupTest::efq_r_sha512_str = {
    {{{0x8C, 0x62, 0xA0, 0x2D, 0x55, 0x55, 0x55, 0x86, 0xBC, 0x82, 0xA6, 0xA2,
       0x21, 0x97, 0x9B, 0x9B, 0xB4, 0x03, 0x3D, 0x83, 0xF3, 0xBA, 0xDA, 0x9C,
       0x42, 0xF7, 0xB3, 0x94, 0x99, 0x2A, 0x96, 0xE4}}},
    {{{0x4C, 0x0E, 0xA7, 0x62, 0x17, 0xB9, 0xFB, 0xE5, 0x21, 0x7D, 0x54, 0x24,
       0xE0, 0x2B, 0x87, 0xF7, 0x69, 0x54, 0x0C, 0xC6, 0xAD, 0xF2, 0xF2, 0x7B,
       0xE6, 0x91, 0xD8, 0xF3, 0x40, 0x6C, 0x8F, 0x03}}}};

const G2ElemStr EcGroupTest::efq2_a_str = {
    {
        {0x2F, 0x8C, 0xC7, 0xD7, 0xD4, 0x1E, 0x4A, 0xCB, 0x82, 0x92, 0xC7, 0x9C,
         0x0F, 0xA2, 0xF2, 0x1B, 0xDF, 0xEA, 0x96, 0x64, 0x8B, 0xA2, 0x32, 0x7C,
         0xDF, 0xD8, 0x89, 0x10, 0xFD, 0xBB, 0x38, 0xCD},
        {0xB1, 0x23, 0x46, 0x13, 0x4D, 0x9B, 0x8E, 0x8A, 0x95, 0x64, 0xDD, 0x37,
         0x29, 0x44, 0x1F, 0x76, 0xB5, 0x3A, 0x47, 0xD3, 0xE0, 0x18, 0x1E, 0x60,
         0xE9, 0x94, 0x13, 0xA4, 0x47, 0xCD, 0xBE, 0x03},
    },
    {
        {0xD3, 0x67, 0xA5, 0xCC, 0xEF, 0x7B, 0xD1, 0x8D, 0x4A, 0x7F, 0xF1, 0x8F,
         0x66, 0xCB, 0x5E, 0x86, 0xAC, 0xCB, 0x36, 0x5F, 0x29, 0x90, 0x28, 0x55,
         0xF0, 0xDC, 0x6E, 0x8B, 0x87, 0xB5, 0xD8, 0x32},
        {0x6C, 0x0A, 0xC5, 0x58, 0xB1, 0x4E, 0xCA, 0x85, 0x44, 0x3E, 0xDE, 0x71,
         0x9B, 0xC7, 0x90, 0x19, 0x06, 0xD2, 0xA0, 0x4E, 0xC7, 0x33, 0xF4, 0x5C,
         0xE8, 0x16, 0xE2, 0x67, 0xDB, 0xBF, 0x64, 0x84},
    },
};

const G2ElemStr EcGroupTest::efq2_b_str = {
    {
        {0x16, 0xF1, 0x61, 0x76, 0x06, 0x3E, 0xE9, 0xC0, 0xB9, 0xB1, 0x3A, 0x75,
         0xFC, 0xDB, 0x90, 0xCD, 0x01, 0xF4, 0x9F, 0xCC, 0xAA, 0x24, 0x69, 0x83,
         0xBE, 0x20, 0x44, 0x87, 0x58, 0x90, 0x0F, 0x4F},
        {0xC7, 0x50, 0x37, 0xC1, 0xB9, 0x2D, 0xE1, 0xE3, 0x79, 0x20, 0x7B, 0x62,
         0x90, 0xF8, 0xC7, 0xF0, 0xD7, 0x5A, 0xE7, 0xAD, 0x65, 0xE1, 0xC7, 0x50,
         0x59, 0xA1, 0xFC, 0x49, 0xBC, 0x2A, 0xE5, 0xD7},
    },
    {
        {0x12, 0x73, 0x3B, 0xA4, 0xDD, 0x0F, 0xBB, 0x35, 0x38, 0x4A, 0xE0, 0x3D,
         0x79, 0x63, 0x66, 0x73, 0x9C, 0x07, 0xE1, 0xEC, 0x71, 0x16, 0x50, 0x75,
         0xA1, 0xBA, 0xE5, 0x37, 0x45, 0x1A, 0x0C, 0x59},
        {0xC9, 0x49, 0xB9, 0xDB, 0x7E, 0x76, 0xC5, 0xC5, 0x0A, 0x87, 0xB7, 0x56,
         0x88, 0x09, 0x21, 0xC6, 0xF6, 0x6C, 0xCC, 0x5E, 0x80, 0xFD, 0x05, 0xD0,
         0x5F, 0xC6, 0x2E, 0x06, 0xA1, 0xBE, 0x5B, 0xA0},
    },
};

const G2ElemStr EcGroupTest::efq2_mul_ab_str = {
    {
        {0x25, 0xCC, 0x11, 0x80, 0x8F, 0x08, 0x1D, 0x66, 0xF8, 0xDB, 0xBC, 0x98,
         0x26, 0x24, 0x26, 0xCF, 0x04, 0x02, 0xB6, 0x99, 0x1B, 0x52, 0xA8, 0xE3,
         0x4E, 0x9A, 0x85, 0xB0, 0x5C, 0xCE, 0xDD, 0xC5},
        {0xFC, 0x3C, 0xC2, 0x2C, 0x4B, 0x63, 0x72, 0x5F, 0xA9, 0xF9, 0x8C, 0x62,
         0xF4, 0xE7, 0x30, 0x71, 0x6F, 0x78, 0xF5, 0xFE, 0xF6, 0xDF, 0xF7, 0xB5,
         0x21, 0x69, 0x7C, 0x50, 0xAC, 0x56, 0xD9, 0xB5},
    },
    {
        {0xA5, 0xD6, 0xAB, 0x2D, 0xED, 0x8E, 0xFE, 0x43, 0xCB, 0xC9, 0xEF, 0x09,
         0xC8, 0x2D, 0xE8, 0xD0, 0x3B, 0xC0, 0x5C, 0x7F, 0xE5, 0x3A, 0x1D, 0x72,
         0xF2, 0xF5, 0x03, 0xBD, 0xE5, 0xEB, 0x08, 0xA0},
        {0xE6, 0xF3, 0x59, 0xE4, 0xD2, 0x52, 0xFD, 0x4F, 0xEC, 0xCE, 0x49, 0x9F,
         0x86, 0x50, 0x2D, 0x4A, 0x59, 0x2C, 0xA2, 0x4E, 0xE3, 0xFE, 0xF2, 0xFC,
         0xB9, 0xF4, 0x22, 0x88, 0xBC, 0x79, 0x21, 0xD0},
    },
};

const G2ElemStr EcGroupTest::efq2_exp_ax_str = {
    {
        {0xC0, 0x5A, 0x37, 0xAD, 0x08, 0xAB, 0x22, 0xCF, 0xF7, 0xF9, 0xCC, 0xD4,
         0x5A, 0x47, 0x38, 0x82, 0xE1, 0xC2, 0x06, 0x35, 0x4D, 0x5B, 0x95, 0xA1,
         0xA3, 0xC1, 0x83, 0x6C, 0x0F, 0x31, 0x24, 0xD2},
        {0xC7, 0x86, 0xE1, 0x59, 0x63, 0xCE, 0x21, 0x2A, 0x57, 0x77, 0xE5, 0x48,
         0xF7, 0x60, 0x21, 0x00, 0x40, 0x2F, 0x09, 0x18, 0x5C, 0x32, 0x32, 0x75,
         0xD7, 0xB9, 0xE7, 0xB1, 0x95, 0xD5, 0xDF, 0x02},
    },
    {
        {0xE5, 0xDE, 0xC6, 0x3E, 0x05, 0xFC, 0x6F, 0x7A, 0xE3, 0x2D, 0x7D, 0x90,
         0x5F, 0x43, 0xE2, 0xB0, 0x9E, 0xCD, 0xEC, 0x7B, 0x37, 0x4C, 0x0A, 0x3E,
         0x87, 0x4E, 0xE6, 0xDA, 0xD1, 0x90, 0xC0, 0xD1},
        {0x70, 0x90, 0x54, 0x7F, 0x78, 0x93, 0xFA, 0xC4, 0xF7, 0x3A, 0x4D, 0xBC,
         0x03, 0x5E, 0x83, 0xDF, 0xEF, 0xF7, 0x52, 0xF9, 0x64, 0x7F, 0x17, 0xC1,
         0x69, 0xD6, 0xD7, 0x96, 0x18, 0x62, 0x46, 0xD1},
    },
};

const G2ElemStr EcGroupTest::efq2_multiexp_abxy_str = {
    {
        {0xE8, 0x6E, 0x02, 0x7A, 0xEC, 0xEA, 0xBA, 0x7E, 0xE5, 0x7C, 0xAD, 0x98,
         0x37, 0x54, 0xB2, 0x15, 0x64, 0x9C, 0x81, 0xFF, 0x69, 0xCC, 0xD6, 0xA6,
         0xAA, 0xA7, 0x10, 0x4F, 0x9B, 0x0C, 0x50, 0x14},
        {0x7C, 0xAF, 0xC0, 0x6F, 0xC8, 0x87, 0xFF, 0x4A, 0x6F, 0xB5, 0x9E, 0x63,
         0x74, 0x20, 0xB5, 0xC6, 0x4F, 0x14, 0x0B, 0x6C, 0xBF, 0x00, 0x71, 0xE2,
         0x6D, 0x6C, 0x41, 0x6A, 0x0B, 0xA5, 0x5B, 0xCF},
    },
    {
        {0x16, 0xCC, 0x9B, 0x37, 0xE7, 0xCB, 0x16, 0x5C, 0x39, 0x7C, 0x10, 0x7E,
         0xE0, 0xDD, 0x34, 0x90, 0xBE, 0x56, 0x28, 0x76, 0x27, 0x59, 0xCE, 0xB3,
         0xD7, 0xB4, 0x56, 0xD4, 0x0D, 0xD1, 0xB8, 0xFB},
        {0x5E, 0x9E, 0x27, 0x30, 0x60, 0x87, 0x3B, 0xA4, 0x9B, 0x15, 0xEE, 0x86,
         0x15, 0x1D, 0xF4, 0xF3, 0x07, 0x31, 0x46, 0xFD, 0xB7, 0x51, 0xFF, 0xC0,
         0x42, 0x94, 0x38, 0xB7, 0x84, 0x5F, 0x86, 0x3A},
    },
};

const G2ElemStr EcGroupTest::efq2_inv_a_str = {
    {
        {0x2F, 0x8C, 0xC7, 0xD7, 0xD4, 0x1E, 0x4A, 0xCB, 0x82, 0x92, 0xC7, 0x9C,
         0x0F, 0xA2, 0xF2, 0x1B, 0xDF, 0xEA, 0x96, 0x64, 0x8B, 0xA2, 0x32, 0x7C,
         0xDF, 0xD8, 0x89, 0x10, 0xFD, 0xBB, 0x38, 0xCD},
        {0xB1, 0x23, 0x46, 0x13, 0x4D, 0x9B, 0x8E, 0x8A, 0x95, 0x64, 0xDD, 0x37,
         0x29, 0x44, 0x1F, 0x76, 0xB5, 0x3A, 0x47, 0xD3, 0xE0, 0x18, 0x1E, 0x60,
         0xE9, 0x94, 0x13, 0xA4, 0x47, 0xCD, 0xBE, 0x03},
    },
    {
        {0x2C, 0x98, 0x5A, 0x33, 0x10, 0x81, 0x1F, 0x3F, 0xFC, 0x66, 0x00, 0xCF,
         0x87, 0xA6, 0x46, 0x18, 0x60, 0x11, 0x2F, 0x9B, 0xE9, 0x07, 0xE2, 0x2C,
         0xE2, 0x4C, 0xBF, 0x50, 0x27, 0x1D, 0x57, 0xE1},
        {0x93, 0xF5, 0x3A, 0xA7, 0x4E, 0xAE, 0x26, 0x48, 0x02, 0xA7, 0x13, 0xED,
         0x52, 0xAA, 0x14, 0x86, 0x06, 0x09, 0xC5, 0xAC, 0x4B, 0x64, 0x16, 0x25,
         0xEB, 0x12, 0x4B, 0x73, 0xD3, 0x13, 0xCB, 0x8F},
    },
};

const G2ElemStr EcGroupTest::efq2_identity_str = {
    {
        {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
        {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    },
    {
        {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
        {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    },
};

// msg=aad, size=3
// algorithm code path: sqrt result <= modulus/2, high bit is 0
const G1ElemStr EcGroupTest::kAadHash = {
    0xB2, 0x12, 0x39, 0x3A, 0xA0, 0xCF, 0xA0, 0xDE, 0xB8, 0x85, 0xE7,
    0x5B, 0x1C, 0x13, 0x01, 0x0D, 0x0D, 0xA2, 0xBA, 0xC5, 0xB4, 0x3F,
    0x5E, 0xC7, 0x5B, 0x5A, 0xE2, 0x49, 0x1B, 0x3F, 0x65, 0x08, 0xC2,
    0x47, 0x40, 0xF3, 0xC7, 0x08, 0xA2, 0x41, 0x61, 0x99, 0x65, 0x4D,
    0x82, 0x2B, 0x9A, 0x06, 0x2C, 0xDF, 0x07, 0x71, 0xCC, 0xFA, 0x73,
    0x51, 0x45, 0x87, 0x55, 0x07, 0x17, 0xD1, 0x9C, 0x0B};

// msg=bsn0, size=4
// algorithm code path: sqrt result <= modulus/2, high bit is 1
const G1ElemStr EcGroupTest::kBsn0Hash = {
    0x04, 0x0C, 0xB6, 0x57, 0x26, 0xD0, 0xE1, 0x48, 0x23, 0xC2, 0x40,
    0x5A, 0x91, 0x7C, 0xC6, 0x33, 0xFE, 0x0C, 0xC2, 0x2B, 0x52, 0x9D,
    0x6B, 0x87, 0xF9, 0xA7, 0x82, 0xCB, 0x36, 0x90, 0xFB, 0x09, 0x10,
    0xB1, 0x55, 0xAD, 0x98, 0x0D, 0x4F, 0x94, 0xDD, 0xBE, 0x52, 0x21,
    0x87, 0xC6, 0x3E, 0x52, 0x22, 0x83, 0xE3, 0x10, 0x36, 0xEF, 0xF8,
    0x6B, 0x04, 0x4D, 0x9F, 0x14, 0xA8, 0x51, 0xAF, 0xC3};

// msg=test, size=4
// algorithm code path: sqrt result > modulus/2, high bit is 0
const G1ElemStr EcGroupTest::kTestHash = {
    0x82, 0x14, 0xAD, 0xE2, 0x0E, 0xCC, 0x95, 0x27, 0x14, 0xD0, 0x70,
    0xF1, 0x70, 0x17, 0xC2, 0xC2, 0x8C, 0x9F, 0x05, 0x79, 0xCD, 0xC8,
    0x72, 0x55, 0xFE, 0xAB, 0x80, 0x6F, 0x40, 0x5A, 0x6E, 0x64, 0x37,
    0x14, 0x7F, 0x8B, 0xF9, 0xD7, 0xEB, 0xA4, 0x5D, 0x9E, 0x57, 0x85,
    0xFF, 0x0F, 0xE5, 0xC6, 0x73, 0x4F, 0x17, 0x19, 0x96, 0x31, 0x3A,
    0xD1, 0xE1, 0x4E, 0xA8, 0xF9, 0x56, 0xD4, 0xBA, 0x4D};

// msg=aac, size=3
const G1ElemStr EcGroupTest::kAacHash = {
    0xAF, 0x5C, 0xBC, 0xD4, 0x88, 0x18, 0xD0, 0x35, 0xBD, 0xE0, 0x2F,
    0x77, 0x8B, 0x76, 0x52, 0x78, 0x92, 0x66, 0x36, 0x3A, 0x72, 0x15,
    0x20, 0x84, 0xE7, 0x1E, 0xFE, 0x94, 0x77, 0xFD, 0x83, 0x08, 0xEF,
    0x4B, 0x6B, 0xDE, 0x24, 0xD8, 0x42, 0x34, 0x88, 0xB8, 0x87, 0x4A,
    0xA8, 0x5D, 0x5A, 0xC1, 0x82, 0xFF, 0xE5, 0x25, 0xD7, 0x20, 0x2D,
    0x99, 0x49, 0xFE, 0x72, 0x34, 0xAA, 0xC9, 0xD2, 0xAA};

///////////////////////////////////////////////////////////////////////
// NewEcGroup
TEST_F(EcGroupTest, NewFailsGivenArgumentsMismatch) {
  // construct Fq^2 finite field
  FqElemStr beta_str = {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46,
                         0xE5, 0xF2, 0x5E, 0xEE, 0x71, 0xA4, 0x9F, 0x0C, 0xDC,
                         0x65, 0xFB, 0x12, 0x98, 0x0A, 0x82, 0xD3, 0x29, 0x2D,
                         0xDB, 0xAE, 0xD3, 0x30, 0x12}};
  FfElementObj neg_beta(&fq);
  THROW_ON_EPIDERR(FfNeg(fq, FfElementObj(&fq, beta_str), neg_beta));
  FiniteFieldObj fq2(fq, neg_beta, 2);

  FfElementObj fq2_a(&fq2);
  FfElementObj fq2_b(&fq2);
  FfElementObj g2_x(&fq2);
  FfElementObj g2_y(&fq2);

  EcGroup* g = nullptr;
  EXPECT_EQ(kEpidBadArgErr, NewEcGroup(fq2, this->fq_a, this->fq_b, this->g1_x,
                                       this->g1_y, this->bn_p, this->bn_h, &g));
  DeleteEcGroup(&g);
  EXPECT_EQ(kEpidBadArgErr, NewEcGroup(this->fq, fq2_a, this->fq_b, this->g1_x,
                                       this->g1_y, this->bn_p, this->bn_h, &g));
  DeleteEcGroup(&g);
  EXPECT_EQ(kEpidBadArgErr, NewEcGroup(this->fq, this->fq_a, fq2_b, this->g1_x,
                                       this->g1_y, this->bn_p, this->bn_h, &g));
  DeleteEcGroup(&g);
  EXPECT_EQ(kEpidBadArgErr, NewEcGroup(this->fq, this->fq_a, this->fq_b, g2_x,
                                       this->g1_y, this->bn_p, this->bn_h, &g));
  DeleteEcGroup(&g);
  EXPECT_EQ(kEpidBadArgErr,
            NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x, g2_y,
                       this->bn_p, this->bn_h, &g));
  DeleteEcGroup(&g);
}
TEST_F(EcGroupTest, NewFailsGivenNullParameters) {
  EcGroup* g;
  EpidStatus sts;
  sts = NewEcGroup(this->fq, nullptr, this->fq_b, this->g1_x, this->g1_y,
                   this->bn_p, this->bn_h, &g);
  EXPECT_EQ(kEpidBadArgErr, sts);
  sts = NewEcGroup(this->fq, this->fq_a, nullptr, this->g1_x, this->g1_y,
                   this->bn_p, this->bn_h, &g);
  EXPECT_EQ(kEpidBadArgErr, sts);
  sts = NewEcGroup(this->fq, this->fq_a, this->fq_b, nullptr, this->g1_y,
                   this->bn_p, this->bn_h, &g);
  EXPECT_EQ(kEpidBadArgErr, sts);
  sts = NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x, nullptr,
                   this->bn_p, this->bn_h, &g);
  EXPECT_EQ(kEpidBadArgErr, sts);
  sts = NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x, this->g1_y,
                   nullptr, this->bn_h, &g);
  EXPECT_EQ(kEpidBadArgErr, sts);
  sts = NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x, this->g1_y,
                   this->bn_p, nullptr, &g);
  EXPECT_EQ(kEpidBadArgErr, sts);
  sts = NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x, this->g1_y,
                   this->bn_p, this->bn_h, nullptr);
  EXPECT_EQ(kEpidBadArgErr, sts);
}
TEST_F(EcGroupTest, CanCreateEcGroupBasedOnFq) {
  EcGroup* g;
  EpidStatus sts = NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x,
                              this->g1_y, this->bn_p, this->bn_h, &g);
  EXPECT_EQ(kEpidNoErr, sts);

  DeleteEcGroup(&g);
}
TEST_F(EcGroupTest, CanCreateEcGroupBasedOnFq2) {
  EcGroup* g;
  EXPECT_EQ(kEpidNoErr,
            NewEcGroup(efq2_par->fq2, efq2_par->a, efq2_par->b, efq2_par->x,
                       efq2_par->y, efq2_par->order, efq2_par->cofactor, &g));

  DeleteEcGroup(&g);
}

///////////////////////////////////////////////////////////////////////
// DeleteEcGroup
TEST_F(EcGroupTest, DeleteWorksGivenNewlyCreatedEcGroup) {
  EcGroup* g;
  THROW_ON_EPIDERR(NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x,
                              this->g1_y, this->bn_p, this->bn_h, &g));
  EXPECT_NO_THROW(DeleteEcGroup(&g));
}
TEST_F(EcGroupTest, DeleteWorksGivenNewlyCreatedEcGroupFq2) {
  EcGroup* g;
  THROW_ON_EPIDERR(NewEcGroup(efq2_par->fq2, efq2_par->a, efq2_par->b,
                              efq2_par->x, efq2_par->y, efq2_par->order,
                              efq2_par->cofactor, &g));
  EXPECT_NO_THROW(DeleteEcGroup(&g));
}
TEST_F(EcGroupTest, DeleteNullsPointer) {
  EcGroup* g = nullptr;
  THROW_ON_EPIDERR(NewEcGroup(this->fq, this->fq_a, this->fq_b, this->g1_x,
                              this->g1_y, this->bn_p, this->bn_h, &g));

  EXPECT_NO_THROW(DeleteEcGroup(&g));
  EXPECT_EQ(nullptr, g);
}
TEST_F(EcGroupTest, DeleteWorksGivenNullPointer) {
  EXPECT_NO_THROW(DeleteEcGroup(nullptr));
  EcGroup* g = nullptr;
  EXPECT_NO_THROW(DeleteEcGroup(&g));
}
///////////////////////////////////////////////////////////////////////
// NewEcPoint
TEST_F(EcGroupTest, NewEcPointSucceedsGivenEcGroupBasedOnFq) {
  EcPoint* point = nullptr;
  EXPECT_EQ(kEpidNoErr, NewEcPoint(this->efq, &point));
  DeleteEcPoint(&point);
}
TEST_F(EcGroupTest, NewEcPointFailsGivenNullPointer) {
  EcPoint* point = nullptr;
  EXPECT_EQ(kEpidBadArgErr, NewEcPoint(nullptr, &point));
  EXPECT_EQ(kEpidBadArgErr, NewEcPoint(this->efq, nullptr));
  DeleteEcPoint(&point);
}
TEST_F(EcGroupTest, NewEcPointSucceedsGivenEcGroupBasedOnFq2) {
  EcPoint* point = nullptr;
  EXPECT_EQ(kEpidNoErr, NewEcPoint(this->efq2, &point));
  DeleteEcPoint(&point);
}
TEST_F(EcGroupTest, DefaultEcPointIsIdentity) {
  G1ElemStr g1_elem_str = {{{{0}}}, {{{0}}}};
  EcPoint* point = nullptr;
  EXPECT_EQ(kEpidNoErr, NewEcPoint(this->efq, &point));
  EpidStatus sts =
      WriteEcPoint(this->efq, point, &g1_elem_str, sizeof(g1_elem_str));
  EXPECT_EQ(this->efq_identity_str, g1_elem_str);
  DeleteEcPoint(&point);
  THROW_ON_EPIDERR(sts);

  G2ElemStr g2_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr, NewEcPoint(this->efq2, &point));
  sts = WriteEcPoint(this->efq2, point, &g2_elem_str, sizeof(g2_elem_str));
  EXPECT_EQ(this->efq2_identity_str, g2_elem_str);
  DeleteEcPoint(&point);
  THROW_ON_EPIDERR(sts);
}
///////////////////////////////////////////////////////////////////////
// DeleteEcPoint
TEST_F(EcGroupTest, DeleteEcPointNullsPointer) {
  EcPoint* point = nullptr;
  THROW_ON_EPIDERR(NewEcPoint(this->efq, &point));
  EXPECT_NO_THROW(DeleteEcPoint(&point));
  EXPECT_EQ(nullptr, point);
}
TEST_F(EcGroupTest, DeleteEcPointWorksGivenNullPointer) {
  EXPECT_NO_THROW(DeleteEcPoint(nullptr));
  EcPoint* point = nullptr;
  EXPECT_NO_THROW(DeleteEcPoint(&point));
  EXPECT_EQ(nullptr, point);
}
///////////////////////////////////////////////////////////////////////
// ReadEcPoint
TEST_F(EcGroupTest, ReadFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr, ReadEcPoint(nullptr, &(this->efq_a_str),
                                        sizeof(this->efq_a_str), this->efq_a));
  EXPECT_EQ(kEpidBadArgErr, ReadEcPoint(this->efq, nullptr,
                                        sizeof(this->efq_a_str), this->efq_a));
  EXPECT_EQ(kEpidBadArgErr, ReadEcPoint(this->efq, &(this->efq_a_str),
                                        sizeof(this->efq_a_str), nullptr));
}
TEST_F(EcGroupTest, ReadFailsGivenInvalidBufferSize) {
  EXPECT_EQ(kEpidBadArgErr,
            ReadEcPoint(this->efq, &(this->efq_a_str), 0, this->efq_a));
  EXPECT_EQ(kEpidBadArgErr,
            ReadEcPoint(this->efq, &(this->efq_a_str),
                        sizeof(this->efq_a_str) - 1, this->efq_a));
  EXPECT_EQ(kEpidBadArgErr,
            ReadEcPoint(this->efq, &(this->efq_a_str),
                        std::numeric_limits<size_t>::max(), this->efq_a));
}
TEST_F(EcGroupTest, ReadEcPointReadsG1PointCorrectly) {
  G1ElemStr g1_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr, ReadEcPoint(this->efq, &this->efq_a_str,
                                    sizeof(this->efq_a_str), this->efq_a));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_a, &g1_elem_str, sizeof(g1_elem_str)));
  EXPECT_EQ(this->efq_a_str, g1_elem_str);
}
TEST_F(EcGroupTest, ReadEcPointReadsG1IdentityPointCorrectly) {
  G1ElemStr g1_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr,
            ReadEcPoint(this->efq, &this->efq_identity_str,
                        sizeof(this->efq_identity_str), this->efq_a));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_a, &g1_elem_str, sizeof(g1_elem_str)));
  EXPECT_EQ(this->efq_identity_str, g1_elem_str);
}
TEST_F(EcGroupTest, ReadEcPointReadsG2IdentityPointCorrectly) {
  G2ElemStr g2_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr,
            ReadEcPoint(this->efq2, &this->efq2_identity_str,
                        sizeof(this->efq2_identity_str), this->efq2_r));
  THROW_ON_EPIDERR(WriteEcPoint(this->efq2, this->efq2_r, &g2_elem_str,
                                sizeof(g2_elem_str)));
  EXPECT_EQ(this->efq2_identity_str, g2_elem_str);
}
TEST_F(EcGroupTest, ReadEcPointReadsG2PointCorrectly) {
  G2ElemStr g2_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr, ReadEcPoint(this->efq2, &this->efq2_a_str,
                                    sizeof(this->efq2_a_str), this->efq2_r));
  THROW_ON_EPIDERR(WriteEcPoint(this->efq2, this->efq2_r, &g2_elem_str,
                                sizeof(g2_elem_str)));
  EXPECT_EQ(this->efq2_a_str, g2_elem_str);
}

TEST_F(EcGroupTest, ReadFailsGivenPointDoesNotBelongToEcGroup) {
  G1ElemStr bad_g1_point = this->efq_a_str;
  bad_g1_point.x.data.data[31]++;  // make point not belong to the group
  EXPECT_EQ(kEpidBadArgErr, ReadEcPoint(this->efq, &bad_g1_point,
                                        sizeof(bad_g1_point), this->efq_a));

  G2ElemStr bad_g2_point = this->efq2_a_str;
  bad_g2_point.x[0].data.data[31]++;  // make point not belong to the group
  EXPECT_EQ(kEpidBadArgErr, ReadEcPoint(this->efq2, &bad_g2_point,
                                        sizeof(bad_g2_point), this->efq2_a));
}
///////////////////////////////////////////////////////////////////////
// WriteEcPoint
TEST_F(EcGroupTest, WriteFailsGivenNullPointer) {
  G1ElemStr g1_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidBadArgErr, WriteEcPoint(nullptr, this->efq_a, &g1_elem_str,
                                         sizeof(g1_elem_str)));
  EXPECT_EQ(kEpidBadArgErr, WriteEcPoint(this->efq, nullptr, &g1_elem_str,
                                         sizeof(g1_elem_str)));
  EXPECT_EQ(kEpidBadArgErr,
            WriteEcPoint(this->efq, this->efq_a, nullptr, sizeof(g1_elem_str)));
}
TEST_F(EcGroupTest, WriteFailsGivenInvalidBufferSize) {
  G1ElemStr g1_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidBadArgErr,
            WriteEcPoint(this->efq, this->efq_a, &g1_elem_str, 0));
  EXPECT_EQ(kEpidBadArgErr, WriteEcPoint(this->efq, this->efq_a, &g1_elem_str,
                                         sizeof(g1_elem_str) - 1));
  EXPECT_EQ(kEpidBadArgErr, WriteEcPoint(this->efq, this->efq_a, &g1_elem_str,
                                         std::numeric_limits<size_t>::max()));
}
TEST_F(EcGroupTest, WriteEcPointWritesG1PointCorrectly) {
  G1ElemStr g1_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr, WriteEcPoint(this->efq, this->efq_a, &g1_elem_str,
                                     sizeof(g1_elem_str)));
  EXPECT_EQ(this->efq_a_str, g1_elem_str);
}
TEST_F(EcGroupTest, WriteEcPointWritesG1IdentityPointCorrectly) {
  G1ElemStr g1_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr, WriteEcPoint(this->efq, this->efq_identity,
                                     &g1_elem_str, sizeof(g1_elem_str)));
  EXPECT_EQ(this->efq_identity_str, g1_elem_str);
}
TEST_F(EcGroupTest, WriteEcPointWritesG2IdentityPointCorrectly) {
  G2ElemStr g2_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr, WriteEcPoint(this->efq2, this->efq2_identity,
                                     &g2_elem_str, sizeof(g2_elem_str)));
  EXPECT_EQ(this->efq2_identity_str, g2_elem_str);
}
TEST_F(EcGroupTest, WriteEcPointWritesG2PointCorrectly) {
  G2ElemStr g2_elem_str = {{{{0}}}, {{{0}}}};
  EXPECT_EQ(kEpidNoErr, WriteEcPoint(this->efq2, this->efq2_a, &g2_elem_str,
                                     sizeof(g2_elem_str)));
  EXPECT_EQ(this->efq2_a_str, g2_elem_str);
}
///////////////////////////////////////////////////////////////////////
// EcMul
TEST_F(EcGroupTest, MulFailsGivenArgumentsMismatch) {
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(this->efq2, this->efq_a, this->efq_b, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(this->efq, this->efq2_a, this->efq_b, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(this->efq, this->efq_a, this->efq2_b, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(this->efq, this->efq_a, this->efq_b, this->efq2_r));
}
TEST_F(EcGroupTest, MulFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(nullptr, this->efq_a, this->efq_b, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(this->efq, nullptr, this->efq_b, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(this->efq, this->efq_a, nullptr, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMul(this->efq, this->efq_a, this->efq_b, nullptr));
}
TEST_F(EcGroupTest, MulSucceedsGivenIdentityElement) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcMul(this->efq, this->efq_a, this->efq_identity, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_a_str, efq_r_str);

  EXPECT_EQ(kEpidNoErr,
            EcMul(this->efq, this->efq_identity, this->efq_a, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_a_str, efq_r_str);
}

TEST_F(EcGroupTest, MulSucceedsGivenTwoElements) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcMul(this->efq, this->efq_a, this->efq_b, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_mul_ab_str, efq_r_str);
}
TEST_F(EcGroupTest, MulSucceedsGivenG2IdentityElement) {
  G2ElemStr efq2_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcMul(this->efq2, this->efq2_a, this->efq2_identity, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_a_str, efq2_r_str);

  EXPECT_EQ(kEpidNoErr,
            EcMul(this->efq2, this->efq2_identity, this->efq2_a, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_a_str, efq2_r_str);
}
TEST_F(EcGroupTest, MulSucceedsGivenTwoG2Elements) {
  G2ElemStr efq2_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcMul(this->efq2, this->efq2_a, this->efq2_b, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_mul_ab_str, efq2_r_str);
}
///////////////////////////////////////////////////////////////////////
// EcExp
TEST_F(EcGroupTest, ExpFailsGivenArgumentsMismatch) {
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(this->efq2, this->efq_a, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(this->efq, this->efq2_a, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(this->efq, this->efq_a, &zero_bn_str, this->efq2_r));
}
TEST_F(EcGroupTest, ExpFailsGivenNullPointer) {
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(nullptr, this->efq_a, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(this->efq, nullptr, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(this->efq, this->efq_a, nullptr, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(this->efq, this->efq_a, &zero_bn_str, nullptr));
}
TEST_F(EcGroupTest, ExpSucceedsGivenZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidNoErr,
            EcExp(this->efq, this->efq_a, &zero_bn_str, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, ExpResultIsCorrect) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcExp(this->efq, this->efq_a, &this->x_str, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_exp_ax_str, efq_r_str);
}
TEST_F(EcGroupTest, ExpFailsGivenOutOfRangeExponent) {
  // The exponent should be less than elliptic curve group order
  EXPECT_EQ(kEpidBadArgErr,
            EcExp(this->efq, this->efq_a, &this->p, this->efq_r));
}
TEST_F(EcGroupTest, ExpSucceedsGivenG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidNoErr,
            EcExp(this->efq2, this->efq2_a, &zero_bn_str, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, ExpResultIsCorrectForG2) {
  G2ElemStr efq2_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcExp(this->efq2, this->efq2_a, &this->x_str, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_exp_ax_str, efq2_r_str);
}
///////////////////////////////////////////////////////////////////////
// EcSscmExp
TEST_F(EcGroupTest, SscmExpFailsGivenArgumentsMismatch) {
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(this->efq2, this->efq_a, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(this->efq, this->efq2_a, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(this->efq, this->efq_a, &zero_bn_str, this->efq2_r));
}
TEST_F(EcGroupTest, SscmExpFailsGivenNullPointer) {
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(nullptr, this->efq_a, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(this->efq, nullptr, &zero_bn_str, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(this->efq, this->efq_a, nullptr, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(this->efq, this->efq_a, &zero_bn_str, nullptr));
}
TEST_F(EcGroupTest, SscmExpSucceedsGivenZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidNoErr,
            EcSscmExp(this->efq, this->efq_a, &zero_bn_str, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, SscmExpResultIsCorrect) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcSscmExp(this->efq, this->efq_a, &this->x_str, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_exp_ax_str, efq_r_str);
}
TEST_F(EcGroupTest, SscmExpFailsGivenOutOfRangeExponent) {
  // The exponent should be less than elliptic curve group order
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmExp(this->efq, this->efq_a, &this->p, this->efq_r));
}
TEST_F(EcGroupTest, SscmExpSucceedsGivenG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EXPECT_EQ(kEpidNoErr,
            EcSscmExp(this->efq2, this->efq2_a, &zero_bn_str, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, SscmExpResultIsCorrectForG2) {
  G2ElemStr efq2_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcSscmExp(this->efq2, this->efq2_a, &this->x_str, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_exp_ax_str, efq2_r_str);
}
///////////////////////////////////////////////////////////////////////
// EcMultiExp
TEST_F(EcGroupTest, MultiExpFailsGivenArgumentsMismatch) {
  EcPoint const* pts_ec1[] = {this->efq_a, this->efq_b};
  EcPoint const* pts_ec2[] = {this->efq2_a, this->efq2_b};
  EcPoint const* pts_ec1_ec2[] = {this->efq_a, this->efq2_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumStr const* b[] = {&bnm0, &bnm1};
  size_t m = 2;

  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq2, pts_ec1, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, pts_ec2, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, pts_ec1, b, m, this->efq2_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExp(this->efq, pts_ec1_ec2, b, m, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpFailsGivenNullPointer) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  EcPoint const* pts_withnull[] = {nullptr, this->efq_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumStr const* b[] = {&bnm0, &bnm1};
  BigNumStr const* b_withnull[] = {nullptr, &bnm1};
  size_t m = 2;

  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(nullptr, pts, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, nullptr, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExp(this->efq, pts, nullptr, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, pts, b, m, nullptr));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExp(this->efq, pts_withnull, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExp(this->efq, pts, b_withnull, m, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpFailsGivenIncorrectMLen) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumStr const* b[] = {&bnm0, &bnm1};
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, pts, b, 0, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExp(this->efq, pts, b, std::numeric_limits<size_t>::max(),
                       this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExp(this->efq, pts, b, (size_t)INT_MAX + 1, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpFailsGivenOutOfRangeExponent) {
  EcPoint const* pts[] = {this->efq_a};
  BigNumStr const* b_1[] = {&this->p};
  // The exponent should be less than elliptic curve group order
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, pts, b_1, 1, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpFailsGivenOutOfRangeExponents) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  const BigNumStr bnm_1 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3,
                            0x6B, 0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27,
                            0xFF, 0xB8, 0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF,
                            0xF2, 0x6A, 0xFF, 0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  BigNumStr const* b_1[] = {&bnm_1, &this->p};
  BigNumStr const* b_2[] = {&this->p, &bnm_1};
  // The exponent should be less than elliptic curve group order
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, pts, b_1, 2, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExp(this->efq, pts, b_2, 2, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpWorksGivenOneZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a};
  BigNumStr const* b[] = {&zero_bn_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenTwoZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a, this->efq_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenSixZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a, this->efq_a, this->efq_a,
                          this->efq_a, this->efq_a, this->efq_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str, &zero_bn_str,
                          &zero_bn_str, &zero_bn_str, &zero_bn_str};
  size_t m = 6;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenOneG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a};
  BigNumStr const* b[] = {&zero_bn_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenTwoG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a, this->efq2_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenSixG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a, this->efq2_a, this->efq2_a,
                          this->efq2_a, this->efq2_a, this->efq2_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str, &zero_bn_str,
                          &zero_bn_str, &zero_bn_str, &zero_bn_str};
  size_t m = 6;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenOneExponent) {
  G1ElemStr efq_r_str;
  EcPoint const* pts[] = {this->efq_a};
  BigNumStr const* b[] = {&this->x_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_exp_ax_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenTwoExponents) {
  G1ElemStr efq_r_str;
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  BigNumStr const* b[] = {&this->x_str, &this->y_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_multiexp_abxy_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenOneG2Exponent) {
  G2ElemStr efq2_r_str;
  EcPoint const* pts[] = {this->efq2_a};
  BigNumStr const* b[] = {&this->x_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_exp_ax_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpWorksGivenTwoG2Exponents) {
  G2ElemStr efq2_r_str;
  EcPoint const* pts[] = {this->efq2_a, this->efq2_b};
  BigNumStr const* b[] = {&this->x_str, &this->y_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_multiexp_abxy_str, efq2_r_str);
}
///////////////////////////////////////////////////////////////////////
// EcMultiExpBn
TEST_F(EcGroupTest, MultiExpBnFailsGivenArgumentsMismatch) {
  EcPoint const* pts_ec1[] = {this->efq_a, this->efq_b};
  EcPoint const* pts_ec2[] = {this->efq2_a, this->efq2_b};
  EcPoint const* pts_ec1_ec2[] = {this->efq_a, this->efq2_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumObj bno0(bnm0);
  BigNumObj bno1(bnm1);
  BigNum const* b[] = {bno0, bno1};
  size_t m = 2;
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq2, pts_ec1, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts_ec2, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts_ec1, b, m, this->efq2_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts_ec1_ec2, b, m, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpBnFailsGivenNullPointer) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  EcPoint const* pts_withnull[] = {nullptr, this->efq_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumObj bno0(bnm0);
  BigNumObj bno1(bnm1);
  BigNum const* b[] = {bno0, bno1};
  BigNum const* b_withnull[] = {nullptr, bno1};
  size_t m = 2;
  EXPECT_EQ(kEpidBadArgErr, EcMultiExpBn(nullptr, pts, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, nullptr, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts, nullptr, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExpBn(this->efq, pts, b, m, nullptr));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts_withnull, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts, b_withnull, m, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpBnFailsGivenIncorrectMLen) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumObj bno0(bnm0);
  BigNumObj bno1(bnm1);
  BigNum const* b[] = {bno0, bno1};
  EXPECT_EQ(kEpidBadArgErr, EcMultiExpBn(this->efq, pts, b, 0, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts, b, std::numeric_limits<size_t>::max(),
                         this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcMultiExpBn(this->efq, pts, b, (size_t)INT_MAX + 1, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpBnFailsGivenOutOfRangeExponent) {
  EcPoint const* pt[] = {this->efq_a};
  BigNumObj bno_p(this->p);
  BigNum const* b[] = {bno_p};
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  const BigNumStr bnm_1 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3,
                            0x6B, 0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27,
                            0xFF, 0xB8, 0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF,
                            0xF2, 0x6A, 0xFF, 0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  BigNumObj bno_1(bnm_1);
  BigNum const* b_1[] = {bno_1, bno_p};
  BigNum const* b_2[] = {bno_p, bno_1};
  EXPECT_EQ(kEpidBadArgErr, EcMultiExpBn(this->efq, pt, b, 1, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExpBn(this->efq, pts, b_1, 2, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMultiExpBn(this->efq, pts, b_2, 2, this->efq_r));
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenOneZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a};
  BigNumObj bno_zero(zero_bn_str);
  BigNum const* b[] = {bno_zero};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenTwoZeroExponents) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a, this->efq_a};
  BigNumObj bno_zero0(zero_bn_str);
  BigNumObj bno_zero1(zero_bn_str);
  BigNum const* b[] = {bno_zero0, bno_zero1};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenSixZeroExponents) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a, this->efq_a, this->efq_a,
                          this->efq_a, this->efq_a, this->efq_a};
  BigNumObj bno_zero0(zero_bn_str);
  BigNumObj bno_zero1(zero_bn_str);
  BigNumObj bno_zero2(zero_bn_str);
  BigNumObj bno_zero3(zero_bn_str);
  BigNumObj bno_zero4(zero_bn_str);
  BigNumObj bno_zero5(zero_bn_str);
  BigNum const* b[] = {bno_zero0, bno_zero1, bno_zero2,
                       bno_zero3, bno_zero4, bno_zero5};
  size_t m = 6;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenOneG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a};
  BigNumObj bno_zero(zero_bn_str);
  BigNum const* b[] = {bno_zero};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenTwoG2ZeroExponents) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a, this->efq2_a};
  BigNumObj bno_zero0(zero_bn_str);
  BigNumObj bno_zero1(zero_bn_str);
  BigNum const* b[] = {bno_zero0, bno_zero1};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenSixG2ZeroExponents) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  BigNumObj bno_zero0(zero_bn_str);
  BigNumObj bno_zero1(zero_bn_str);
  BigNumObj bno_zero2(zero_bn_str);
  BigNumObj bno_zero3(zero_bn_str);
  BigNumObj bno_zero4(zero_bn_str);
  BigNumObj bno_zero5(zero_bn_str);
  EcPoint const* pts[] = {this->efq2_a, this->efq2_a, this->efq2_a,
                          this->efq2_a, this->efq2_a, this->efq2_a};
  BigNum const* b[] = {bno_zero0, bno_zero1, bno_zero2,
                       bno_zero3, bno_zero4, bno_zero5};
  size_t m = 6;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenOneExponent) {
  G1ElemStr efq_r_str;
  EcPoint const* pts[] = {this->efq_a};
  BigNumObj bno_x(this->x_str);
  BigNum const* b[] = {bno_x};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_exp_ax_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenTwoExponents) {
  G1ElemStr efq_r_str;
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  BigNumObj bno_x(this->x_str);
  BigNumObj bno_y(this->y_str);
  BigNum const* b[] = {bno_x, bno_y};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_multiexp_abxy_str, efq_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenOneG2Exponent) {
  G2ElemStr efq2_r_str;
  EcPoint const* pts[] = {this->efq2_a};
  BigNumObj bno_x(this->x_str);
  BigNum const* b[] = {bno_x};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_exp_ax_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenTwoG2Exponents) {
  G2ElemStr efq2_r_str;
  EcPoint const* pts[] = {this->efq2_a, this->efq2_b};
  BigNumObj bno_x(this->x_str);
  BigNumObj bno_y(this->y_str);
  BigNum const* b[] = {bno_x, bno_y};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_multiexp_abxy_str, efq2_r_str);
}
TEST_F(EcGroupTest, MultiExpBnWorksGivenTwoDifferentSizeG3Exponents) {
  const G1ElemStr g3_b_str = {
      {{{
          0x09, 0x0d, 0x6f, 0x82, 0x77, 0x88, 0x49, 0x53, 0xba, 0x1e, 0x1b,
          0x0e, 0x5e, 0xae, 0xc0, 0x27, 0xad, 0xe3, 0xb1, 0x09, 0x4f, 0xcd,
          0xb6, 0xe6, 0x6f, 0x7f, 0xa3, 0x1a, 0x1e, 0xfb, 0x52, 0x72,
      }}},
      {{{
          0xfa, 0x85, 0x0f, 0x5c, 0x97, 0x61, 0xbf, 0x46, 0x7e, 0xec, 0xd6,
          0x64, 0xda, 0xa9, 0x8e, 0xf5, 0xd3, 0xdf, 0xfa, 0x13, 0x5a, 0xb2,
          0x3e, 0xeb, 0x0a, 0x9d, 0x02, 0xc0, 0x33, 0xec, 0x2a, 0x70,
      }}}};
  const G1ElemStr g3_k_str = {
      {{{
          0x41, 0xb7, 0xa4, 0xc8, 0x43, 0x3f, 0x0b, 0xc2, 0x80, 0x31, 0xbe,
          0x75, 0x65, 0xe9, 0xbb, 0x81, 0x73, 0x5b, 0x91, 0x4f, 0x3f, 0xd7,
          0xbe, 0xb5, 0x19, 0x56, 0x3f, 0x18, 0x95, 0xea, 0xc1, 0xd7,
      }}},
      {{{
          0xa4, 0x5e, 0xb9, 0x86, 0xfc, 0xe5, 0xc4, 0x0f, 0x54, 0x37, 0xab,
          0xed, 0x59, 0x20, 0xce, 0x67, 0x68, 0x3c, 0x25, 0x4d, 0xbc, 0x5f,
          0x6a, 0x4d, 0x5a, 0xa7, 0x93, 0xce, 0x90, 0x2d, 0x3e, 0x5a,
      }}}};
  EcPointObj B(&this->epid11_G3, g3_b_str);
  EcPointObj K(&this->epid11_G3, g3_k_str);
  EcPoint const* pts[] = {B, K};
  const std::vector<uint8_t> bnm_sf_str = {
      0x00, 0x3c, 0xc1, 0x73, 0x35, 0x3c, 0x99, 0x61, 0xb0, 0x80, 0x9a,
      0x0e, 0x8d, 0xbf, 0x5d, 0x0b, 0xa9, 0x18, 0x2b, 0x36, 0x3c, 0x06,
      0xbc, 0x1c, 0xc7, 0x9f, 0x76, 0xba, 0x5a, 0x26, 0xcd, 0x5e, 0x24,
      0xb9, 0x68, 0xde, 0x47, 0x72, 0xf9, 0xf9, 0x1e, 0xaa, 0x74, 0x17,
      0x31, 0xe4, 0x66, 0x59, 0x69, 0xe5, 0x9e, 0x27, 0x1d, 0x57, 0xe5,
      0x39, 0x57, 0xd4, 0xc5, 0x78, 0xf2, 0x77, 0x5c, 0x9f, 0x6c, 0xfe,
      0x12, 0x00, 0xa8, 0xe0, 0xd3, 0x81, 0x38, 0xaa, 0x5a};
  const BigNumStr bnm_nc_tick_str = {{{
      0xcd, 0x2e, 0xe8, 0xf4, 0x85, 0x95, 0x04, 0x09, 0xbd, 0xa4, 0xfa, 0x07,
      0xe3, 0x1c, 0xb9, 0x5a, 0x82, 0x73, 0xa6, 0xea, 0x47, 0x5c, 0x31, 0x74,
      0x3c, 0x0a, 0xeb, 0x62, 0x94, 0x2f, 0x7b, 0x10,
  }}};
  BigNumObj bno_sf(bnm_sf_str);
  // In order to callculate exp sf data should be devided by group order
  THROW_ON_EPIDERR(BigNumMod(bno_sf, epid11_p_tick, bno_sf));
  BigNumObj bno_nc_tick(bnm_nc_tick_str);
  BigNum const* b[] = {bno_sf, bno_nc_tick};
  EcPointObj R3 = EcPointObj(&this->epid11_G3);
  const std::vector<uint8_t> expected_r_str = {
      // X
      0x1E, 0xDF, 0x9E, 0xA5, 0xF5, 0xED, 0xB3, 0x3F, 0xCC, 0x83, 0x10, 0x5E,
      0x3E, 0xB7, 0xE5, 0x06, 0x5F, 0x19, 0xF9, 0xFD, 0xE9, 0x57, 0x0B, 0x31,
      0xC8, 0xDA, 0x0A, 0x7B, 0xCD, 0xB5, 0xAA, 0x2E,
      // Y
      0x6A, 0x6B, 0x5A, 0x8D, 0x48, 0x5F, 0x2F, 0x72, 0x77, 0x93, 0xD6, 0xD0,
      0x49, 0xE1, 0x84, 0x35, 0x98, 0xF1, 0xDE, 0x71, 0xC5, 0xF4, 0x40, 0xFB,
      0x1C, 0x75, 0x83, 0xD7, 0x4F, 0x58, 0x0A, 0x8D};
  std::vector<uint8_t> g3_r_str;
  g3_r_str.resize(expected_r_str.size(), 0);
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcMultiExpBn(this->epid11_G3, pts, b, m, R3));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->epid11_G3, R3, g3_r_str.data(), g3_r_str.size()));
  EXPECT_EQ(g3_r_str, expected_r_str);
}
///////////////////////////////////////////////////////////////////////
// EcSscmMultiExp
TEST_F(EcGroupTest, SscmMultiExpFailsGivenArgumentsMismatch) {
  EcPoint const* pts_ec1[] = {this->efq_a, this->efq_b};
  EcPoint const* pts_ec2[] = {this->efq2_a, this->efq2_b};
  EcPoint const* pts_ec1_ec2[] = {this->efq_a, this->efq2_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumStr const* b[] = {&bnm0, &bnm1};
  size_t m = 2;

  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq2, pts_ec1, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts_ec2, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts_ec1, b, m, this->efq2_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts_ec1_ec2, b, m, this->efq_r));
}
TEST_F(EcGroupTest, SscmMultiExpFailsGivenNullPointer) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  EcPoint const* pts_withnull[] = {nullptr, this->efq_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumStr const* b[] = {&bnm0, &bnm1};
  BigNumStr const* b_withnull[] = {nullptr, &bnm1};
  size_t m = 2;

  EXPECT_EQ(kEpidBadArgErr, EcSscmMultiExp(nullptr, pts, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, nullptr, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts, nullptr, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcSscmMultiExp(this->efq, pts, b, m, nullptr));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts_withnull, b, m, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts, b_withnull, m, this->efq_r));
}
TEST_F(EcGroupTest, SscmMultiExpFailsGivenIncorrectMLen) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  const BigNumStr bnm0 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3, 0x6B,
                           0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27, 0xFF, 0xB8,
                           0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF, 0xF2, 0x6A, 0xFF,
                           0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  const BigNumStr bnm1 = {{0xE2, 0xFF, 0x03, 0x1D, 0xFF, 0x19, 0x81, 0xCB, 0xFF,
                           0xFF, 0x6B, 0xD5, 0x3E, 0xFF, 0xFF, 0xFF, 0xFF, 0xBD,
                           0xFF, 0x5A, 0xFF, 0x5C, 0x7C, 0xFF, 0x84, 0xFF, 0xFF,
                           0x8C, 0x03, 0xB2, 0x26, 0xFF}};
  BigNumStr const* b[] = {&bnm0, &bnm1};
  EXPECT_EQ(kEpidBadArgErr, EcSscmMultiExp(this->efq, pts, b, 0, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts, b,
                           std::numeric_limits<size_t>::max(), this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcSscmMultiExp(this->efq, pts, b,
                                           (size_t)INT_MAX + 1, this->efq_r));
}
TEST_F(EcGroupTest, SscmMultiExpFailsGivenOutOfRangeExponent) {
  EcPoint const* pts[] = {this->efq_a};
  BigNumStr const* b_1[] = {&this->p};
  // The exponent should be less than elliptic curve group order
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts, b_1, 1, this->efq_r));
}
TEST_F(EcGroupTest, SscmMultiExpFailsGivenOutOfRangeExponents) {
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  const BigNumStr bnm_1 = {{0x11, 0xFF, 0xFF, 0xFF, 0x4F, 0x59, 0xB1, 0xD3,
                            0x6B, 0x08, 0xFF, 0xFF, 0x0B, 0xF3, 0xAF, 0x27,
                            0xFF, 0xB8, 0xFF, 0xFF, 0x98, 0xFF, 0xEB, 0xFF,
                            0xF2, 0x6A, 0xFF, 0xFF, 0xEA, 0x31, 0xFF, 0xFF}};
  BigNumStr const* b_1[] = {&bnm_1, &this->p};
  BigNumStr const* b_2[] = {&this->p, &bnm_1};
  // The exponent should be less than elliptic curve group order
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts, b_1, 2, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcSscmMultiExp(this->efq, pts, b_2, 2, this->efq_r));
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenOneZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a};
  BigNumStr const* b[] = {&zero_bn_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenTwoZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a, this->efq_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenSixZeroExponent) {
  G1ElemStr efq_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq_a, this->efq_a, this->efq_a,
                          this->efq_a, this->efq_a, this->efq_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str, &zero_bn_str,
                          &zero_bn_str, &zero_bn_str, &zero_bn_str};
  size_t m = 6;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenOneG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a};
  BigNumStr const* b[] = {&zero_bn_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenTwoG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a, this->efq2_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenSixG2ZeroExponent) {
  G2ElemStr efq2_r_str;
  BigNumStr zero_bn_str = {0};
  EcPoint const* pts[] = {this->efq2_a, this->efq2_a, this->efq2_a,
                          this->efq2_a, this->efq2_a, this->efq2_a};
  BigNumStr const* b[] = {&zero_bn_str, &zero_bn_str, &zero_bn_str,
                          &zero_bn_str, &zero_bn_str, &zero_bn_str};
  size_t m = 6;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenOneExponent) {
  G1ElemStr efq_r_str;
  EcPoint const* pts[] = {this->efq_a};
  BigNumStr const* b[] = {&this->x_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_exp_ax_str, efq_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenTwoExponents) {
  G1ElemStr efq_r_str;
  EcPoint const* pts[] = {this->efq_a, this->efq_b};
  BigNumStr const* b[] = {&this->x_str, &this->y_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq, pts, b, m, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_multiexp_abxy_str, efq_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenOneG2Exponent) {
  G2ElemStr efq2_r_str;
  EcPoint const* pts[] = {this->efq2_a};
  BigNumStr const* b[] = {&this->x_str};
  size_t m = 1;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_exp_ax_str, efq2_r_str);
}
TEST_F(EcGroupTest, SscmMultiExpWorksGivenTwoG2Exponents) {
  G2ElemStr efq2_r_str;
  EcPoint const* pts[] = {this->efq2_a, this->efq2_b};
  BigNumStr const* b[] = {&this->x_str, &this->y_str};
  size_t m = 2;
  EXPECT_EQ(kEpidNoErr, EcSscmMultiExp(this->efq2, pts, b, m, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_multiexp_abxy_str, efq2_r_str);
}
///////////////////////////////////////////////////////////////////////
// EcGetRandom
TEST_F(EcGroupTest, GetRandomFailsGivenArgumentsMismatch) {
  Prng my_prng;
  EXPECT_EQ(kEpidBadArgErr,
            EcGetRandom(this->efq2, &Prng::Generate, &my_prng, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcGetRandom(this->efq, &Prng::Generate, &my_prng, this->efq2_r));
}
TEST_F(EcGroupTest, GetRandomFailsGivenNullPointer) {
  Prng my_prng;
  EXPECT_EQ(kEpidBadArgErr,
            EcGetRandom(nullptr, &Prng::Generate, &my_prng, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcGetRandom(this->efq, nullptr, &my_prng, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcGetRandom(this->efq, &Prng::Generate, &my_prng, nullptr));
}
TEST_F(EcGroupTest, GetRandomGeneratesDifferentECPoints) {
  Prng my_prng;
  EcPointObj r1(&this->efq);
  EcPointObj r2(&this->efq);
  bool result;
  // __LINE__ makes sure that r1 and r2 are generated using distinct seeds
  my_prng.set_seed(__LINE__);
  EXPECT_EQ(kEpidNoErr, EcGetRandom(this->efq, &Prng::Generate, &my_prng, r1));
  my_prng.set_seed(__LINE__);
  EXPECT_EQ(kEpidNoErr, EcGetRandom(this->efq, &Prng::Generate, &my_prng, r2));
  THROW_ON_EPIDERR(EcIsEqual(this->efq, r1, r2, &result));
  EXPECT_FALSE(result);
}
///////////////////////////////////////////////////////////////////////
// EcInGroup
TEST_F(EcGroupTest, InGroupFailsGivenNullPointer) {
  bool in_group;
  EXPECT_EQ(kEpidBadArgErr, EcInGroup(nullptr, &(this->efq_a_str),
                                      sizeof(this->efq_a_str), &in_group));
  EXPECT_EQ(kEpidBadArgErr,
            EcInGroup(this->efq, nullptr, sizeof(this->efq_a_str), &in_group));
  EXPECT_EQ(kEpidBadArgErr, EcInGroup(this->efq, &(this->efq_a_str),
                                      sizeof(this->efq_a_str), nullptr));
}
TEST_F(EcGroupTest, InGroupFailsGivenInvalidBufferSize) {
  bool in_group;
  EXPECT_EQ(kEpidBadArgErr,
            EcInGroup(this->efq, &(this->efq_a_str), 0, &in_group));
  EXPECT_EQ(kEpidBadArgErr,
            EcInGroup(this->efq, &(this->efq_a_str),
                      std::numeric_limits<size_t>::max(), &in_group));
#if (SIZE_MAX >= 0x100000001)  // When size_t value allowed to be 0x100000001
  EXPECT_EQ(kEpidBadArgErr,
            EcInGroup(this->efq, &(this->efq_a_str), 0x100000001, &in_group));
#endif
}
TEST_F(EcGroupTest, InGroupDetectsElementNotInGroup) {
  // element be not in group if Y coordinate increased by 1
  G1ElemStr p_str = this->efq_a_str;
  p_str.y.data.data[31] -= 1;

  bool in_group;
  EXPECT_EQ(kEpidNoErr, EcInGroup(this->efq, &p_str, sizeof(p_str), &in_group));
  EXPECT_FALSE(in_group);

  G2ElemStr p2_str = this->efq2_a_str;
  p2_str.y[0].data.data[31] -= 1;

  EXPECT_EQ(kEpidNoErr,
            EcInGroup(this->efq2, &p2_str, sizeof(p2_str), &in_group));
  EXPECT_FALSE(in_group);
}
TEST_F(EcGroupTest, InGroupDetectsIdentityElementInGroup) {
  bool in_group;
  EXPECT_EQ(kEpidNoErr, EcInGroup(this->efq, &(this->efq_identity_str),
                                  sizeof(this->efq_identity_str), &in_group));
  EXPECT_TRUE(in_group);

  EXPECT_EQ(kEpidNoErr, EcInGroup(this->efq2, &(this->efq2_identity_str),
                                  sizeof(this->efq2_identity_str), &in_group));
  EXPECT_TRUE(in_group);
}
TEST_F(EcGroupTest, InGroupFailsGivenContextMismatch) {
  bool in_group;
  EXPECT_EQ(kEpidBadArgErr, EcInGroup(this->efq2, &(this->efq_a_str),
                                      sizeof(this->efq_a_str), &in_group));
  EXPECT_FALSE(in_group);

  EXPECT_EQ(kEpidBadArgErr, EcInGroup(this->efq, &(this->efq2_a_str),
                                      sizeof(this->efq2_a_str), &in_group));
  EXPECT_FALSE(in_group);
}
///////////////////////////////////////////////////////////////////////
// EcHash
TEST_F(EcGroupTest, HashFailsGivenArgumentsMismatch) {
  uint8_t const msg[] = {0};
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq2, msg, sizeof(msg), kSha256, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq, msg, sizeof(msg), kSha256, this->efq2_r));
}
TEST_F(EcGroupTest, HashFailsGivenNullPointer) {
  uint8_t const msg[] = {0};
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(nullptr, msg, sizeof(msg), kSha256, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq, nullptr, sizeof(msg), kSha256, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq, msg, sizeof(msg), kSha256, nullptr));
}
TEST_F(EcGroupTest, HashFailsGivenUnsupportedHashAlg) {
  uint8_t const msg[] = {0};
  EXPECT_EQ(kEpidHashAlgorithmNotSupported,
            EcHash(this->efq, msg, sizeof(msg), kSha512_256, this->efq_r));
  EXPECT_EQ(kEpidHashAlgorithmNotSupported,
            EcHash(this->efq, msg, sizeof(msg), kSha3_256, this->efq_r));
  EXPECT_EQ(kEpidHashAlgorithmNotSupported,
            EcHash(this->efq, msg, sizeof(msg), kSha3_384, this->efq_r));
  EXPECT_EQ(kEpidHashAlgorithmNotSupported,
            EcHash(this->efq, msg, sizeof(msg), kSha3_512, this->efq_r));
}
TEST_F(EcGroupTest, HashFailsGivenIncorrectMsgLen) {
  uint8_t const msg[] = {0};
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq, nullptr, 1, kSha256, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq, msg, std::numeric_limits<size_t>::max(), kSha256,
                   this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq, msg, (size_t)INT_MAX + 1, kSha256, this->efq_r));
#if (SIZE_MAX >= 0x100000001)  // When size_t value allowed to be 0x100000001
  EXPECT_EQ(kEpidBadArgErr,
            EcHash(this->efq, msg, (size_t)0x100000001, kSha256, this->efq_r));
#endif
}
TEST_F(EcGroupTest, HashAcceptsZeroLengthMessage) {
  EXPECT_EQ(kEpidNoErr, EcHash(this->efq, "", 0, kSha256, this->efq_r));
}
TEST_F(EcGroupTest, HashWorksGivenSHA256HashAlg) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcHash(this->efq, sha_msg, sizeof(sha_msg), kSha256, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_r_sha256_str, efq_r_str);
}
TEST_F(EcGroupTest, HashWorksGivenSHA384HashAlg) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcHash(this->efq, sha_msg, sizeof(sha_msg), kSha384, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_r_sha384_str, efq_r_str);
}
TEST_F(EcGroupTest, HashWorksGivenSHA512HashAlg) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcHash(this->efq, sha_msg, sizeof(sha_msg), kSha512, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_r_sha512_str, efq_r_str);
}
///////////////////////////////////////////////////////////////////////
// 1.1 EcHash
TEST_F(EcGroupTest, Epid11HashFailsGivenMismatchedArguments) {
  uint8_t const msg[] = {0};
  EXPECT_EQ(kEpidBadArgErr,
            Epid11EcHash(this->efq2, msg, sizeof(msg), this->efq_r));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11EcHash(this->efq, msg, sizeof(msg), this->efq2_r));
}
TEST_F(EcGroupTest, Epid11HashFailsGivenNullPointer) {
  uint8_t const msg[] = {0};
  EXPECT_EQ(kEpidBadArgErr,
            Epid11EcHash(nullptr, msg, sizeof(msg), this->epid11_G3_r));
  EXPECT_EQ(kEpidBadArgErr, Epid11EcHash(this->epid11_G3, nullptr, sizeof(msg),
                                         this->epid11_G3_r));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11EcHash(this->epid11_G3, msg, sizeof(msg), nullptr));
}
TEST_F(EcGroupTest, Epid11HashFailsGivenInvalidMsgLen) {
  uint8_t const msg[] = {0};
  EXPECT_EQ(kEpidBadArgErr,
            Epid11EcHash(this->epid11_G3, nullptr, 1, this->epid11_G3_r));
  EXPECT_EQ(kEpidBadArgErr, Epid11EcHash(this->epid11_G3, msg,
                                         std::numeric_limits<size_t>::max(),
                                         this->epid11_G3_r));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11EcHash(this->epid11_G3, msg, (size_t)INT_MAX + 1,
                         this->epid11_G3_r));
#if (SIZE_MAX >= 0x100000001)  // When size_t value allowed to be 0x100000001
  EXPECT_EQ(kEpidBadArgErr,
            Epid11EcHash(this->epid11_G3, msg, (size_t)0x100000001,
                         this->epid11_G3_r));
#endif
}
TEST_F(EcGroupTest, Epid11HashAcceptsZeroLengthMessage) {
  EXPECT_EQ(kEpidNoErr,
            Epid11EcHash(this->epid11_G3, "", 0, this->epid11_G3_r));
}
TEST_F(EcGroupTest, Epid11HashWorksGivenValidParameters) {
  Epid11G3ElemStr r_str;

  uint8_t const msg0[] = {'a', 'a', 'd'};
  EXPECT_EQ(kEpidNoErr, Epid11EcHash(this->epid11_G3, msg0, sizeof(msg0),
                                     this->epid11_G3_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->epid11_G3, this->epid11_G3_r, &r_str, sizeof(r_str)));
  EXPECT_EQ(this->kAadHash, r_str);

  uint8_t const msg1[] = {'b', 's', 'n', '0'};
  EXPECT_EQ(kEpidNoErr, Epid11EcHash(this->epid11_G3, msg1, sizeof(msg1),
                                     this->epid11_G3_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->epid11_G3, this->epid11_G3_r, &r_str, sizeof(r_str)));
  EXPECT_EQ(this->kBsn0Hash, r_str);

  uint8_t const msg2[] = {'t', 'e', 's', 't'};
  EXPECT_EQ(kEpidNoErr, Epid11EcHash(this->epid11_G3, msg2, sizeof(msg2),
                                     this->epid11_G3_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->epid11_G3, this->epid11_G3_r, &r_str, sizeof(r_str)));
  EXPECT_EQ(this->kTestHash, r_str);

  uint8_t const msg3[] = {'a', 'a', 'c'};
  EXPECT_EQ(kEpidNoErr, Epid11EcHash(this->epid11_G3, msg3, sizeof(msg3),
                                     this->epid11_G3_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->epid11_G3, this->epid11_G3_r, &r_str, sizeof(r_str)));
  EXPECT_EQ(this->kAacHash, r_str);
}
///////////////////////////////////////////////////////////////////////
// EcMakePoint
TEST_F(EcGroupTest, MakePointFailsGivenArgumentsMismatch) {
  FfElementObj fq2_a(&this->efq2_par->fq2);

  EXPECT_EQ(kEpidBadArgErr, EcMakePoint(this->efq2, this->fq_a, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMakePoint(this->efq, fq2_a, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMakePoint(this->efq2, this->fq_a, this->efq2_r));
}
TEST_F(EcGroupTest, MakePointFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr, EcMakePoint(nullptr, this->fq_a, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMakePoint(this->efq, nullptr, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcMakePoint(this->efq, this->fq_a, nullptr));
}
TEST_F(EcGroupTest, MakePointSucceedsGivenElement) {
  Prng my_prng;
  G1ElemStr efq_r_str;

  // a pre-computed point in eqf
  G1ElemStr efq_ref_str = {
      {{0X1C, 0X53, 0X40, 0X69, 0X8B, 0X77, 0X75, 0XAA, 0X2B, 0X7D, 0X91, 0XD6,
        0X29, 0X49, 0X05, 0X7F, 0XF6, 0X4C, 0X63, 0X90, 0X58, 0X22, 0X06, 0XF5,
        0X1F, 0X3B, 0X9F, 0XA2, 0X04, 0X39, 0XA9, 0X67}},
      {{0X3B, 0X65, 0X58, 0XAC, 0X97, 0X46, 0X47, 0XC9, 0X84, 0X57, 0X3F, 0XFA,
        0X4F, 0XB0, 0X64, 0X8D, 0X48, 0XC8, 0X14, 0XEB, 0XF1, 0X94, 0X87, 0XDC,
        0XB3, 0X73, 0X90, 0X1D, 0X75, 0XAD, 0XD5, 0X56}}};

  // create a point with x == ref.x
  FfElementObj elem(&this->fq, efq_ref_str.x);
  EXPECT_EQ(kEpidNoErr, EcMakePoint(this->efq, elem, this->efq_r));

  // check that the point matches ref
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(efq_ref_str, efq_r_str);
}
TEST_F(EcGroupTest, MakePointFailsGivenZeroElement) {
  EXPECT_EQ(kEpidBadArgErr,
            EcMakePoint(this->efq, FfElementObj(&this->fq), this->efq_r));
  // EcMakePoint is only defined for G1
  EXPECT_EQ(kEpidBadArgErr,
            EcMakePoint(this->efq2, FfElementObj(&this->efq2_par->fq2),
                        this->efq2_r));
}
///////////////////////////////////////////////////////////////////////
// EcInverse
TEST_F(EcGroupTest, InverseFailsGivenArgumentsMismatch) {
  EXPECT_EQ(kEpidBadArgErr, EcInverse(this->efq2, this->efq_a, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcInverse(this->efq, this->efq2_a, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcInverse(this->efq, this->efq_a, this->efq2_r));
}

TEST_F(EcGroupTest, InverseFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr, EcInverse(nullptr, this->efq_a, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcInverse(this->efq, nullptr, this->efq_r));
  EXPECT_EQ(kEpidBadArgErr, EcInverse(this->efq, this->efq_a, nullptr));
}

TEST_F(EcGroupTest, InverseSucceedsGivenIdentity) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr, EcInverse(this->efq, this->efq_identity, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_identity_str, efq_r_str);

  G2ElemStr efq2_r_str;
  EXPECT_EQ(kEpidNoErr,
            EcInverse(this->efq2, this->efq2_identity, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_identity_str, efq2_r_str);
}

TEST_F(EcGroupTest, InverseSucceedsGivenElement) {
  G1ElemStr efq_r_str;
  EXPECT_EQ(kEpidNoErr, EcInverse(this->efq, this->efq_a, this->efq_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq, this->efq_r, &efq_r_str, sizeof(efq_r_str)));
  EXPECT_EQ(this->efq_inv_a_str, efq_r_str);

  G2ElemStr efq2_r_str;
  EXPECT_EQ(kEpidNoErr, EcInverse(this->efq2, this->efq2_a, this->efq2_r));
  THROW_ON_EPIDERR(
      WriteEcPoint(this->efq2, this->efq2_r, &efq2_r_str, sizeof(efq2_r_str)));
  EXPECT_EQ(this->efq2_inv_a_str, efq2_r_str);
}
///////////////////////////////////////////////////////////////////////
// EcIsEqual
TEST_F(EcGroupTest, IsEqualFailsGivenArgumentsMismatch) {
  bool result;
  EXPECT_EQ(kEpidBadArgErr,
            EcIsEqual(this->efq2, this->efq_a, this->efq_a, &result));
  EXPECT_EQ(kEpidBadArgErr,
            EcIsEqual(this->efq, this->efq2_a, this->efq_a, &result));
  EXPECT_EQ(kEpidBadArgErr,
            EcIsEqual(this->efq, this->efq_a, this->efq2_a, &result));
}
TEST_F(EcGroupTest, IsEqualFailsGivenNullPointer) {
  bool result;
  EXPECT_EQ(kEpidBadArgErr,
            EcIsEqual(nullptr, this->efq_a, this->efq_a, &result));
  EXPECT_EQ(kEpidBadArgErr,
            EcIsEqual(this->efq, nullptr, this->efq_a, &result));
  EXPECT_EQ(kEpidBadArgErr,
            EcIsEqual(this->efq, this->efq_a, nullptr, &result));
  EXPECT_EQ(kEpidBadArgErr,
            EcIsEqual(this->efq, this->efq_a, this->efq_a, nullptr));
}
TEST_F(EcGroupTest, IsEqualCanCompareElementWithItself) {
  bool result;
  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq, this->efq_a, this->efq_a, &result));
  EXPECT_TRUE(result);

  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq2, this->efq2_a, this->efq2_a, &result));
  EXPECT_TRUE(result);
}
TEST_F(EcGroupTest, DifferentEFqElementsAreNotEqual) {
  bool result;
  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq, this->efq_a, this->efq_b, &result));
  EXPECT_FALSE(result);
}
TEST_F(EcGroupTest, SameEFqElementsAreEqual) {
  THROW_ON_EPIDERR(ReadEcPoint(this->efq, &(this->efq_a_str),
                               sizeof(this->efq_a_str), this->efq_b));
  bool result;
  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq, this->efq_a, this->efq_b, &result));
  EXPECT_TRUE(result);
}
TEST_F(EcGroupTest, IsEqualCanCompareIdentityEFqElements) {
  THROW_ON_EPIDERR(ReadEcPoint(this->efq, &(this->efq_identity_str),
                               sizeof(this->efq_identity_str), this->efq_b));
  bool result;
  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq, this->efq_identity, this->efq_b, &result));
  EXPECT_TRUE(result);
}

TEST_F(EcGroupTest, DifferentEFq2ElementsAreNotEqual) {
  bool result;
  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq2, this->efq2_a, this->efq2_b, &result));
  EXPECT_FALSE(result);
}
TEST_F(EcGroupTest, SameEFq2ElementsAreEqual) {
  THROW_ON_EPIDERR(ReadEcPoint(this->efq2, &(this->efq2_a_str),
                               sizeof(this->efq2_a_str), this->efq2_b));
  bool result;
  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq2, this->efq2_a, this->efq2_b, &result));
  EXPECT_TRUE(result);
}
TEST_F(EcGroupTest, IsEqualCanCompareIdentityEFq2Elements) {
  THROW_ON_EPIDERR(ReadEcPoint(this->efq2, &(this->efq2_identity_str),
                               sizeof(this->efq2_identity_str), this->efq2_b));
  bool result;
  ASSERT_EQ(kEpidNoErr,
            EcIsEqual(this->efq2, this->efq2_identity, this->efq2_b, &result));
  EXPECT_TRUE(result);
}
///////////////////////////////////////////////////////////////////////
// EcIsIdentity
TEST_F(EcGroupTest, IsIdentityFailsGivenArgumentsMismatch) {
  bool result;
  EXPECT_EQ(kEpidBadArgErr,
            EcIsIdentity(this->efq2, this->efq_identity, &result));
  EXPECT_EQ(kEpidBadArgErr,
            EcIsIdentity(this->efq, this->efq2_identity, &result));
}
TEST_F(EcGroupTest, IsIdentityFailsGivenNullPointer) {
  bool result;
  EXPECT_EQ(kEpidBadArgErr, EcIsIdentity(nullptr, this->efq_identity, &result));
  EXPECT_EQ(kEpidBadArgErr, EcIsIdentity(this->efq, nullptr, &result));
  EXPECT_EQ(kEpidBadArgErr,
            EcIsIdentity(this->efq, this->efq_identity, nullptr));
}
TEST_F(EcGroupTest, IsIdentityDetectsIdentityElement) {
  bool result;
  EXPECT_EQ(kEpidNoErr, EcIsIdentity(this->efq, this->efq_identity, &result));
  EXPECT_TRUE(result);
  EXPECT_EQ(kEpidNoErr, EcIsIdentity(this->efq2, this->efq2_identity, &result));
  EXPECT_TRUE(result);
}
TEST_F(EcGroupTest, IsIdentityDetectsNonIdentityElement) {
  bool result;
  EXPECT_EQ(kEpidNoErr, EcIsIdentity(this->efq, this->efq_a, &result));
  EXPECT_FALSE(result);
  EXPECT_EQ(kEpidNoErr, EcIsIdentity(this->efq2, this->efq2_a, &result));
  EXPECT_FALSE(result);
}
}  // namespace
