package com.aimes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class Requests {

    private Requests() {
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String captchaId;
        private String captchaAnswer;
    }

    @Data
    public static class PlanSaveRequest {
        private String planNo;
        @NotBlank(message = "产品名称不能为空")
        private String productName;
        @NotNull(message = "计划数量不能为空")
        @Min(value = 1, message = "计划数量必须大于 0")
        private Integer planQty;
        @NotNull(message = "计划日期不能为空")
        private LocalDate planDate;
        private String status;
        private String remark;
    }

    @Data
    public static class WorkOrderCreateRequest {
        private Long planId;
        @NotBlank(message = "产品名称不能为空")
        private String productName;
        private String orderNo;
        private Long teamId;
        private String processName;
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        private Integer progress;
        private String status;
        @Min(value = 1, message = "优先级最小为 1")
        @Max(value = 3, message = "优先级最大为 3")
        private Integer priority;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime deadline;
        private String remark;
    }

    @Data
    public static class WorkOrderUpdateRequest {
        private Long planId;
        private String productName;
        private Long teamId;
        private String processName;
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        private Integer progress;
        private String status;
        @Min(value = 1, message = "优先级最小为 1")
        @Max(value = 3, message = "优先级最大为 3")
        private Integer priority;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime deadline;
        private String remark;
    }

    @Data
    public static class WorkOrderAssignRequest {
        @NotNull(message = "班组不能为空")
        private Long teamId;
        @NotNull(message = "优先级不能为空")
        @Min(value = 1, message = "优先级最小为 1")
        @Max(value = 3, message = "优先级最大为 3")
        private Integer priority;
        private String remark;
    }

    @Data
    public static class WorkOrderProgressRequest {
        @NotNull(message = "进度不能为空")
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        private Integer progress;
        @NotBlank(message = "当前工序不能为空")
        private String processName;
        private Boolean completeCurrentProcess;
        private String remark;
    }

    @Data
    public static class ExceptionCreateRequest {
        @NotBlank(message = "异常类型不能为空")
        private String eventType;
        @NotNull(message = "关联工单不能为空")
        private Long workOrderId;
        private Long deviceId;
        @NotBlank(message = "异常描述不能为空")
        private String description;
        @NotNull(message = "发生时间不能为空")
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime occurTime;
    }

    @Data
    public static class ExceptionHandleRequest {
        @NotBlank(message = "处理措施不能为空")
        private String handleAction;
        @NotBlank(message = "处理结果不能为空")
        private String handleResult;
    }

    @Data
    public static class MaterialCreateRequest {
        private String materialCode;
        @NotBlank(message = "物料名称不能为空")
        private String materialName;
        @Min(value = 0, message = "当前库存不能小于 0")
        private Double stockQty;
        @NotNull(message = "安全库存不能为空")
        @Min(value = 0, message = "安全库存不能小于 0")
        private Double safetyStock;
        private String unit;
        private String remark;
    }

    @Data
    public static class MaterialUpdateRequest {
        private Double stockQty;
        private Double inboundQty;
        private String remark;
    }

    @Data
    public static class TeamSaveRequest {
        private String teamCode;
        @NotBlank(message = "班组名称不能为空")
        private String teamName;
        private Long leaderId;
        @Min(value = 0, message = "成员数不能小于 0")
        private Integer memberCount;
        private String lineName;
    }

    @Data
    public static class CozeChatRequest {
        @NotBlank(message = "消息不能为空")
        private String message;
        private String sessionId;
    }

    @Data
    public static class ProfileUpdateRequest {
        @NotBlank(message = "姓名不能为空")
        private String realName;
        private String avatar;
    }

    @Data
    public static class PasswordChangeRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }

    @Data
    public static class CozeSchedulingRequest {
        private LocalDate planDate;
        @NotEmpty(message = "工单列表不能为空")
        private List<Long> workOrderIds;
    }

    @Data
    public static class SchedulingDispatchItem {
        private String workOrderCode;
        private String workOrderNo;
        private String teamName;
        private String startTime;
        private String hours;
    }

    @Data
    public static class SchedulingApplyRequest {
        private LocalDate planDate;
        @NotEmpty(message = "派工建议不能为空")
        private List<SchedulingDispatchItem> dispatches;
    }

    @Data
    public static class UserSaveRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        private String password;
        @NotBlank(message = "姓名不能为空")
        private String realName;
        @NotBlank(message = "角色不能为空")
        private String role;
        private Long teamId;
        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度为 6-64 位")
        private String password;
    }

    @Data
    public static class CozeConfigSaveRequest {
        private String apiToken;
        @NotBlank(message = "Bot ID 不能为空")
        private String botId;
        @NotBlank(message = "API 地址不能为空")
        private String apiUrl;
        private String workflowId;
        private String welcomeMessage;
        @NotNull(message = "启用状态不能为空")
        private Boolean enabled;
    }
}
