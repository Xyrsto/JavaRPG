-- JavaRPG progress schema (plan section 4). Idempotent: runs on every start.
CREATE TABLE IF NOT EXISTS player (
  id           VARCHAR(36) PRIMARY KEY,
  hero_x       INT NOT NULL,
  hero_y       INT NOT NULL,
  created_at   TIMESTAMP NOT NULL,
  last_seen_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS quest_progress (
  player_id       VARCHAR(36) NOT NULL,
  quest_id        VARCHAR(3)  NOT NULL,
  failed_attempts INT NOT NULL DEFAULT 0,
  hints_unlocked  INT NOT NULL DEFAULT 0,
  completed       BOOLEAN NOT NULL DEFAULT FALSE,
  xp_awarded      INT NOT NULL DEFAULT 0,
  completed_at    TIMESTAMP NULL,
  last_source     CLOB NULL,
  PRIMARY KEY (player_id, quest_id),
  FOREIGN KEY (player_id) REFERENCES player(id)
);
