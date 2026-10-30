package com.aditya.dataautomation.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFileDto {

    private String fileId;
    private String fileName;
    private Long fileSize;
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private Integer rowCount;
    private Integer columnCount;
    private LocalDateTime uploadedAt;
    private String source; // "PROJECT" or "WORKING_SESSION"
}
