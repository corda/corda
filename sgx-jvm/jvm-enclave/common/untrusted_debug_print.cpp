#include <iostream>

extern "C" {
    void debug_print(const char *str) {
        // Don't allow 'str' to contain format string codes that might get interpreted and screw with memory.
        printf("%s", str);
        fflush(stdout);
    }
}
