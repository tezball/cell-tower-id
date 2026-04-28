package com.celltowerid.android.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Placeholder for MainActivity UI tests.
 *
 * Full smoke tests are disabled because MapLibre's native rendering
 * triggers OOM kills under the instrumented test runner, and the
 * background location dialog blocks Espresso interactions.
 *
 * TODO: Re-enable once MapLibre is mockable or the dialog flow is
 *       extracted behind an interface that tests can stub.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun given_test_infrastructure_when_running_then_test_runner_works() {
        assertThat(true).isTrue()
    }
}
