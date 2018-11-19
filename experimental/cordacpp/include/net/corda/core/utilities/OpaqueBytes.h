////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_UTILITIES_OPAQUEBYTES_H
#define NET_CORDA_CORE_UTILITIES_OPAQUEBYTES_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace utilities {
class ByteSequence;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace utilities {

class OpaqueBytes : public net::corda::core::utilities::ByteSequence {
public:
    proton::binary bytes;

    OpaqueBytes() : bytes() {}

    explicit OpaqueBytes(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.utilities.OpaqueBytes", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    const std::string descriptor() { return "net.corda:nh0FjtbnhJcqG3dN29nAPw=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif