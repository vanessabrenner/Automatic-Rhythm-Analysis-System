package com.example.feeltherythm

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.*
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class SongPlayerFragment : Fragment() {

    // componente media si vizuale
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var seekBar: SeekBar
    private lateinit var textCurrentTime: TextView
    private lateinit var textTotalTime: TextView
    private lateinit var buttonPlay: ImageView
    private lateinit var buttonPause: ImageView
    private lateinit var buttonStop: ImageView
    private lateinit var playerImage: ImageView
    private lateinit var playerTitle: TextView
    private lateinit var textTempo: TextView


    // handler si vibrator pentru vibratii sincronizate
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var vibrator: Vibrator
    private val triggeredBeats = mutableSetOf<Int>()
    private var beatList: List<Pair<Double, Int>> = listOf()

    // primeste argumentele de navigare (titlu, calea audio, imagine)
    private val args: SongPlayerFragmentArgs by navArgs()

    // runnable care actualizeaza timpul si aplica vibratii pe beat
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                val currentSec = mediaPlayer.currentPosition / 1000.0
                seekBar.progress = mediaPlayer.currentPosition
                textCurrentTime.text = formatTime(mediaPlayer.currentPosition)

                // parcurge lista de batai si declanseaza vibratia daca suntem aproape de un beat
                beatList.forEachIndexed { index, (time, pos) ->
                    if (!triggeredBeats.contains(index) && kotlin.math.abs(currentSec - time) < 0.05) {
                        val duration = if (pos == 1) 100 else 30
                        val amplitude = if (pos == 1) 255 else 100

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(duration.toLong(), amplitude))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(duration.toLong())
                        }

                        triggeredBeats.add(index)
                    }
                }

                handler.postDelayed(this, 25)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // incarca layout-ul fragmentului de player
        return inflater.inflate(R.layout.fragment_song_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // initializare componente vizuale
        playerTitle = view.findViewById(R.id.playerTitle)
        playerImage = view.findViewById(R.id.playerImage)
        seekBar = view.findViewById(R.id.seekBar)
        textCurrentTime = view.findViewById(R.id.textCurrentTime)
        textTotalTime = view.findViewById(R.id.textTotalTime)
        buttonPlay = view.findViewById(R.id.buttonPlay)
        buttonPause = view.findViewById(R.id.buttonPause)
        buttonStop = view.findViewById(R.id.buttonStop)
        textTempo = view.findViewById(R.id.textTempo)

        // animatie la incarcare
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_slide_up)
        playerImage.startAnimation(anim)
        playerTitle.startAnimation(anim)
        seekBar.startAnimation(anim)
        textCurrentTime.startAnimation(anim)
        textTotalTime.startAnimation(anim)
        buttonPlay.startAnimation(anim)
        buttonPause.startAnimation(anim)
        buttonStop.startAnimation(anim)
        textTempo.startAnimation(anim)

        // initializeaza vibratorul
        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // seteaza titlul si imaginea piesei
        playerTitle.text = args.title
        args.imagePath?.let {
            val imgFile = File(it)
            if (imgFile.exists()) {
                playerImage.setImageBitmap(BitmapFactory.decodeFile(it))
            }
        }

        // pregateste playerul audio
        mediaPlayer = MediaPlayer().apply {
            setDataSource(args.audioPath)
            prepare()
        }

        // seteaza durata maxima a seekbar-ului si afiseaza timpul total
        seekBar.max = mediaPlayer.duration
        textTotalTime.text = formatTime(mediaPlayer.duration)

        // incarca bataile si downbeat-urile din fisierul json
        loadBeats(args.title)

        // buton play
        buttonPlay.setOnClickListener {
            mediaPlayer.start()
            handler.post(updateRunnable)
        }

        // buton pauza
        buttonPause.setOnClickListener {
            mediaPlayer.pause()
        }

        // buton stop: opreste playback-ul si reseteaza la inceput
        buttonStop.setOnClickListener {
            mediaPlayer.pause()
            mediaPlayer.seekTo(0)
            seekBar.progress = 0
            textCurrentTime.text = "0:00"
            triggeredBeats.clear()
        }

        // control manual al pozitiei in melodie
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                    textCurrentTime.text = formatTime(progress)
                    triggeredBeats.clear()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // formateaza timpul in minute:secunde
    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    // incarca bataile si marcheaza downbeat-urile (pozitie 1) din json
    private fun loadBeats(title: String) {
        val file = File(requireContext().filesDir, "songs/$title.json")
        if (!file.exists()) return

        val json = JSONObject(file.readText())
        val rawBeats = json.optJSONArray("beats")
        val rawDownbeats = json.optJSONArray("downbeats")
        val tempo = json.optDouble("tempo", -1.0)

        val beats = mutableListOf<Pair<Double, Int>>()

        for (i in 0 until (rawBeats?.length() ?: 0)) {
            val time = rawBeats!!.getDouble(i)
            beats.add(time to 2) // 2 = beat normal
        }

        for (i in 0 until (rawDownbeats?.length() ?: 0)) {
            val obj = rawDownbeats!!.getJSONObject(i)
            val time = obj.getDouble("time")
            val pos = obj.getInt("position")
            if (pos == 1) {
                beats.removeAll { kotlin.math.abs(it.first - time) < 0.01 }
                beats.add(time to 1) // 1 = downbeat
            }
        }

        beatList = beats.sortedBy { it.first }

        // afiseaza tempo-ul daca este valid
        if (tempo > 0) {
            textTempo.text = "Tempo: %.1f BPM".format(tempo)
        }
    }


    // elibereaza resursele la iesirea din fragment
    override fun onDestroyView() {
        view?.let { fragmentView ->
            val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_slide_down)
            fragmentView.startAnimation(anim)
        }

        handler.removeCallbacks(updateRunnable)

        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }

        super.onDestroyView()
    }
}
