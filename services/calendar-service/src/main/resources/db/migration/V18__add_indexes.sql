CREATE INDEX idx_attachment_event ON attachments(event_id);
CREATE INDEX idx_subtask_event ON subtasks(event_id);
CREATE INDEX idx_member_event ON members(event_id);
CREATE INDEX idx_note_event ON notes(event_id);