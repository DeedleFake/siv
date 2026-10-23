package dev.deedles.simpleimageviewer.ui

import android.app.Activity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class IgnoresOrientationRequestsTest {
    private fun ignores() = Robolectric.buildActivity(Activity::class.java).setup().get().ignoresOrientationRequests()

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneKeepsOrientationRequests() = assertFalse(ignores())

    @Test
    @Config(sdk = [36], qualifiers = "w599dp-h1200dp-mdpi")
    fun justUnderTheThresholdKeepsOrientationRequests() = assertFalse(ignores())

    @Test
    @Config(sdk = [36], qualifiers = "w600dp-h1000dp-mdpi")
    fun theThresholdIgnoresOrientationRequests() = assertTrue(ignores())

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp-mdpi")
    fun tabletIgnoresOrientationRequests() = assertTrue(ignores())

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletHeldUprightIgnoresOrientationRequests() = assertTrue(ignores())

    @Test
    @Config(sdk = [36], qualifiers = "w400dp-h640dp-xxhdpi")
    fun manyPixelsOnADenseDisplayAreStillAPhone() = assertFalse(ignores())
}
