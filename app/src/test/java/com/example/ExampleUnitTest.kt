package com.example

import com.example.ai.QuranAudioService
import com.example.ai.ReciterVoicePacks
import com.example.data.local.IslamicDataSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun reciterVoicePacks_containsAllRequiredReciters() {
        val reciters = ReciterVoicePacks.RECITERS
        assertTrue(reciters.isNotEmpty())
        assertTrue(reciters.any { it.id == "mishary" })
        assertTrue(reciters.any { it.id == "sudais" })
        assertTrue(reciters.any { it.id == "muaiqly" })
        assertTrue(reciters.any { it.id == "ghamdi" })
        assertTrue(reciters.any { it.id == "husary" })
        assertEquals("mishary", ReciterVoicePacks.DEFAULT_RECITER.id)
    }

    @Test
    fun wavHeaderGeneration_producesValidRiffPcmHeader() {
        val dummyPcm = ByteArray(4800) // 100ms of 24kHz 16-bit mono
        val wavBytes = QuranAudioService.createWavFileBytes(
            pcmData = dummyPcm,
            sampleRate = 24000,
            channels = 1,
            bitsPerSample = 16
        )

        assertEquals(44 + 4800, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Check RIFF chunk
        val riff = String(byteArrayOf(buffer.get(), buffer.get(), buffer.get(), buffer.get()))
        assertEquals("RIFF", riff)

        val totalDataLen = buffer.int
        assertEquals(wavBytes.size - 8, totalDataLen)

        val wave = String(byteArrayOf(buffer.get(), buffer.get(), buffer.get(), buffer.get()))
        assertEquals("WAVE", wave)

        // Check fmt subchunk
        val fmt = String(byteArrayOf(buffer.get(), buffer.get(), buffer.get(), buffer.get()))
        assertEquals("fmt ", fmt)

        val subchunk1Size = buffer.int
        assertEquals(16, subchunk1Size)

        val audioFormat = buffer.short
        assertEquals(1.toShort(), audioFormat) // PCM = 1

        val numChannels = buffer.short
        assertEquals(1.toShort(), numChannels)

        val sampleRate = buffer.int
        assertEquals(24000, sampleRate)

        val byteRate = buffer.int
        assertEquals(48000, byteRate) // 24000 * 1 * 16 / 8

        val blockAlign = buffer.short
        assertEquals(2.toShort(), blockAlign)

        val bitsPerSample = buffer.short
        assertEquals(16.toShort(), bitsPerSample)

        // Check data subchunk
        val data = String(byteArrayOf(buffer.get(), buffer.get(), buffer.get(), buffer.get()))
        assertEquals("data", data)

        val audioLen = buffer.int
        assertEquals(4800, audioLen)
    }

    @Test
    fun islamicDataSource_containsSurahsAndAyahs() {
        val surahs = IslamicDataSource.SURAHS
        assertEquals(114, surahs.size)
        val fatihaAyahs = IslamicDataSource.AYAHS_BY_SURAH[1]
        assertNotNull(fatihaAyahs)
        assertEquals(7, fatihaAyahs?.size)
    }
}
