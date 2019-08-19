#pragma once

/******************************************************************************/

#include <any>
#include <list>
#include <string>
#include <vector>
#include <memory>

#include "amqp/schema/Schema.h"
#include "amqp/reader/IReader.h"

/******************************************************************************/

namespace amqp::internal::reader {

    class Value : public amqp::reader::IValue {
        public :
            std::string dump() const override = 0;

            ~Value() override = default;
    };

    class Single : public Value {
        public :
            std::string dump() const override = 0;

            ~Single() override = default;
    };

    template<typename T>
    class TypedSingle : public Single {
        private:
            T m_value;

        public:
            explicit TypedSingle (const T & value_)
                : Single()
                , m_value (value_)
            { }

            explicit TypedSingle (T && value_)
                : Single()
                , m_value { std::move (value_) }
            { }

            TypedSingle (const TypedSingle && value_) noexcept
                : Single()
                , m_value { std::move (value_.m_value) }
            { }

            const T & value() const {
                return m_value;
            }

            std::string dump() const override;
    };

    class Pair : public Value {
        protected :
            std::string m_property;

        public:
            explicit Pair (std::string property_)
                : m_property (std::move (property_))
            { }

            ~Pair() override = default;

            Pair (Pair && pair_) noexcept
                : m_property (std::move (pair_.m_property))
            { }

            std::string dump() const override = 0;
    };


    template<typename T>
    class TypedPair : public Pair {
        private:
            T m_value;

        public:
            TypedPair (const std::string & property_, T & value_)
                : Pair (property_)
                , m_value (value_)
            { }

            TypedPair (const std::string & property_, T && value_)
                : Pair (property_)
                , m_value (std::move (value_))
            { }

            TypedPair (TypedPair && pair_) noexcept
                : Pair (std::move (pair_.m_property))
                , m_value (std::move (pair_.m_value))
            { }

            const T & value() const {
                return m_value;
            }

            std::string dump() const override;
    };

}

/******************************************************************************
 *
 * amqp::internal::reader::TypedSingle
 *
 ******************************************************************************/

template<typename T>
inline std::string
amqp::internal::reader::
TypedSingle<T>::dump() const {
    return std::to_string(m_value);
}

template<>
inline std::string
amqp::internal::reader::
TypedSingle<std::string>::dump() const {
    return m_value;
}

template<>
std::string
amqp::internal::reader::
TypedSingle<sVec<uPtr<amqp::reader::IValue>>>::dump() const;

template<>
std::string
amqp::internal::reader::
TypedSingle<sList<uPtr<amqp::reader::IValue>>>::dump() const;

template<>
std::string
amqp::internal::reader::
TypedSingle<sVec<uPtr<amqp::internal::reader::Single>>>::dump() const;

template<>
std::string
amqp::internal::reader::
TypedSingle<sList<uPtr<amqp::internal::reader::Single>>>::dump() const;

/******************************************************************************
 *
 * amqp::internal::reader::TypedPair
 *
 ******************************************************************************/

template<typename T>
inline std::string
amqp::internal::reader::
TypedPair<T>::dump() const {
    return m_property + " : " + std::to_string (m_value);
}

template<>
inline std::string
amqp::internal::reader::
TypedPair<std::string>::dump() const {
    return m_property + " : " + m_value;
}

template<>
std::string
amqp::internal::reader::
TypedPair<sVec<uPtr<amqp::reader::IValue>>>::dump() const;

template<>
std::string
amqp::internal::reader::
TypedPair<sList<uPtr<amqp::reader::IValue>>>::dump() const;


template<>
std::string
amqp::internal::reader::
TypedPair<sVec<uPtr<amqp::internal::reader::Pair>>>::dump() const;

template<>
std::string
amqp::internal::reader::
TypedPair<sList<uPtr<amqp::internal::reader::Pair>>>::dump() const;

/******************************************************************************
 *
 *
 *
 *
 ******************************************************************************/

namespace amqp::internal::reader  {

    using IReader = amqp::reader::IReader<schema::SchemaMap::const_iterator>;

    class Reader : public IReader {
        public :
            ~Reader() override = default;

            const std::string & name() const override = 0;
            const std::string & type() const override = 0;

            std::any read (struct pn_data_t *) const override = 0;
            std::string readString (struct pn_data_t *) const override = 0;

            uPtr<amqp::reader::IValue> dump(
                const std::string &,
                pn_data_t *,
                const SchemaType &) const override = 0;

            uPtr<amqp::reader::IValue> dump(
                pn_data_t *,
                const SchemaType &) const override = 0;
    };

}

/******************************************************************************/

