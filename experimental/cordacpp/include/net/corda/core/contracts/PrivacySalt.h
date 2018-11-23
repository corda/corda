////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_PRIVACYSALT_H
#define NET_CORDA_CORE_CONTRACTS_PRIVACYSALT_H

#include "corda.h"
#include "net/corda/core/utilities/OpaqueBytes.h"

namespace net {
namespace corda {
namespace core {
namespace utilities {
class OpaqueBytes;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace contracts {

class PrivacySalt : public net::corda::core::utilities::OpaqueBytes {
public:
    PrivacySalt() = default;

    explicit PrivacySalt(proton::codec::decoder &decoder) : net::corda::core::utilities::OpaqueBytes(decoder) {
    }
};

}
}
}
}

net::corda::TypeRegistration Registration6("net.corda:1skUfBacU1AgmLX8M1z83A==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::PrivacySalt(decoder); }); // NOLINT(cert-err58-cpp)

#endif