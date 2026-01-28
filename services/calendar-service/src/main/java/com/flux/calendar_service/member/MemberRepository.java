package com.flux.calendar_service.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {
    List<Member> findAllMembersByEventId(String id);
    Optional<Member> findMemberById(String id);
}
