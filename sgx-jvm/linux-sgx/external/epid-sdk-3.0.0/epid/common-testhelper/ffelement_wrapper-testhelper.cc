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
 * \brief FfElement C++ wrapper implementation.
 */
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common/math/bignum.h"

/// ffelement deleter type
struct FfElementDeleter {
  /// ffelement deleter
  void operator()(FfElement* ffe) {
    if (ffe) {
      DeleteFfElement(&ffe);
    }
  }
};

/// ffelement deleter singlton
FfElementDeleter ff_element_deleter;

/// Internal state of the ffelement wrapper
struct FfElementObj::State {
  /// The containing field
  FiniteFieldObj ff_;
  /// size of the element data
  size_t size;
  /// The stored FfElement
  std::shared_ptr<FfElement> ffe_;

  State() : ff_(), size(0), ffe_() {}
  /// write a new value
  void write(FiniteFieldObj* ff, unsigned char const* buf, size_t buflen) {
    ff_ = *ff;
    bool orig_has_data = (buf != nullptr) && (buflen > 0);
    std::shared_ptr<FfElement> ffe;
    FfElement* ffe_ptr;
    THROW_ON_EPIDERR(NewFfElement(ff_, &ffe_ptr));
    ffe.reset(ffe_ptr, ff_element_deleter);
    size = buflen;
    if (orig_has_data) {
      THROW_ON_EPIDERR(ReadFfElement(ff_, buf, buflen, ffe.get()));
    }
    ffe_ = ffe;
  }
};

FfElementObj::FfElementObj() : state_(new State) {}

FfElementObj::FfElementObj(FfElementObj const& other) : state_(new State) {
  std::vector<unsigned char> buf = other.data();
  state_->write(&other.state_->ff_, &buf[0], buf.size());
}

FfElementObj& FfElementObj::operator=(FfElementObj const& other) {
  std::vector<unsigned char> buf = other.data();
  state_->write(&other.state_->ff_, &buf[0], buf.size());
  return *this;
}

FfElementObj::FfElementObj(FiniteFieldObj* ff) : state_(new State) {
  state_->write(ff, nullptr, 0);
}

FfElementObj::FfElementObj(FiniteFieldObj* ff, FpElemStr const& bytes)
    : state_(new State) {
  init(ff, (unsigned char*)&bytes, sizeof(bytes));
}

FfElementObj::FfElementObj(FiniteFieldObj* ff, FqElemStr const& bytes)
    : state_(new State) {
  init(ff, (unsigned char*)&bytes, sizeof(bytes));
}

FfElementObj::FfElementObj(FiniteFieldObj* ff, Fq2ElemStr const& bytes)
    : state_(new State) {
  init(ff, (unsigned char*)&bytes, sizeof(bytes));
}

FfElementObj::FfElementObj(FiniteFieldObj* ff, Fq3ElemStr const& bytes)
    : state_(new State) {
  init(ff, (unsigned char*)&bytes, sizeof(bytes));
}

FfElementObj::FfElementObj(FiniteFieldObj* ff, Fq6ElemStr const& bytes)
    : state_(new State) {
  init(ff, (unsigned char*)&bytes, sizeof(bytes));
}

FfElementObj::FfElementObj(FiniteFieldObj* ff, Fq12ElemStr const& bytes)
    : state_(new State) {
  init(ff, (unsigned char*)&bytes, sizeof(bytes));
}

FfElementObj::FfElementObj(FiniteFieldObj* ff,
                           std::vector<unsigned char> const& bytes)
    : state_(new State) {
  init(ff, &bytes[0], bytes.size());
}

FfElementObj::FfElementObj(FiniteFieldObj* ff, void const* bytes, size_t size)
    : state_(new State) {
  init(ff, (unsigned char const*)bytes, size);
}

void FfElementObj::init(FiniteFieldObj* ff, unsigned char const* bytes,
                        size_t size) {
  state_->write(ff, bytes, size);
}

FfElementObj::~FfElementObj() {}

FfElementObj::operator FfElement*() { return state_->ffe_.get(); }

FfElementObj::operator const FfElement*() const { return state_->ffe_.get(); }

FfElement* FfElementObj::get() { return state_->ffe_.get(); }

FfElement const* FfElementObj::getc() const { return state_->ffe_.get(); }

std::vector<unsigned char> FfElementObj::data() const {
  std::vector<unsigned char> buf;
  if (state_->ffe_.get() != nullptr) {
    buf.resize(state_->ff_.GetElementMaxSize());
    THROW_ON_EPIDERR(
        WriteFfElement(state_->ff_, state_->ffe_.get(), &buf[0], buf.size()));
  }
  return buf;
}
