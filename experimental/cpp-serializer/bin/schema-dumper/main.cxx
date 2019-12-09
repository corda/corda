#include <iostream>
#include <iomanip>
#include <fstream>
#include <cstddef>

#include <assert.h>
#include <string.h>
#include <proton/types.h>
#include <proton/codec.h>
#include <sys/stat.h>
#include <sstream>

#include "debug.h"

#include "proton/proton_wrapper.h"

#include "amqp/AMQPHeader.h"
#include "amqp/AMQPSectionId.h"
#include "amqp/schema/descriptors/AMQPDescriptorRegistory.h"

#include "amqp/schema/described-types/Envelope.h"
#include "amqp/CompositeFactory.h"

/******************************************************************************/

void
printNode (pn_data_t * d_) {
    std::stringstream ss;

    if (pn_data_is_described (d_)) {
        amqp::internal::AMQPDescriptorRegistory[22UL]->read (d_, ss);
    }

    std::cout << ss.str() << std::endl;
}


/******************************************************************************/

void
data_and_stop(std::ifstream & f_, ssize_t sz) {
    char * blob = new char[sz];
    memset (blob, 0, sz);
    f_.read(blob, sz);

    pn_data_t * d = pn_data(sz);

    // returns how many bytes we processed which right now we don't care
    // about but I assume there is a case where it doesn't process the
    // entire file
    auto rtn = pn_data_decode (d, blob, sz);
    assert (rtn == sz);

    printNode (d);

}

/******************************************************************************/

int
main (int argc, char **argv) {
    struct stat results { };

    if (stat(argv[1], &results) != 0) {
        return EXIT_FAILURE;
    }

    std::ifstream f (argv[1], std::ios::in | std::ios::binary);
    std::array<char, 7> header { };
    f.read(header.data(), 7);

    if (header != amqp::AMQP_HEADER) {
        std::cerr << "Bad Header in blob" << std::endl;
        return EXIT_FAILURE;
    }

    amqp::amqp_section_id_t encoding;
    f.read((char *)&encoding, 1);

    if (encoding == amqp::DATA_AND_STOP) {
        data_and_stop(f, results.st_size - 8);
    } else {
        std::cerr << "BAD ENCODING " << encoding << " != "
            << amqp::DATA_AND_STOP << std::endl;

        return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}

/******************************************************************************/
