package com.yamosaed.assistant.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorderHelper(private val context: Context) {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var outputFile: File? = null

    fun isRecording(): Boolean = isRecording.get()

    /**
     * بدء تسجيل الصوت المباشر من الميكروفون وحفظه بتنسيق WAV القياسي
     */
    fun startRecording(): File? {
        if (isRecording.get()) return outputFile

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return null
        }
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return null
            }

            outputFile = File(context.cacheDir, "yamosaed_voice_input.wav")
            isRecording.set(true)
            audioRecord?.startRecording()

            recordThread = Thread {
                val data = ByteArray(bufferSize)
                val fos = FileOutputStream(outputFile)
                // كتابة ترويسة أولية سيتم تعديلها عند انتهاء التسجيل
                writeWavHeader(fos, 0, 0, sampleRate, 1, 16)

                var totalAudioLen = 0L
                while (isRecording.get()) {
                    val read = audioRecord?.read(data, 0, data.size) ?: 0
                    if (read > 0) {
                        fos.write(data, 0, read)
                        totalAudioLen += read
                    }
                }
                fos.close()
                // تصحيح أحجام ملف WAV بعد اكتمال التسجيل
                fixWavHeader(outputFile, totalAudioLen)
            }.apply { start() }

            return outputFile
        } catch (_: Exception) {
            stopRecordingSync()
            return null
        }
    }

    private fun stopRecordingSync(): File? {
        if (!isRecording.get()) return outputFile
        isRecording.set(false)

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            recordThread?.join(1000L)
        } catch (_: Exception) {}
        recordThread = null

        return outputFile
    }

    /**
     * إيقاف التسجيل وإرجاع ملف الـ WAV المكتمل
     */
    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        stopRecordingSync()
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Int,
        channels: Int,
        byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte()
        header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        val calculatedByteRate = (longSampleRate * channels * 16 / 8).toLong()
        header[28] = (calculatedByteRate and 0xffL).toByte()
        header[29] = (calculatedByteRate shr 8 and 0xffL).toByte()
        header[30] = (calculatedByteRate shr 16 and 0xffL).toByte()
        header[31] = (calculatedByteRate shr 24 and 0xffL).toByte()
        header[32] = (channels * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
        header[42] = (totalAudioLen shr 16 and 0xffL).toByte()
        header[43] = (totalAudioLen shr 24 and 0xffL).toByte()
        out.write(header, 0, 44)
    }

    private fun fixWavHeader(file: File?, totalAudioLen: Long) {
        if (file == null || !file.exists()) return
        val totalDataLen = totalAudioLen + 36
        val raf = RandomAccessFile(file, "rw")
        raf.seek(4)
        raf.write((totalDataLen and 0xffL).toInt())
        raf.write((totalDataLen shr 8 and 0xffL).toInt())
        raf.write((totalDataLen shr 16 and 0xffL).toInt())
        raf.write((totalDataLen shr 24 and 0xffL).toInt())

        raf.seek(40)
        raf.write((totalAudioLen and 0xffL).toInt())
        raf.write((totalAudioLen shr 8 and 0xffL).toInt())
        raf.write((totalAudioLen shr 16 and 0xffL).toInt())
        raf.write((totalAudioLen shr 24 and 0xffL).toInt())
        raf.close()
    }
}
