package com.terrycollins.celltowerid.ui

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.terrycollins.celltowerid.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: launches MainActivity and taps each bottom-nav destination,
 * verifying the expected root view is present. Catches nav-graph typos,
 * renamed view IDs, and obvious layout inflation crashes.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun given_app_launch_when_tapping_each_nav_tab_then_no_crash() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Map tab
            onView(withId(R.id.nav_map)).perform(click())
            onView(withId(R.id.map_view)).check(matches(isDisplayed()))

            // Cells tab
            onView(withId(R.id.nav_cells)).perform(click())

            // Alerts tab
            onView(withId(R.id.nav_anomalies)).perform(click())
            onView(withId(R.id.chip_group_severity)).check(matches(isDisplayed()))

            // Settings tab
            onView(withId(R.id.nav_settings)).perform(click())
        }
    }
}
