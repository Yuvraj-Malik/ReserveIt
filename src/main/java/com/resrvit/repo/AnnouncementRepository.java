package com.resrvit.repo;

import com.resrvit.domain.Announcement;
import com.resrvit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findTop50ByOrderByCreatedAtDesc();
    List<Announcement> findTop50BySenderOrderByCreatedAtDesc(User sender);
}
