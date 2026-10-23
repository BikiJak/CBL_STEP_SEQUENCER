
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

    // ---------------------------------------------------------------
    // Theme
    // ---------------------------------------------------------------
    private static final Color BG = new Color(0x13131A);
    private static final Color PANEL = new Color(0x1D1D27);
    private static final Color OFF = new Color(0x262633);
    private static final Color OFF_ALT = new Color(0x2F2F40);
    private static final Color TEXT = new Color(0xE6E6F0);
    private static final Color DIM = new Color(0x777789);
    private static final Color ACCENT = new Color(0x3DDC97);

    // Kick, Snare, Hi-hat, Clap
    private static final Color[] INSTRUMENT_COLORS = {
        new Color(0xFF5C8A),
        new Color(0xFFB454),
        new Color(0x4FD1C5),
        new Color(0x8B7CF6)
    };

    private static final String FONT = Font.SANS_SERIF;

    // ---------------------------------------------------------------
    // State
    // ---------------------------------------------------------------
    private volatile boolean playing = false;
    private volatile Thread playbackThread;
    private StepButton[][] cells = new StepButton[4][16];
    private final Object patternLock = new Object();
    private final boolean[][] activeSteps = new boolean[4][16];
    private static final int VOICES_PER_INSTRUMENT = 4;
    private int currentStep = -1;
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
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

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

    // ---------------------------------------------------------------
    // Audio
    // ---------------------------------------------------------------
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

    // ---------------------------------------------------------------
    // UI
    // ---------------------------------------------------------------
    private void createWindow() {
        JFrame frame = new JFrame("Step Sequencer");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeSamples();
            }
        });
        frame.setSize(1100, 420);
        frame.setMinimumSize(new Dimension(900, 340));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);

        JPanel mainPanel = new JPanel(new GridLayout(4, 1, 0, 10));
        mainPanel.setOpaque(false);

        String[] instruments = {
            "Kick", "Snare", "Hi-hat", "Clap"
        };

        for (int j = 0; j < instruments.length; j++) {
            final int instrumentIndex = j;
            Color accent = INSTRUMENT_COLORS[j];

            JPanel row = new JPanel(new BorderLayout(16, 0));
            row.setOpaque(false);

            JLabel label = new JLabel(instruments[j]);
            label.setFont(new Font(FONT, Font.BOLD, 14));
            label.setForeground(accent);
            label.setPreferredSize(new Dimension(56, 30));

            JComboBox<String> sampleSelector
                    = new JComboBox<>(sampleNames[j]);
            sampleSelector.setFont(new Font(FONT, Font.PLAIN, 13));
            sampleSelector.setBackground(PANEL);
            sampleSelector.setForeground(TEXT);
            sampleSelector.setFocusable(false);
            sampleSelector.setPreferredSize(new Dimension(112, 30));

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

            JPanel instrumentControls = new JPanel(new GridBagLayout());
            instrumentControls.setOpaque(false);
            instrumentControls.setPreferredSize(new Dimension(200, 35));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 0, 0, 10);
            instrumentControls.add(label, gbc);
            gbc.insets = new Insets(0, 0, 0, 0);
            instrumentControls.add(sampleSelector, gbc);

            JPanel steps = new JPanel(new GridLayout(1, 4, 16, 0));
            steps.setOpaque(false);

            for (int group = 0; group < 4; group++) {
                JPanel groupPanel = new JPanel(new GridLayout(1, 4, 5, 0));
                groupPanel.setOpaque(false);

                for (int k = 0; k < 4; k++) {
                    final int stepIndex = group * 4 + k;
                    StepButton cell
                            = new StepButton(accent, group % 2 == 0);

                    cells[j][stepIndex] = cell;

                    cell.addActionListener(e -> {
                        boolean selected = cell.isSelected();

                        synchronized (patternLock) {
                            activeSteps[instrumentIndex][stepIndex]
                                    = selected;
                        }
                    });

                    groupPanel.add(cell);
                }

                steps.add(groupPanel);
            }

            row.add(instrumentControls, BorderLayout.WEST);
            row.add(steps, BorderLayout.CENTER);
            mainPanel.add(row);
        }

        JSlider tempoSlider = new JSlider(60, 200, 120);
        tempoSlider.setOpaque(false);
        tempoSlider.setFocusable(false);
        tempoSlider.setPreferredSize(new Dimension(220, 30));

        JLabel tempoLabel = new JLabel("120 BPM", SwingConstants.RIGHT);
        tempoLabel.setFont(new Font(FONT, Font.BOLD, 16));
        tempoLabel.setForeground(TEXT);
        tempoLabel.setPreferredSize(new Dimension(90, 30));

        tempoSlider.addChangeListener(e -> {
            bpm = tempoSlider.getValue();
            tempoLabel.setText(bpm + " BPM");
        });

        FlatButton playButton = new FlatButton("Play", ACCENT, BG);
        FlatButton stopButton = new FlatButton("Stop", OFF_ALT, TEXT);

        playButton.addActionListener(e -> startPlayback());
        stopButton.addActionListener(e -> stopPlayback());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(playButton);
        left.add(stopButton);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(tempoSlider);
        right.add(tempoLabel);

        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        controls.add(left, BorderLayout.WEST);
        controls.add(right, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        content.add(mainPanel, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);

        frame.add(content);
        frame.setVisible(true);
    }

    private void updatePlayhead(int newStep) {
        SwingUtilities.invokeLater(() -> {

            if (currentStep != -1) {
                for (int instrument = 0; instrument < 4; instrument++) {
                    cells[instrument][currentStep].setPlayhead(false);
                }
            }

            currentStep = newStep;

            if (currentStep == -1) {
                return;
            }

            for (int instrument = 0; instrument < 4; instrument++) {
                cells[instrument][currentStep].setPlayhead(true);
            }
        });
    }

    // ---------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------
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

    // ---------------------------------------------------------------
    // Custom-painted components
    // ---------------------------------------------------------------
    private static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    /**
     * A rounded step cell.
     */
    private static class StepButton extends JToggleButton {

        private final Color accent;
        private final boolean lightOff;
        private boolean playhead;

        StepButton(Color accent, boolean lightOff) {
            this.accent = accent;
            this.lightOff = lightOff;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setPlayhead(boolean value) {
            if (playhead != value) {
                playhead = value;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth();
            int h = getHeight();
            int arc = 12;

            Color base = isSelected() ? accent : (lightOff ? OFF_ALT : OFF);

            if (getModel().isRollover()) {
                base = blend(base, Color.WHITE, isSelected() ? 0.15f : 0.08f);
            }

            g2.setColor(base);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            if (playhead) {
                g2.setColor(new Color(255, 255, 255,
                        isSelected() ? 110 : 45));
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }

            g2.dispose();
        }
    }

    /**
     * Flat rounded button for Play / Stop.
     */
    private static class FlatButton extends JButton {

        private final Color bg;

        FlatButton(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(new Font(FONT, Font.BOLD, 13));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setRolloverEnabled(true);
            setPreferredSize(new Dimension(92, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            Color c = bg;

            if (getModel().isPressed()) {
                c = blend(bg, Color.BLACK, 0.2f);
            } else if (getModel().isRollover()) {
                c = blend(bg, Color.WHITE, 0.12f);
            }

            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();

            super.paintComponent(g);
        }
    }
}
