package com.studenthub.modules.identity.mapper;

import lombok.Data;

@Data
public class WorkerCandidateRow {

    private Long userId;
    private String username;
    private String displayName;
    private String phone;
}
