#include <sgx_urts.h>
#include <iostream>
#include <vector>
#include <fstream>
#include "sgx_utilities.h"
#include "java_u.h"
#include "enclave_map.h"
#include "enclave_metadata.h"

static sgx_measurement_t mr_enclave;

int main(int argc, char **argv) {
    sgx_launch_token_t token = {0};
    sgx_enclave_id_t enclave_id = {0};
    int updated = 0;

    const char *enclave_path = "../../enclave/build/cordaenclave.signed.so";
    enclave_hash_result_t hash_result = retrieve_enclave_hash(enclave_path, mr_enclave.m);
    if (EHR_SUCCESS != hash_result) {
        printf("Unable to retrieve MRENCLAVE from enclave\n");
        exit(1);
    }

    CHECK_SGX(sgx_create_enclave(enclave_path, SGX_DEBUG_FLAG, &token, &updated, &enclave_id, NULL));
    add_enclave_mapping(&mr_enclave, enclave_id);

    if (argc < 2) {
        printf("Usage: <executable> /path/to/req/file\n");
        exit(1);
    }
    std::ifstream file(argv[1]);
    std::vector<char> reqbytes;
    if (!file.eof() && !file.fail()) {
        file.seekg(0, std::ios_base::end);
        std::streampos fileSize = file.tellg();
        reqbytes.resize(fileSize);

        file.seekg(0, std::ios_base::beg);
        file.read(&reqbytes[0], fileSize);
    }
    if (reqbytes.size() == 0) {
        printf("Could not load %s\n", argv[1]);
    }

    char error[1024];
    CHECK_SGX(check_transaction(enclave_id, reqbytes.data(), reqbytes.size(), &error[0]));

    sgx_destroy_enclave(enclave_id);

    return 0;
}
