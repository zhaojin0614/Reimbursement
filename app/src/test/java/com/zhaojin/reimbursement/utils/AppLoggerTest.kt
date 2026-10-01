package com.zhaojin.reimbursement.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class AppLoggerTest {

    @get:org.junit.Rule
    val tmp = TemporaryFolder()

    @Test
    fun `日志文件名解析`() {
        assertEquals(LocalDate.of(2026, 10, 2), AppLogger.parseLogDate("log-2026-10-02.log"))
        assertNull(AppLogger.parseLogDate("other.txt"))
        assertNull(AppLogger.parseLogDate("log-bad.log"))
    }

    @Test
    fun `清理 - 只删超过保留期的日志文件`() {
        val dir = tmp.newFolder("logs")
        val today = LocalDate.of(2026, 10, 2)
        val keep = File(dir, "log-2026-10-01.log")   // 昨天：保留
        val drop = File(dir, "log-2026-09-01.log")   // 31 天前：删除
        val other = File(dir, "not-a-log.txt")       // 非日志：不动
        listOf(keep, drop, other).forEach { it.writeText("x") }

        val deleted = AppLogger.cleanUp(dir, retentionDays = 30, today = today)

        assertEquals(1, deleted)
        assertTrue(keep.exists())
        assertTrue(!drop.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `清理 - 保留期边界当天不删`() {
        val dir = tmp.newFolder("logs2")
        val today = LocalDate.of(2026, 10, 2)
        val edge = File(dir, "log-2026-09-02.log") // 恰好 30 天前：保留
        edge.writeText("x")

        assertEquals(0, AppLogger.cleanUp(dir, 30, today))
        assertTrue(edge.exists())
    }
}
