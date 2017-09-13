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
 *
 * \brief This file does renames to allow using of the lib with
 *        commercial merged ippcp
 */


#ifndef IPPEPID_Y8_H_
#define IPPEPID_Y8_H_

# define cpDiv_BNU32 y8_##cpDiv_BNU32
# define cpLSR_BNU y8_##cpLSR_BNU
# define cpNLZ_BNU y8_##cpNLZ_BNU
# define cpNLZ_BNU32 y8_##cpNLZ_BNU32
# define cpToOctStr_BNU y8_##cpToOctStr_BNU
# define cpFromOctStr_BNU y8_##cpFromOctStr_BNU
# define cpMulAdc_BNU_school y8_##cpMulAdc_BNU_school
# define cpMontRedAdc_BNU y8_##cpMontRedAdc_BNU
# define cpSqrAdc_BNU_school y8_##cpSqrAdc_BNU_school
# define cpModInv_BNU y8_##cpModInv_BNU
# define cpAdd_BNU y8_##cpAdd_BNU
# define cpSub_BNU y8_##cpSub_BNU
# define cpDec_BNU32 y8_##cpDec_BNU32
# define cpNTZ_BNU y8_##cpNTZ_BNU
# define cpMontExpBin_BNU y8_##cpMontExpBin_BNU

#endif  // IPPEPID_Y8_H_
