package com.nbtaad.editor.contribute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WizardStepTest {
    @Test fun theStepsAreNumberedInOrder() {
        assertEquals(listOf(1, 2, 3, 4, 5), WizardStep.entries.map { it.number })
        assertEquals(
            listOf(WizardStep.VERSION, WizardStep.SETTINGS, WizardStep.TEST, WizardStep.RETURN, WizardStep.LOGS),
            WizardStep.entries.toList(),
        )
    }

    @Test fun backWalksTowardsTheFirstStep() {
        assertNull(WizardStep.VERSION.before())
        assertEquals(WizardStep.VERSION, WizardStep.SETTINGS.before())
        assertEquals(WizardStep.SETTINGS, WizardStep.TEST.before())
        assertEquals(WizardStep.TEST, WizardStep.RETURN.before())
    }

    @Test fun backFromTheLogsSkipsTheWifiStepThatWouldMoveOnAgain() {
        assertEquals(WizardStep.TEST, WizardStep.LOGS.before())
    }

    @Test fun theCommentBoxLinkKeepsTheAddressOfThePage() {
        assertEquals(
            "https://github.com/Sq00ky/Supra-iDrive6-Android-Auto/issues/187#issue-comment-box",
            PageLinks.commentBox("https://github.com/Sq00ky/Supra-iDrive6-Android-Auto/issues/187"),
        )
    }

    @Test fun anAddressThatHasAnAnchorGetsTheCommentBoxInstead() {
        assertEquals(
            "https://github.com/x/y/pull/9#issue-comment-box",
            PageLinks.commentBox("https://github.com/x/y/pull/9#issuecomment-123"),
        )
    }
}
