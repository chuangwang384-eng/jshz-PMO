package com.jshz.expert.service;

import com.jshz.expert.entity.ProjectSpeaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 一对一匹配出场，保留多场出场和同场多角色。 */
final class ScheduleSpeakerDiff {
    record Change(ProjectSpeaker before, ProjectSpeaker after) {}
    record Result(List<ProjectSpeaker> additions, List<Change> changes) {}

    static Result compare(List<ProjectSpeaker> saved, List<ProjectSpeaker> parsed) {
        var previous = new ArrayList<>(saved.stream().filter(row -> row.getScheduleId() != null).toList());
        var additions = new ArrayList<ProjectSpeaker>();
        for (ProjectSpeaker row : parsed) {
            ProjectSpeaker exact = previous.stream().filter(old -> sameAppearance(old, row)).findFirst().orElse(null);
            if (exact == null) additions.add(row);
            else previous.remove(exact);
        }

        var changes = new ArrayList<Change>();
        var unpaired = new ArrayList<ProjectSpeaker>();
        for (ProjectSpeaker row : additions) {
            ProjectSpeaker match = uniqueBest(row, previous);
            if (match != null && uniqueBest(match, additions) == row) {
                changes.add(new Change(match, row));
            } else {
                unpaired.add(row);
            }
        }
        previous.removeAll(changes.stream().map(Change::before).toList());
        previous.forEach(row -> changes.add(new Change(row, null)));
        return new Result(unpaired, changes);
    }

    static boolean sameAppearance(ProjectSpeaker left, ProjectSpeaker right) {
        return samePerson(left, right) && Objects.equals(left.getRole(), right.getRole())
                && time(left).equals(time(right)) && title(left).equals(title(right));
    }

    private static ProjectSpeaker uniqueBest(ProjectSpeaker row, List<ProjectSpeaker> options) {
        ProjectSpeaker best = null;
        int highest = 0;
        for (ProjectSpeaker option : options) {
            int score = samePerson(row, option) ? slotScore(row, option) : 0;
            if (score > highest) {
                best = option;
                highest = score;
            } else if (score == highest) {
                best = null;
            }
        }
        return best;
    }

    private static int slotScore(ProjectSpeaker left, ProjectSpeaker right) {
        String time = time(left);
        String title = title(left);
        int score = !time.isEmpty() && time.equals(time(right)) ? 1 : 0;
        if (!title.isEmpty() && title.equals(title(right))) score++;
        if (time.isEmpty() && title.isEmpty() && time(right).isEmpty() && title(right).isEmpty()) return 1;
        return score;
    }

    private static boolean samePerson(ProjectSpeaker left, ProjectSpeaker right) {
        if (left.getExpertId() != null && right.getExpertId() != null) {
            return left.getExpertId().equals(right.getExpertId());
        }
        return compact(left.getExpertName()).equals(compact(right.getExpertName()));
    }

    private static String title(ProjectSpeaker row) {
        return compact(row.getSegmentTitle());
    }

    private static String time(ProjectSpeaker row) {
        return compact(row.getSegmentTime()).replace('：', ':').replaceAll("[—–~～至]", "-");
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{Z}]+", "");
    }
}
