
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class StepSequencer {

    private volatile boolean playing = false;
    private Thread playbackThread;
    private JToggleButton[][] cells = new JToggleButton[4][16];
    private static final int VOICES_PER_INSTRUMENT = 4;
    private int currentStep = -1;
    private Color playheadColor = Color.YELLOW;
    private volatile int bpm = 120;

    private Clip[][] instrumentClips
            = new Clip[4][VOICES_PER_INSTRUMENT];

    private int[] nextVoice = new int[4];

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StepSequencer sequencer = new StepSequencer();

            try {
                sequencer.loadSamples();
                sequencer.createWindow();
            } catch (IOException | UnsupportedAudioFileException
                    | LineUnavailableException e) {
                e.printStackTrace();

                JOptionPane.showMessageDialog(
                        null,
                        "Could not load audio samples: " + e.getMessage(),
                        "Audio Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private Clip loadClip(String resourcePath)
            throws IOException, UnsupportedAudioFileException,
            LineUnavailableException {

        InputStream fileStream
                = getClass().getResourceAsStream(resourcePath);

        if (fileStream == null) {
            throw new IOException("Sample not found: " + resourcePath);
        }

        try (AudioInputStream originalStream
                = AudioSystem.getAudioInputStream(fileStream)) {

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100f,
                    16,
                    2,
                    4,
                    44100f,
                    false
            );

            try (AudioInputStream convertedStream
                    = AudioSystem.getAudioInputStream(
                            targetFormat, originalStream)) {

                Clip clip = AudioSystem.getClip();

                try {
                    clip.open(convertedStream);
                    return clip;
                } catch (LineUnavailableException | IOException
                        | IllegalArgumentException e) {
                    clip.close();
                    throw e;
                }
            }
        }
    }

    private void loadSamples()
            throws IOException, UnsupportedAudioFileException,
            LineUnavailableException {

        String[] samplePaths = {
            "/samples/kicks/Kick 1.wav",
            "/samples/snares/Snare 1.wav",
            "/samples/hi-hats/Hi-Hat 1.wav",
            "/samples/claps/Clap 1.wav"
        };

        for (int instrument = 0; instrument < 4; instrument++) {
            for (int voice = 0; voice < VOICES_PER_INSTRUMENT; voice++) {
                instrumentClips[instrument][voice]
                        = loadClip(samplePaths[instrument]);
            }
        }
    }

    private void playClip(int instrument) {
        int voice = nextVoice[instrument];

        Clip clip = instrumentClips[instrument][voice];

        nextVoice[instrument]
                = (voice + 1) % VOICES_PER_INSTRUMENT;

        if (clip != null) {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }

    private void createWindow() {
        JFrame frame = new JFrame("Step Sequencer");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 350);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        mainPanel.setBorder(
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );

        String[] instruments = {
            "Kick", "Snare", "Hi-hat", "Clap"
        };

        for (int j = 0; j < instruments.length; j++) {
            String instrument = instruments[j];
            JPanel row = new JPanel(new BorderLayout(10, 0));

            JLabel label = new JLabel(instrument);
            label.setPreferredSize(new Dimension(110, 35));
            label.setFont(new Font("Arial", Font.BOLD, 14));

            JPanel steps = new JPanel(new GridLayout(1, 16, 4, 0));

            for (int i = 0; i < 16; i++) {
                JToggleButton cell = new JToggleButton();

                cells[j][i] = cell;
                cell.setBackground(Color.LIGHT_GRAY);
                cell.setOpaque(true);
                cell.setBorderPainted(false);
                cell.setFocusPainted(false);

                cell.addActionListener(e -> {
                    if (cell.isSelected()) {
                        cell.setBackground(Color.BLUE);
                    } else {
                        cell.setBackground(Color.LIGHT_GRAY);
                    }
                });

                steps.add(cell);
            }

            row.add(label, BorderLayout.WEST);
            row.add(steps, BorderLayout.CENTER);
            mainPanel.add(row);
        }

        JSlider tempoSlider = new JSlider(60, 200, 120);
        JLabel tempoLabel = new JLabel("Tempo: 120 BPM");

        tempoSlider.setMajorTickSpacing(20);
        tempoSlider.setMinorTickSpacing(5);
        tempoSlider.setPaintTicks(true);
        tempoSlider.setPaintLabels(true);

        tempoSlider.addChangeListener(e -> {
            bpm = tempoSlider.getValue();
            tempoLabel.setText("Tempo: " + bpm + " BPM");
        });

        JButton playButton = new JButton("Play");
        JButton stopButton = new JButton("Stop");

        playButton.addActionListener(e -> startPlayback());
        stopButton.addActionListener(e -> stopPlayback());

        JPanel controls = new JPanel();

        controls.add(tempoLabel);
        controls.add(tempoSlider);
        controls.add(playButton);
        controls.add(stopButton);

        JPanel content = new JPanel(new BorderLayout());
        content.add(mainPanel, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);

        frame.add(content);
        frame.setVisible(true);
    }

    private void updatePlayhead(int newStep) {
        SwingUtilities.invokeLater(() -> {
            // Restore the previous column's normal colors.
            if (currentStep != -1) {
                for (int instrument = 0; instrument < 4; instrument++) {
                    JToggleButton cell = cells[instrument][currentStep];

                    cell.setBackground(cell.isSelected()
                            ? Color.BLUE
                            : Color.LIGHT_GRAY);
                }
            }

            currentStep = newStep;

            // A value of -1 means playback has stopped.
            if (currentStep == -1) {
                return;
            }

            // Highlight the current column.
            for (int instrument = 0; instrument < 4; instrument++) {
                cells[instrument][currentStep]
                        .setBackground(playheadColor);
            }
        });
    }

    private void startPlayback() {
        if (playing) {
            return;
        }

        playing = true;

        playbackThread = new Thread(() -> {
            int step = 0;

            // 120 BPM, with 16 steps per bar.
            long stepDuration = 60_000_000_000L / (bpm * 4L);
            long nextStepTime = System.nanoTime();

            while (playing) {
                updatePlayhead(step);

                for (int instrument = 0; instrument < 4; instrument++) {
                    if (cells[instrument][step].isSelected()) {
                        playClip(instrument);
                    }
                }

                step = (step + 1) % 16;

                nextStepTime += 60_000_000_000L / (bpm * 4L);

                long remaining = nextStepTime - System.nanoTime();

                if (remaining > 0) {
                    try {
                        Thread.sleep(
                                remaining / 1_000_000,
                                (int) (remaining % 1_000_000)
                        );
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    nextStepTime = System.nanoTime();
                }
            }
        });

        playbackThread.start();
    }

    private void stopPlayback() {
        playing = false;

        if (playbackThread != null) {
            playbackThread.interrupt();
        }

        updatePlayhead(-1);
    }
}
