package net.kollnig.reddblockandroid.util

import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class TamperProtectionPolicyTest {
    @Test
    fun theStrongestOfMultipleActiveSchedulesSetsTheFloorRegardlessOfOrder() {
        val active = listOf(schedule("cheap", 5), schedule("strict", 30), schedule("middle", 15))
        assertEquals(30, requiredTamperWordCount(10, active))
        assertEquals(30, requiredTamperWordCount(10, active.reversed()))
    }

    @Test
    fun aStrongerTamperSettingStillAppliesToEverySettingsEntryPoint() {
        assertEquals(50, requiredTamperWordCount(50, listOf(schedule("strict", 30))))
    }

    @Test
    fun aGateCanFinishAfterAllSchedulesEndWithoutLoweringTheConfiguredFloor() {
        assertEquals(10, requiredTamperWordCount(10, emptyList()))
    }

    private fun schedule(id: String, wordCount: Int) = Schedule(
        id = id,
        name = id,
        timing = ScheduleTiming(ScheduleTiming.ScheduleType.MANUAL),
        frictionWordCount = wordCount,
    )
}
