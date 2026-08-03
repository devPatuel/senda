-- Habit tracking (first non-financial module). Two tables: the definition and
-- one row per habit and day. `done` is stored, not derived: raising a target
-- must not turn past successes into failures.

CREATE TABLE habits (
    id                  BIGSERIAL      PRIMARY KEY,
    user_id             BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    space_id            BIGINT         REFERENCES spaces (id) ON DELETE CASCADE,
    name                VARCHAR(100)   NOT NULL,
    emoji               VARCHAR(8),
    type                VARCHAR(10)    NOT NULL CHECK (type IN ('CHECK', 'COUNTER', 'MEASURE')),
    target              NUMERIC(10, 2) CHECK (target > 0),
    unit                VARCHAR(20),
    schedule_type       VARCHAR(12)    NOT NULL
                        CHECK (schedule_type IN ('WEEKDAYS', 'INTERVAL', 'WEEKLY_COUNT')),
    -- VARCHAR, not CHAR: bpchar pads with spaces and Hibernate's schema validation
    -- rejects it against a plain String. The CHECK already pins the length at seven.
    weekdays            VARCHAR(7)     CHECK (weekdays ~ '^[01]{7}$'),
    interval_days       INT            CHECK (interval_days BETWEEN 1 AND 365),
    weekly_target       INT            CHECK (weekly_target BETWEEN 1 AND 7),
    active              BOOLEAN        NOT NULL DEFAULT true,
    sort_order          INT            NOT NULL DEFAULT 0,
    schedule_changed_at DATE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_habits_user ON habits (user_id);

CREATE TABLE habit_entries (
    id         BIGSERIAL      PRIMARY KEY,
    habit_id   BIGINT         NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    user_id    BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    entry_date DATE           NOT NULL,
    value      NUMERIC(10, 2),
    done       BOOLEAN        NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_habit_entry UNIQUE (habit_id, entry_date)
);

CREATE INDEX idx_habit_entries_habit_date ON habit_entries (habit_id, entry_date);
CREATE INDEX idx_habit_entries_user_date ON habit_entries (user_id, entry_date);
