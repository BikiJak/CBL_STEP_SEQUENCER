
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
    private Clip[] instrumentClips = new Clip[4];

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

        instrumentClips[0] = loadClip("/samples/kicks/Kick 1.wav");
        instrumentClips[1] = loadClip("/samples/snares/Snare 1.wav");
        instrumentClips[2] = loadClip("/samples/hi-hats/Hi-Hat 1.wav");
        instrumentClips[3] = loadClip("/samples/claps/Clap 1.wav");
    }

    private void playClip(int instrument) {
        Clip clip = instrumentClips[instrument];

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

        JButton playButton = new JButton("Play");
        JButton stopButton = new JButton("Stop");

        playButton.addActionListener(e -> startPlayback());
        stopButton.addActionListener(e -> stopPlayback());

        JPanel controls = new JPanel();
        controls.add(playButton);
        controls.add(stopButton);

        JPanel content = new JPanel(new BorderLayout());
        content.add(mainPanel, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);

        frame.add(content);
        frame.setVisible(true);
    }

    private void startPlayback() {
        if (playing) {
            return;
        }

        playing = true;

        playbackThread = new Thread(() -> {
            int step = 0;

            while (playing) {

                for (int instrument = 0; instrument < 4; instrument++) {
                    if (cells[instrument][step].isSelected()) {
                        playClip(instrument);
                    }
                }

                System.out.println("Step " + (step + 1));

                step = (step + 1) % 16;

                try {
                    Thread.sleep(125);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
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
    }
}
