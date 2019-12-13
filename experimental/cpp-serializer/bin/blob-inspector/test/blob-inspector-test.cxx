#include <gtest/gtest.h>
#include "CordaBytes.h"
#include "BlobInspector.h"

const std::string filepath ("../../test-files/"); // NOLINT

/******************************************************************************
 *
 * mapType Tests
 *
 ******************************************************************************/

void
test (const std::string & file_, const std::string & result_) {
    auto path { filepath + file_ } ;
    CordaBytes cb (path);
    auto val = BlobInspector (cb).dump();
    ASSERT_EQ(result_, val);
}

/******************************************************************************/

/**
 * int
 */
TEST (BlobInspector, _i_) { // NOLINT
    test ("_i_", "{ Parsed : { a : 69 } }");
}

/******************************************************************************/

/**
 * long
 */
TEST (BlobInspector, _l_) { // NOLINT
    test ("_l_", "{ Parsed : { x : 100000000000 } }");
}

/******************************************************************************/

/**
 * int
 */
TEST (BlobInspector, _Oi_) { // NOLINT
    test ("_Oi_", "{ Parsed : { a : 1 } }");
}

/******************************************************************************/

/**
 * int
 */
TEST (BlobInspector, _Ai_) { // NOLINT
    test ("_Ai_", "{ Parsed : { z : [ 1, 2, 3, 4, 5, 6 ] } }");
}

/******************************************************************************/

/**
 * List of ints
 */
TEST (BlobInspector, _Li_) { // NOLINT
    test ("_Li_", "{ Parsed : { a : [ 1, 2, 3, 4, 5, 6 ] } }");
}

/******************************************************************************/

/**
 * List of a class with a single int property
 */
TEST (BlobInspector, _L_i__) { // NOLINT
    test (
        "_L_i__",
        "{ Parsed : { listy : [ { a : 1 }, { a : 2 }, { a : 3 } ] } }");
}

/******************************************************************************/

TEST (BlobInspector, _Le_) { // NOLINT
    test ("_Le_", "{ Parsed : { listy : [ A, B, C ] } }");
}

/******************************************************************************/

TEST (BlobInspector,_Le_2) { // NOLINT
    EXPECT_THROW (
        {
            test ("_Le_2", "");
        },
        std::runtime_error);
}

/******************************************************************************/

/**
 * A map of ints to strings
 */
TEST (BlobInspector, _Mis_) { // NOLINT
    test ("_Mis_",
        R"({ Parsed : { a : { 1 : "two", 3 : "four", 5 : "six" } } })");
}

/******************************************************************************/

/**
 * A map of ints to lists of Strings
 */
TEST (BlobInspector, _MiLs_) { // NOLINT
    test ("_MiLs_",
        R"({ Parsed : { a : { 1 : [ "two", "three", "four" ], 5 : [ "six" ], 7 : [  ] } } })");
}

/******************************************************************************/

/**
 * a map of ints to a composite with a n int and string property
 */
TEST (BlobInspector, _Mi_is__) { // NOLINT
    test ("_Mi_is__",
        R"({ Parsed : { a : { 1 : { a : 2, b : "three" }, 4 : { a : 5, b : "six" }, 7 : { a : 8, b : "nine" } } } })");
}

/******************************************************************************/

TEST (BlobInspector,_Pls_) { // NOLINT
    test ("_Pls_",
            R"({ Parsed : { a : { first : 1, second : "two" } } })");
}

/******************************************************************************/

TEST (BlobInspector, _e_) { // NOLINT
    test ("_e_", "{ Parsed : { e : A } }");
}

/******************************************************************************/

TEST (BlobInspector, _i_is__) { // NOLINT
    test ("_i_is__",
            R"({ Parsed : { a : 1, b : { a : 2, b : "three" } } })");
}

/******************************************************************************/

// Array of unboxed integers
TEST (BlobInspector, _Ci_) { // NOLINT
    test ("_Ci_",
        R"({ Parsed : { z : [ 1, 2, 3 ] } })");
}

/******************************************************************************/

/**
 * Composite with
 *   * one int property
 *   * one long property
 *   * one list property that is a list of Maps of int to strings
 */
TEST (BlobInspector, __i_LMis_l__) { // NOLINT
    test ("__i_LMis_l__",
        R"({ Parsed : { x : [ { 1 : "two", 3 : "four", 5 : "six" }, { 7 : "eight", 9 : "ten" } ], y : { x : 1000000 }, z : { a : 666 } } })");
}

/******************************************************************************/

TEST (BlobInspector, _ALd_) { // NOLINT
    test ("_ALd_",
            R"({ Parsed : { a : [ [ 10.100000, 11.200000, 12.300000 ], [  ], [ 13.400000 ] ] } })");
}

/******************************************************************************/
