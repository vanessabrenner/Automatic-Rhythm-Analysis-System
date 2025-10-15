# Sistem inteligent pentru detectarea automată a ritmului muzical

Lucrare de licență – Universitatea Babeș-Bolyai, Facultatea de Matematică și Informatică  
**Autor:** Brenner Vanessa Noemi  
**Coordonator științific:** conf. dr. Dan Mircea Suciu  
**An:** 2025

---

## Descriere generală

Această lucrare propune un **sistem inteligent de detectare automată a ritmului muzical**, care identifică **beat-urile**, **downbeat-urile** și **tempo-ul** unei piese audio folosind rețele neuronale profunde de tip **Temporal Convolutional Network (TCN)**.  

Modelul antrenat este integrat într-o **aplicație Android** care transformă ritmul muzicii în **vibrații tactile**, permițând persoanelor cu deficiențe de auz, fără ureche muzicală sau fără instruirea necesară să **perceapă muzica prin simț tactil**.

---

## Arhitectura sistemului

### Componente principale

1. **Server Python (Flask + TensorFlow/Keras):**
   - Primește fișierul audio de la aplicația Android.
   - Convertește fișierul în format WAV.
   - Generează spectrograma log-mel.
   - Rulează modelul TCN antrenat pentru predicția beat-urilor, downbeat-urilor și tempo-ului.
   - Trimite rezultatele înapoi în format JSON.

2. **Aplicația Android (Kotlin):**
   - Permite selectarea și încărcarea unei piese locale.
   - Trimite fișierul către server pentru analiză.
   - Primește informațiile despre beat, downbeat și tempo.
   - Redă melodia cu **vibrații sincronizate** (downbeat = vibrație mai puternică).
   - Permite listarea și redarea pieselor procesate local.

3. **Modelul AI (TCN):**
   - Input: log-mel spectrogram.
   - Output: vector binar al momentelor de beat/downbeat + valoare tempo.
   - Antrenare pe dataset-uri precum **GTZAN**, **Ballroom** și altele din domeniul MIR.

---

Mai multe detalii [aici](https://acrobat.adobe.com/id/urn:aaid:sc:EU:3474b25d-ca07-4437-8983-7a50079ae185).

