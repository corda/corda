#ifndef __ENCLAVE_MAP_H__
#define __ENCLAVE_MAP_H__

#include <functional>
#include <map>
#include <sgx_eid.h>
#include <sgx_report.h>

struct enclave_hash_comparer : public std::binary_function<const sgx_measurement_t*, const sgx_measurement_t*, bool> {
public:
    bool operator() (const sgx_measurement_t* a, const sgx_measurement_t* b) const
    { return memcmp(a->m, b->m, 32) < 0; }
};

class enclave_map_t : public std::map<sgx_measurement_t*, sgx_enclave_id_t, enclave_hash_comparer> { };

void add_enclave_mapping(sgx_measurement_t *mr_enclave, sgx_enclave_id_t enclave_id);

sgx_enclave_id_t get_enclave_id(sgx_measurement_t *mr_enclave);

#endif /* __ENCLAVE_MAP_H__ */
