package com.celltowerid.android.util

import android.app.Application
import android.content.Intent
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MapAttributionBinderTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `when bind is called, then the TextView becomes clickable and exposes a screen-reader description`() {
        // Given
        val textView = TextView(app)

        // When
        MapAttributionBinder.bind(textView)

        // Then
        assertThat(textView.isClickable).isTrue()
        assertThat(textView.isFocusable).isTrue()
        assertThat(textView.contentDescription.toString()).contains("OpenStreetMap")
    }

    @Test
    fun `given a bound TextView, when it is clicked, then an ACTION_VIEW intent for the OSM copyright page is fired`() {
        // Given
        val textView = TextView(app)
        MapAttributionBinder.bind(textView)

        // When
        textView.performClick()

        // Then
        val started = shadowOf(app).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.dataString).isEqualTo(MapAttributionBinder.OSM_COPYRIGHT_URL)
    }
}
