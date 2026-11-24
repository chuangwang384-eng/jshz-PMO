package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.CustomerEmploymentForm;
import com.jshz.expert.entity.CustomerEmployment;
import com.jshz.expert.mapper.CustomerEmploymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerEmploymentService {
    private static final int TEXT_MAX_LENGTH = 255;
    private static final Pattern MONTH = Pattern.compile("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])");

    private final CustomerService customerService;
    private final CustomerEmploymentMapper employmentMapper;

    public List<CustomerEmployment> list(Long customerId) {
        customerService.getVisible(customerId);
        return employmentMapper.selectList(new LambdaQueryWrapper<CustomerEmployment>()
                .eq(CustomerEmployment::getCustomerId, customerId)
                .orderByDesc(CustomerEmployment::getStartMonth, CustomerEmployment::getId));
    }

    public CustomerEmployment save(Long customerId, Long employmentId, CustomerEmploymentForm form) {
        customerService.getVisible(customerId);
        String start = normalizeMonth(form.startMonth(), "开始月份");
        String end = normalizeMonth(form.endMonth(), "结束月份");
        if (start != null && end != null && start.compareTo(end) > 0) {
            throw new BusinessException("结束月份不能早于开始月份");
        }
        CustomerEmployment entry = new CustomerEmployment();
        entry.setId(employmentId);
        entry.setCustomerId(customerId);
        entry.setWorkUnit(requireText(form.workUnit(), "单位"));
        entry.setPosition(requireText(form.position(), "职务"));
        entry.setStartMonth(start);
        entry.setEndMonth(end);
        if (employmentId == null) {
            employmentMapper.insert(entry);
        } else if (employmentMapper.update(entry, entryScope(customerId, employmentId)) == 0) {
            throw new BusinessException("任职记录不存在");
        }
        return entry;
    }

    public void delete(Long customerId, Long employmentId) {
        customerService.getVisible(customerId);
        if (employmentMapper.delete(entryScope(customerId, employmentId)) == 0) {
            throw new BusinessException("任职记录不存在");
        }
    }

    private LambdaQueryWrapper<CustomerEmployment> entryScope(Long customerId, Long employmentId) {
        return new LambdaQueryWrapper<CustomerEmployment>()
                .eq(CustomerEmployment::getCustomerId, customerId)
                .eq(CustomerEmployment::getId, employmentId);
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value) || value.trim().length() > TEXT_MAX_LENGTH) {
            throw new BusinessException(field + "不能为空且不得超过 " + TEXT_MAX_LENGTH + " 字");
        }
        return value.trim();
    }

    private String normalizeMonth(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String month = value.trim();
        if (!MONTH.matcher(month).matches()) {
            throw new BusinessException(field + "格式应为 YYYY-MM");
        }
        return month;
    }
}
