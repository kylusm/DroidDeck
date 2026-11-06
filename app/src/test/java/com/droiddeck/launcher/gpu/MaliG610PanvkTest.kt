package com.droiddeck.launcher.gpu

import com.droiddeck.launcher.gpu.GpuInfo.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaliG610PanvkTest {
    private fun g610() = GpuInfo(
        name = "Mali-G610 MC4",
        model = 0,
        family = GpuInfo.familyOf(false, 0, mali = "Mali-G610 MC4"),
        soc = "Dimensity",
        oneUi8Gen2 = false,
    )

    private fun bundle(id: String) = TurnipReleases.Asset(
        source = "Non-Adreno",
        tag = id.lowercase(),
        name = "$id-Pair.zip",
        url = "https://example.invalid/$id-Pair.zip",
        size = 9_000_000,
        linux = false,
        label = "Android + Linux",
        sha256 = "0".repeat(64),
        pair = DriverPairs.nonAdreno(id),
        bundle = true,
    )

    @Test
    fun knownDimensitySocIdsResolveToTheGenericG610Family() {
        for (soc in listOf("mt6895", "mt6896", "mt6886")) {
            val name = GpuInfo.maliOfSoc(listOf(soc.uppercase()))
            assertEquals("Mali-G610 (by chip $soc)", name)
            assertEquals(Family.MALI_G610_G710, GpuInfo.familyOf(false, 0, mali = name))
            assertTrue(GpuInfo.maliKbaseCovered(name.orEmpty()))
        }
    }

    @Test
    fun g610SelectsTheGenerationPairBeforeAndroid15AndUniversalKbaseAfter() {
        val gpu = g610()
        assertEquals(DriverPairs.NAD_MALI_G710, DriverPairs.recommendedKey(gpu, emptyList(), sdk = 34))
        assertEquals(DriverPairs.NAD_MALI_KBASE, DriverPairs.recommendedKey(gpu, emptyList(), sdk = 35))
        assertEquals(GpuInfo.Support.UNTESTED, gpu.support)
    }

    @Test
    fun availableBundlesAreSelectedByAndroidApiAndBothCoverG610() {
        val pairs = DriverPairs.from(
            TurnipReleases.Check(
                listOf(bundle("PanVK-Mali-G710"), bundle("PanVK-Mali-kbase")),
                emptyList(),
                emptyList(),
                0L,
            ),
        )
        val gpu = g610()
        val generation = pairs.single { it.key == DriverPairs.NAD_MALI_G710 }
        val universal = pairs.single { it.key == DriverPairs.NAD_MALI_KBASE }

        assertTrue(generation.complete)
        assertTrue(universal.complete)
        assertTrue(generation.suits(gpu))
        assertTrue(universal.suits(gpu))
        assertEquals(DriverPairs.NAD_MALI_G710, DriverPairs.recommendedKey(gpu, pairs, sdk = 34))
        assertEquals(DriverPairs.NAD_MALI_KBASE, DriverPairs.recommendedKey(gpu, pairs, sdk = 35))
        assertFalse(universal.suits(GpuInfo("Mali-G52 MC2", 0, Family.MALI_JM, "", false)))
    }
}
