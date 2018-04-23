IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = '${schema}') EXEC('CREATE SCHEMA ${schema}');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='${schema}') CREATE USER ${schema} FOR LOGIN ${schema} WITH DEFAULT_SCHEMA = ${schema};
GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::${schema} TO ${schema};
GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO ${schema};