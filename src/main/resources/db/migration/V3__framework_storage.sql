-- Adapted Apache-2.0 Spring Session 4.1.1 and db-scheduler 17.0.0 DDL.
-- Upstream identities and checksums are documented in THIRD_PARTY.md.
CREATE TABLE rr_internal.spring_session (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON rr_internal.spring_session (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON rr_internal.spring_session (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON rr_internal.spring_session (PRINCIPAL_NAME);

CREATE TABLE rr_internal.spring_session_attributes (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES rr_internal.spring_session(PRIMARY_ID) ON DELETE CASCADE
);

create table rr_internal.scheduled_tasks (
  task_name text not null,
  task_instance text not null,
  task_data bytea,
  execution_time timestamp with time zone not null,
  picked BOOLEAN not null,
  picked_by text,
  last_success timestamp with time zone,
  last_failure timestamp with time zone,
  consecutive_failures INT,
  last_heartbeat timestamp with time zone,
  version BIGINT not null,
  priority SMALLINT,
  PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX execution_time_idx ON rr_internal.scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON rr_internal.scheduled_tasks (last_heartbeat);
CREATE INDEX priority_execution_time_idx on rr_internal.scheduled_tasks (priority desc, execution_time asc);

-- an optimization for users of priority might be to add priority to the execution_time_idx
-- this _might_ save reads as the priority-value is already in the index
-- CREATE INDEX execution_time_idx ON rr_internal.scheduled_tasks (execution_time asc, priority desc);
