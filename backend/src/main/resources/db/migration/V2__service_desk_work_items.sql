CREATE SEQUENCE work_item_number_seq START WITH 1000;
CREATE TABLE work_item (
 id uuid PRIMARY KEY, number varchar(32) NOT NULL UNIQUE, type varchar(32) NOT NULL,
 title varchar(240) NOT NULL, description text NOT NULL, service varchar(100) NOT NULL,
 state varchar(40) NOT NULL, priority varchar(30) NOT NULL, requester_id varchar(128) NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX work_item_requester_updated_idx ON work_item(requester_id, updated_at DESC);
CREATE INDEX work_item_state_updated_idx ON work_item(state, updated_at DESC);
