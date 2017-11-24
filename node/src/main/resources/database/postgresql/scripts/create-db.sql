
create table link_nodeinfo_party (
   node_info_id int4 not null,
    party_name varchar(255) not null
);


create table node_attachments (
   att_id varchar(255) not null,
    content oid,
    filename varchar(255),
    insertion_date timestamp not null,
    uploader varchar(255),
    primary key (att_id)
);


create table node_bft_committed_states (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    consuming_input_index int4,
    consuming_transaction_id varchar(255),
    requesting_party_name varchar(255),
    requesting_party_key bytea,
    primary key (output_index, transaction_id)
);


create table node_checkpoints (
   checkpoint_id varchar(64) not null,
    checkpoint_value oid,
    primary key (checkpoint_id)
);


create table node_contract_upgrades (
   state_ref varchar(96) not null,
    contract_class_name varchar(255),
    primary key (state_ref)
);


create table node_identities (
   pk_hash varchar(130) not null,
    identity_value oid,
    primary key (pk_hash)
);


create table node_info_hosts (
   host varchar(255) not null,
    port int4 not null,
    node_info_id int4,
    primary key (host, port)
);


create table node_info_party_cert (
   party_name varchar(255) not null,
    isMain boolean not null,
    owning_key_hash varchar(130),
    party_cert_binary oid,
    primary key (party_name)
);


create table node_infos (
   node_info_id int4 not null,
    node_info_hash varchar(64),
    platform_version int4,
    serial int8,
    primary key (node_info_id)
);


create table node_message_ids (
   message_id varchar(36) not null,
    insertion_time timestamp,
    primary key (message_id)
);


create table node_message_retry (
   message_id int8 not null,
    message oid,
    recipients oid,
    primary key (message_id)
);


create table node_named_identities (
   name varchar(128) not null,
    pk_hash varchar(130),
    primary key (name)
);


create table node_notary_commit_log (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    consuming_input_index int4,
    consuming_transaction_id varchar(255),
    requesting_party_name varchar(255),
    requesting_party_key bytea,
    primary key (output_index, transaction_id)
);


create table node_our_key_pairs (
   public_key_hash varchar(130) not null,
    private_key oid,
    public_key oid,
    primary key (public_key_hash)
);


create table node_raft_committed_states (
   id varchar(255) not null,
    state_index int8,
    state_value oid,
    primary key (id)
);


create table node_scheduled_states (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    scheduled_at timestamp not null,
    primary key (output_index, transaction_id)
);


create table node_transaction_mappings (
   tx_id varchar(64) not null,
    state_machine_run_id varchar(36),
    primary key (tx_id)
);


create table node_transactions (
   tx_id varchar(64) not null,
    transaction_value oid,
    primary key (tx_id)
);


create table vault_fungible_states (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    issuer_name varchar(255),
    issuer_ref bytea,
    owner_name varchar(255),
    quantity int8,
    primary key (output_index, transaction_id)
);


create table vault_fungible_states_parts (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    participants varchar(255)
);


create table vault_linear_states (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    external_id varchar(255),
    uuid bytea not null,
    primary key (output_index, transaction_id)
);


create table vault_linear_states_parts (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    participants varchar(255)
);


create table vault_states (
   output_index int4 not null,
    transaction_id varchar(64) not null,
    consumed_timestamp timestamp,
    contract_state_class_name varchar(255),
    lock_id varchar(255),
    lock_timestamp timestamp,
    notary_name varchar(255),
    recorded_timestamp timestamp,
    state_status int4,
    primary key (output_index, transaction_id)
);


create table vault_transaction_notes (
   seq_no int4 not null,
    note varchar(255),
    transaction_id varchar(64),
    primary key (seq_no)
);

create index att_id_idx on node_attachments (att_id);
create index external_id_index on vault_linear_states (external_id);
create index uuid_index on vault_linear_states (uuid);
create index state_status_idx on vault_states (state_status);
create index lock_id_idx on vault_states (lock_id, state_status);
create index transaction_id_index on vault_transaction_notes (transaction_id);
create sequence hibernate_sequence start 1 increment 1;


alter table link_nodeinfo_party
   add constraint FK1ua3h6nwwfji0mn23c5d1xx8e
   foreign key (party_name)
   references node_info_party_cert;


alter table link_nodeinfo_party
   add constraint FK544l9wsec35ph7hxrtwfd2lws
   foreign key (node_info_id)
   references node_infos;


alter table node_info_hosts
   add constraint FK5ie46htdrkftmwe6rpwrnp0mp
   foreign key (node_info_id)
   references node_infos;


alter table vault_fungible_states_parts
   add constraint FKchmfeq1ldqnoq9idv9ogxauqm
   foreign key (output_index, transaction_id)
   references vault_fungible_states;


alter table vault_linear_states_parts
   add constraint FKhafsv733d0bo9j1tg352koq3y
   foreign key (output_index, transaction_id)
   references vault_linear_states;
