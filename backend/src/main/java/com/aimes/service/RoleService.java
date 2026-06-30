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

    private final SysRolePermissionMapper sysRolePermissionMapper;

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        result.add(getRoleData("admin", "管理员"));
        result.add(getRoleData("supervisor", "车间主管"));
        result.add(getRoleData("worker", "普通员工"));
        
        return result;
    }

    private Map<String, Object> getRoleData(String roleKey, String roleName) {
        List<String> permissions = sysRolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleKey, roleKey)
        ).stream().map(SysRolePermission::getPermission).toList();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", roleKey);
        map.put("roleName", roleName);
        map.put("permissions", permissions);
        return map;
    }

    @Transactional
    public void updatePermissions(String roleKey, List<String> permissions) {
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
