package com.celltowerid.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CellPropertyHelpTest {

    @Test
    fun `given a known key, when get is called, then returns title and non-empty body`() {
        // When
        val help = requireNotNull(CellPropertyHelp.get(CellPropertyHelp.Key.RSRP))

        // Then
        assertThat(help.title).isNotEmpty()
        assertThat(help.body).isNotEmpty()
    }

    @Test
    fun `given every enum key, when get is called, then returns a non-null entry`() {
        for (key in CellPropertyHelp.Key.entries) {
            val help = requireNotNull(CellPropertyHelp.get(key)) { "missing entry for $key" }
            assertThat(help.title).isNotEmpty()
            assertThat(help.body).isNotEmpty()
        }
    }

    @Test
    fun `given the RSRP help, when read, then mentions dBm and a typical range`() {
        // When
        val body = requireNotNull(CellPropertyHelp.get(CellPropertyHelp.Key.RSRP)).body.lowercase()

        // Then
        assertThat(body).contains("dbm")
    }

    @Test
    fun `given the MCC help, when read, then mentions country`() {
        // When
        val body = requireNotNull(CellPropertyHelp.get(CellPropertyHelp.Key.MCC)).body.lowercase()

        // Then
        assertThat(body).contains("country")
    }

    @Test
    fun `given the PCI help, when read, then mentions that it is not unique globally`() {
        // When
        val body = requireNotNull(CellPropertyHelp.get(CellPropertyHelp.Key.PCI)).body.lowercase()

        // Then
        assertThat(body).contains("not unique")
    }
}
