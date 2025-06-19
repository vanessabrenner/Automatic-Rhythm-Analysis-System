from flask import Flask, request, jsonify
import os
import uuid
import traceback
import warnings
import hashlib
import numpy as np
import librosa
import soundfile as sf

from preprocess import load_all, process_file

# ignora anumite avertismente si output-ul tf
warnings.filterwarnings('ignore')
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

# initializeaza aplicatia flask
app = Flask(__name__)

# defineste folderele pentru fisierele incarcate si cele procesate
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

PROCESSED_FOLDER = "processed"
os.makedirs(PROCESSED_FOLDER, exist_ok=True)

# incarca modelul si trackerele
print("se incarca modelul...")
model, beat_tracker, downbeat_tracker, bar_tracker = load_all()
print("model si trackere incarcate cu succes")

def save_audio_with_clicks(wav_path, beats, downbeats=None, output_name=None):
    """
    adauga click-uri peste audio-ul original si salveaza rezultatul
    """
    y, sr = librosa.load(wav_path, sr=None)

    y_beats = librosa.clicks(times=beats, sr=sr, click_freq=1000.0, click_duration=0.05)
    y_downbeats = librosa.clicks(times=downbeats, sr=sr, click_freq=1500.0, click_duration=0.07) if downbeats is not None else 0

    # ajusteaza toate semnalele la aceeasi lungime
    max_len = max(len(y), len(y_beats), len(y_downbeats) if isinstance(y_downbeats, np.ndarray) else 0)
    y = np.pad(y, (0, max_len - len(y)))
    y_beats = np.pad(y_beats, (0, max_len - len(y_beats)))
    y_downbeats = np.pad(y_downbeats, (0, max_len - len(y_downbeats))) if isinstance(y_downbeats, np.ndarray) else np.zeros(max_len)

    # amesteca semnalul original cu click-urile
    y_mixed = 0.8 * y + 0.2 * y_beats + 0.2 * y_downbeats

    # genereaza calea catre fisierul final
    if not output_name:
        output_name = os.path.basename(wav_path).replace(".wav", "_with_clicks.wav")

    output_path = os.path.join(PROCESSED_FOLDER, output_name)
    sf.write(output_path, y_mixed, sr)
    return output_path

def get_file_hash(file_stream):
    """
    returneaza hash-ul sha256 al fisierului
    """
    hash_func = hashlib.sha256()
    for chunk in iter(lambda: file_stream.read(4096), b""):
        hash_func.update(chunk)
    file_stream.seek(0)
    return hash_func.hexdigest()

@app.route("/predict", methods=["POST"])
def predict():
    """
    endpoint pentru incarcare audio si predictie beat/downbeat/tempo
    """
    print("cerere POST /predict primita.")

    if "file" not in request.files:
        print("nu s-a trimis niciun fisier.")
        return jsonify({"error": "no file uploaded"}), 400

    file = request.files["file"]
    title = request.form.get("title", "fara titlu")
    print(f"piesa primita: {title}")

    file_hash = get_file_hash(file.stream)
    filename = f"{file_hash}.wav"
    filepath = os.path.join(UPLOAD_FOLDER, filename)

    try:
        # salveaza fisierul daca nu exista deja
        if not os.path.exists(filepath):
            print(f"fisier nou salvat la: {filepath}")
            file.save(filepath)
        else:
            print(f"fisierul exista deja: {filepath}")

        # ruleaza procesarea
        print("rulez process_file()...")
        beats, downbeats, tempo, bars = process_file(filepath, model, beat_tracker, downbeat_tracker, bar_tracker)
        print("procesare finalizata")

        beats_times = np.array(beats)
        downbeats_times = np.array([t for t, pos in downbeats if pos == 1])

        # genereaza fisierul audio cu click-uri daca nu exista deja
        processed_path = os.path.join(PROCESSED_FOLDER, f"{file_hash}_with_clicks.wav")
        if not os.path.exists(processed_path):
            processed_path = save_audio_with_clicks(
                wav_path=filepath,
                beats=beats_times,
                downbeats=downbeats_times,
                output_name=f"{file_hash}_with_clicks.wav"
            )
            print(f"piesa cu click-uri salvata la: {processed_path}")
        else:
            print(f"piesa cu click-uri exista deja la: {processed_path}")

        # returneaza rezultatul in format json
        return jsonify({
            "title": title,
            "beats": beats.tolist(),
            "downbeats": [{"time": float(t), "position": int(p)} for t, p in downbeats],
            "tempo": float(tempo[0][0]) if len(tempo) > 0 else None
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0", port=5000)
