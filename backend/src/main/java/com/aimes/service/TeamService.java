package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.TeamSaveRequest;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final ProdTeamMapper prodTeamMapper;
    private final SysUserMapper sysUserMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;

    public List<Map<String, Object>> list() {
        return prodTeamMapper.selectList(new LambdaQueryWrapper<ProdTeam>().orderByAsc(ProdTeam::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    public Map<String, Object> detail(Long id) {
        ProdTeam team = getTeam(id);
        Map<String, Object> detail = new LinkedHashMap<>(toView(team));
        List<SysUser> members = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTeamId, id)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        List<Map<String, Object>> tasks = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                        .eq(ProdWorkOrder::getTeamId, id)
                        .orderByAsc(ProdWorkOrder::getPriority)
                        .orderByDesc(ProdWorkOrder::getDeadline))
                .stream()
                .map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("workOrderId", order.getId());
                    row.put("orderNo", order.getOrderNo());
                    row.put("productName", order.getProductName());
                    row.put("processName", order.getProcessName());
                    row.put("status", order.getStatus());
                    row.put("progress", order.getProgress() == null ? 0 : order.getProgress());
                    return row;
                })
                .toList();

        detail.put("members", members.stream().map(member -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", member.getId());
            row.put("username", member.getUsername());
            row.put("realName", member.getRealName());
            row.put("role", member.getRole());
            row.put("status", member.getStatus());
            return row;
        }).toList());
        detail.put("tasks", tasks);
        detail.put("taskSummary", Map.of(
                "pending", tasks.stream().filter(task -> "assigned".equals(task.get("status")) || "pending".equals(task.get("status"))).count(),
                "producing", tasks.stream().filter(task -> "producing".equals(task.get("status"))).count(),
                "done", tasks.stream().filter(task -> "done".equals(task.get("status"))).count()
        ));
        return detail;
    }

    @Transactional
    public Map<String, Object> create(TeamSaveRequest request) {
        ProdTeam team = new ProdTeam();
        team.setTeamCode(StringUtils.hasText(request.getTeamCode()) ? request.getTeamCode() : nextTeamCode());
        team.setTeamName(request.getTeamName());
        team.setLeaderId(request.getLeaderId());
        team.setMemberCount(request.getMemberCount() == null ? 0 : request.getMemberCount());
        team.setLineName(request.getLineName());
        team.setCreatedTime(LocalDateTime.now());
        prodTeamMapper.insert(team);
        return detail(team.getId());
    }

    @Transactional
    public Map<String, Object> update(Long id, TeamSaveRequest request) {
        ProdTeam team = getTeam(id);
        team.setTeamName(request.getTeamName());
        team.setLeaderId(request.getLeaderId());
        if (request.getMemberCount() != null) {
            team.setMemberCount(request.getMemberCount());
        }
        if (request.getLineName() != null) {
            team.setLineName(request.getLineName());
        }
        prodTeamMapper.updateById(team);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        long users = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getTeamId, id));
        if (users > 0) {
            throw new BusinessException("班组下仍有成员，不能删除");
        }
        long orders = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>().eq(ProdWorkOrder::getTeamId, id));
        if (orders > 0) {
            throw new BusinessException("班组已关联工单，不能删除");
        }
        prodTeamMapper.deleteById(id);
    }

    private ProdTeam getTeam(Long id) {
        ProdTeam team = prodTeamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException("班组不存在");
        }
        return team;
    }

    private Map<String, Object> toView(ProdTeam team) {
        SysUser leader = team.getLeaderId() == null ? null : sysUserMapper.selectById(team.getLeaderId());
        long activeOrders = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getTeamId, team.getId())
                .in(ProdWorkOrder::getStatus, List.of("assigned", "producing", "exception")));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", team.getId());
        row.put("teamCode", team.getTeamCode());
        row.put("teamName", team.getTeamName());
        row.put("leaderId", team.getLeaderId());
        row.put("leaderName", leader == null ? null : leader.getRealName());
        row.put("memberCount", team.getMemberCount());
        row.put("lineName", team.getLineName());
        row.put("activeOrderCount", activeOrders);
        row.put("createdTime", team.getCreatedTime());
        row.put("updatedTime", team.getUpdatedTime());
        return row;
    }

    private String nextTeamCode() {
        long count = prodTeamMapper.selectCount(null) + 1;
        return "T-" + String.format("%03d", count);
    }
}
