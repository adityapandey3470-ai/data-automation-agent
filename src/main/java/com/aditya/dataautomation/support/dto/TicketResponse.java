package com.aditya.dataautomation.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private String ticketCode;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
