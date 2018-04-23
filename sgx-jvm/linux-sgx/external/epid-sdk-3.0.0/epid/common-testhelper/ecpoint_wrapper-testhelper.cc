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
 * \brief EcPoint C++ wrapper implementation.
 */
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/ecpoint_wrapper-testhelper.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"
#include "epid/common/math/bignum.h"

/// ecpoint deleter type
struct EcPointDeleter {
  /// ecpoint deleter
  void operator()(EcPoint* ptr) {
    if (ptr) {
      DeleteEcPoint(&ptr);
    }
  }
};

/// ecpoint deleter singlton
EcPointDeleter ecpoint_deleter;

/// Internal state of the ecpoint wrapper
struct EcPointObj::State {
  /// The containing field
  EcGroupObj group_;
  /// The stored EcPoint
  std::shared_ptr<EcPoint> point_;

  State() : group_(), point_() {}
  /// write a new value
  void write(EcGroupObj* group, unsigned char const* buf, size_t buflen) {
    group_ = *group;
    bool orig_has_data = (buf != nullptr) && (buflen > 0);
    std::shared_ptr<EcPoint> point;
    EcPoint* point_ptr;
    THROW_ON_EPIDERR(NewEcPoint(group_, &point_ptr));
    point.reset(point_ptr, ecpoint_deleter);
    if (orig_has_data) {
      THROW_ON_EPIDERR(ReadEcPoint(group_, buf, buflen, point.get()));
    }
    point_ = point;
  }
};

EcPointObj::EcPointObj() : state_(new State) {}

EcPointObj::EcPointObj(EcPointObj const& other) : state_(new State) {
  std::vector<unsigned char> buf = other.data();
  state_->write(&other.state_->group_, &buf[0], buf.size());
}

EcPointObj& EcPointObj::operator=(EcPointObj const& other) {
  std::vector<unsigned char> buf = other.data();
  state_->write(&other.state_->group_, &buf[0], buf.size());
  return *this;
}

EcPointObj::EcPointObj(EcGroupObj* group) : state_(new State) {
  state_->write(group, nullptr, 0);
}

EcPointObj::EcPointObj(EcGroupObj* group, G1ElemStr const& bytes)
    : state_(new State) {
  init(group, (unsigned char*)&bytes, sizeof(bytes));
}

EcPointObj::EcPointObj(EcGroupObj* group, G2ElemStr const& bytes)
    : state_(new State) {
  init(group, (unsigned char*)&bytes, sizeof(bytes));
}

EcPointObj::EcPointObj(EcGroupObj* group, Epid11G2ElemStr const& bytes)
    : state_(new State) {
  init(group, (unsigned char*)&bytes, sizeof(bytes));
}

EcPointObj::EcPointObj(EcGroupObj* group,
                       std::vector<unsigned char> const& bytes)
    : state_(new State) {
  init(group, &bytes[0], bytes.size());
}

EcPointObj::EcPointObj(EcGroupObj* group, void const* bytes, size_t size)
    : state_(new State) {
  init(group, (unsigned char const*)bytes, size);
}

void EcPointObj::init(EcGroupObj* group, unsigned char const* bytes,
                      size_t size) {
  state_->write(group, bytes, size);
}

EcPointObj::~EcPointObj() {}

EcPointObj::operator EcPoint*() { return state_->point_.get(); }

EcPointObj::operator const EcPoint*() const { return state_->point_.get(); }

EcPoint* EcPointObj::get() { return state_->point_.get(); }

EcPoint const* EcPointObj::getc() const { return state_->point_.get(); }

std::vector<unsigned char> EcPointObj::data() const {
  std::vector<unsigned char> buf;
  if (state_->point_.get() != nullptr) {
    buf.resize(state_->group_.GetElementMaxSize());
    THROW_ON_EPIDERR(WriteEcPoint(state_->group_, state_->point_.get(), &buf[0],
                                  buf.size()));
  }
  return buf;
}
