#pragma once

/******************************************************************************/

#include <iostream>
#include "Reader.h"

#include "amqp/schema/Field.h"

/******************************************************************************/

namespace amqp {

    class PropertyReader : public Reader {
        private :
            using FieldPtr = std::unique_ptr<internal::schema::Field>;

        public :
            /**
             * Static Factory method for creating appropriate derived types
             */
            static std::shared_ptr<PropertyReader> make (const FieldPtr &);
            static std::shared_ptr<PropertyReader> make (const std::string &);

            ~PropertyReader() override = default;

            std::string readString(pn_data_t *) const override = 0;

            std::any read (pn_data_t *) const override = 0;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override = 0;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override = 0;

            const std::string & name() const override = 0;

            const std::string & type() const override = 0;
    };


    class StringPropertyReader : public PropertyReader {
        private :
            static const std::string m_name;
            static const std::string m_type;

        public :
            std::string readString (pn_data_t *) const override;

            std::any read (pn_data_t *) const override;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            const std::string & name() const override {
                return m_name;
            }

            const std::string & type() const override {
                return m_type;
            }
    };

    class IntPropertyReader : public PropertyReader {
        private :
            static const std::string m_name;
            static const std::string m_type;

        public :
            ~IntPropertyReader() override = default;

            std::string readString (pn_data_t *) const override;

            std::any read (pn_data_t *) const override;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            const std::string & name() const override {
                return m_name;
            }

            const std::string & type() const override {
                return m_type;
            }
    };

    class BoolPropertyReader : public PropertyReader {
        private :
            static const std::string m_name;
            static const std::string m_type;

        public :
            std::string readString (pn_data_t *) const override;

            std::any read (pn_data_t *) const override;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            const std::string & name() const override {
                return m_name;
            }

            const std::string & type() const override {
                return m_type;
            }
    };

    class LongPropertyReader : public PropertyReader {
        private :
            static const std::string m_name;
            static const std::string m_type;

        public :
            std::string readString (pn_data_t *) const override;

            std::any read (pn_data_t *) const override;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            const std::string & name() const override {
                return m_name;
            }

            const std::string & type() const override {
                return m_type;
            }
    };

    class DoublePropertyReader : public PropertyReader {
        private :
            static const std::string m_name;
            static const std::string m_type;

        public :
            std::string readString (pn_data_t *) const override;

            std::any read (pn_data_t *) const override;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &
            ) const override;

            const std::string & name() const override {
                return m_name;
            }

            const std::string & type() const override {
                return m_type;
            }
    };

}

/******************************************************************************/


