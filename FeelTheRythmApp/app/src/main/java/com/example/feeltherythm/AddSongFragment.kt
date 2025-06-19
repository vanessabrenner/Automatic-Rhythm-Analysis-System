package com.example.feeltherythm

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.*
import java.util.concurrent.TimeUnit

class AddSongFragment : Fragment() {

    private var audioUri: Uri? = null
    private var imageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_add_song, container, false)

        val songImage = view.findViewById<ImageView>(R.id.songImage)
        val songTitle = view.findViewById<EditText>(R.id.songTitle)
        val audioStatus = view.findViewById<TextView>(R.id.audioStatus)
        val uploadButton = view.findViewById<Button>(R.id.uploadSong)
        val progressBar = view.findViewById<ProgressBar>(R.id.uploadProgress)
        val uploadStatus = view.findViewById<TextView>(R.id.uploadStatus)

        val anim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.fade_slide_up)
        view.startAnimation(anim)

        // selector pentru fisier audio
        val selectAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                audioUri = it
                audioStatus.text = getFileNameFromUri(it)
                val rawName = getFileNameFromUri(it)
                val cleanTitle = File(rawName).nameWithoutExtension
                audioStatus.text = rawName
                songTitle.setText(cleanTitle)

            }
        }

        // selector pentru imagine asociata
        val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imageUri = it
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                songImage.setImageBitmap(bitmap)
            }
        }

        view.findViewById<Button>(R.id.selectAudio).setOnClickListener {
            selectAudioLauncher.launch("audio/*")
        }

        songImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        // butonul de upload
        uploadButton.setOnClickListener {
            if (audioUri == null) {
                Toast.makeText(requireContext(), "selecteaza un fisier audio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val title = songTitle.text.toString().ifBlank { "piesa" }

            val tempFile = File(requireContext().cacheDir, "upload_song.wav")
            requireContext().contentResolver.openInputStream(audioUri!!)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            uploadSongToServer(tempFile, title, progressBar, uploadStatus) { response ->
                saveSongLocally(title, tempFile, response)
            }
        }

        return view
    }

    // obtine numele fisierului din uri
    private fun getFileNameFromUri(uri: Uri): String {
        var name = "piesa"
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    // salveaza local piesa si metadatele ei
    private fun saveSongLocally(title: String, file: File, serverResponse: String) {
        val songsDir = File(requireContext().filesDir, "songs").apply { mkdirs() }

        file.copyTo(File(songsDir, "$title.wav"), overwrite = true)
        File(songsDir, "$title.json").writeText(serverResponse)

        imageUri?.let { uri ->
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(File(songsDir, "$title.png")).use { output ->
                    input.copyTo(output)
                }
            }
        }

        Toast.makeText(requireContext(), "The song was saved.", Toast.LENGTH_SHORT).show()
    }

    // trimite piesa catre serverul flask pentru procesare
    private fun uploadSongToServer(
        file: File,
        title: String,
        progressBar: ProgressBar,
        statusView: TextView,
        onSuccess: (String) -> Unit
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()


        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
            .addFormDataPart("title", title)
            .build()

        val request = Request.Builder()
            .url("http://192.168.0.113:5000/predict") // TREBUIE MODIFICAT
            .post(requestBody)
            .build()

        progressBar.visibility = View.VISIBLE
        statusView.text = "Wait a moment..."

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusView.text = "eroare: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "{}"
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusView.text = "Finish upload!"
                    onSuccess(body)
                }
            }
        })
    }
}
