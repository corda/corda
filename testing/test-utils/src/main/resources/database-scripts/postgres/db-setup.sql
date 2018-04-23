DROP SCHEMA IF EXISTS "${schema}" CASCADE;
CREATE SCHEMA "${schema}";
GRANT ALL ON SCHEMA "${schema}" TO "${schema}";
ALTER DEFAULT privileges IN SCHEMA "${schema}" GRANT ALL ON tables TO "${schema}";
ALTER DEFAULT privileges IN SCHEMA "${schema}" GRANT ALL ON sequences TO "${schema}";