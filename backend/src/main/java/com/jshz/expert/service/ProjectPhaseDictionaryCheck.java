package com.jshz.expert.service;

import com.jshz.expert.common.ProjectPhaseNames;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.mapper.ProjectPhaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目阶段字典完整性校验（2026-11-26 P2-15）。
 *
 * <p>阶段名此前在多处硬编码，一旦管理员改了 {@code project_phase.name}，
 * 业务判定会静默失效（例如「会前财务资料上传」改名后，表单不再生成合规立项申请表，
 * 但不抛异常、不报错，表现为「保存成功但没有产出文件」）。
 *
 * <p>本组件在应用就绪时核对阶段字典是否仍包含 {@link ProjectPhaseNames#LIFECYCLE} 中的阶段名，
 * 缺失时打 ERROR 日志（不阻断启动——阶段字典允许被业务调整，但代码常量与字典必须同步）。
 */
@Component
public class ProjectPhaseDictionaryCheck {
    private static final Logger log = LoggerFactory.getLogger(ProjectPhaseDictionaryCheck.class);

    private final ProjectPhaseMapper phaseMapper;

    public ProjectPhaseDictionaryCheck(ProjectPhaseMapper phaseMapper) {
        this.phaseMapper = phaseMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyLifecyclePhasesExist() {
        List<ProjectPhase> phases;
        try {
            phases = phaseMapper.selectList(null);
        } catch (Exception e) {
            log.warn("阶段字典校验跳过：读取 project_phase 失败（{}）", e.getMessage());
            return;
        }
        Set<String> names = phases.stream()
                .map(ProjectPhase::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
        List<String> missing = ProjectPhaseNames.LIFECYCLE.stream()
                .filter(name -> !names.contains(name))
                .toList();
        if (missing.isEmpty()) {
            log.info("阶段字典校验通过：默认生命周期 {} 个阶段名均存在", ProjectPhaseNames.LIFECYCLE.size());
            return;
        }
        log.error("阶段字典与代码常量不一致：字典缺少阶段 {}，而代码按这些名称判定阶段归属。"
                + "若字典已被改名，请同步修改 ProjectPhaseNames 常量（否则对应阶段的业务会静默失效）。",
                missing);
    }
}
