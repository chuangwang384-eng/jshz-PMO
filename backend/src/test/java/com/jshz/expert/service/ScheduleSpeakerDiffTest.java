package com.jshz.expert.service;

import com.jshz.expert.entity.ProjectSpeaker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleSpeakerDiffTest {
    @Test
    void keepsExistingAppearanceAndSuggestsSecondAppearanceWithSameRoleAndTopic() {
        var saved = row(1L, "何振", "SPEAKER", "09:00—09:20", "病例分享");
        var same = row(null, "何 振", "SPEAKER", "09:00-09:20", "病例分享");
        var second = row(null, "何振", "SPEAKER", "10:00-10:20", "病例分享");

        var diff = ScheduleSpeakerDiff.compare(List.of(saved), List.of(same, second));
        assertEquals(List.of(second), diff.additions());
        assertEquals(List.of(), diff.changes());
    }

    @Test
    void pairsRoleChangeWithMatchingSlotAndKeepsAdditionalAppearance() {
        var saved = row(1L, "何振", "SPEAKER", "09:00", "病例分享");
        var host = row(null, "何振", "HOST", "09:00", "病例分享");
        var second = row(null, "何振", "SPEAKER", "10:00", "病例分享");
        var diff = ScheduleSpeakerDiff.compare(List.of(saved), List.of(host, second));
        assertEquals(List.of(second), diff.additions());
        assertEquals(List.of(new ScheduleSpeakerDiff.Change(saved, host)), diff.changes());
    }

    @Test
    void proposesRemovalPerScheduleAppearanceAndProtectsManualRecords() {
        var manual = row(null, "刘丽华", "HOST", "10:30", "总结");
        var kept = row(1L, "何振", "SPEAKER", "09:00", "开场");
        var removed = row(2L, "何振", "HOST", "10:00", "讨论");
        var missingPerson = row(2L, "郭三星", "SPEAKER", "10:05", "分享");
        var changed = row(null, "何振", "HOST", "11:00", "总结");

        var diff = ScheduleSpeakerDiff.compare(List.of(manual, kept, removed, missingPerson), List.of(kept, changed));
        assertEquals(List.of(changed), diff.additions());
        assertEquals(List.of(new ScheduleSpeakerDiff.Change(removed, null),
                new ScheduleSpeakerDiff.Change(missingPerson, null)), diff.changes());
    }

    @Test
    void doesNotPairDifferentLinkedExpertsWithSameName() {
        var saved = row(1L, "王军", "SPEAKER", "09:00", "专题报告");
        saved.setExpertId(1L);
        var different = row(null, "王军", "SPEAKER", "09:00", "专题报告");
        different.setExpertId(2L);
        var diff = ScheduleSpeakerDiff.compare(List.of(saved), List.of(different));
        assertEquals(List.of(different), diff.additions());
        assertEquals(List.of(new ScheduleSpeakerDiff.Change(saved, null)), diff.changes());
    }

    private ProjectSpeaker row(Long scheduleId, String name, String role, String time, String title) {
        var row = new ProjectSpeaker();
        row.setScheduleId(scheduleId);
        row.setExpertName(name);
        row.setRole(role);
        row.setSegmentTime(time);
        row.setSegmentTitle(title);
        return row;
    }
}
