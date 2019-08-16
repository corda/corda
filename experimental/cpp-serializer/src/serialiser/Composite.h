#pragma once

/******************************************************************************/

namespace amqp {
namespace internal {
namespace serialiser {

    template <typename T>
    class Composite : public ProtonReader<T> {

    };

}
}
}

/******************************************************************************/

