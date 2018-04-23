#include "simple_t.h"

extern "C" {
    int get_number() {
        ocall_print("message from enclave");
        return 12345;
    }
}
