#ifndef __ENCLAVE_METADATA_H__
#define __ENCLAVE_METADATA_H__

typedef enum _enclave_hash_result_t {
    EHR_SUCCESS,                            // The hash of the enclave was retrieved successfully
    EHR_ERROR_READ_FILE,                    // Unable to read the file
    EHR_ERROR_READ_ELF_HEADER,              // Unable to read the file header
    EHR_ERROR_NOT_ELF_FORMAT,               // The file is not an ELF file
    EHR_ERROR_NOT_ELF64_FORMAT,             // The file is an ELF file, but only 64-bit ELF files are supported
    EHR_ERROR_OUT_OF_MEMORY,                // Unable to allocate memory
    EHR_ERROR_READ_SECTION_HEADERS,         // Unable to read section headers from file
    EHR_ERROR_NO_SGX_META_DATA_SECTION,     // Unable to find note section named ".note.sgxmeta"
    EHR_ERROR_INVALID_SECTION_NAME,         // Invalid name of note section
    EHR_ERROR_INVALID_SECTION_SIZE,         // Invalid size of note section
    EHR_ERROR_READ_META_DATA,               // Unable to read meta data from file
} enclave_hash_result_t;

extern "C" enclave_hash_result_t retrieve_enclave_hash(const char *path, uint8_t *enclave_hash);

#endif /* __ENCLAVE_METADATA_H__ */
