create database if not exists corda;

create table corda.notary_committed_states (
	state_ref varchar(73) not null, 
	consuming_transaction_id varchar(64) not null, 
	constraint id1 primary key (state_ref)
	);
	
create table corda.notary_committed_transactions (
	transaction_id varchar(64) not null,
	constraint id2 primary key (transaction_id)
	);
	
create table corda.notary_request_log (
	id varchar(76) not null,
	consuming_transaction_id varchar(64),
	requesting_party_name varchar(255),
	request_timestamp timestamp not null,
	request_signature bytes not null,
	constraint id3 primary key (id)
	);