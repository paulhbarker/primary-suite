package com.primarysuite.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** MainActivity locks phones to portrait and lets tablets rotate. */
@RunWith(AndroidJUnit4::class)
class OrientationLockTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "sw411dp-w411dp-h891dp")
    fun phoneIsPortraitOnly() {
        assertTrue(context.resources.getBoolean(R.bool.portrait_only))
    }

    @Test
    @Config(qualifiers = "sw800dp-w1280dp-h800dp-land")
    fun tabletCanRotate() {
        assertFalse(context.resources.getBoolean(R.bool.portrait_only))
    }
}
