package app.wichtelein

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Führt die echte Wichtelstimme (Modell + sherpa-onnx) auf dem Emulator aus und speichert WAV-Dateien. */
@RunWith(AndroidJUnit4::class)
class VoiceTest {
    private fun writeWav(file: File, a: VoiceAudio) {
        val pcm = ByteBuffer.allocate(a.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in a.samples) pcm.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        val data = pcm.array()
        val out = ByteArrayOutputStream()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        h.putInt(a.sampleRate).putInt(a.sampleRate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(data.size)
        out.write(h.array())
        out.write(data)
        file.writeBytes(out.toByteArray())
    }

    private fun rms(x: FloatArray) = sqrt(x.map { it * it }.average())

    @Test fun elfVoiceSpeaksGerman() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.filesDir, "shots").apply { mkdirs() }
        ElfVoice.prepareAsync(ctx)
        val t0 = System.currentTimeMillis()
        while ((ElfVoice.state == ElfVoice.State.PREPARING || ElfVoice.state == ElfVoice.State.IDLE) &&
            System.currentTimeMillis() - t0 < 300_000
        ) Thread.sleep(500)
        val prepMs = System.currentTimeMillis() - t0
        assertEquals("Stimme nicht bereit: ${ElfVoice.error}", ElfVoice.State.READY, ElfVoice.state)

        val text = "Hallo Max, ich bin Pippo, dein Wichtel! Heute habe ich wieder etwas angestellt."
        val s0 = System.nanoTime()
        val plain = ElfVoice.synthesize(text, Mood.AMUSED, 1.0f, 1.0f)
        val synthMs = (System.nanoTime() - s0) / 1_000_000
        assertNotNull(plain)
        val elf = ElfVoice.synthesize(text, Mood.AMUSED, 1.0f, 1.42f)
        assertNotNull(elf)
        val sleepy = ElfVoice.synthesize("Gähn... ich bin so müde. Gute Nacht.", Mood.SLEEPY, 0.9f, 1.3f)
        assertNotNull(sleepy)

        writeWav(File(dir, "voice_plain.wav"), plain!!)
        writeWav(File(dir, "voice_elf.wav"), elf!!)
        writeWav(File(dir, "voice_sleepy.wav"), sleepy!!)
        File(dir, "voice_timing.txt").writeText(
            "vorbereiten=${prepMs}ms synthese=${synthMs}ms laenge=${plain.samples.size * 1000 / plain.sampleRate}ms rate=${plain.sampleRate}\n",
        )

        assertTrue("Satz zu kurz: ${plain.samples.size}", plain.samples.size > plain.sampleRate * 3)
        assertTrue("Stimme ist still", rms(plain.samples) > 0.05)
        assertTrue("Wichtelstimme ist still", rms(elf.samples) > 0.05)
        val ratio = elf.samples.size.toDouble() / plain.samples.size
        assertTrue("Dauer weicht ab: $ratio", abs(ratio - 1.0) < 0.15)
    }
}
