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
* \brief Epid11AreSigsLinked implementation.
*/

#include <string.h>
#include "epid/verifier/1.1/api.h"

bool Epid11AreSigsLinked(Epid11BasicSignature const* sig1,
                         Epid11BasicSignature const* sig2) {
  return (sig1 && sig2 && 0 == memcmp(&sig1->B, &sig2->B, sizeof(sig2->B)) &&
          0 == memcmp(&sig1->K, &sig2->K, sizeof(sig2->K)));
}
