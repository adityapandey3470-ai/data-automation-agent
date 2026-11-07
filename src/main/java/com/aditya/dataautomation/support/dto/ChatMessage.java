package com.aditya.dataautomation.support.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role;

    @Size(max = 2000, message = "Chat message too long")
    private String text;
}
