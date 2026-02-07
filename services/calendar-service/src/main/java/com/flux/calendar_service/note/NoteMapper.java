package com.flux.calendar_service.note;

import com.flux.calendar_service.note.dto.NoteResponse;
import org.springframework.stereotype.Service;

@Service
public class NoteMapper {

    public NoteResponse toNoteResponse(Note note) {
        if (note == null) {
            return null;
        }
        return new NoteResponse(
                note.getId(),
                note.getContent(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
