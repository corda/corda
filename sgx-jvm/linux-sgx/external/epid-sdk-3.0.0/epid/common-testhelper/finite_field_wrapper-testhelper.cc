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
 * \brief FiniteField C++ wrapper implementation.
 */
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common/math/bignum.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"

/// finite field deleter type
struct FiniteFieldDeleter {
  /// finite field deleter
  void operator()(FiniteField* ff) {
    if (ff) {
      DeleteFiniteField(&ff);
    }
  }
};

/// finite field deleter singlton
FiniteFieldDeleter finite_field_deleter;

/// Internal state of the finite field wrapper
struct FiniteFieldObj::State {
  /// Inner state of complex fields
  struct InnerState {
    /// The ground field
    FiniteFieldObj gf_;
  };
  /// Inner state
  /*!
  We store a pointer to InnerState so simple fields
  that are not composed from other fields do not result
  in an infinite series of fields.

  Instead simple fields have a NULL inner_state and
  complex fields have it set.
  */
  InnerState* inner_state;

  /// The stored FiniteField
  std::shared_ptr<FiniteField> ff_;

  ///  Maximum size of field element
  size_t size_;

  /// constructor
  State() : ff_(nullptr, finite_field_deleter), size_(0) {
    inner_state = nullptr;
  }

  // State instances are not meant to be copied.
  // Explicitly delete copy constructor and assignment operator.
  State(const State&) = delete;
  State& operator=(const State&) = delete;

  /// destructor
  ~State() {
    if (inner_state) {
      delete inner_state;
      inner_state = nullptr;
    }
  }

  /// setter for inner_state
  void SetInnerState(FiniteFieldObj const& gf) {
    if (!inner_state) {
      inner_state = new InnerState;
      inner_state->gf_ = gf;
    }
  }

  /// setter for inner_state
  void SetInnerState(InnerState* state) {
    if (state) {
      if (!inner_state) {
        inner_state = new InnerState;
      }
      if (!inner_state) {
        inner_state->gf_ = state->gf_;
      }
    } else {
      if (inner_state) {
        delete inner_state;
        inner_state = nullptr;
      }
    }
  }
};

FiniteFieldObj::FiniteFieldObj() : state_(new State()) {
  /*
  to avoid a bug in ipp this is one less than the
  actual max value we could take.
  */
  const BigNumStr max_prime = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                               0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                               0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                               0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFe};
  FiniteField* temp = nullptr;
  NewFiniteField(&max_prime, &temp);
  state_->ff_.reset(temp, finite_field_deleter);
  state_->size_ = sizeof(max_prime);
}

FiniteFieldObj::FiniteFieldObj(FiniteFieldObj const& other)
    : state_(new State) {
  state_->ff_ = other.state_->ff_;
  state_->size_ = other.state_->size_;
  state_->SetInnerState(other.state_->inner_state);
}

FiniteFieldObj& FiniteFieldObj::operator=(FiniteFieldObj const& other) {
  state_->ff_ = other.state_->ff_;
  state_->size_ = other.state_->size_;
  state_->SetInnerState(other.state_->inner_state);
  return *this;
}

FiniteFieldObj::FiniteFieldObj(BigNumStr const& prime) : state_(new State) {
  FiniteField* temp = nullptr;
  NewFiniteField(&prime, &temp);
  state_->ff_.reset(temp, finite_field_deleter);
  state_->size_ = sizeof(prime);
}

FiniteFieldObj::FiniteFieldObj(FiniteFieldObj const& ground_field,
                               FfElementObj const& ground_element, int degree)
    : state_(new State) {
  FiniteField* temp = nullptr;
  state_->SetInnerState(ground_field);
  NewFiniteFieldViaBinomalExtension(ground_field, ground_element, degree,
                                    &temp);
  state_->ff_.reset(temp, finite_field_deleter);
  state_->size_ = ground_field.GetElementMaxSize() * degree;
}

FiniteFieldObj::FiniteFieldObj(FiniteFieldObj const& ground_field,
                               BigNumStr const* irr_polynomial, int degree)
    : state_(new State) {
  FiniteField* temp = nullptr;
  state_->SetInnerState(ground_field);
  NewFiniteFieldViaPolynomialExtension(ground_field, irr_polynomial, degree,
                                       &temp);
  state_->ff_.reset(temp, finite_field_deleter);
  state_->size_ = ground_field.GetElementMaxSize() * degree;
}

FiniteFieldObj::~FiniteFieldObj() {}

FiniteFieldObj::operator FiniteField*() { return state_->ff_.get(); }

FiniteFieldObj::operator const FiniteField*() const {
  return state_->ff_.get();
}

FiniteField* FiniteFieldObj::get() { return state_->ff_.get(); }

FiniteField const* FiniteFieldObj::getc() const { return state_->ff_.get(); }

size_t FiniteFieldObj::GetElementMaxSize() const { return state_->size_; }
