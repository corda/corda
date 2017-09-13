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
 * \brief AreSigsLinked implementation.
 */

#include <epid/verifier/api.h>

#include <string.h>

// implements section 4.3 "Signature Linking" from Intel(R) EPID 2.0 Spec
bool EpidAreSigsLinked(BasicSignature const* sig1, BasicSignature const* sig2) {
  // Step 1. If B1 = B2 and K1 = K2, output true, otherwise output false.
  return (sig1 && sig2 && 0 == memcmp(&sig1->B, &sig2->B, sizeof(sig2->B)) &&
          0 == memcmp(&sig1->K, &sig2->K, sizeof(sig2->K)));
}
