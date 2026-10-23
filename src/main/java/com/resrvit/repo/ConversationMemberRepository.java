package com.resrvit.repo;

import com.resrvit.domain.Conversation;
import com.resrvit.domain.ConversationMember;
import com.resrvit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
    Optional<ConversationMember> findByConversationAndUser(Conversation c, User u);
    List<ConversationMember> findByConversation(Conversation c);
    List<ConversationMember> findByUser(User u);
}
