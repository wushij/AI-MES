package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.UserSaveRequest;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final SysUserMapper sysUserMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final PasswordEncoder passwordEncoder;

    public List<Map<String, Object>> list() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    public Map<String, Object> detail(Long id) {
        SysUser user = requireUser(id);
        return toView(user);
    }

    @Transactional
    public Map<String, Object> create(UserSaveRequest request) {
        ensureUniqueUsername(request.getUsername(), null);
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(StringUtils.hasText(request.getPassword()) ? request.getPassword() : "123456"));
        user.setRealName(request.getRealName());
        user.setRole(request.getRole());
        user.setTeamId(request.getTeamId());
        user.setStatus(request.getStatus());
        sysUserMapper.insert(user);
        syncTeamMemberCount(request.getTeamId());
        return toView(user);
    }

    @Transactional
    public Map<String, Object> update(Long id, UserSaveRequest request) {
        SysUser user = requireUser(id);
        ensureUniqueUsername(request.getUsername(), id);
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setRole(request.getRole());
        Long oldTeamId = user.getTeamId();
        user.setTeamId(request.getTeamId());
        user.setStatus(request.getStatus());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        sysUserMapper.updateById(user);
        syncTeamMemberCount(oldTeamId);
        syncTeamMemberCount(user.getTeamId());
        return toView(user);
    }

    @Transactional
    public Map<String, Object> resetPassword(Long id, String password) {
        SysUser user = requireUser(id);
        user.setPassword(passwordEncoder.encode(password));
        sysUserMapper.updateById(user);
        return Map.of("id", id);
    }

    @Transactional
    public Map<String, Object> toggleStatus(Long id) {
        SysUser user = requireUser(id);
        user.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        sysUserMapper.updateById(user);
        return toView(user);
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void ensureUniqueUsername(String username, Long ignoreId) {
        SysUser existed = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(ignoreId != null, SysUser::getId, ignoreId)
                .last("limit 1"));
        if (existed != null) {
            throw new BusinessException("用户名已存在");
        }
    }

    private void syncTeamMemberCount(Long teamId) {
        if (teamId == null) {
            return;
        }
        ProdTeam team = prodTeamMapper.selectById(teamId);
        if (team == null) {
            return;
        }
        long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTeamId, teamId)
                .eq(SysUser::getStatus, 1));
        team.setMemberCount((int) count);
        prodTeamMapper.updateById(team);
    }

    private Map<String, Object> toView(SysUser user) {
        ProdTeam team = user.getTeamId() == null ? null : prodTeamMapper.selectById(user.getTeamId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("realName", user.getRealName());
        row.put("role", user.getRole());
        row.put("teamId", user.getTeamId());
        row.put("teamName", team == null ? null : team.getTeamName());
        row.put("status", user.getStatus());
        row.put("createTime", user.getCreateTime());
        row.put("updateTime", user.getUpdateTime());
        return row;
    }
}
