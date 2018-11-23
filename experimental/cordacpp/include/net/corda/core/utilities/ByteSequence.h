////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_UTILITIES_BYTESEQUENCE_H
#define NET_CORDA_CORE_UTILITIES_BYTESEQUENCE_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace utilities {

class ByteSequence : public net::corda::Any {
public:
    int32_t offset;
    int32_t size;

    ByteSequence() = default;

    explicit ByteSequence(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, offset);
        net::corda::Parser::read_to(decoder, size);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration22("net.corda:0UvJuq940P0jrySmql4EPg==", [](proton::codec::decoder &decoder) { return new net::corda::core::utilities::ByteSequence(decoder); }); // NOLINT(cert-err58-cpp)

#endif