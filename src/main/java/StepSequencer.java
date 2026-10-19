
import javax.swing.*;
import java.awt.*;

public class StepSequencer {

    private volatile boolean playing = false;
    private Thread playbackThread;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new StepSequencer().createWindow();
        });
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
            "Kick", "Clap", "Hi-hat", "Open Hi-hat"
        };

        for (String instrument : instruments) {
            JPanel row = new JPanel(new BorderLayout(10, 0));

            JLabel label = new JLabel(instrument);
            label.setPreferredSize(new Dimension(110, 35));
            label.setFont(new Font("Arial", Font.BOLD, 14));

            JPanel steps = new JPanel(new GridLayout(1, 16, 4, 0));

            for (int i = 0; i < 16; i++) {
                JToggleButton cell = new JToggleButton();

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
                System.out.println("Step " + (step + 1));

                step = (step + 1) % 16;

                try {
                    Thread.sleep(250);
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
