package com.flux.calendar_service.member;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.event.EventStatus;
import com.flux.calendar_service.event.EventType;
import com.flux.calendar_service.member.dto.MemberRequest;
import com.flux.calendar_service.member.dto.MemberResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberService memberService;

    private Event testEvent;
    private Member testMember;
    private MemberRequest testMemberRequest;
    private MemberResponseDTO testMemberResponse;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id("event-123")
                .title("Test Event")
                .description("Test Description")
                .colorHex("#FF0000")
                .type(EventType.MEETING)
                .status(EventStatus.NOTSTARTED)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(1))
                .allDay(false)
                .members(new ArrayList<>())
                .build();

        testMember = Member.builder()
                .id("member-123")
                .email("test@example.com")
                .fullName("Test User")
                .event(testEvent)
                .isOwner(false)
                .createdAt(LocalDateTime.now())
                .build();

        testMemberRequest = new MemberRequest("Test User");

        testMemberResponse = new MemberResponseDTO(
                "member-123",
                "test@example.com",
                "Test User",
                false,
                LocalDateTime.now());
    }

    @Test
    void addMember_ShouldAddMemberSuccessfully() {
        // Given
        when(eventRepository.findById("event-123")).thenReturn(Optional.of(testEvent));
        when(memberRepository.save(any(Member.class))).thenReturn(testMember);

        // When
        String result = memberService.addMember("event-123", "test@example.com", testMemberRequest);

        // Then
        assertThat(result).isEqualTo("member-123");
        verify(eventRepository).findById("event-123");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void getMemberByEventId_ShouldReturnMembers() {
        // Given
        List<Member> members = List.of(testMember);
        when(memberRepository.findAllMembersByEventId("event-123")).thenReturn(members);
        when(memberMapper.toMemberResponse(testMember)).thenReturn(testMemberResponse);

        // When
        List<MemberResponseDTO> result = memberService.getMemberByEventId("event-123");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("test@example.com");
        verify(memberRepository).findAllMembersByEventId("event-123");
    }

    @Test
    void getMemberByEventId_ShouldReturnEmptyList_WhenNoMembers() {
        // Given
        when(memberRepository.findAllMembersByEventId("event-123")).thenReturn(List.of());

        // When
        List<MemberResponseDTO> result = memberService.getMemberByEventId("event-123");

        // Then
        assertThat(result).isEmpty();
        verify(memberRepository).findAllMembersByEventId("event-123");
    }

    @Test
    void deleteMember_ShouldDeleteSuccessfully() {
        // Given
        Member ownerMember = Member.builder()
                .id("owner-123")
                .email("owner@example.com")
                .isOwner(true)
                .event(testEvent)
                .build();

        when(memberRepository.findMemberById("owner-123")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findMemberById("member-123")).thenReturn(Optional.of(testMember));

        // When
        memberService.deleteMember("member-123", "owner-123");

        // Then
        verify(memberRepository).findMemberById("member-123");
        verify(memberRepository).delete(testMember);
    }

    @Test
    void deleteMember_ShouldThrowException_WhenMemberNotFound() {
        // Given
        when(memberRepository.findMemberById(anyString())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> memberService.deleteMember("member-123", "user@example.com"))
                .isInstanceOf(RuntimeException.class);

        verify(memberRepository, never()).delete(any());
    }
}
