# Pocket Session — Native Android Music Sketchpad & Workstation

**Pocket Session** is a fully functional, native Android music-production application inspired by the creative immediacy, touch ergonomics, and workflow of Ableton Note. It features real-time audio playback and synthesis, drum sequencing, retrospective performance capture, a piano-roll MIDI editor, 8-track by 8-scene clip launching, track mixing, and structured Ableton Live 12 export.

---

## Architecture Overview

Pocket Session is built on Clean Architecture / MVVM principles using Kotlin and Jetpack Compose:

```
com.example/
├── MainActivity.kt                  // App root, navigation container, share provider
├── core/
│   ├── model/MusicModel.kt         // Musical domain models (Project, Track, Clip, NoteEvent, SynthParams)
│   ├── music/MusicalTimeUtils.kt   // 960 PPQ timebase, frame/tick/BPM conversions, pitch-to-freq
│   ├── audio/
│   │   ├── RealtimeAudioEngine.kt  // Low-latency AudioTrack streaming, voice pool, soft limiter, offline WAV
│   │   └── StereoDelayEffect.kt    // Stereo delay DSP line with tempo sync & damping
│   ├── synthesis/
│   │   ├── DrumSynthGenerator.kt   // 44.1kHz synthetic starter drum kit (Kick, Snare, Hats, Toms, Rim)
│   │   ├── SynthVoice.kt           // Polyphonic voice with 4 waveforms, SVF lowpass filter, ADSR
│   │   └── PresetDatabase.kt       // Structured preset library with Live 12 target device mappings
│   ├── scheduler/
│   │   └── MusicalScheduler.kt     // Master clock driven by audio render thread, clip launch quantization
│   ├── persistence/
│   │   └── ProjectRepository.kt    // Atomic file persistence, JSON serialization, demo project
│   ├── midi/
│   │   └── MidiFileExporter.kt     // Standard MIDI File (SMF Type 1) multitrack exporter (960 PPQ)
│   └── interop/
│       └── AbletonLiveTransferExporter.kt // .pocketbundle ZIP packager & live-transfer.json generator
├── feature/
│   ├── library/ProjectLibraryScreen.kt    // Sketch browser, duplicate, delete, and session cards
│   ├── session/SessionViewScreen.kt      // 8x8 Clip Launch Grid, scene triggers, stop buttons
│   ├── instrument/InstrumentViewScreen.kt// 4x4 drum pads, scale-constrained melodic pads, sound controls
│   ├── editor/MidiPianoRollScreen.kt     // Interactive touch piano roll, multi-step undo/redo, quantization
│   ├── mixer/MixerScreen.kt              // 8-track faders, pan, solo/mute, stereo master meters
│   └── settings/ProjectSettingsScreen.kt // Tap tempo, musical key & scale modes, export actions
└── ui/
    ├── theme/ (Color.kt, Theme.kt)       // Dark minimal charcoal aesthetic with neon accents
    └── components/TopTransportBar.kt     // Shared transport bar with play/record/capture/meters
```

---

## Core Subsystems

### 1. Real-Time Audio Engine (`RealtimeAudioEngine.kt`)
- Streams continuously using an `AudioTrack` configured with low-latency attributes (`USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`, 16-bit PCM stereo, 44.1 kHz).
- Executes on a dedicated high-priority audio thread (`THREAD_PRIORITY_URGENT_AUDIO`) decoupled from Compose UI recompositions.
- Polyphonic voice pool with anti-click voice stealing.
- Master bus includes a soft-saturation limiter (`tanh` shaping) to prevent harsh digital clipping under loud polyphony.
- Peak meters are calculated from real audio buffers.

### 2. High-Precision Master Musical Clock (`MusicalScheduler.kt`)
- Master timing is derived directly from sample frames rendered in `onRenderBlock`, guaranteeing zero clock drift.
- Supports integer musical ticks at 960 PPQ.
- Handles Bar, Beat, and None launch quantization for seamless loop transitions across varying clip lengths (1, 2, 4, 8, 16 bars).
- Implements **Retrospective MIDI Capture**: maintains an in-memory rolling ring buffer of played note-on/off events so ideas can be played freely and captured into a looping clip afterwards with a single tap.

### 3. Built-in Instruments & Synthesis
- **Drum Engine**: Synthetic kick, snare, open/closed hi-hats, clap, low/mid/hi toms, rim, and perc generated in pure 16-bit PCM audio. Hi-hats support choke groups.
- **Polyphonic Subtractive Synthesizer**: 4 waveforms (Sine, Triangle, Sawtooth, Square), detuned sub-oscillator, resonant 12dB/oct Chamberlin State Variable Filter (SVF), and smooth ADSR envelope.
- **Presets**: Includes Warm Analog Pad, Deep Sub Bass, Acid Reso Bass, Soft Dream Keys, 80s Punch Lead, and Organic Pluck.

### 4. Interactive MIDI Piano Roll (`MidiPianoRollScreen.kt`)
- Touch-to-insert, select, and quantize notes.
- Multi-step Undo and Redo history stack.
- Grid snap resolutions (1/4, 1/8, 1/16, 1/32).
- Real-time animated playhead synchronized to master audio transport.

### 5. Ableton Live 12 Interoperability
- Exports standalone `.pocketbundle` ZIP archives containing:
  - `multitrack.mid`: Spec-compliant Standard MIDI File (SMF Type 1) with 960 PPQ division, microsecond tempo meta events, time signature markers, and individual track names.
  - `live-transfer.json`: Structured session manifest mapping tracks, clips, and scene positions to native Ableton Live 12 devices (Drift, Analog, Drum Rack, Simpler).
  - `project.json`: Session document allowing re-importing into Pocket Session.
  - `README-LIVE-TRANSFER.txt`: Step-by-step instructions for opening the bundle in Ableton Live 12 on Windows.
- Also supports exporting master stereo 44.1 kHz 16-bit PCM WAV mixdowns.

---

## Replacing the Audio Engine with Oboe / AAudio in Android Studio

To substitute the Kotlin `AudioTrack` layer with an Oboe C++ native implementation later:
1. In Android Studio, add `implementation("com.google.oboe:oboe:1.9.0")` and configure CMake in `build.gradle.kts`.
2. Wrap `SynthVoice` and `DrumSynthGenerator` in a C++ `AudioStreamDataCallback` rendering into `oboe::AudioStream`.
3. Keep `MusicalScheduler` and `MusicModel` as the authoritative Kotlin state layer, passing note-on and note-off events across JNI.
