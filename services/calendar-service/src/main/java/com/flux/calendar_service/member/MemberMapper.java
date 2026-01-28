package com.flux.calendar_service.member;

import com.flux.calendar_service.member.dto.MemberResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberMapper {

    public MemberResponseDTO toMemberResponse(Member member) {
        return new MemberResponseDTO(
                member.getId(),
                member.getEmail(),
                member.getFullName(),
                member.isOwner(),
                member.getCreatedAt()
        );
    }
}
