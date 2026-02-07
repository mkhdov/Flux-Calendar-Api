CREATE TABLE notes (
    id VARCHAR(255) PRIMARY KEY,
    content TEXT NOT NULL,
    event_id VARCHAR(255),
    task_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT chk_note_parent CHECK (
        (event_id IS NOT NULL AND task_id IS NULL) OR
        (event_id IS NULL AND task_id IS NOT NULL)
    )
);

CREATE INDEX idx_notes_event_id ON notes(event_id);
CREATE INDEX idx_notes_task_id ON notes(task_id);
