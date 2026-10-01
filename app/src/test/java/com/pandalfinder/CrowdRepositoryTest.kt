package com.pandalfinder

import com.pandalfinder.data.CrowdRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CrowdRepositoryTest {

    @Test
    fun testLocalDayBoundariesCalculation() {
        val repo = CrowdRepository()
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.SEPTEMBER, 28, 14, 30, 0)
        cal.set(Calendar.MILLISECOND, 500)
        val testTime = cal.timeInMillis

        val (startOfDay, startOfNextDay) = repo.getLocalDayBoundaries(testTime)

        val calStart = Calendar.getInstance().apply { timeInMillis = startOfDay }
        val calEnd = Calendar.getInstance().apply { timeInMillis = startOfNextDay }

        assertEquals(2026, calStart.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, calStart.get(Calendar.MONTH))
        assertEquals(28, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calStart.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calStart.get(Calendar.MINUTE))
        assertEquals(0, calStart.get(Calendar.SECOND))
        assertEquals(0, calStart.get(Calendar.MILLISECOND))

        assertEquals(2026, calEnd.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, calEnd.get(Calendar.MONTH))
        assertEquals(29, calEnd.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calEnd.get(Calendar.MINUTE))
        assertEquals(0, calEnd.get(Calendar.SECOND))
        assertEquals(0, calEnd.get(Calendar.MILLISECOND))
    }

    @Test
    fun testReportEligibilityAcrossDays() {
        val repo = CrowdRepository()
        val nowCal = Calendar.getInstance()
        nowCal.set(2026, Calendar.SEPTEMBER, 29, 10, 0, 0)
        val todayTime = nowCal.timeInMillis
        val (startOfToday, startOfTomorrow) = repo.getLocalDayBoundaries(todayTime)

        // Yesterday report (Sept 28, 23:59:59)
        val yesterdayCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 28, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val yesterdayTimestamp = yesterdayCal.timeInMillis

        // Today report (Sept 29, 00:00:01)
        val todayReportCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 29, 0, 0, 1)
        }
        val todayReportTimestamp = todayReportCal.timeInMillis

        // Yesterday report should NOT be eligible today
        val yesterdayEligible = yesterdayTimestamp in startOfToday until startOfTomorrow
        assertFalse("Yesterday report should not be eligible in today's crowd calculation", yesterdayEligible)

        // Today report SHOULD be eligible today
        val todayEligible = todayReportTimestamp in startOfToday until startOfTomorrow
        assertTrue("Today report should be eligible in today's crowd calculation", todayEligible)
    }

    @Test
    fun testCrowdLabelMapping() {
        val repo = CrowdRepository()
        assertEquals("No reports today", repo.getCrowdLabel(null).first)
        assertEquals("Empty", repo.getCrowdLabel(1).first)
        assertEquals("Empty", repo.getCrowdLabel(2).first)
        assertEquals("Light", repo.getCrowdLabel(3).first)
        assertEquals("Light", repo.getCrowdLabel(4).first)
        assertEquals("Moderate", repo.getCrowdLabel(5).first)
        assertEquals("Moderate", repo.getCrowdLabel(6).first)
        assertEquals("Very busy", repo.getCrowdLabel(7).first)
        assertEquals("Very busy", repo.getCrowdLabel(8).first)
        assertEquals("Extremely busy", repo.getCrowdLabel(9).first)
        assertEquals("Packed", repo.getCrowdLabel(10).first)
    }
}
