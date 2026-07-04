package com.aimes.service;

import cn.dev33.satoken.stp.StpUtil;
import com.aimes.dto.Requests.LoginRequest;
import com.aimes.entity.SysOperationLog;
import com.aimes.entity.SysUser;
import com.aimes.mapper.SysOperationLogMapper;
import com.aimes.mapper.SysUserMapper;
import com.aimes.util.ClientIpUtil;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysOperationLogService {

    private final SysOperationLogMapper sysOperationLogMapper;
    private final SysUserMapper sysUserMapper;

    @Builder
    public record LogContext(
            String module,
            String action,
            String methodName,
            Object[] args,
            Object result,
            boolean success,
            String errorMsg
    ) {
    }

    public void record(LogContext context) {
        SysOperationLog log = new SysOperationLog();
        log.setModule(context.module());
        log.setAction(context.action());
        log.setIpAddress(ClientIpUtil.current());
        log.setSuccess(context.success() ? 1 : 0);
        log.setErrorMsg(context.errorMsg());
        log.setCreateTime(LocalDateTime.now());

        resolveOperator(log, context);
        resolveTarget(log, context);
        sysOperationLogMapper.insert(log);
    }

    private void resolveOperator(SysOperationLog log, LogContext context) {
        try {
            if (StpUtil.isLogin()) {
                SysUser user = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
                if (user != null) {
                    log.setUserId(user.getId());
                    log.setUsername(user.getUsername());
                    log.setRealName(user.getRealName());
                }
            }
        } catch (Exception ex) {
            if ("登录".equals(log.getAction())) {
                Object[] args = context.args();
                if (args.length > 0 && args[0] instanceof LoginRequest login) {
                    log.setUsername(login.getUsername());
                }
                if (context.result() instanceof Map<?, ?> payload) {
                    Object id = payload.get("id");
                    if (id instanceof Number number) {
                        log.setUserId(number.longValue());
                    }
                    Object realName = payload.get("realName");
                    if (realName != null) {
                        log.setRealName(realName.toString());
                    }
                }
            }
        }
    }

    private void resolveTarget(SysOperationLog log, LogContext context) {
        String methodName = context.methodName();
        Object[] args = context.args();
        Object result = context.result();

        if (args.length > 0 && args[0] instanceof Long id) {
            log.setTargetId(String.valueOf(id));
        }

        switch (methodName) {
            case "login" -> {
                log.setTargetType("user");
                log.setDescription("用户登录");
            }
            case "release" -> {
                log.setTargetType("plan");
                log.setDescription("下发计划 ID=" + log.getTargetId());
            }
            case "assign" -> {
                log.setTargetType("work_order");
                log.setDescription("派工工单 ID=" + log.getTargetId());
            }
            case "approveRoute" -> {
                log.setTargetType("routing");
                log.setDescription("审批通过工艺 ID=" + log.getTargetId());
            }
            case "rejectRoute" -> {
                log.setTargetType("routing");
                String reason = args.length > 1 && args[1] != null ? args[1].toString() : "";
                log.setDescription("驳回工艺 ID=" + log.getTargetId()
                        + (StringUtils.hasText(reason) ? "，原因：" + reason : ""));
            }
            case "delete", "deleteRoute" -> log.setDescription(log.getModule() + "删除 ID=" + log.getTargetId());
            default -> log.setDescription(log.getModule() + " " + log.getAction());
        }

        if (result instanceof Map<?, ?> map) {
            enrichFromResult(log, map, methodName);
        }
    }

    private void enrichFromResult(SysOperationLog log, Map<?, ?> map, String methodName) {
        if ("assign".equals(methodName) && map.get("orderNo") != null) {
            log.setDescription("派工工单 " + map.get("orderNo"));
            return;
        }
        if ("release".equals(methodName) && map.get("plan") instanceof Map<?, ?> plan && plan.get("planNo") != null) {
            log.setDescription("下发计划 " + plan.get("planNo"));
            return;
        }
        if (("approveRoute".equals(methodName) || "rejectRoute".equals(methodName)) && map.get("routeName") != null) {
            String prefix = "rejectRoute".equals(methodName) ? "驳回工艺 " : "审批通过工艺 ";
            log.setDescription(prefix + map.get("routeName"));
        }
    }
}
