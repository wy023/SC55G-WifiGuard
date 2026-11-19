package com.sc55g.wifiguard.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class SsidUtilTest {
    @Test public void normalizesQuotedSsid() {
        assertEquals("LAB_WIFI", SsidUtil.normalize("\"LAB_WIFI\""));
    }

    @Test public void preservesCaseSensitivity() {
        assertFalse(SsidUtil.same("lab_wifi", "LAB_WIFI"));
    }

    @Test public void temporaryConnectionEndsWhenPreviouslyMissingTargetReturns() {
        assertEquals(PolicyDecision.Action.END_TEMPORARY_AND_CONNECT_TARGET,
                PolicyDecision.decide("Guest", "LAB_WIFI", true, false, true));
    }

    @Test public void temporaryConnectionStaysWhenTargetHasNotReturned() {
        assertEquals(PolicyDecision.Action.KEEP_TEMPORARY,
                PolicyDecision.decide("Guest", "LAB_WIFI", true, false, false));
    }

    @Test public void nonTemporaryConnectionIsCleaned() {
        assertEquals(PolicyDecision.Action.CLEAN_AND_CONNECT_TARGET,
                PolicyDecision.decide("Guest", "LAB_WIFI", false, false, true));
    }
}
