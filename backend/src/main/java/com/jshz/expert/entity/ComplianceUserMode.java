package com.jshz.expert.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("compliance_user_mode")
public class ComplianceUserMode {
    @TableId(type=IdType.INPUT) private Long userId;
    private String mode;
    private String legacySnapshot;
    private LocalDateTime switchedAt;
    private LocalDateTime updatedAt;
}
