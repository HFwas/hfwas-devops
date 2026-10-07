package com.hfwas.devops.container.service.helm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SemVer ordering for chart versions. Build metadata ({@code +}) is rejected by the archive inspector
 * because OCI tags cannot carry it.
 */
final class HelmVersions {

    static final Comparator<String> NEWER_FIRST = (left, right) -> compare(right, left);

    private HelmVersions() {
    }

    static boolean isSemVer(String version) {
        return parse(version) != null;
    }

    static int compare(String left, String right) {
        Sem leftSem = parse(left);
        Sem rightSem = parse(right);
        if (leftSem != null && rightSem != null) {
            return leftSem.compareTo(rightSem);
        }
        if (leftSem != null) {
            return 1;
        }
        if (rightSem != null) {
            return -1;
        }
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private static Sem parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.indexOf('+') >= 0) {
            return null;
        }
        String core = raw;
        String pre = "";
        int dash = raw.indexOf('-');
        if (dash == 0) {
            return null;
        }
        if (dash > 0) {
            core = raw.substring(0, dash);
            pre = raw.substring(dash + 1);
            if (pre.isEmpty()) {
                return null;
            }
        }
        String[] parts = core.split("\\.", -1);
        if (parts.length != 3) {
            return null;
        }
        int[] nums = new int[3];
        for (int i = 0; i < 3; i++) {
            Integer value = numeric(parts[i]);
            if (value == null) {
                return null;
            }
            nums[i] = value;
        }
        List<String> preIds = new ArrayList<>();
        if (!pre.isEmpty()) {
            for (String id : pre.split("\\.", -1)) {
                if (!validPreId(id)) {
                    return null;
                }
                preIds.add(id);
            }
        }
        return new Sem(nums[0], nums[1], nums[2], preIds);
    }

    private static boolean validPreId(String id) {
        if (id.isEmpty() || !id.matches("[0-9A-Za-z-]+")) {
            return false;
        }
        if (id.chars().allMatch(Character::isDigit)) {
            return numeric(id) != null;
        }
        return true;
    }

    /** Rejects leading zeros so {@code 01} and {@code 1} stay distinct and invalid. */
    private static Integer numeric(String text) {
        if (text.isEmpty() || text.length() > 9 || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        if (text.length() > 1 && text.charAt(0) == '0') {
            return null;
        }
        return Integer.parseInt(text);
    }

    private record Sem(int major, int minor, int patch, List<String> pre) implements Comparable<Sem> {
        @Override
        public int compareTo(Sem other) {
            int c = Integer.compare(major, other.major);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(minor, other.minor);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(patch, other.patch);
            if (c != 0) {
                return c;
            }
            if (pre.isEmpty() && other.pre.isEmpty()) {
                return 0;
            }
            if (pre.isEmpty()) {
                return 1;
            }
            if (other.pre.isEmpty()) {
                return -1;
            }
            int n = Math.min(pre.size(), other.pre.size());
            for (int i = 0; i < n; i++) {
                c = compareId(pre.get(i), other.pre.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(pre.size(), other.pre.size());
        }

        private static int compareId(String left, String right) {
            boolean leftNum = left.chars().allMatch(Character::isDigit);
            boolean rightNum = right.chars().allMatch(Character::isDigit);
            if (leftNum && rightNum) {
                return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
            }
            if (leftNum) {
                return -1;
            }
            if (rightNum) {
                return 1;
            }
            return left.compareTo(right);
        }
    }
}
