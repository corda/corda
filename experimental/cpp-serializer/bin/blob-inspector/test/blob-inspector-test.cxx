#include <gtest/gtest.h>
#include "CordaBytes.h"
#include "BlobInspector.h"

const std::string filepath ("../../test-files/");

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

TEST (BlobInspector, _Li_) {
    test ("_Li_", "{ Parsed : { a : [ 1, 2, 3, 4, 5, 6 ] } }");
}

/******************************************************************************/

TEST (BlobInspector, _L_i__) {
    test (
        "_L_i__",
        "{ Parsed : { listy : [ { a : 1 }, { a : 1 }, { a : 1 } ] } }");
}

/******************************************************************************/

TEST (BlobInspector, _Le_) {
    test ("_Le_", "{ Parsed : { listy : [ A, B, C ] } }");
}

/******************************************************************************/

TEST (BlobInspector,_Le_2) {
    EXPECT_THROW (
        {
            test ("_Le_2", "");
        },
        std::runtime_error);
}

/******************************************************************************/

TEST (BlobInspector, _Mis_) {
    test ("_Mis_",
        R"({ Parsed : { a : { 1 : "two", 3 : "four", 5 : "six" } } })");
}

/******************************************************************************/

TEST (BlobInspector,_Pls_) {
    test ("_Pls_",
            R"({ Parsed : { a : { first : 1, second : "two" } } })");
}

/******************************************************************************/

TEST (BlobInspector, _e_) {
    test ("_e_", "{ Parsed : { e : A } }");
}

/******************************************************************************/

TEST (BlobInspector, _i_is__) {
    test ("_i_is__",
            R"({ Parsed : { a : 1, b : { a : 2, b : "three" } } })");
}

/******************************************************************************/
