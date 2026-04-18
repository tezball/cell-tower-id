package com.terrycollins.celltowerid.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        AppLog.clear()
    }

    @After
    fun tearDown() {
        AppLog.clear()
        // Detach file sink so later tests don't write into a deleted temp file.
        AppLog.init(File("/dev/null"))
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

    @Test
    fun `given initialized file sink, when logging, then line is persisted to disk`() {
        // Given
        val file = File(tmp.newFolder("logs"), "app.log")
        AppLog.init(file)

        // When
        AppLog.d("MapFragment", "hello world")

        // Then
        assertThat(file.exists()).isTrue()
        val content = file.readText()
        assertThat(content).contains("D/MapFragment: hello world")
    }

    @Test
    fun `given file over 2MB, when appending, then rolls over to app_log_1`() {
        // Given
        val dir = tmp.newFolder("logs")
        val file = File(dir, "app.log")
        file.writeBytes(ByteArray((2L * 1024 * 1024 + 1).toInt()) { '-'.code.toByte() })
        AppLog.init(file)

        // When
        AppLog.d("TAG", "after rollover")

        // Then
        val rollover = File(dir, "app.log.1")
        assertThat(rollover.exists()).isTrue()
        assertThat(rollover.length()).isAtLeast(2L * 1024 * 1024)
        assertThat(file.exists()).isTrue()
        assertThat(file.readText()).contains("after rollover")
        assertThat(file.length()).isLessThan(2L * 1024 * 1024)
    }

    @Test
    fun `given clear with file sink, when called, then log file is deleted`() {
        // Given
        val file = File(tmp.newFolder("logs"), "app.log")
        AppLog.init(file)
        AppLog.d("TAG", "line")
        assertThat(file.exists()).isTrue()

        // When
        AppLog.clear()

        // Then
        assertThat(file.exists()).isFalse()
    }
}
