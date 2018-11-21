////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_UTILITIES_OPAQUEBYTES_H
#define NET_CORDA_CORE_UTILITIES_OPAQUEBYTES_H

#include "corda.h"
#include "net/corda/core/utilities/ByteSequence.h"

namespace net {
namespace corda {
namespace core {
namespace utilities {
class ByteSequence;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace utilities {

class OpaqueBytes : public net::corda::core::utilities::ByteSequence {
public:
    proton::binary bytes;

    OpaqueBytes() = default;

    explicit OpaqueBytes(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.utilities.OpaqueBytes", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    virtual const std::string descriptor() { return "net.corda:pgT0Kc3t/bvnzmgu/nb4Cg=="; }
};

}
}
}
}

#endif