
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

/**
 * Threading model: - Swing thread (EDT): all UI components, currentStep,
 * playbackThread - Playback thread: reads activeSteps (patternLock), bpm
 * (volatile), plays clips (audioLock). - patternLock guards activeSteps. -
 * audioLock guards instrumentClips, nextVoice and closed.
 */
public class StepSequencer {

    private volatile boolean playing = false;
    private volatile Thread playbackThread;
    private JToggleButton[][] cells = new JToggleButton[4][16];
    private final Object patternLock = new Object();
    private final boolean[][] activeSteps = new boolean[4][16];
    private static final int VOICES_PER_INSTRUMENT = 4;
    private int currentStep = -1;
    private final Color playheadColor = Color.YELLOW;
    private volatile int bpm = 120;

    @SuppressWarnings("unchecked")
    private JComboBox<String>[] sampleSelectors = new JComboBox[4];

    private final String[][] samplePaths = {
        {
            "/samples/kicks/Kick 1.wav",
            "/samples/kicks/Kick 2.wav",
            "/samples/kicks/Kick 3.wav"
        },
        {
            "/samples/snares/Snare 1.wav",
            "/samples/snares/Snare 2.wav",
            "/samples/snares/Snare 3.wav"
        },
        {
            "/samples/hi-hats/Hi-Hat 1.wav",
            "/samples/hi-hats/Hi-Hat 2.wav",
            "/samples/hi-hats/Hi-Hat 3.wav"
        },
        {
            "/samples/claps/Clap 1.wav",
            "/samples/claps/Clap 2.wav",
            "/samples/claps/Clap 3.wav"
        }
    };

    private final String[][] sampleNames = {
        {"Kick 1", "Kick 2", "Kick 3"},
        {"Snare 1", "Snare 2", "Snare 3"},
        {"Hi-Hat 1", "Hi-Hat 2", "Hi-Hat 3"},
        {"Clap 1", "Clap 2", "Clap 3"}
    };

    private final Object audioLock = new Object();
    private final Clip[][] instrumentClips = new Clip[4][];
    private final int[] nextVoice = new int[4];
    private boolean closed = false;

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

        for (int instrument = 0; instrument < 4; instrument++) {
            loadInstrumentSamples(instrument, 0);
        }
    }

    private void loadInstrumentSamples(int instrument, int sampleIndex)
            throws IOException, UnsupportedAudioFileException,
            LineUnavailableException {

        Clip[] newVoices = new Clip[VOICES_PER_INSTRUMENT];

        try {
            for (int voice = 0; voice < VOICES_PER_INSTRUMENT; voice++) {
                newVoices[voice]
                        = loadClip(samplePaths[instrument][sampleIndex]);
            }
        } catch (IOException | UnsupportedAudioFileException
                | LineUnavailableException | RuntimeException e) {
            closeAll(newVoices);
            throw e;
        }

        Clip[] oldVoices;

        synchronized (audioLock) {
            if (closed) {
                oldVoices = newVoices;
            } else {
                oldVoices = instrumentClips[instrument];
                instrumentClips[instrument] = newVoices;
                nextVoice[instrument] = 0;
            }
        }

        closeAll(oldVoices);
    }

    private static void closeAll(Clip[] clips) {
        if (clips == null) {
            return;
        }

        for (Clip clip : clips) {
            if (clip != null) {
                clip.close();
            }
        }
    }

    private void playClip(int instrument) {

        synchronized (audioLock) {
            if (closed) {
                return;
            }

            Clip[] voices = instrumentClips[instrument];

            if (voices == null) {
                return;
            }

            int voice = nextVoice[instrument];
            nextVoice[instrument] = (voice + 1) % VOICES_PER_INSTRUMENT;

            Clip clip = voices[voice];

            if (clip != null) {
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            }
        }
    }

    private void createWindow() {
        JFrame frame = new JFrame("Step Sequencer");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeSamples();
            }
        });
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
            final int instrumentIndex = j;
            JPanel row = new JPanel(new BorderLayout(10, 0));

            JLabel label = new JLabel(instrument);
            label.setFont(new Font("Arial", Font.BOLD, 14));

            JComboBox<String> sampleSelector
                    = new JComboBox<>(sampleNames[j]);

            sampleSelectors[j] = sampleSelector;

            sampleSelector.addActionListener(e -> {
                try {
                    loadInstrumentSamples(
                            instrumentIndex, sampleSelector.getSelectedIndex()
                    );
                } catch (IOException | UnsupportedAudioFileException
                        | LineUnavailableException ex) {

                    JOptionPane.showMessageDialog(
                            null,
                            "Could not load sample: " + ex.getMessage(),
                            "Audio Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });

            JPanel instrumentControls = new JPanel(
                    new FlowLayout(FlowLayout.LEFT, 8, 0)
            );

            instrumentControls.setPreferredSize(new Dimension(230, 35));
            instrumentControls.add(label);
            instrumentControls.add(sampleSelector);

            JPanel steps = new JPanel(new GridLayout(1, 16, 4, 0));

            for (int i = 0; i < 16; i++) {
                JToggleButton cell = new JToggleButton();

                cells[j][i] = cell;
                final int stepIndex = i;
                cell.setBackground(Color.LIGHT_GRAY);
                cell.setOpaque(true);
                cell.setBorderPainted(false);
                cell.setFocusPainted(false);

                cell.addActionListener(e -> {
                    boolean selected = cell.isSelected();

                    synchronized (patternLock) {
                        activeSteps[instrumentIndex][stepIndex] = selected;
                    }

                    if (selected) {
                        cell.setBackground(Color.BLUE);
                    } else {
                        cell.setBackground(Color.LIGHT_GRAY);
                    }
                });

                steps.add(cell);
            }

            row.add(instrumentControls, BorderLayout.WEST);
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

            if (currentStep != -1) {
                for (int instrument = 0; instrument < 4; instrument++) {
                    JToggleButton cell = cells[instrument][currentStep];

                    cell.setBackground(cell.isSelected()
                            ? Color.BLUE
                            : Color.LIGHT_GRAY);
                }
            }

            currentStep = newStep;

            if (currentStep == -1) {
                return;
            }

            for (int instrument = 0; instrument < 4; instrument++) {
                cells[instrument][currentStep]
                        .setBackground(playheadColor);
            }
        });
    }

    private void startPlayback() {
        Thread existing = playbackThread;

        if (playing || (existing != null && existing.isAlive())) {
            return;
        }

        playing = true;

        for (JComboBox<String> selector : sampleSelectors) {
            selector.setEnabled(false);
        }

        Thread thread = new Thread(this::runPlayback,
                "StepSequencer-Playback");
        thread.setDaemon(true);
        playbackThread = thread;
        thread.start();
    }

    private void runPlayback() {
        Thread thisThread = Thread.currentThread();
        boolean[] hits = new boolean[4];

        try {
            int step = 0;
            long nextStepTime = System.nanoTime();

            while (playing) {
                updatePlayhead(step);

                synchronized (patternLock) {
                    for (int instrument = 0; instrument < 4; instrument++) {
                        hits[instrument] = activeSteps[instrument][step];
                    }
                }

                for (int instrument = 0; instrument < 4; instrument++) {
                    if (hits[instrument]) {
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
        } finally {
            playing = false;

            SwingUtilities.invokeLater(() -> {

                if (playbackThread == thisThread) {
                    updatePlayhead(-1);

                    for (JComboBox<String> selector : sampleSelectors) {
                        selector.setEnabled(true);
                    }
                }
            });
        }
    }

    private void stopPlayback() {
        playing = false;

        Thread thread = playbackThread;

        if (thread != null) {
            thread.interrupt();
        }
    }

    private void closeSamples() {
        playing = false;

        Thread thread = playbackThread;

        if (thread != null) {
            thread.interrupt();
        }

        Clip[][] toClose = new Clip[4][];

        synchronized (audioLock) {
            closed = true;

            for (int i = 0; i < 4; i++) {
                toClose[i] = instrumentClips[i];
                instrumentClips[i] = null;
            }
        }

        for (Clip[] voices : toClose) {
            closeAll(voices);
        }
    }
}
