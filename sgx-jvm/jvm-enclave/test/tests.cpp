#define CATCH_CONFIG_MAIN
#include "catch.hpp"

#include "enclave_map.h"

SCENARIO(
    "enclave identifiers can be looked up based on enclaves' measurements",
    "[enclave-map]"
) {

    GIVEN( "an empty map" ) {
        enclave_map_t map;

        REQUIRE( map.size() == 0 );

        WHEN( "a new entry gets added" ) {
            sgx_measurement_t mr_enclave = { 0 };
            sgx_enclave_id_t enclave_id = 1L;
            map[&mr_enclave] = enclave_id;

            THEN( "the size of the map increases" ) {
                REQUIRE( map.size() == 1 );
            }

            THEN( "the mapping can be found" ) {
                auto result = map.find(&mr_enclave);
                REQUIRE( result != map.end() );
            }

            THEN( "the mapping returns the correct value" ) {
                auto result = map.find(&mr_enclave);
                REQUIRE( result->second == 1L );
            }

            THEN( "a non-existent mapping cannot be looked up" ) {
                sgx_measurement_t non_existent_mr_enclave = { 1 };
                auto result = map.find(&non_existent_mr_enclave);
                REQUIRE( result == map.end() );
            }
        }
    }

    GIVEN( "a populated map" ) {
        enclave_map_t map;
        sgx_measurement_t mr_enclave_orig = { 0 };
        sgx_enclave_id_t enclave_id = 1L;
        map[&mr_enclave_orig] = enclave_id;

        REQUIRE( map.size() == 1 );

        WHEN( "a new entry gets added" ) {
            sgx_measurement_t mr_enclave = { 1 };
            sgx_enclave_id_t enclave_id = 2L;
            map[&mr_enclave] = enclave_id;

            THEN( "the size of the map increases" ) {
                REQUIRE( map.size() == 2 );
            }

            THEN( "the mapping can be found" ) {
                auto result = map.find(&mr_enclave);
                REQUIRE( result != map.end() );
            }

            THEN( "the mapping returns the correct value" ) {
                auto result = map.find(&mr_enclave);
                REQUIRE( result->second == 2L );
            }

            THEN( "the value for the pre-existing entry is correct" ) {
                auto result = map.find(&mr_enclave_orig);
                REQUIRE( result->second == 1L );
            }

            THEN( "a non-existent mapping cannot be looked up" ) {
                sgx_measurement_t non_existent_mr_enclave = { 2 };
                auto result = map.find(&non_existent_mr_enclave);
                REQUIRE( result == map.end() );
            }
        }

        WHEN( "a existing entry gets overwritten" ) {
            sgx_measurement_t mr_enclave = { 0 };
            sgx_enclave_id_t enclave_id = 2L;
            map[&mr_enclave] = enclave_id;

            THEN( "the size of the map stays the same" ) {
                REQUIRE( map.size() == 1 );
            }

            THEN( "the mapping can be found" ) {
                auto result = map.find(&mr_enclave);
                REQUIRE( result != map.end() );
            }

            THEN( "the mapping returns the correct value" ) {
                auto result = map.find(&mr_enclave);
                REQUIRE( result->second == 2L );
            }
        }
    }

}
