-- V2 — ShedLock's lock table (standard PostgreSQL layout from the ShedLock docs).
-- Holds one row per named lock; the scheduled sync uses the name "modelSyncJob".
-- Not a JPA entity — ShedLock manages it directly, so Hibernate's validate ignores it.

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
