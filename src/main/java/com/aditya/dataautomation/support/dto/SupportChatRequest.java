package com.aditya.dataautomation.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatRequest {

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;

    @Builder.Default
    private List<ChatMessage> history = new ArrayList<>();
}
