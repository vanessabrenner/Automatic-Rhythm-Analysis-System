package com.example.feeltherythm

import android.graphics.BitmapFactory
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import android.view.MotionEvent

class SongAdapter(private val songs: List<Song>) :
    RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.songTitle)
        val image: ImageView = view.findViewById(R.id.songImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]

        // afiseaza titlul
        holder.title.text = song.title

        // incarca imaginea daca exista, altfel seteaza imagine default
        if (!song.imagePath.isNullOrEmpty()) {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(song.imagePath, options)

            val scale = maxOf(options.outWidth / 300, options.outHeight / 300).coerceAtLeast(1)

            val scaledOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }

            val bitmap = BitmapFactory.decodeFile(song.imagePath, scaledOptions)
            holder.image.setImageBitmap(bitmap)
        } else {
            holder.image.setImageResource(R.drawable.default_cover)
        }

        // efect vizual la apasare (scalare)
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            false
        }

        // navigare catre ecranul de player la click pe item
        holder.itemView.setOnClickListener {
            val action = SongListFragmentDirections.actionSongListFragmentToSongPlayerFragment(
                title = song.title,
                audioPath = song.audioPath,
                imagePath = song.imagePath ?: ""
            )
            holder.itemView.findNavController().navigate(action)
        }
    }

    override fun getItemCount(): Int = songs.size
}
