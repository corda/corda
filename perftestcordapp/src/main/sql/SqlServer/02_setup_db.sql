USE perftesting

IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'perfnode1') EXEC('CREATE SCHEMA perfnode1');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='perfnode1') CREATE USER perfnode1 FOR LOGIN perfnode1 WITH DEFAULT_SCHEMA = perfnode1;
GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::perfnode1 TO perfnode1;
GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO perfnode1;

IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'perfnode2') EXEC('CREATE SCHEMA perfnode2');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='perfnode2') CREATE USER perfnode2 FOR LOGIN perfnode2 WITH DEFAULT_SCHEMA = perfnode2;
GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::perfnode2 TO perfnode2;
GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO perfnode2;

IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'perfnode3') EXEC('CREATE SCHEMA perfnode3');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='perfnode3') CREATE USER perfnode3 FOR LOGIN perfnode3 WITH DEFAULT_SCHEMA = perfnode3;
GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::perfnode3 TO perfnode3;
GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO perfnode3;

IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'perfnode4') EXEC('CREATE SCHEMA perfnode4');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='perfnode4') CREATE USER perfnode4 FOR LOGIN perfnode4 WITH DEFAULT_SCHEMA = perfnode4;
GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::perfnode4 TO perfnode4;
GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO perfnode4;

IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'perfnotary') EXEC('CREATE SCHEMA perfnotary');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='perfnotary') CREATE USER perfnotary FOR LOGIN perfnotary WITH DEFAULT_SCHEMA = perfnotary;
GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::perfnotary TO perfnotary;
GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO perfnotary;

IF NOT EXISTS (SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'perfnetworkmap') EXEC('CREATE SCHEMA perfnetworkmap');
IF NOT EXISTS (SELECT * FROM sys.sysusers WHERE name='perfnetworkmap') CREATE USER perfnetworkmap FOR LOGIN perfnetworkmap WITH DEFAULT_SCHEMA = perfnetworkmap;
GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::perfnetworkmap TO perfnetworkmap;
GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO perfnetworkmap;