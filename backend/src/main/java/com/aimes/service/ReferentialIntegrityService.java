package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.ProdPlan;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.ProdPlanMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReferentialIntegrityService {

    private final SysUserMapper sysUserMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final ProdPlanMapper prodPlanMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ExcEventMapper excEventMapper;

    public void ensureTeamDeletable(Long teamId) {
        requireTeam(teamId);
        long users = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getTeamId, teamId));
        if (users > 0) {
            throw new BusinessException("班组下仍有成员，不能删除");
        }
        long orders = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getTeamId, teamId));
        if (orders > 0) {
            throw new BusinessException("班组已关联工单，不能删除");
        }
    }

    public void ensurePlanDeletable(Long planId) {
        requirePlan(planId);
        long relatedOrders = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getPlanId, planId));
        if (relatedOrders > 0) {
            throw new BusinessException("计划已关联工单，不能删除");
        }
    }

    public void ensureUserDeletable(Long userId) {
        SysUser user = requireUser(userId);
        if ("admin".equals(user.getRole())) {
            long adminCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, "admin")
                    .eq(SysUser::getStatus, 1));
            if (adminCount <= 1) {
                throw new BusinessException("不能删除唯一的管理员账号");
            }
        }
        long createdPlans = prodPlanMapper.selectCount(new LambdaQueryWrapper<ProdPlan>()
                .eq(ProdPlan::getCreatedBy, userId));
        if (createdPlans > 0) {
            throw new BusinessException("该用户创建了生产计划，不能删除");
        }
        long reportedExceptions = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getReporterId, userId));
        if (reportedExceptions > 0) {
            throw new BusinessException("该用户上报过异常记录，不能删除");
        }
    }

    private ProdTeam requireTeam(Long teamId) {
        ProdTeam team = prodTeamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("班组不存在");
        }
        return team;
    }

    private ProdPlan requirePlan(Long planId) {
        ProdPlan plan = prodPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("生产计划不存在");
        }
        return plan;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
