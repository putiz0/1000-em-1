package com.playbox.offline.core.designsystem.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import kotlinx.coroutines.flow.StateFlow

/**
 * Preferências de áudio do aplicativo (Ajustes → "Som e música").
 * Os arquivos de áudio (res/raw, gerados por tools/make-audio.ps1) são
 * 100% originais — nenhuma música/efeito de terceiros é usado no projeto.
 */
data class AudioPrefs(
    val soundOn: Boolean = true,
    val musicOn: Boolean = true
)

/**
 * Runtime de áudio dos jogos:
 *  - efeitos curtos ([playSfx]) via SoundPool (latência baixa, polifonia);
 *  - trilha em loop ([startMusic]/[stopMusic]) via MediaPlayer.
 *
 * Os recursos são resolvidos por nome (`sfx_<nome>.wav`, `music_<tag>.wav`
 * em res/raw) e protegidos do resource shrinker por res/raw/keep.xml.
 * Um recurso ausente só vira silêncio — nunca um crash.
 */
class GameAudio(
    context: Context,
    private val prefsFlow: StateFlow<AudioPrefs>
) : AutoCloseable {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setMaxStreams(6)
        .build()

    /** nome do recurso → id no SoundPool (-1 = recurso não existe). */
    private val soundIds = HashMap<String, Int>()

    private var musicPlayer: MediaPlayer? = null
    private var musicTag: String? = null
    private var musicSeq = 0

    /** Toca um efeito curto: "move", "capture", "stone", "dice", "card", "win", "lose"... */
    fun playSfx(sound: String, volume: Float = 1f) {
        if (!prefsFlow.value.soundOn) return
        val resName = "sfx_$sound"
        val soundId = soundIds.getOrPut(resName) {
            val resId = rawId(resName)
            if (resId == 0) -1 else soundPool.load(appContext, resId, 1)
        }
        if (soundId != -1) {
            val v = volume.coerceIn(0f, 1f)
            soundPool.play(soundId, v, v, 1, 0, 1f)
        }
    }

    /**
     * Toca a trilha do jogo em loop. Chame no LaunchedEffect da tela do jogo
     * (mesma tag em re-entradas não reinicia a trilha — continuidade).
     */
    fun startMusic(tag: String) {
        if (!prefsFlow.value.musicOn) return
        if (musicTag == tag) return
        val mySeq = ++musicSeq
        musicTag = tag
        stopMusicPlayer()
        val resId = rawId("music_$tag")
        if (resId == 0) {
            musicTag = null
            return
        }
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val afd = appContext.resources.openRawResourceFd(resId)
            try {
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                afd.close()
            }
            player.isLooping = true
            player.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            player.setOnPreparedListener { mp ->
                // só toca se ninguém trocou de trilha/parou enquanto preparava
                if (musicSeq == mySeq && musicTag == tag && prefsFlow.value.musicOn) {
                    mp.start()
                } else {
                    try { mp.release() } catch (_: Exception) {}
                }
            }
            player.setOnErrorListener { mp, _, _ ->
                if (musicPlayer === mp) {
                    musicPlayer = null
                    musicTag = null
                }
                try { mp.release() } catch (_: Exception) {}
                true
            }
            player.prepareAsync()
            musicPlayer = player
        } catch (_: Exception) {
            musicPlayer = null
            musicTag = null
        }
    }

    /** Para a trilha (saindo da tela do jogo, desligou música em Ajustes...). */
    fun stopMusic() {
        musicSeq++
        musicTag = null
        stopMusicPlayer()
    }

    private fun stopMusicPlayer() {
        val old = musicPlayer
        musicPlayer = null
        if (old != null) {
            try { old.stop() } catch (_: Exception) {}
            try { old.release() } catch (_: Exception) {}
        }
    }

    override fun close() {
        stopMusic()
        try { soundPool.release() } catch (_: Exception) {}
    }

    private fun rawId(name: String): Int =
        appContext.resources.getIdentifier(name, "raw", appContext.packageName)

    private companion object {
        const val MUSIC_VOLUME = 0.55f
    }
}
