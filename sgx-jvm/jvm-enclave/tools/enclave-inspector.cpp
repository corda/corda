#include <iostream>
#include <iomanip>
#include <cstddef>

#include "../common/enclave_metadata.h"

using namespace std;

static const char *status_messages[] = {
    "Success",
    "Unable to read the file",
    "Unable to read the file header",
    "The file is not an ELF file",
    "The file is an ELF file, but only 64-bit ELF files are supported",
    "Unable to allocate memory",
    "Unable to read section headers from file",
    "Unable to find note section named \".note.sgxmeta\"",
    "Invalid name of note section",
    "Invalid size of note section",
    "Unable to read meta data from file",
};

static void print_hex(uint8_t *buffer, size_t len) {
    for (int i = 0; i < len; i++) {
        cout << setfill('0') << setw(2) << setbase(16) << static_cast<int>(buffer[i]) << " ";
    }
    cout << endl;
}

int main(int argc, char *argv[]) {
    if (argc != 2) {
        cout << "SGX Enclave Inspector" << endl;
        cout << "Usage: " << argv[0] << " <enclave-object>" << endl;
        return 1;
    }

    uint8_t enclave_hash[32] = { 0 };
    enclave_hash_result_t result = retrieve_enclave_hash(argv[1], enclave_hash);

    cout << "Outcome: " << status_messages[result] << endl;
    if (EHR_SUCCESS == result) {
        cout << "  Path = " << argv[1] << endl;
        cout << "  Hash = ";
        print_hex(enclave_hash, 32);
    }

    return 0;
}
