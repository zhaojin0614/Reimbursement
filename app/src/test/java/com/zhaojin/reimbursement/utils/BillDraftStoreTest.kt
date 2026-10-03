package com.zhaojin.reimbursement.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 草稿行式文本编解码纯函数测试：往返一致、值含「=」、缺字段容错、非法格式拒绝。
 */
class BillDraftStoreTest {

    @Test
    fun `round trip 保留全部字段与照片列表`() {
        val draft = BillDraftStore.Draft(
            category = "维修",
            dateIso = "2026-10-03",
            driver = "张三",
            plate = "京A12345",
            region = "朝阳",
            title = "发动机维修费",
            amountText = "500.5",
            photos = listOf("pending_a.jpg", "pending_b.jpg")
        )
        val decoded = decodeDraft(encodeDraft(draft))
        assertEquals(draft, decoded)
    }

    @Test
    fun `值中含等号不被截断`() {
        val draft = BillDraftStore.Draft(title = "费用=总额=500", amountText = "1")
        val decoded = decodeDraft(encodeDraft(draft))
        assertEquals("费用=总额=500", decoded?.title)
    }

    @Test
    fun `空字段与空照片列表照常编解码`() {
        val draft = BillDraftStore.Draft(title = "t", photos = emptyList())
        val decoded = decodeDraft(encodeDraft(draft))
        assertEquals("", decoded?.category)
        assertEquals("", decoded?.plate)
        assertTrue(decoded?.photos.isNullOrEmpty())
    }

    @Test
    fun `缺字段按空值容错`() {
        val decoded = decodeDraft("bill_draft_v1\ntitle=只有标题\n")
        assertEquals("只有标题", decoded?.title)
        assertEquals("", decoded?.amountText)
        assertTrue(decoded?.photos.isNullOrEmpty())
    }

    @Test
    fun `magic 不符与空文本返回 null`() {
        assertNull(decodeDraft("random text\nfoo=bar"))
        assertNull(decodeDraft(""))
        assertNull(decodeDraft("bill_draft_v2\ntitle=x"))
    }

    @Test
    fun `isEmpty 判定忽略分类与日期`() {
        assertTrue(BillDraftStore.Draft(category = "维修", dateIso = "2026-10-03").isEmpty)
        assertFalse(BillDraftStore.Draft(dateIso = "2026-10-03", amountText = "1").isEmpty)
        assertFalse(BillDraftStore.Draft(photos = listOf("pending_a.jpg")).isEmpty)
    }
}
