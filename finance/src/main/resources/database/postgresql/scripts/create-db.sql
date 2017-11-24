create table contract_cash_states (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    ccy_code varchar(3),
    issuer_key_hash varchar(130),
    issuer_ref bytea,
    owner_name varchar(255),
    pennies int8,
    primary key (output_index, transaction_id)
);

create index ccy_code_idx on contract_cash_states (ccy_code);
create index pennies_idx on contract_cash_states (pennies);


create table cp_states (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    ccy_code varchar(3),
    face_value int8,
    face_value_issuer_key_hash varchar(130),
    face_value_issuer_ref bytea,
    issuance_key_hash varchar(130),
    issuance_ref bytea,
    maturity_instant timestamp,
    owner_key_hash varchar(130),
    primary key (output_index, transaction_id)
);

create index ccy_code_index on cp_states (ccy_code);
create index maturity_index on cp_states (maturity_instant);
create index face_value_index on cp_states (face_value);
