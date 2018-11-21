////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_UTILITIES_BYTESEQUENCE_H
#define NET_CORDA_CORE_UTILITIES_BYTESEQUENCE_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace utilities {

class ByteSequence {
public:
    int32_t offset;
    int32_t size;

    ByteSequence() = default;

    explicit ByteSequence(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.utilities.ByteSequence", descriptor(), 2);
        net::corda::Parser::read_to(decoder, offset);
        net::corda::Parser::read_to(decoder, size);
    }

    virtual const std::string descriptor() { return "net.corda:0UvJuq940P0jrySmql4EPg=="; }
};

}
}
}
}

#endif