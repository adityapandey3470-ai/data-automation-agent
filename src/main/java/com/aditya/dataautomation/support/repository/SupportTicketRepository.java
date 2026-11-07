package com.aditya.dataautomation.support.repository;

import com.aditya.dataautomation.support.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    List<SupportTicket> findTop200ByOrderByCreatedAtDesc();

    Optional<SupportTicket> findByTicketCode(String ticketCode);

    boolean existsByTicketCode(String ticketCode);
}
