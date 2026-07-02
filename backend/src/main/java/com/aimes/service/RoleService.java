package com.aimes.service;

import com.aimes.entity.SysRolePermission;
import com.aimes.mapper.SysRolePermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String ADMIN_ROLE_KEY = "admin";

    private static final List<String> ALL_PERMISSIONS = List.of(
            "生产计划", "工单管理", "班组", "物料", "排产",
            "工序进度", "工单反馈", "异常上报", "AI 客服",
            "用户管理", "角色管理", "Coze 配置", "系统配置"
    );

    private static final List<String> DEFAULT_SUPERVISOR_PERMISSIONS = List.of(
            "生产计划", "工单管理", "班组", "物料", "排产", "异常上报", "AI 客服"
    );

    private static final List<String> DEFAULT_WORKER_PERMISSIONS = List.of(
            "工序进度", "工单反馈", "异常上报", "AI 客服"
    );

    private final SysRolePermissionMapper sysRolePermissionMapper;

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        result.add(getRoleData("admin", "管理员"));
        result.add(getRoleData("supervisor", "车间主管"));
        result.add(getRoleData("worker", "普通员工"));
        
        return result;
    }

    public List<String> getPermissionsByRoleKey(String roleKey) {
        if (ADMIN_ROLE_KEY.equals(roleKey)) {
            return ALL_PERMISSIONS;
        }
        List<String> permissions = sysRolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleKey, roleKey)
        ).stream().map(SysRolePermission::getPermission).toList();
        if (!permissions.isEmpty()) {
            return permissions;
        }
        return switch (roleKey) {
            case "supervisor" -> DEFAULT_SUPERVISOR_PERMISSIONS;
            case "worker" -> DEFAULT_WORKER_PERMISSIONS;
            default -> List.of();
        };
    }

    public boolean hasFullAccess(String roleKey) {
        return ADMIN_ROLE_KEY.equals(roleKey);
    }

    private Map<String, Object> getRoleData(String roleKey, String roleName) {
        boolean fullAccess = hasFullAccess(roleKey);
        List<String> permissions = getPermissionsByRoleKey(roleKey);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", roleKey);
        map.put("roleName", roleName);
        map.put("permissions", permissions);
        map.put("fullAccess", fullAccess);
        return map;
    }

    @Transactional
    public void updatePermissions(String roleKey, List<String> permissions) {
        if (ADMIN_ROLE_KEY.equals(roleKey)) {
            permissions = ALL_PERMISSIONS;
        }
        sysRolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleKey, roleKey)
        );
        if (permissions != null) {
            for (String perm : permissions) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleKey(roleKey);
                rp.setPermission(perm);
                sysRolePermissionMapper.insert(rp);
            }
        }
    }
}

