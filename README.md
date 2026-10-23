# Step Sequencer

## Description

The Step Sequencer is a Java application built with Swing that allows users to create and play drum patterns. The interface contains four instrument tracks, each with 16 steps that can be activated or deactivated by clicking the buttons to create a rhythm.

Users can adjust the tempo and choose between different audio samples for each instrument.

## Features

- Four instrument tracks: Kick, Snare, Hi-hat and Clap
- A 16-step grid for creating rhythmic patterns
- Play and Stop controls
- Adjustable tempo from 60 to 200 BPM
- Three selectable samples for each instrument
- Visual indication of the current playback step
- Clear Pattern button to reset the grid

## Requirements

- Java Development Kit (JDK): 25 or newer
- Apache Maven
- The project's audio samples, included in the resources directory

## How to Run

### Using NetBeans

1. Open the project in NetBeans.
2. Allow Maven to load the project and its dependencies.
3. Locate the `StepSequencer` main class.
4. Run the project.

## How to Use

1. Click the cells in the grid to activate or deactivate individual steps.
2. Select a sample from the dropdown menu next to each instrument.
3. Adjust the tempo using the BPM slider.
4. Press **Play** to start the sequence.
5. Press **Stop** to stop playback.
6. Press **Clear** to deactivate all steps and reset the pattern.

## Project Structure

- `src/main/java/` — Java source code
- `src/main/resources/samples/` — Audio samples used by the sequencer
- `pom.xml` — Maven project configuration
