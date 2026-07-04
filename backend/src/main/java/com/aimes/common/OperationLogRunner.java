package com.aimes.common;

import com.aimes.service.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * 操作审计执行器（与 {@link OperationLog} 注解配合，在 Service 方法内显式包裹写操作）。
 * 等价于 AOP 切面，避免强依赖 aspectjweaver。
 */
@Component
@RequiredArgsConstructor
public class OperationLogRunner {

    private final SysOperationLogService sysOperationLogService;

    public <T> T run(String module, String action, String methodName, Object[] args, Callable<T> callable) throws Exception {
        T result = null;
        boolean success = true;
        String errorMsg = null;
        try {
            result = callable.call();
            return result;
        } catch (Exception ex) {
            success = false;
            errorMsg = ex.getMessage();
            throw ex;
        } finally {
            sysOperationLogService.record(SysOperationLogService.LogContext.builder()
                    .module(module)
                    .action(action)
                    .methodName(methodName)
                    .args(args)
                    .result(result)
                    .success(success)
                    .errorMsg(errorMsg)
                    .build());
        }
    }

    public <T> T runUnchecked(String module, String action, String methodName, Object[] args, Callable<T> callable) {
        try {
            return run(module, action, methodName, args, callable);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(ex);
        }
    }

    public void runVoid(String module, String action, String methodName, Object[] args, Runnable runnable) {
        runUnchecked(module, action, methodName, args, () -> {
            runnable.run();
            return null;
        });
    }
}
