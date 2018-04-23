#pragma once

// Exits the enclave if passed in boolean is false.
inline void aex_assert(bool predicate) {
    if (!predicate) {
        abort();
    }
}
