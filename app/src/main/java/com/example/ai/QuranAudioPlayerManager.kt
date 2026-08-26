package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.example.data.local.IslamicDataSource
import com.example.data.model.Ayah
import com.example.data.model.Surah
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class QuranPlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val currentSurah: Surah? = null,
    val currentAyah: Ayah? = null,
    val currentAyahIndex: Int = 0,
    val totalAyahsInSurah: Int = 0,
    val selectedReciter: ReciterVoice = ReciterVoicePacks.DEFAULT_RECITER,
    val playbackProgress: Float = 0f,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val isFromAi: Boolean = false,
    val errorMessage: String? = null
)

class QuranAudioPlayerManager(private val context: Context) {

    private val TAG = "QuranAudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var loadJob: Job? = null

    private val _playbackState = MutableStateFlow(QuranPlaybackState())
    val playbackState: StateFlow<QuranPlaybackState> = _playbackState.asStateFlow()

    private var currentAyahsList: List<Ayah> = emptyList()

    fun setSurahAyahs(ayahs: List<Ayah>) {
        currentAyahsList = ayahs
    }

    fun selectReciter(reciter: ReciterVoice) {
        _playbackState.value = _playbackState.value.copy(selectedReciter = reciter)
        // If already playing, replay current ayah with new reciter voice
        val currentAyah = _playbackState.value.currentAyah
        val currentSurah = _playbackState.value.currentSurah
        if (currentAyah != null && currentSurah != null && (_playbackState.value.isPlaying || _playbackState.value.isPaused)) {
            playAyah(currentSurah, currentAyah)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.playbackParams = mediaPlayer!!.playbackParams.setSpeed(speed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing playback speed", e)
        }
    }

    fun playAyah(surah: Surah, ayah: Ayah) {
        loadJob?.cancel()
        stopProgressTracker()
        releasePlayer()

        // If currentAyahsList is empty, default to this Surah's ayahs
        if (currentAyahsList.isEmpty()) {
            currentAyahsList = IslamicDataSource.getAyahsForSurah(surah)
        }

        val indexInList = currentAyahsList.indexOfFirst { 
            it.surahNumber == surah.number && it.numberInSurah == ayah.numberInSurah 
        }.let { if (it >= 0) it else (ayah.numberInSurah - 1).coerceIn(0, (surah.numberOfAyahs - 1).coerceAtLeast(0)) }

        val totalCount = if (currentAyahsList.isNotEmpty()) currentAyahsList.size else surah.numberOfAyahs

        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isPaused = false,
            isLoading = true,
            currentSurah = surah,
            currentAyah = ayah,
            currentAyahIndex = indexInList,
            totalAyahsInSurah = totalCount,
            playbackProgress = 0f,
            currentPositionMs = 0,
            durationMs = 0,
            errorMessage = null
        )

        loadJob = coroutineScope.launch {
            val reciter = _playbackState.value.selectedReciter
            val result = QuranAudioService.getAyahAudioFile(context, surah, ayah, reciter)

            when (result) {
                is QuranAudioResult.Success -> {
                    startMediaPlayer(result.audioFile, isFromAi = result.isFromAi)
                }
                is QuranAudioResult.Error -> {
                    _playbackState.value = _playbackState.value.copy(
                        isLoading = false,
                        isPlaying = false,
                        isPaused = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    private fun startMediaPlayer(audioFile: File, isFromAi: Boolean) {
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        playbackParams = playbackParams.setSpeed(_playbackState.value.playbackSpeed)
                    } catch (_: Exception) {}
                }
                setOnCompletionListener {
                    handleTrackCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        isPaused = false,
                        isLoading = false,
                        errorMessage = "Audio playback error. Tap to retry recitation."
                    )
                    true
                }
            }

            player.start()
            mediaPlayer = player

            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                isPaused = false,
                isLoading = false,
                durationMs = player.duration,
                isFromAi = isFromAi,
                errorMessage = null
            )

            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer", e)
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPaused = false,
                isLoading = false,
                errorMessage = "Could not initialize audio player: ${e.localizedMessage}"
            )
        }
    }

    private fun handleTrackCompletion() {
        stopProgressTracker()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isPaused = false,
            playbackProgress = 1f
        )
        // Automatically play next ayah in strict sequential order (1 -> 2 -> 3 -> 4 ...)
        nextAyah()
    }

    fun pause() {
        try {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                stopProgressTracker()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio", e)
        }
    }

    fun resume() {
        try {
            if (mediaPlayer != null && _playbackState.value.isPaused) {
                mediaPlayer?.start()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    isPaused = false
                )
                startProgressTracker()
            } else if (_playbackState.value.currentAyah != null && _playbackState.value.currentSurah != null) {
                playAyah(_playbackState.value.currentSurah!!, _playbackState.value.currentAyah!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio", e)
        }
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else if (_playbackState.value.isPaused) {
            resume()
        } else {
            val surah = _playbackState.value.currentSurah
            val ayah = _playbackState.value.currentAyah ?: currentAyahsList.firstOrNull()
            if (surah != null && ayah != null) {
                playAyah(surah, ayah)
            }
        }
    }

    fun stop() {
        loadJob?.cancel()
        stopProgressTracker()
        releasePlayer()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isPaused = false,
            isLoading = false,
            playbackProgress = 0f,
            currentPositionMs = 0,
            errorMessage = null
        )
    }

    /**
     * Advances strictly to the NEXT sequential Ayah (N -> N + 1).
     * Works seamlessly across Surah and Juz boundaries.
     */
    fun nextAyah() {
        val currentAyah = _playbackState.value.currentAyah ?: return
        val currentSurah = _playbackState.value.currentSurah ?: return

        if (currentAyahsList.isEmpty()) {
            currentAyahsList = IslamicDataSource.getAyahsForSurah(currentSurah)
        }

        val currentIndex = currentAyahsList.indexOfFirst {
            it.surahNumber == currentAyah.surahNumber && it.numberInSurah == currentAyah.numberInSurah
        }

        if (currentIndex in currentAyahsList.indices && currentIndex + 1 < currentAyahsList.size) {
            val nextAyah = currentAyahsList[currentIndex + 1]
            val nextSurah = IslamicDataSource.SURAHS.find { it.number == nextAyah.surahNumber } ?: currentSurah
            playAyah(nextSurah, nextAyah)
        } else {
            // Reached the end of the loaded reading queue
            stop()
        }
    }

    /**
     * Navigates to PREVIOUS Ayah (N -> N - 1) or restarts current Ayah if > 3 seconds elapsed.
     */
    fun previousAyah() {
        val currentAyah = _playbackState.value.currentAyah ?: return
        val currentSurah = _playbackState.value.currentSurah ?: return

        // If played for more than 3 seconds, replay current Ayah from start
        if (_playbackState.value.currentPositionMs > 3000) {
            playAyah(currentSurah, currentAyah)
            return
        }

        if (currentAyahsList.isEmpty()) {
            currentAyahsList = IslamicDataSource.getAyahsForSurah(currentSurah)
        }

        val currentIndex = currentAyahsList.indexOfFirst {
            it.surahNumber == currentAyah.surahNumber && it.numberInSurah == currentAyah.numberInSurah
        }

        if (currentIndex > 0 && currentIndex < currentAyahsList.size) {
            val prevAyah = currentAyahsList[currentIndex - 1]
            val prevSurah = IslamicDataSource.SURAHS.find { it.number == prevAyah.surahNumber } ?: currentSurah
            playAyah(prevSurah, prevAyah)
        } else {
            // Restart first ayah from beginning
            playAyah(currentSurah, currentAyah)
        }
    }

    fun seekTo(progressFraction: Float) {
        try {
            if (mediaPlayer != null) {
                val duration = mediaPlayer!!.duration
                if (duration > 0) {
                    val targetMs = (duration * progressFraction.coerceIn(0f, 1f)).toInt()
                    mediaPlayer!!.seekTo(targetMs)
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = targetMs,
                        playbackProgress = progressFraction
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking audio", e)
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = coroutineScope.launch {
            while (isActive) {
                try {
                    if (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                        val current = mediaPlayer!!.currentPosition
                        val duration = mediaPlayer!!.duration.coerceAtLeast(1)
                        val progress = (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = current,
                            durationMs = duration,
                            playbackProgress = progress
                        )
                    }
                } catch (_: Exception) {}
                delay(100)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                mediaPlayer = null
            }
        } catch (_: Exception) {}
    }

    fun release() {
        stop()
        coroutineScope.cancel()
    }
}
