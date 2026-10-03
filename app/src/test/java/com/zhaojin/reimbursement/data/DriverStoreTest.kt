package com.zhaojin.reimbursement.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驾驶员记忆库纯函数测试：编解码往返、记忆逻辑（去重/置首/最近优先）。
 */
class DriverStoreTest {

    @Test
    fun `round trip 保留驾驶员与车牌顺序`() {
        val entries = listOf(
            DriverStore.DriverEntry("朱建", listOf("京A12345", "京B67890")),
            DriverStore.DriverEntry("李四", listOf("沪C11111"))
        )
        assertEquals(entries, decodeDrivers(encodeDrivers(entries)))
    }

    @Test
    fun `无车牌驾驶员与空列表编解码`() {
        val entries = listOf(DriverStore.DriverEntry("王五"))
        assertEquals(entries, decodeDrivers(encodeDrivers(entries)))
        assertEquals(emptyList<DriverStore.DriverEntry>(), decodeDrivers(encodeDrivers(emptyList())))
    }

    @Test
    fun `值中含等号不被截断`() {
        val entries = listOf(DriverStore.DriverEntry("张=三", listOf("京=A1")))
        val decoded = decodeDrivers(encodeDrivers(entries))
        assertEquals("张=三", decoded?.first()?.name)
        assertEquals("京=A1", decoded?.first()?.plates?.first())
    }

    @Test
    fun `magic 不符与空文本返回 null`() {
        assertNull(decodeDrivers("random\nfoo=bar"))
        assertNull(decodeDrivers(""))
        assertNull(decodeDrivers("drivers_v2\ndriver=x"))
    }

    @Test
    fun `新驾驶员插到首位`() {
        val result = rememberUsagePure(
            listOf(DriverStore.DriverEntry("李四", listOf("沪C11111"))), "朱建", "京A12345"
        )
        assertEquals(listOf("朱建", "李四"), result.map { it.name })
        assertEquals(listOf("京A12345"), result[0].plates)
    }

    @Test
    fun `已有驾驶员新车牌置首且去重`() {
        val result = rememberUsagePure(
            listOf(DriverStore.DriverEntry("朱建", listOf("京A12345", "京B67890"))), "朱建", "京C00001"
        )
        assertEquals(listOf("京C00001", "京A12345", "京B67890"), result[0].plates)
    }

    @Test
    fun `已有车牌再次使用只保留一份并置首`() {
        val result = rememberUsagePure(
            listOf(DriverStore.DriverEntry("朱建", listOf("京A12345", "京B67890"))), "朱建 ", "京B67890"
        )
        assertEquals(listOf("京B67890", "京A12345"), result[0].plates)
    }

    @Test
    fun `无车牌记忆只提升驾驶员不动车牌`() {
        val result = rememberUsagePure(
            listOf(
                DriverStore.DriverEntry("朱建", listOf("京A12345")),
                DriverStore.DriverEntry("李四")
            ), "李四", ""
        )
        assertEquals(listOf("李四", "朱建"), result.map { it.name })
        assertEquals(listOf("京A12345"), result[1].plates)
    }

    @Test
    fun `姓名与车牌两端空白被 trim`() {
        val result = rememberUsagePure(emptyList(), "  朱建  ", " 京A12345 ")
        assertEquals("朱建", result[0].name)
        assertEquals(listOf("京A12345"), result[0].plates)
    }

    @Test
    fun `空白姓名不记忆`() {
        val original = listOf(DriverStore.DriverEntry("李四", listOf("沪C11111")))
        assertEquals(original, rememberUsagePure(original, "  ", "京A12345"))
        assertTrue(rememberUsagePure(emptyList(), "", "").isEmpty())
    }

    @Test
    fun `历史账单按时间升序回放 最近的最前`() {
        val bills = listOf(
            bill(id = 1, ts = 1000, driver = "朱建", plate = "京A12345"),
            bill(id = 2, ts = 2000, driver = "朱建", plate = "京B67890"),
            bill(id = 3, ts = 2000, driver = "李四", plate = "沪C11111"),
            bill(id = 4, ts = 3000, driver = "朱建", plate = "京A12345")
        )
        val result = migrateFromBillsPure(bills)
        assertEquals("朱建", result[0].name)
        // 朱建最近一次（ts=3000）用的京A 排首位，京B 在后
        assertEquals(listOf("京A12345", "京B67890"), result[0].plates)
        // 同时刻（ts=2000）账单按 id 升序回放：李四在 ts=2000 被 id=2 的朱建压过吗？
        // id=2 是朱建 → 朱建被提到首位；id=3 李四加入后，最终朱建（ts=3000）仍在首位
        assertEquals(listOf("朱建", "李四"), result.map { it.name })
    }

    @Test
    fun `历史账单驾驶员为空的跳过`() {
        val result = migrateFromBillsPure(
            listOf(bill(id = 1, ts = 1000, driver = "", plate = "京A12345"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `合并迁移 现有记忆新于历史并保持车牌完整`() {
        val current = listOf(DriverStore.DriverEntry("朱建", listOf("京B67890")))
        val history = migrateFromBillsPure(
            listOf(
                bill(id = 1, ts = 1000, driver = "朱建", plate = "京A12345"),
                bill(id = 2, ts = 2000, driver = "李四", plate = "沪C11111")
            )
        )
        val merged = mergeMigratedPure(current, history)
        // 朱建整体（新版保存的）比历史新，其新车牌居首，历史车牌保留
        assertEquals("朱建", merged[0].name)
        assertEquals(listOf("京B67890", "京A12345"), merged[0].plates)
        assertEquals("李四", merged[1].name)
        assertEquals(listOf("沪C11111"), merged[1].plates)
    }

    @Test
    fun `合并迁移 现有多条保持相对新旧顺序`() {
        val current = listOf(
            DriverStore.DriverEntry("朱建", listOf("京B67890")),
            DriverStore.DriverEntry("王五", listOf("粤D22222"))
        )
        val merged = mergeMigratedPure(current, emptyList())
        assertEquals(listOf("朱建", "王五"), merged.map { it.name })
    }
}

private fun bill(id: Long, ts: Long, driver: String, plate: String) = com.zhaojin.reimbursement.data.BillEntity(
    id = id, amount = 100.0, title = "t", category = "", driver = driver,
    plate = plate, region = "", isIncome = false, timestamp = ts
)
