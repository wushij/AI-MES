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
        private Long productId;
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
        private Long productId;
        @NotBlank(message = "产品名称不能为空")
        private String productName;
        @Min(value = 1, message = "工单数量必须大于 0")
        private Integer orderQty;
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
        private Long productId;
        private String productName;
        @Min(value = 1, message = "工单数量必须大于 0")
        private Integer orderQty;
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
        private Long deviceId;
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
        private Boolean materialConstraint;
        private Boolean deviceConstraint;
        private Boolean teamConstraint;
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
    public static class SchedulingPriorityItem {
        private Integer rank;
        private String workOrderCode;
        private String workOrderNo;
        private Integer priority;
        private String priorityLabel;
        private String reason;
    }

    @Data
    public static class SchedulingBottleneckItem {
        private String processName;
        private Integer loadRate;
        private String suggestion;
    }

    @Data
    public static class SchedulingApplyRequest {
        private LocalDate planDate;
        private String summary;
        private List<SchedulingPriorityItem> priorities;
        private List<SchedulingBottleneckItem> bottlenecks;
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

    @Data
    public static class DeviceSaveRequest {
        private String deviceCode;
        @NotBlank(message = "设备名称不能为空")
        private String deviceName;
        private Long categoryId;
        private String deviceType;
        private String brand;
        private String model;
        private String serialNumber;
        private String workshop;
        private String lineName;
        private String station;
        private Long managerId;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate purchaseDate;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate installDate;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate enableDate;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate warrantyDate;
        private String status;
        private Long teamId;
        private String remark;
    }

    @Data
    public static class DeviceStatusRequest {
        @NotBlank(message = "设备状态不能为空")
        private String status;
        private String remark;
    }

    @Data
    public static class DeviceCategorySaveRequest {
        private Long parentId;
        @NotBlank(message = "分类名称不能为空")
        private String categoryName;
        private Integer sortNo;
    }

    @Data
    public static class ProcessRouteSaveRequest {
        private String routeCode;
        @NotBlank(message = "路线名称不能为空")
        private String routeName;
        private Long productId;
        private String productName;
        private String remark;
        /** draft | submit | publish */
        private String saveMode;
        /** 可选；为空时仅保存路线元数据（草稿），不更新工序 */
        private List<ProcessOperationItem> operations;
    }

    @Data
    public static class ProcessOperationItem {
        private Long id;
        @NotNull(message = "工序序号不能为空")
        private Integer seqNo;
        private String operationCode;
        @NotBlank(message = "工序名称不能为空")
        private String operationName;
        private Double standardHours;
        private Double prepHours;
        private Double changeoverHours;
        private Integer needReport;
        private Integer needCheck;
        private Integer needScan;
        private String remark;
        private List<ProcessParameterItem> parameters;
        private List<Long> deviceIds;
        private List<Long> categoryIds;
        private List<ProcessMaterialItem> materials;
    }

    @Data
    public static class ProcessMaterialItem {
        private Long materialId;
        private java.math.BigDecimal qty;
        private String unit;
        private String materialType;
        private String remark;
    }

    @Data
    public static class ProcessRouteRejectRequest {
        @NotBlank(message = "驳回原因不能为空")
        private String reason;
    }

    @Data
    public static class ProcessParameterItem {
        @NotBlank(message = "参数名称不能为空")
        private String paramName;
        private String paramValue;
        private String minValue;
        private String maxValue;
        private String unit;
    }

    @Data
    public static class ProductSaveRequest {
        private String productCode;
        @NotBlank(message = "产品名称不能为空")
        private String productName;
        private String spec;
        private String unit;
        private String status;
        private String remark;
    }

    @Data
    public static class BomSaveRequest {
        private String version;
        private String remark;
        private List<BomItemRequest> items;

        @Data
        public static class BomItemRequest {
            private Long materialId;
            private Double qty;
            private String unit;
            private Double lossRate;
            private String remark;
        }
    }

    @Data
    public static class InspectionSubmitRequest {
        @NotNull(message = "工单ID不能为空")
        private Long workOrderId;
        @NotBlank(message = "工序名称不能为空")
        private String processName;
        @NotEmpty(message = "检验项不能为空")
        private List<InspectionItemRequest> items;

        @Data
        public static class InspectionItemRequest {
            private Long planId;
            @NotBlank(message = "检验项名称不能为空")
            private String itemName;
            private String measuredValue;
            private String result;
            private String remark;
        }
    }
}
