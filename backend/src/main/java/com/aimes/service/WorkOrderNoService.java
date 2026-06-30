package com.aimes.service;

import com.aimes.entity.ProdWorkOrder;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkOrderNoService {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("^WO-(\\d{4})-(\\d{3,})$");

    private final ProdWorkOrderMapper prodWorkOrderMapper;

    public String nextOrderNo() {
        int year = LocalDateTime.now().getYear();
        String prefix = "WO-" + year + "-";
        List<ProdWorkOrder> orders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                .likeRight(ProdWorkOrder::getOrderNo, prefix));

        int maxSeq = 0;
        for (ProdWorkOrder order : orders) {
            if (!StringUtils.hasText(order.getOrderNo())) {
                continue;
            }
            Matcher matcher = ORDER_NO_PATTERN.matcher(order.getOrderNo().trim());
            if (!matcher.matches()) {
                continue;
            }
            int orderYear = Integer.parseInt(matcher.group(1));
            if (orderYear != year) {
                continue;
            }
            maxSeq = Math.max(maxSeq, Integer.parseInt(matcher.group(2)));
        }
        return prefix + String.format("%03d", maxSeq + 1);
    }
}
