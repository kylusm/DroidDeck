package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaliG610McVariantsTest {
    @Test
    fun mc3Mc4AndMc6NamesShareTheSameGenericPanvkFamily() {
        for (cores in listOf(3, 4, 6)) {
            val name = "Mali-G610 MC$cores"
            val family = GpuInfo.familyOf(adreno = false, model = 0, mali = name)
            assertEquals(GpuInfo.Family.MALI_G610_G710, family)
            assertTrue(GpuInfo.maliKbaseCovered(name))
        }
    }

    @Test
    fun genericKbaseCoverageDoesNotIncludeLegacyMaliG52() {
        assertTrue(!GpuInfo.maliKbaseCovered("Mali-G52 MC2"))
    }
}
