package com.zhaojin.reimbursement.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BillPhotoStoreTest {

    @Test
    fun `大原图按2的幂采样压进像素预算`() {
        // 12MP 相机原图（4032x3024 ≈ 12.2M 像素）→ iss=2，解码约 3M 像素
        assertEquals(2, calcInSampleSize(4032, 3024, 4_000_000L))
        // 8MP（3264x2448 ≈ 8M 像素）→ iss=2
        assertEquals(2, calcInSampleSize(3264, 2448, 4_000_000L))
    }

    @Test
    fun `小图不采样`() {
        assertEquals(1, calcInSampleSize(1080, 1920, 4_000_000L))
        assertEquals(1, calcInSampleSize(120, 120, 14_400L))
    }

    @Test
    fun `恰好等于预算时不采样`() {
        assertEquals(1, calcInSampleSize(2000, 2000, 4_000_000L))
    }

    @Test
    fun `非法尺寸返回1`() {
        assertEquals(1, calcInSampleSize(0, 100, 4_000_000L))
        assertEquals(1, calcInSampleSize(100, -1, 4_000_000L))
        assertEquals(1, calcInSampleSize(100, 100, 0L))
    }
}
