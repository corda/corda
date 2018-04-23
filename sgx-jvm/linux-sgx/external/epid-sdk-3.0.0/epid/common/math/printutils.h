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
 * \brief Print helper interface.
 */
#ifndef EPID_COMMON_MATH_PRINTUTILS_H_
#define EPID_COMMON_MATH_PRINTUTILS_H_

#include "epid/common/types.h"
#include "epid/common/math/bignum.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/ecgroup.h"

/// Debug print routines
/*!
  \defgroup EpidPrint print_utils
  Defines an API to print formatted versions of the types used for
  mathematical operations.

  If the symbol EPID_ENABLE_DEBUG_PRINT is not defined, all calls to the
  functions in this module are ignored.

  \ingroup EpidCommon
  @{
*/

/// Print format
typedef enum {
  kPrintUtilUnannotated = 0,  //!< Unannotated output format
  kPrintUtilAnnotated = 1,    //!< Annotated output format
  kPrintUtilFormatCount = 2,  //!< Count of print formats.
} PrintUtilFormat;

#if !defined(EPID_ENABLE_DEBUG_PRINT)

/// Do not print bignum if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintBigNum(...)

/// Do not print ff element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintFfElement(...)

/// Do not print ec point if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintEcPoint(...)

/// Do not print serialized bignum if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintBigNumStr(...)

/// Do not print Fp element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintFpElemStr(...)

/// Do not print Fq element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintFqElemStr(...)

/// Do not print Fq2 element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintFq2ElemStr(...)

/// Do not print Fq6 element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintFq6ElemStr(...)

/// Do not print Fq12 element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintFq12ElemStr(...)

/// Do not print G1 element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintG1ElemStr(...)

/// Do not print G2 element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintG2ElemStr(...)

/// Do not print Gt element if EPID_ENABLE_DEBUG_PRINT is undefined
#define PrintGtElemStr(...)

#else

/// Prints BigNum
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] big_num
  BigNum to be printed
  \param[in] var_name
  Result variable name

*/
void PrintBigNum(BigNum const* big_num, char const* var_name);

/// Prints finite field element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] ff
  Finite field that element to be printed belongs to
  \param[in] ff_element
  Finite field element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintFfElement(FiniteField const* ff, FfElement const* ff_element,
                    char const* var_name, PrintUtilFormat format);

/// Prints elliptic curve group element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] g
  Elliptic curve group that element to be printed belongs to
  \param[in] ec_point
  Elliptic curve group element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintEcPoint(EcGroup const* g, EcPoint const* ec_point,
                  char const* var_name, PrintUtilFormat format);

/// Prints serialized BigNum
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] big_num_str
  Serialized BigNum to be printed
  \param[in] var_name
  Result variable name

*/
void PrintBigNumStr(BigNumStr const* big_num_str, char const* var_name);

/// Prints serialized Fp element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] fp_elem_str
  Serialized Fp element to be printed
  \param[in] var_name
  Result variable name

*/
void PrintFpElemStr(FpElemStr const* fp_elem_str, char const* var_name);

/// Prints serialized Fq element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] fq_elem_str
  Serialized Fq element to be printed
  \param[in] var_name
  Result variable name

*/
void PrintFqElemStr(FqElemStr const* fq_elem_str, char const* var_name);

/// Prints serialized Fq2 element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] fq2_elem_str
  Serialized Fq2 element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintFq2ElemStr(Fq2ElemStr const* fq2_elem_str, char const* var_name,
                     PrintUtilFormat format);

/// Prints serialized Fq6 element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] fq6_elem_str
  Serialized Fq6 element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintFq6ElemStr(Fq6ElemStr const* fq6_elem_str, char const* var_name,
                     PrintUtilFormat format);

/// Prints serialized Fq12 element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] fq12_elem_str
  Serialized Intel(R) EPID Fq12 element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintFq12ElemStr(Fq12ElemStr const* fq12_elem_str, char const* var_name,
                      PrintUtilFormat format);

/// Prints serialized G1 element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] g1_elem_str
  Serialized G1 element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintG1ElemStr(G1ElemStr const* g1_elem_str, char const* var_name,
                    PrintUtilFormat format);

/// Prints serialized G2 element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] g2_elem_str
  Serialized G2 element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintG2ElemStr(G2ElemStr const* g2_elem_str, char const* var_name,
                    PrintUtilFormat format);

/// Prints serialized Gt element
/*!
  Macro EPID_ENABLE_DEBUG_PRINT needs to be defined
  in order to activate this routine; otherwise,
  it prints nothing.

  \param[in] gt_elem_str
  Serialized G2 element to be printed
  \param[in] var_name
  Result variable name
  \param[in] format
  Output format

*/
void PrintGtElemStr(GtElemStr const* gt_elem_str, char const* var_name,
                    PrintUtilFormat format);

#endif  // !defined( EPID_ENABLE_DEBUG_PRINT )
/*! @} */

#endif  // EPID_COMMON_MATH_PRINTUTILS_H_
