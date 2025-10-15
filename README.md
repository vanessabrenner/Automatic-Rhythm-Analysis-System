# Intelligent System for Automatic Musical Rhythm Detection

**Bachelor’s Thesis** – Babeș-Bolyai University, Faculty of Mathematics and Computer Science  
**Author:** Brenner Vanessa Noemi  
**Scientific Supervisor:** Conf. Dr. Dan Mircea Suciu  
**Year:** 2025

---

## General Description

This project presents an **intelligent system for the automatic detection of musical rhythm**, capable of identifying **beats**, **downbeats**, and **tempo** within audio tracks using a deep learning architecture based on **Temporal Convolutional Networks (TCN)**.

The trained model is integrated into an **Android mobile application** that converts musical rhythm into **tactile vibrations**, allowing individuals with **hearing impairments**, **tone deafness**, or **no musical training** to **experience music through touch**.

---

## System Architecture

### Main Components

#### 1. Python Server (Flask + TensorFlow/Keras)
- Receives audio files from the Android app  
- Converts the audio to WAV format  
- Generates a **log-mel spectrogram**  
- Runs the trained **TCN model** to predict beats, downbeats, and tempo  
- Returns the results as a **JSON response**

#### 2. Android Application (Kotlin)
- Allows users to select and upload local songs  
- Sends the chosen file to the Flask server for analysis  
- Receives beat, downbeat, and tempo information  
- Plays the song with **synchronized vibrations** (downbeats trigger stronger vibrations)
- Displays a list of locally processed songs with title and cover image  

#### 3. AI Model (Temporal Convolutional Network – TCN)
- **Input:** log-mel spectrogram  
- **Output:** binary vector for beat/downbeat moments + tempo value  
- **Training datasets:** GTZAN, Ballroom, and other MIR datasets  

---

For more details, you can read the full paper:  
👉 [Sistem inteligent pentru detectarea automată a ritmului muzical](https://acrobat.adobe.com/id/urn:aaid:sc:EU:3474b25d-ca07-4437-8983-7a50079ae185)

