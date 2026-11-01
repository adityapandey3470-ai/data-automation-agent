package com.aditya.dataautomation.ai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalystRequest {

    private String sheetName;

    @NotBlank(message = "Question must not be empty")
    @Size(max = 500, message = "Question cannot exceed 500 characters")
    private String question;
}
