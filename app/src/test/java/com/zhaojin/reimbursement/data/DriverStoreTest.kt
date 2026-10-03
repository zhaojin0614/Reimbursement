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
}
