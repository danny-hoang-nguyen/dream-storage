package com.flownetworks.bot.dto;

public record JiraIssue(
    String key,           // VD: PROJ-123
    String summary,       // Tiêu đề ticket
    String status,        // To Do / In Progress / Done
    String priority,      // High / Medium / Low
    String description,   // Mô tả issue (đã strip markdown)
    String url            // Link tới ticket
) {}
