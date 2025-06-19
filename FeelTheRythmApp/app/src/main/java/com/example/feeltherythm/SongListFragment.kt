package com.example.feeltherythm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.File

class SongListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // incarca layout-ul fragmentului care contine recycler view si mesajul de lipsa
        return inflater.inflate(R.layout.fragment_song_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // referinte catre componentele vizuale din layout
        val recyclerView = view.findViewById<RecyclerView>(R.id.songRecyclerView)
        val emptyMessage = view.findViewById<TextView>(R.id.emptyMessage)

        // seteaza layout vertical pentru recycler view
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // creeaza lista unde vor fi incarcate piesele gasite
        val songsDir = File(requireContext().filesDir, "songs")
        val songs = mutableListOf<Song>()

        // parcurge fisierele din directorul "songs"
        songsDir.listFiles()?.forEach { file ->
            if (file.extension == "wav") {
                val title = file.nameWithoutExtension
                val audioPath = file.absolutePath
                val imagePath = File(songsDir, "$title.png").takeIf { it.exists() }?.absolutePath
                val jsonFile = File(songsDir, "$title.json")
                var tempo: Double? = null
                var beatsCount = 0

                // daca exista fisier json asociat, extrage tempo si numarul de batai
                if (jsonFile.exists()) {
                    val content = jsonFile.readText()
                    val json = JSONObject(content)
                    tempo = json.optDouble("tempo", -1.0)
                    beatsCount = json.optJSONArray("beats")?.length() ?: 0
                }

                // adauga piesa in lista
                songs.add(Song(title, audioPath, imagePath, tempo, beatsCount))
            }
        }

        // afiseaza lista sau mesajul de gol in functie de continut
        if (songs.isEmpty()) {
            emptyMessage.visibility = View.VISIBLE
        } else {
            emptyMessage.visibility = View.GONE
            recyclerView.adapter = SongAdapter(songs)
        }
    }
}
