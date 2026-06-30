package com.aimes.service;

import cn.dev33.satoken.stp.StpUtil;
import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.LoginRequest;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.SysUserMapper;
import com.aimes.security.CaptchaService;
import com.aimes.security.LoginProtectionService;
import com.aimes.util.ClientIpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final LoginProtectionService loginProtectionService;

    public Map<String, Object> login(LoginRequest request) {
        String ip = ClientIpUtil.current();
        String username = request.getUsername().trim();
        loginProtectionService.checkAllowed(ip, username);
        if (loginProtectionService.captchaRequired(ip)) {
            captchaService.verify(request.getCaptchaId(), request.getCaptchaAnswer());
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginProtectionService.recordFailure(ip, username);
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        loginProtectionService.clearOnSuccess(ip, username);
        StpUtil.login(user.getId());
        StpUtil.getSession().set("role", user.getRole());
        return buildAuthPayload(user);
    }

    public void logout() {
        StpUtil.logout();
    }

    public Map<String, Object> info() {
        return buildAuthPayload(currentUser());
    }

    public Map<String, Object> captcha() {
        return captchaService.create();
    }

    public Map<String, Object> captchaRequired() {
        return Map.of("required", loginProtectionService.captchaRequired(ClientIpUtil.current()));
    }

    public SysUser currentUser() {
        SysUser user = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException(401, "用户不存在或登录已失效");
        }
        return user;
    }

    private Map<String, Object> buildAuthPayload(SysUser user) {
        ProdTeam team = user.getTeamId() == null ? null : prodTeamMapper.selectById(user.getTeamId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("tokenType", "Bearer");
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("avatar", user.getAvatar());
        data.put("role", user.getRole());
        data.put("teamId", user.getTeamId());
        data.put("teamName", team == null ? null : team.getTeamName());
        data.put("status", user.getStatus());
        return data;
    }

    public Map<String, Object> updateProfile(com.aimes.dto.Requests.ProfileUpdateRequest request) {
        SysUser user = currentUser();
        user.setRealName(request.getRealName());
        user.setAvatar(request.getAvatar());
        sysUserMapper.updateById(user);
        return buildAuthPayload(user);
    }

    public void changePassword(com.aimes.dto.Requests.PasswordChangeRequest request) {
        SysUser user = currentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(user);
    }
}
