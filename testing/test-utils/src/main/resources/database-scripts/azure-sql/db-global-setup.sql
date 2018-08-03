IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = '${schema}') EXEC('CREATE SCHEMA ${schema}');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='${schema}') CREATE USER ${schema} FOR LOGIN ${schema} WITH DEFAULT_SCHEMA = ${schema};
GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, ALTER, REFERENCES ON SCHEMA::${schema} TO ${schema};
GRANT CREATE TABLE TO ${schema};