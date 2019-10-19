#include "CordaBytes.h"

#include <array>
#include <sys/stat.h>
#include "amqp/AMQPHeader.h"

/******************************************************************************/

CordaBytes::CordaBytes (const std::string & file_)
    : m_blob { nullptr }
{
    std::ifstream file { file_, std::ios::in | std::ios::binary };
    struct stat results { };

    if (::stat(file_.c_str(), &results) != 0) {
        throw std::runtime_error ("Not a file");
    }

    // Disregard the Corda header
    m_size = results.st_size - (amqp::AMQP_HEADER.size() + 1);

    std::array<char, 7> header { };
    file.read (header.data(), 7);

    if (header != amqp::AMQP_HEADER) {
        throw std::runtime_error ("Not a Corda stream");
    }

    file.read (reinterpret_cast<char *>(&m_encoding), 1);

    m_blob = new char[m_size];

    memset (m_blob, 0, m_size);
    file.read (m_blob, m_size);
}

/******************************************************************************/

