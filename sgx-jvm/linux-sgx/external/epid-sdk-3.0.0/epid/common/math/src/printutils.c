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
 * \brief Print helper implementation.
 */
#ifndef EPID_ENABLE_DEBUG_PRINT
#define EPID_ENABLE_DEBUG_PRINT
#endif

#include "epid/common/math/printutils.h"

#include <stdio.h>
#include <string.h>

#include "ext/ipp/include/ippcp.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/math/src/ecgroup-internal.h"
#include "epid/common/math/src/finitefield-internal.h"
#include "epid/common/src/memory.h"

/// Allowed number of characters printed in one line
#define WIDTH 49

/// Amount of identation added in the beginning of each line
#define INDENT 2

/// Number of charaters used to represent one byte. For example, "ab" or "05".
#define BYTE_LENGTH 2

/// Separator
#define SEPARATOR (" ")

/// Make configured number of identation
#define MAKE_INDENT()                    \
  {                                      \
    uint8_t ind = 0;                     \
    for (ind = 0; ind < INDENT; ind++) { \
      PRINT(" ");                        \
    }                                    \
  }

/// Print to specified stream
#define PRINT(...) fprintf(stdout, __VA_ARGS__)

static int PrintBuf(void const* buf, size_t size) {
  size_t curr_column = 0;
  size_t i = 0;
  if (!buf || size == 0) {
    return -1;
  }
  for (i = 0; i < size; i++) {
    if (curr_column == 0) {
      MAKE_INDENT();
      curr_column += INDENT;
    }
    if (BYTE_LENGTH != PRINT("%.2x", ((unsigned char const*)buf)[i])) {
      return -1;
    }
    curr_column += BYTE_LENGTH;
    if (i < size - 1) {
      if ((curr_column + BYTE_LENGTH + strlen(SEPARATOR)) > WIDTH) {
        PRINT("\n");
        curr_column = 0;
      } else {
        PRINT("%s", SEPARATOR);
        curr_column += (uint8_t)strlen(SEPARATOR);
      }
    }
  }
  PRINT("\n");
  return 0;
}

void PrintBigNum(BigNum const* big_num, char const* var_name) {
  IppStatus sts = ippStsNoErr;
  unsigned char* buf = NULL;
  int ipp_word_buf_size;
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (BigNum):\n", var_name);
  if (!big_num) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (!big_num->ipp_bn) {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
  sts = ippsGetSize_BN(big_num->ipp_bn, &ipp_word_buf_size);
  if (ippStsNoErr != sts) {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
  do {
    buf = SAFE_ALLOC(ipp_word_buf_size * sizeof(Ipp32u));
    if (!buf) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }
    sts = ippsGetOctString_BN((Ipp8u*)buf, ipp_word_buf_size * sizeof(Ipp32u),
                              big_num->ipp_bn);
    if (ippStsNoErr != sts) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }
    if (0 != PrintBuf((const void*)buf, ipp_word_buf_size * sizeof(Ipp32u))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }
  } while (0);

  SAFE_FREE(buf);
}

void PrintFfElement(FiniteField const* ff, FfElement const* ff_element,
                    char const* var_name, PrintUtilFormat format) {
  IppStatus sts;
  uint8_t ff_element_str[sizeof(Fq12ElemStr)];
  int ipp_ff_element_size;
  if (!var_name) {
    var_name = "<no name>";
  }
  if (!ff_element || !ff) {
    PRINT("%s (FfElement):\n", var_name);
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (!ff_element->ipp_ff_elem || !ff->ipp_ff ||
      (format != kPrintUtilUnannotated && format != kPrintUtilAnnotated)) {
    PRINT("%s (FfElement):\n", var_name);
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }

  // get the data
  ipp_ff_element_size = ff_element->info.elementLen * sizeof(Ipp32u);
  sts = ippsGFpGetElementOctString(ff_element->ipp_ff_elem,
                                   (Ipp8u*)&ff_element_str, ipp_ff_element_size,
                                   ff->ipp_ff);
  if (ippStsNoErr != sts) {
    PRINT("%s (FfElement):\n", var_name);
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }

  if (ipp_ff_element_size == sizeof(FqElemStr)) {
    PrintFqElemStr((const FqElemStr*)&ff_element_str, var_name);
  } else if (ipp_ff_element_size == sizeof(FpElemStr)) {
    PrintFpElemStr((const FpElemStr*)&ff_element_str, var_name);
  } else if (ipp_ff_element_size == sizeof(Fq2ElemStr)) {
    PrintFq2ElemStr((const Fq2ElemStr*)&ff_element_str, var_name, format);
  } else if (ipp_ff_element_size == sizeof(Fq6ElemStr)) {
    PrintFq6ElemStr((const Fq6ElemStr*)&ff_element_str, var_name, format);
  } else if (ipp_ff_element_size == sizeof(Fq6ElemStr)) {
    PrintFq12ElemStr((const Fq12ElemStr*)&ff_element_str, var_name, format);
  } else if (ipp_ff_element_size == sizeof(GtElemStr)) {
    PrintGtElemStr((const GtElemStr*)&ff_element_str, var_name, format);
  } else {
    PRINT("%s (FfElement):\n", var_name);
    MAKE_INDENT();
    PRINT("<invalid>\n");
  }
}

void PrintEcPoint(EcGroup const* g, EcPoint const* ec_point,
                  char const* var_name, PrintUtilFormat format) {
  FiniteField fp;
  FfElement* fp_x = NULL;
  FfElement* fp_y = NULL;
  uint8_t ec_point_str[sizeof(G2ElemStr)];
  if (!var_name) {
    var_name = "<no name>";
  }
  if (!ec_point || !g) {
    PRINT("%s (EcPoint):\n", var_name);
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (!ec_point->ipp_ec_pt || !g->ipp_ec) {
    PRINT("%s (EcPoint):\n", var_name);
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
  do {
    IppStatus sts = ippStsNoErr;
    int ipp_half_strlen;
    // get finite field
    sts = ippsGFpECGet(g->ipp_ec, (const IppsGFpState**)&(fp.ipp_ff), 0, 0, 0,
                       0, 0, 0, 0, 0);
    if (ippStsNoErr != sts) {
      PRINT("%s (EcPoint):\n", var_name);
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }

    // create element X
    if (kEpidNoErr != NewFfElement(&fp, &fp_x)) {
      PRINT("%s (EcPoint):\n", var_name);
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }
    // create element Y
    if (kEpidNoErr != NewFfElement(&fp, &fp_y)) {
      PRINT("%s (EcPoint):\n", var_name);
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }

    ipp_half_strlen = fp_x->info.elementLen * sizeof(Ipp32u);

    // get elements from point
    sts = ippsGFpECGetPoint(ec_point->ipp_ec_pt, fp_x->ipp_ff_elem,
                            fp_y->ipp_ff_elem, g->ipp_ec);
    // check return codes
    if (ippStsNoErr != sts) {
      PRINT("%s (EcPoint):\n", var_name);
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }

    // get element X data
    sts = ippsGFpGetElementOctString(fp_x->ipp_ff_elem, (Ipp8u*)&ec_point_str,
                                     ipp_half_strlen, fp.ipp_ff);
    // check return codes
    if (ippStsNoErr != sts) {
      PRINT("%s (EcPoint):\n", var_name);
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }
    // get element Y data
    sts = ippsGFpGetElementOctString(fp_y->ipp_ff_elem,
                                     (Ipp8u*)&ec_point_str + ipp_half_strlen,
                                     ipp_half_strlen, fp.ipp_ff);
    // check return codes
    if (ippStsNoErr != sts) {
      PRINT("%s (EcPoint):\n", var_name);
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }

    if (2 * ipp_half_strlen == sizeof(G1ElemStr)) {
      PrintG1ElemStr((const G1ElemStr*)&ec_point_str, var_name, format);
    } else if (2 * ipp_half_strlen == sizeof(G2ElemStr)) {
      PrintG2ElemStr((const G2ElemStr*)&ec_point_str, var_name, format);
    } else {
      PRINT("%s (EcPoint):\n", var_name);
      MAKE_INDENT();
      PRINT("<invalid>\n");
      break;
    }
  } while (0);

  DeleteFfElement(&fp_x);
  DeleteFfElement(&fp_y);
}

void PrintBigNumStr(BigNumStr const* big_num_str, char const* var_name) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (BigNumStr):\n", var_name);
  if (!big_num_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (0 != PrintBuf((const void*)big_num_str, sizeof(*big_num_str))) {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintFpElemStr(FpElemStr const* fp_elem_str, char const* var_name) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (FpElemStr):\n", var_name);
  if (!fp_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (0 != PrintBuf((const void*)fp_elem_str, sizeof(*fp_elem_str))) {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintFqElemStr(FqElemStr const* fq_elem_str, char const* var_name) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (FqElemStr):\n", var_name);
  if (!fq_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (0 != PrintBuf((const void*)fq_elem_str, sizeof(*fq_elem_str))) {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintFq2ElemStr(Fq2ElemStr const* fq2_elem_str, char const* var_name,
                     PrintUtilFormat format) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (Fq2ElemStr):\n", var_name);
  if (!fq2_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (format == kPrintUtilAnnotated) {
    MAKE_INDENT();
    PRINT("a0:\n");
    if (0 != PrintBuf((const void*)&fq2_elem_str->a[0],
                      sizeof(fq2_elem_str->a[0]))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
    MAKE_INDENT();
    PRINT("a1:\n");
    if (0 != PrintBuf((const void*)&fq2_elem_str->a[1],
                      sizeof(fq2_elem_str->a[1]))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else if (format == kPrintUtilUnannotated) {
    if (0 != PrintBuf((const void*)fq2_elem_str, sizeof(*fq2_elem_str))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintFq6ElemStr(Fq6ElemStr const* fq6_elem_str, char const* var_name,
                     PrintUtilFormat format) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (Fq6ElemStr):\n", var_name);
  if (!fq6_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (format == kPrintUtilAnnotated) {
    unsigned int i = 0;
    unsigned int j = 0;
    for (i = 0; i < sizeof(fq6_elem_str->a) / sizeof(fq6_elem_str->a[0]); i++) {
      for (j = 0;
           j < sizeof(fq6_elem_str->a[0]) / sizeof(fq6_elem_str->a[0].a[0]);
           j++) {
        MAKE_INDENT();
        PRINT("a%u.%u:\n", i, j);
        if (0 != PrintBuf((const void*)&fq6_elem_str->a[i].a[j],
                          sizeof(fq6_elem_str->a[i].a[j]))) {
          MAKE_INDENT();
          PRINT("<invalid>\n");
          return;
        }
      }
    }
  } else if (format == kPrintUtilUnannotated) {
    if (0 != PrintBuf((const void*)fq6_elem_str, sizeof(*fq6_elem_str))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintFq12ElemStr(Fq12ElemStr const* fq12_elem_str, char const* var_name,
                      PrintUtilFormat format) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (Fq12ElemStr):\n", var_name);
  if (!fq12_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (format == kPrintUtilAnnotated) {
    unsigned int i = 0;
    unsigned int j = 0;
    unsigned int k = 0;
    for (i = 0; i < sizeof(fq12_elem_str->a) / sizeof(fq12_elem_str->a[0]);
         i++) {
      for (j = 0;
           j < sizeof(fq12_elem_str->a[0]) / sizeof(fq12_elem_str->a[0].a[0]);
           j++) {
        for (k = 0; k < sizeof(fq12_elem_str->a[0].a[0]) /
                            sizeof(fq12_elem_str->a[0].a[0].a[0]);
             k++) {
          MAKE_INDENT();
          PRINT("a%u.%u.%u:\n", i, j, k);
          if (0 != PrintBuf((const void*)&fq12_elem_str->a[i].a[j].a[k],
                            sizeof(fq12_elem_str->a[i].a[j].a[k]))) {
            MAKE_INDENT();
            PRINT("<invalid>\n");
            return;
          }
        }
      }
    }
  } else if (format == kPrintUtilUnannotated) {
    if (0 != PrintBuf((const void*)fq12_elem_str, sizeof(*fq12_elem_str))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintG1ElemStr(G1ElemStr const* g1_elem_str, char const* var_name,
                    PrintUtilFormat format) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (G1ElemStr):\n", var_name);
  if (!g1_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (format == kPrintUtilAnnotated) {
    MAKE_INDENT();
    PRINT("x:\n");
    if (0 != PrintBuf((const void*)&g1_elem_str->x, sizeof(g1_elem_str->x))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
    MAKE_INDENT();
    PRINT("y:\n");
    if (0 != PrintBuf((const void*)&g1_elem_str->y, sizeof(g1_elem_str->y))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else if (format == kPrintUtilUnannotated) {
    if (0 != PrintBuf((const void*)g1_elem_str, sizeof(*g1_elem_str))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintG2ElemStr(G2ElemStr const* g2_elem_str, char const* var_name,
                    PrintUtilFormat format) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (G2ElemStr):\n", var_name);
  if (!g2_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (format == kPrintUtilAnnotated) {
    MAKE_INDENT();
    PRINT("x0:\n");
    if (0 !=
        PrintBuf((const void*)&g2_elem_str->x[0], sizeof(g2_elem_str->x[0]))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
    MAKE_INDENT();
    PRINT("x1:\n");
    if (0 !=
        PrintBuf((const void*)&g2_elem_str->x[1], sizeof(g2_elem_str->x[1]))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
    MAKE_INDENT();
    PRINT("y0:\n");
    if (0 !=
        PrintBuf((const void*)&g2_elem_str->y[0], sizeof(g2_elem_str->y[0]))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
    MAKE_INDENT();
    PRINT("y1:\n");
    if (0 !=
        PrintBuf((const void*)&g2_elem_str->y[1], sizeof(g2_elem_str->y[1]))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else if (format == kPrintUtilUnannotated) {
    if (0 != PrintBuf((const void*)g2_elem_str, sizeof(*g2_elem_str))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

void PrintGtElemStr(GtElemStr const* gt_elem_str, char const* var_name,
                    PrintUtilFormat format) {
  if (!var_name) {
    var_name = "<no name>";
  }
  PRINT("%s (GtElemStr):\n", var_name);
  if (!gt_elem_str) {
    MAKE_INDENT();
    PRINT("<null>\n");
    return;
  }
  if (format == kPrintUtilAnnotated) {
    unsigned int i = 0;
    for (i = 0; i < sizeof(gt_elem_str->x) / sizeof(gt_elem_str->x[0]); i++) {
      MAKE_INDENT();
      PRINT("x%u:\n", i);
      if (0 != PrintBuf((const void*)&gt_elem_str->x[i],
                        sizeof(gt_elem_str->x[i]))) {
        MAKE_INDENT();
        PRINT("<invalid>\n");
        return;
      }
    }
  } else if (format == kPrintUtilUnannotated) {
    if (0 != PrintBuf((const void*)gt_elem_str, sizeof(*gt_elem_str))) {
      MAKE_INDENT();
      PRINT("<invalid>\n");
      return;
    }
  } else {
    MAKE_INDENT();
    PRINT("<invalid>\n");
    return;
  }
}

#ifdef EPID_ENABLE_DEBUG_PRINT
#undef EPID_ENABLE_DEBUG_PRINT
#endif
