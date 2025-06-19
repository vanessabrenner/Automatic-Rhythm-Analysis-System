import numpy as np
import tensorflow as tf
from keras.models import load_model
from keras import backend as K
from tensorflow_addons.optimizers import RectifiedAdam, Lookahead

import madmom
from madmom.audio.signal import Signal
from madmom.features.beats import DBNBeatTrackingProcessor
from madmom.features.downbeats import DBNDownBeatTrackingProcessor
from madmom.features.downbeats import DBNBarTrackingProcessor
from scipy.interpolate import interp1d
from scipy.signal import argrelmax
from scipy.ndimage import gaussian_filter1d, maximum_filter1d

# parametri globali
FPS = 100
MASK_VALUE = -1.0
FFT_SIZE = 2048
NUM_BANDS = 12

# define pre-processor folosit atat la antrenare cat si la inferenta
from madmom.processors import SequentialProcessor
from madmom.audio.signal import SignalProcessor, FramedSignalProcessor
from madmom.audio.stft import ShortTimeFourierTransformProcessor
from madmom.audio.spectrogram import FilteredSpectrogramProcessor, LogarithmicSpectrogramProcessor

class PreProcessor(SequentialProcessor):
    def __init__(self, frame_size=FFT_SIZE, num_bands=NUM_BANDS, log=np.log, add=1e-6, fps=FPS):
        sig = SignalProcessor(num_channels=1, sample_rate=44100)
        frames = FramedSignalProcessor(frame_size=frame_size, fps=fps)
        stft = ShortTimeFourierTransformProcessor()
        filt = FilteredSpectrogramProcessor(num_bands=num_bands)
        spec = LogarithmicSpectrogramProcessor(log=log, add=add)
        super(PreProcessor, self).__init__((sig, frames, stft, filt, spec, np.array))
        self.fps = fps

# defineste o functie de pierdere care ignora valorile mascate (fara anotari)
def build_masked_loss(loss_function, mask_value=MASK_VALUE):
    def masked_loss_function(y_true, y_pred):
        mask = K.cast(K.not_equal(y_true, mask_value), K.floatx())
        return loss_function(y_true * mask, y_pred * mask)
    return masked_loss_function

masked_loss_function = build_masked_loss(K.binary_crossentropy, MASK_VALUE)

# incarca modelul si trackerele pentru beat, downbeat si masura
def load_all():
    model = load_model(
        "model/model_best.h5",
        custom_objects={
            "RectifiedAdam": RectifiedAdam,
            "masked_loss_function": masked_loss_function
        }
    )

    beat_tracker = DBNBeatTrackingProcessor(
        min_bpm=55.0, max_bpm=215.0, fps=FPS, transition_lambda=100, threshold=0.05
    )

    downbeat_tracker = DBNDownBeatTrackingProcessor(
        beats_per_bar=[3, 4], min_bpm=55.0, max_bpm=215.0, fps=FPS, transition_lambda=100
    )

    bar_tracker = DBNBarTrackingProcessor(
        beats_per_bar=(3, 4), meter_change_prob=1e-3, observation_weight=4
    )

    return model, beat_tracker, downbeat_tracker, bar_tracker

# detecteaza tempo-ul estimand varfurile din histograma de activare
def detect_tempo(bins, hist_smooth=11, min_bpm=10):
    min_bpm = int(np.floor(min_bpm))
    tempi = np.arange(min_bpm, len(bins))
    bins = bins[min_bpm:]

    if hist_smooth > 0:
        bins = madmom.audio.signal.smooth(bins, hist_smooth)

    interpolation_fn = interp1d(tempi, bins, 'quadratic')
    tempi = np.arange(tempi[0], tempi[-1], 0.001)
    bins = interpolation_fn(tempi)

    peaks = argrelmax(bins, mode='wrap')[0]
    if len(peaks) == 0:
        return np.array([])

    sorted_peaks = peaks[np.argsort(bins[peaks])[::-1]]
    strengths = bins[sorted_peaks]
    strengths /= np.sum(strengths)
    return np.array(list(zip(tempi[sorted_peaks], strengths)))[:2]

# initializare preprocesor global
pre_processor = PreProcessor()

# functie de procesare completa a unui fisier audio
def process_file(filepath, model, beat_tracker, downbeat_tracker, bar_tracker=None):
    signal = Signal(filepath, sample_rate=44100, num_channels=1)
    features = pre_processor(signal)
    x = features[np.newaxis, ..., np.newaxis]

    # predictie activari
    beats_act, downbeats_act, tempo_act = model.predict(x)
    beats_act = beats_act.squeeze()
    downbeats_act = downbeats_act.squeeze()
    tempo_act = tempo_act.squeeze()

    # netezire activari
    beats_act_smooth = gaussian_filter1d(beats_act, sigma=0.5)
    downbeats_act_smooth = gaussian_filter1d(downbeats_act, sigma=0.5)

    # detectie beat-uri
    beats = beat_tracker(beats_act_smooth)

    # combinare activari pentru detectia downbeat-urilor
    beat_prob = np.maximum(beats_act_smooth - downbeats_act_smooth, 0)
    downbeat_prob = downbeats_act_smooth
    combined = np.column_stack((beat_prob, downbeat_prob))

    try:
        downbeats = downbeat_tracker(combined)
    except Exception as e:
        print(f"eroare la detectarea downbeat-urilor: {e}")
        downbeats = []

    # optional: detectie masuri
    bars = []
    if bar_tracker is not None and beats.size > 0:
        try:
            beat_idx = (beats * FPS).astype(int)
            bar_act = maximum_filter1d(downbeats_act_smooth, size=3)
            bar_act = bar_act[beat_idx]
            bar_input = np.vstack((beats, bar_act)).T
            bars = bar_tracker(bar_input)
        except Exception as e:
            print(f"eroare la detectarea masurilor: {e}")

    # detectie tempo
    tempo = detect_tempo(tempo_act)

    print("beats:", beats)
    print("downbeats:", downbeats)
    for time, position in downbeats:
        print(f" - timp: {time:.2f}s, pozitie in masura: {int(position)}")
    print("tempo: ", tempo)

    return beats, downbeats, tempo, bars
