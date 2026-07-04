package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.DeviceCategorySaveRequest;
import com.aimes.entity.DevDevice;
import com.aimes.entity.DevDeviceCategory;
import com.aimes.mapper.DevDeviceCategoryMapper;
import com.aimes.mapper.DevDeviceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCategoryService {

    private final DevDeviceCategoryMapper categoryMapper;
    private final DevDeviceMapper devDeviceMapper;

    public List<Map<String, Object>> listTree() {
        List<DevDeviceCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<DevDeviceCategory>()
                .orderByAsc(DevDeviceCategory::getSortNo)
                .orderByAsc(DevDeviceCategory::getId));
        return buildTree(all, 0L);
    }

    public List<Map<String, Object>> listFlat() {
        return categoryMapper.selectList(new LambdaQueryWrapper<DevDeviceCategory>()
                        .orderByAsc(DevDeviceCategory::getSortNo)
                        .orderByAsc(DevDeviceCategory::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(DeviceCategorySaveRequest request) {
        DevDeviceCategory category = new DevDeviceCategory();
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setCategoryName(request.getCategoryName());
        category.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        category.setCreatedTime(LocalDateTime.now());
        category.setUpdatedTime(LocalDateTime.now());
        categoryMapper.insert(category);
        return toView(category);
    }

    @Transactional
    public Map<String, Object> update(Long id, DeviceCategorySaveRequest request) {
        DevDeviceCategory category = getCategory(id);
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BusinessException("分类不能设置自身为父节点");
            }
            category.setParentId(request.getParentId());
        }
        category.setCategoryName(request.getCategoryName());
        if (request.getSortNo() != null) {
            category.setSortNo(request.getSortNo());
        }
        category.setUpdatedTime(LocalDateTime.now());
        categoryMapper.updateById(category);
        return toView(category);
    }

    @Transactional
    public void delete(Long id) {
        getCategory(id);
        long children = categoryMapper.selectCount(new LambdaQueryWrapper<DevDeviceCategory>()
                .eq(DevDeviceCategory::getParentId, id));
        if (children > 0) {
            throw new BusinessException("请先删除子分类");
        }
        long devices = devDeviceMapper.selectCount(new LambdaQueryWrapper<DevDevice>()
                .eq(DevDevice::getCategoryId, id));
        if (devices > 0) {
            throw new BusinessException("分类下仍有设备，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    public DevDeviceCategory getCategory(Long id) {
        DevDeviceCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("设备分类不存在");
        }
        return category;
    }

    public String resolveCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        DevDeviceCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getCategoryName();
    }

    private List<Map<String, Object>> buildTree(List<DevDeviceCategory> all, Long parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DevDeviceCategory category : all) {
            Long pid = category.getParentId() == null ? 0L : category.getParentId();
            if (!pid.equals(parentId)) {
                continue;
            }
            Map<String, Object> node = toView(category);
            List<Map<String, Object>> children = buildTree(all, category.getId());
            if (!children.isEmpty()) {
                node.put("children", children);
            }
            result.add(node);
        }
        return result;
    }

    private Map<String, Object> toView(DevDeviceCategory category) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", category.getId());
        view.put("parentId", category.getParentId());
        view.put("categoryName", category.getCategoryName());
        view.put("sortNo", category.getSortNo());
        return view;
    }
}
