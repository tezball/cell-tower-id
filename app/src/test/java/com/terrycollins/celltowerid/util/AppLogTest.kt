package com.terrycollins.celltowerid.util

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class AppLogTest {

    @Before
    fun setUp() {
        AppLog.clear()
    }

    @Test
    fun `given 250 appended lines, when lines is called, then returns the most recent 200`() {
        // Given
        for (i in 1..250) {
            AppLog.e("TAG", "msg $i")
        }

        // When
        val result = AppLog.lines()

        // Then
        assertThat(result).hasSize(200)
        assertThat(result.first().message).isEqualTo("msg 51")
        assertThat(result.last().message).isEqualTo("msg 250")
    }

    @Test
    fun `given some lines, when clear is called, then lines is empty`() {
        // Given
        AppLog.e("TAG", "msg")
        AppLog.w("TAG", "another")

        // When
        AppLog.clear()

        // Then
        assertThat(AppLog.lines()).isEmpty()
    }
}
