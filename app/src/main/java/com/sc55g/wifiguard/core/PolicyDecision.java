package com.sc55g.wifiguard.core;

public final class PolicyDecision {
    public enum Action { NONE, KEEP_TEMPORARY, END_TEMPORARY_AND_CONNECT_TARGET, CLEAN_AND_CONNECT_TARGET }

    private PolicyDecision() {}

    public static Action decide(
            String currentSsid,
            String targetSsid,
            boolean temporarySession,
            boolean targetWasVisibleAtSessionStart,
            boolean targetVisibleNow) {
        String current = SsidUtil.normalize(currentSsid);
        String target = SsidUtil.normalize(targetSsid);
        if (!target.isEmpty() && current.equals(target)) return Action.NONE;

        if (temporarySession && !current.isEmpty()) {
            if (!targetWasVisibleAtSessionStart && targetVisibleNow) {
                return Action.END_TEMPORARY_AND_CONNECT_TARGET;
            }
            return Action.KEEP_TEMPORARY;
        }
        return Action.CLEAN_AND_CONNECT_TARGET;
    }
}
