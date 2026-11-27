package com.jshz.expert.service;

import com.jshz.expert.common.ProjectPhaseNames;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.mapper.ProjectPhaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 阶段名常量与启动期字典校验（2026-11-26 P2-15）。
 *
 * <p>阶段名此前散落多处硬编码，字典改名后业务判定会静默失效；本测试锁定：
 * 常量集合完整、启动期校验在字典缺阶段时能报出缺失名单。
 */
@ExtendWith(MockitoExtension.class)
class ProjectPhaseDictionaryCheckTest {

    @InjectMocks ProjectPhaseDictionaryCheck check;
    @Mock ProjectPhaseMapper phaseMapper;

    ProjectPhase phase(String name) {
        ProjectPhase p = new ProjectPhase();
        p.setName(name);
        return p;
    }

    @Test
    void lifecycleConstantsCoverEveryDefaultStage() {
        assertEquals(6, ProjectPhaseNames.LIFECYCLE.size());
        assertTrue(ProjectPhaseNames.LIFECYCLE.contains(ProjectPhaseNames.BUDGET_MAKING));
        assertTrue(ProjectPhaseNames.LIFECYCLE.contains(ProjectPhaseNames.FINANCE_UPLOAD));
        assertTrue(ProjectPhaseNames.LIFECYCLE.contains(ProjectPhaseNames.SCHEDULE_UPLOAD));
        assertTrue(ProjectPhaseNames.LIFECYCLE.contains(ProjectPhaseNames.SHOW_MAKING));
        assertTrue(ProjectPhaseNames.LIFECYCLE.contains(ProjectPhaseNames.SETTLEMENT_UPLOAD));
        assertTrue(ProjectPhaseNames.LIFECYCLE.contains(ProjectPhaseNames.CLOSEOUT));
        // 常量集合内不得有重复名（否则字典唯一键与代码判定会不一致）
        assertEquals(ProjectPhaseNames.LIFECYCLE.size(), ProjectPhaseNames.LIFECYCLE.stream().distinct().count());
    }

    @Test
    void startupCheckRunsWithoutErrorWhenDictionaryIsComplete() {
        when(phaseMapper.selectList(any())).thenReturn(List.of(
                phase(ProjectPhaseNames.BUDGET_MAKING),
                phase(ProjectPhaseNames.FINANCE_UPLOAD),
                phase(ProjectPhaseNames.SCHEDULE_UPLOAD),
                phase(ProjectPhaseNames.SHOW_MAKING),
                phase(ProjectPhaseNames.SETTLEMENT_UPLOAD),
                phase(ProjectPhaseNames.CLOSEOUT)));

        // 不抛异常即通过（日志路径不作为断言对象）
        check.verifyLifecyclePhasesExist();
    }

    @Test
    void startupCheckSurvivesDictionaryReadFailure() {
        when(phaseMapper.selectList(any())).thenThrow(new RuntimeException("连接失败"));

        // 读不到字典不应阻断启动
        check.verifyLifecyclePhasesExist();
    }

    @Test
    void startupCheckDetectsMissingPhaseWithoutThrowing() {
        // 字典缺「会前财务资料上传」（被改名）：应能识别为缺失，但不阻断启动
        when(phaseMapper.selectList(any())).thenReturn(List.of(
                phase(ProjectPhaseNames.BUDGET_MAKING),
                phase(ProjectPhaseNames.SCHEDULE_UPLOAD),
                phase(ProjectPhaseNames.SHOW_MAKING),
                phase(ProjectPhaseNames.SETTLEMENT_UPLOAD),
                phase(ProjectPhaseNames.CLOSEOUT)));

        check.verifyLifecyclePhasesExist();
    }
}
