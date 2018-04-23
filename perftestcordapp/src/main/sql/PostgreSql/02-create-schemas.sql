
CREATE SCHEMA perfnode1;
GRANT ALL ON SCHEMA perfnode1 TO perfnode1;
GRANT ALL ON ALL tables IN SCHEMA perfnode1 TO perfnode1;
ALTER DEFAULT privileges IN SCHEMA perfnode1 GRANT ALL ON tables TO perfnode1;
GRANT ALL ON ALL sequences IN SCHEMA perfnode1 TO perfnode1;
ALTER DEFAULT privileges IN SCHEMA perfnode1 GRANT ALL ON sequences TO perfnode1; 
CREATE sequence perfnode1.hibernate_sequence start 1 increment 1

CREATE SCHEMA perfnode2;
GRANT ALL ON SCHEMA perfnode2 TO perfnode2;
GRANT ALL ON ALL tables IN SCHEMA perfnode2 TO perfnode2;
ALTER DEFAULT privileges IN SCHEMA perfnode2 GRANT ALL ON tables TO perfnode2;
GRANT ALL ON ALL sequences IN SCHEMA perfnode2 TO perfnode2;
ALTER DEFAULT privileges IN SCHEMA perfnode2 GRANT ALL ON sequences TO perfnode2; 
CREATE sequence perfnode2.hibernate_sequence start 1 increment 1

CREATE SCHEMA perfnode3;
GRANT ALL ON SCHEMA perfnode3 TO perfnode3;
GRANT ALL ON ALL tables IN SCHEMA perfnode3 TO perfnode3;
ALTER DEFAULT privileges IN SCHEMA perfnode3 GRANT ALL ON tables TO perfnode3;
GRANT ALL ON ALL sequences IN SCHEMA perfnode3 TO perfnode3;
ALTER DEFAULT privileges IN SCHEMA perfnode3 GRANT ALL ON sequences TO perfnode3; 
CREATE sequence perfnode3.hibernate_sequence start 1 increment 1

CREATE SCHEMA perfnode4;
GRANT ALL ON SCHEMA perfnode4 TO perfnode4;
GRANT ALL ON ALL tables IN SCHEMA perfnode4 TO perfnode4;
ALTER DEFAULT privileges IN SCHEMA perfnode4 GRANT ALL ON tables TO perfnode4;
GRANT ALL ON ALL sequences IN SCHEMA perfnode4 TO perfnode4;
ALTER DEFAULT privileges IN SCHEMA perfnode4 GRANT ALL ON sequences TO perfnode4; 
CREATE sequence perfnode4.hibernate_sequence start 1 increment 1

CREATE SCHEMA perfnotary;
GRANT ALL ON SCHEMA perfnotary TO perfnotary;
GRANT ALL ON ALL tables IN SCHEMA perfnotary TO perfnotary;
ALTER DEFAULT privileges IN SCHEMA perfnotary GRANT ALL ON tables TO perfnotary;
GRANT ALL ON ALL sequences IN SCHEMA perfnotary TO perfnotary;
ALTER DEFAULT privileges IN SCHEMA perfnotary GRANT ALL ON sequences TO perfnotary; 
CREATE sequence perfnotary.hibernate_sequence start 1 increment 1

CREATE SCHEMA perfnetworkmap;
GRANT ALL ON SCHEMA perfnetworkmap TO perfnetworkmap;
GRANT ALL ON ALL tables IN SCHEMA perfnetworkmap TO perfnetworkmap;
ALTER DEFAULT privileges IN SCHEMA perfnetworkmap GRANT ALL ON tables TO perfnetworkmap;
GRANT ALL ON ALL sequences IN SCHEMA perfnetworkmap TO perfnetworkmap;
ALTER DEFAULT privileges IN SCHEMA perfnetworkmap GRANT ALL ON sequences TO perfnetworkmap; 
CREATE sequence perfnetworkmap.hibernate_sequence start 1 increment 1