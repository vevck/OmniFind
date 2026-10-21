package com.omnifind.app

import com.omnifind.app.release.Release
import com.omnifind.app.release.TourGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** TC-09: the release manifest is complete and consistent, and the tour shows once per build. */
class ReleaseTest {
    private val release = Release.parse(File("src/main/assets/release/release.json").readText())

    @Test
    fun tourStepsAreNumberedOneByOne() {
        assertEquals((1..release.tour.size).toList(), release.tour.map { it.number })
    }

    @Test
    fun everyStepNamesKnownTestCasesAndHasItsScreenshot() {
        val ids = release.testCases.map { it.id }.toSet()
        for (step in release.tour) {
            assertTrue("step ${step.number} has no test case", step.tests.isNotEmpty())
            assertTrue("step ${step.number}: unknown ${step.tests - ids}", ids.containsAll(step.tests))
            assertTrue("no screenshot for step ${step.number} (${step.screenshot}): run tools/release/make_docs.py",
                File("src/main/assets/release/tour/${step.screenshot}.webp").exists())
        }
        assertEquals("test case ids must be unique", ids.size, release.testCases.size)
    }

    @Test
    fun buildNumberMatchesTheLabel() {
        assertTrue(release.build > 0)
        assertEquals("${release.version} · Build ${release.build}", release.label)
    }

    @Test
    fun tourShowsOnInstallAndOncePerUpdate() {
        assertTrue(TourGate.shouldShow(seenBuild = 0, build = 5))      // fresh install
        assertTrue(TourGate.shouldShow(seenBuild = 4, build = 5))      // update to a new build
        assertFalse(TourGate.shouldShow(seenBuild = 5, build = 5))     // seen on this build
        assertFalse(TourGate.shouldShow(seenBuild = 6, build = 5))     // a downgrade doesn't nag
    }
}
