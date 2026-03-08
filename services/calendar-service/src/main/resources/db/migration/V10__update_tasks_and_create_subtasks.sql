-- Drop the foreign key constraint from notes to tasks if it exists
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'notes_task_id_fkey') THEN
        ALTER TABLE notes DROP CONSTRAINT notes_task_id_fkey;
    END IF;
END $$;

-- Rename tasks table to subtasks
ALTER TABLE tasks RENAME TO subtasks;

-- Rename task column to subtask in subtasks table
ALTER TABLE subtasks RENAME COLUMN task TO subtask;

-- Make event_id nullable in subtasks
ALTER TABLE subtasks ALTER COLUMN event_id DROP NOT NULL;

-- Add task_id column to subtasks
ALTER TABLE subtasks ADD COLUMN task_id VARCHAR(255);

-- Create new tasks table
CREATE TABLE tasks (
    id VARCHAR(255) PRIMARY KEY,
    calendar_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(255) NOT NULL,
    priority VARCHAR(255),
    date DATE,
    deadline TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (calendar_id) REFERENCES calendars(id) ON DELETE CASCADE
);

-- Add foreign key constraint to subtasks for task_id
ALTER TABLE subtasks ADD CONSTRAINT fk_subtasks_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

-- Re-add the foreign key constraint to notes pointing to the NEW tasks table
ALTER TABLE notes ADD CONSTRAINT notes_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

-- Add indexes for subtasks
CREATE INDEX idx_subtasks_event_id ON subtasks(event_id);
CREATE INDEX idx_subtasks_task_id ON subtasks(task_id);
