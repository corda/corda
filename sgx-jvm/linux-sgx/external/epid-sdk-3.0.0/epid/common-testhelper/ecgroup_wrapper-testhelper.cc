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
 * \brief EcGroup C++ wrapper implementation.
 */
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common/math/bignum.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/bignum_wrapper-testhelper.h"

/// ecgroup deleter type
struct EcGroupDeleter {
  /// ecgroup deleter
  void operator()(EcGroup* ptr) {
    if (ptr) {
      DeleteEcGroup(&ptr);
    }
  }
};

/// ecgroup deleter singlton
EcGroupDeleter ecgroup_deleter;

/// Internal state of the ecgroup wrapper
struct EcGroupObj::State {
  /// The stored EcGroup
  std::shared_ptr<EcGroup> group_;
  FiniteFieldObj fintefield_;

  /// constructor
  State() : group_(nullptr, ecgroup_deleter) {}

  // State instances are not meant to be copied.
  // Explicitly delete copy constructor and assignment operator.
  State(const State&) = delete;
  State& operator=(const State&) = delete;

  /// destructor
  ~State() {}
};

EcGroupObj::EcGroupObj() : state_(new State()) {
  const BigNumStr q_str = {
      {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
        0xEE, 0x71, 0xA4, 0x9F, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x98, 0x0A, 0x82,
        0xD3, 0x29, 0x2D, 0xDB, 0xAE, 0xD3, 0x30, 0x13}}};
  const FqElemStr b_str = {
      {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03}}};
  const BigNumStr p_str = {
      {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
        0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99, 0x92, 0x1A,
        0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D}}};
  const BigNumStr h1 = {
      {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}};
  const G1ElemStr g1_str = {
      {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}},
      {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02}}}};
  FiniteFieldObj fq(q_str);
  EcGroup* temp = nullptr;
  NewEcGroup(fq, FfElementObj(&fq), FfElementObj(&fq, b_str),
             FfElementObj(&fq, g1_str.x), FfElementObj(&fq, g1_str.y),
             BigNumObj(p_str), BigNumObj(h1), &temp);
  state_->group_.reset(temp, ecgroup_deleter);
  state_->fintefield_ = fq;
}

EcGroupObj::EcGroupObj(EcGroupObj const& other) : state_(new State) {
  state_->group_ = other.state_->group_;
  state_->fintefield_ = other.state_->fintefield_;
}

EcGroupObj& EcGroupObj::operator=(EcGroupObj const& other) {
  state_->group_ = other.state_->group_;
  state_->fintefield_ = other.state_->fintefield_;
  return *this;
}

EcGroupObj::EcGroupObj(FiniteFieldObj* ff, FfElement const* a,
                       FfElement const* b, FfElement const* x,
                       FfElement const* y, BigNum const* order,
                       BigNum const* cofactor)
    : state_(new State) {
  EcGroup* temp = nullptr;
  NewEcGroup(*ff, a, b, x, y, order, cofactor, &temp);
  state_->group_.reset(temp, ecgroup_deleter);
  state_->fintefield_ = *ff;
}

EcGroupObj::~EcGroupObj() {}

EcGroupObj::operator EcGroup*() { return state_->group_.get(); }

EcGroupObj::operator const EcGroup*() const { return state_->group_.get(); }

EcGroup* EcGroupObj::get() { return state_->group_.get(); }

EcGroup const* EcGroupObj::getc() const { return state_->group_.get(); }

size_t EcGroupObj::GetElementMaxSize() const {
  return 2 * state_->fintefield_.GetElementMaxSize();
}
