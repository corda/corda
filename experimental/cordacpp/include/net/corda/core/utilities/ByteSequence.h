////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_UTILITIES_BYTESEQUENCE_H
#define NET_CORDA_CORE_UTILITIES_BYTESEQUENCE_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace utilities {

class ByteSequence {
public:
    int32_t offset;
    int32_t size;

    ByteSequence() : offset(0), size(0) {}

    explicit ByteSequence(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.utilities.ByteSequence", descriptor(), 2);
        net::corda::Parser::read_to(decoder, offset);
        net::corda::Parser::read_to(decoder, size);
    }

    const std::string descriptor() { return "net.corda:+U77oDTFDB2Yr2jufz6oxQ=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif