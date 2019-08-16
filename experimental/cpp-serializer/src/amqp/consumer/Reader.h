#pragma once

/******************************************************************************/

#include <any>
#include <list>
#include <string>
#include <vector>
#include <memory>

#include "schema/Schema.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp {

    class Value {
        public :
            virtual std::string dump() const = 0;

            virtual ~Value() = default;
    };

    class Single : public Value {
        public :
            std::string dump() const override = 0;
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
            explicit Pair(const std::string & property_)
                : m_property (property_)
            { }

            virtual ~Pair() = default;

            Pair (amqp::Pair && pair_) noexcept
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

            TypedPair (TypedPair && pair_)
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
 * amqp::TypeSingle
 *
 ******************************************************************************/

template<typename T>
inline std::string
amqp::TypedSingle<T>::dump() const {
    return std::to_string(m_value);
}

template<>
inline std::string
amqp::TypedSingle<std::string>::dump() const {
    return m_value;
}

template<>
std::string
amqp::TypedSingle<std::vector<std::unique_ptr<amqp::Value>>>::dump() const;

template<>
std::string
amqp::TypedSingle<std::list<std::unique_ptr<amqp::Value>>>::dump() const;

template<>
std::string
amqp::TypedSingle<std::vector<std::unique_ptr<amqp::Single>>>::dump() const;

template<>
std::string
amqp::TypedSingle<std::list<std::unique_ptr<amqp::Single>>>::dump() const;

/******************************************************************************
 *
 * amqp::TypedPair
 *
 ******************************************************************************/

template<typename T>
inline std::string
amqp::TypedPair<T>::dump() const {
    return m_property + " : " + std::to_string (m_value);
}

template<>
inline std::string
amqp::TypedPair<std::string>::dump() const {
    return m_property + " : " + m_value;
}

template<>
std::string
amqp::TypedPair<std::vector<std::unique_ptr<amqp::Value>>>::dump() const;

template<>
std::string
amqp::TypedPair<std::list<std::unique_ptr<amqp::Value>>>::dump() const;


template<>
std::string
amqp::TypedPair<std::vector<std::unique_ptr<amqp::Pair>>>::dump() const;

template<>
std::string
amqp::TypedPair<std::list<std::unique_ptr<amqp::Pair>>>::dump() const;

/******************************************************************************
 *
 *
 *
 *
 ******************************************************************************/

namespace amqp {

    class Reader {
        public :
            virtual ~Reader() = default;
            virtual const std::string & name() const = 0;
            virtual const std::string & type() const = 0;

            virtual std::any read(pn_data_t *) const = 0;
            virtual std::string readString(pn_data_t *) const = 0;

            virtual std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const = 0;

            virtual std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const = 0;
    };

}

/******************************************************************************/

