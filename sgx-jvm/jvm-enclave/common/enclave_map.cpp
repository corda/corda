#include <cstring>
#include <stdexcept>
#include "enclave_map.h"

static enclave_map_t map;

void add_enclave_mapping(sgx_measurement_t *mr_enclave, sgx_enclave_id_t enclave_id) {
    // Note: The size of the enclave map is proportional to the number of unique
    // enclaves a system is dealing with. For the time being we don't envision this
    // number to be very big. Longer term, we might want to implement some form of
    // pruning to avoid old entries taking up unnecessary memory space.
    map[mr_enclave] = enclave_id;
}

sgx_enclave_id_t get_enclave_id(sgx_measurement_t *mr_enclave) {
     auto result = map.find(mr_enclave);
     if (result == map.end()) {
         throw std::invalid_argument("no enclave ID associated with enclave measurement");
     }
     return result->second;
}
