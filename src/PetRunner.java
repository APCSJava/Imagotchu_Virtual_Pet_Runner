import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Desktop launcher and simulator for a compiled virtual pet. */
public final class PetRunner extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int CONTINUOUS = -1;

    private final JButton open = new JButton("Open Pet Class...");
    private final JButton single = new JButton("1 Tick");
    private final JButton five = new JButton("5 Ticks");
    private final JButton ten = new JButton("10 Ticks");
    private final JButton custom = new JButton("Custom Ticks...");
    private final JButton run = new JButton("Run");
    private final JSlider speed = new JSlider(1, 10, 2);
    private final JLabel speedLabel = new JLabel();
    private final JLabel status = new JLabel("Open a compiled pet class to begin.");
    private final JTextArea state = textArea(8, 25);
    private final JTextArea history = textArea(17, 55);
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
    private final Timer timer = new Timer(500, event -> onTimer());

    private PetClass petClass;
    private Object pet;
    private Path lastFolder;
    private long ticks;
    private int remaining;
    private boolean dialogOpen;
    private Map<String, String> previous = Collections.emptyMap();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // The default Swing look and feel is also usable.
            }

            PetRunner runner = new PetRunner();
            JFrame frame = new JFrame("Virtual Pet Runner");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(runner);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    runner.close();
                }
            });
            frame.pack();
            frame.setMinimumSize(new Dimension(700, 450));
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    PetRunner() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING));
        header.add(open);
        header.add(status);
        add(header, BorderLayout.NORTH);

        JSplitPane display = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titledScroll("Current state", state), titledScroll("Activity log", history));
        display.setResizeWeight(0.31);
        add(display, BorderLayout.CENTER);

        // A vertical button list fits signatures of different lengths and can scroll.
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JScrollPane actionScroll = titledScroll("Pet actions", actions);
        actionScroll.setPreferredSize(new Dimension(280, 134));

        JPanel time = new JPanel(new FlowLayout(FlowLayout.LEADING));
        time.setBorder(BorderFactory.createTitledBorder("Time controls"));
        for (JButton button : new JButton[] {single, five, ten, custom, run}) {
            time.add(button);
        }
        speed.setPreferredSize(new Dimension(105, speed.getPreferredSize().height));
        speedLabel.setLabelFor(speed);
        time.add(speed);
        time.add(speedLabel);
        JPanel controls = new JPanel(new BorderLayout(0, 6));
        controls.add(actionScroll, BorderLayout.CENTER);
        controls.add(time, BorderLayout.SOUTH);
        add(controls, BorderLayout.SOUTH);

        open.addActionListener(event -> choosePet());
        single.addActionListener(event -> advanceOne());
        five.addActionListener(event -> start(5));
        ten.addActionListener(event -> start(10));
        custom.addActionListener(event -> customTicks());
        run.addActionListener(event -> {
            if (remaining == 0) {
                start(CONTINUOUS);
            } else {
                stop();
            }
        });
        speed.addChangeListener(event -> updateSpeed());
        timer.setInitialDelay(0);
        updateSpeed();
        updateControls();
    }

    private static JTextArea textArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return area;
    }

    private static JScrollPane titledScroll(String title, Component component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        return scroll;
    }

    private void choosePet() {
        withTimePaused(() -> {
            JFileChooser chooser = lastFolder == null ? new JFileChooser(".")
                    : new JFileChooser(lastFolder.toFile());
            chooser.setDialogTitle("Select a compiled pet class");
            chooser.setFileFilter(new FileNameExtensionFilter("Java class files (*.class)", "class"));
            chooser.setAcceptAllFileFilterUsed(false);
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path file = chooser.getSelectedFile().toPath().toAbsolutePath();
            lastFolder = file.getParent();
            PetClass candidate = null;
            try {
                candidate = PetClass.load(file);
                Object instance = ArgumentsPanel.construct(this, candidate);
                if (instance != null) {
                    activate(candidate, instance);
                    candidate = null; // The active session now owns the loader.
                }
            } catch (NoSuchMethodException error) {
                showError("Declare public String tick() (or public void tick() for an older pet).");
            } catch (UnsupportedClassVersionError error) {
                showError("This class was compiled for a newer Java version. "
                        + "Recompile the pet for this Java version or run the viewer on a newer JDK.");
            } catch (NoClassDefFoundError error) {
                showError("Could not load the class. Use a standalone class in the default package. "
                        + describe(error));
            } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError error) {
                showError(describe(error));
            } finally {
                closeLoader(candidate);
            }
        });
    }

    void activate(PetClass candidate, Object instance) throws IllegalAccessException {
        Map<String, String> initial = candidate.snapshot(instance);
        stop();
        closeLoader(petClass);
        petClass = candidate;
        pet = instance;
        ticks = 0;
        previous = Collections.emptyMap();
        history.setText("");
        actions.removeAll();
        for (Method action : petClass.actions) {
            JButton button = new JButton(PetClass.signature(action));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setEnabled(PetClass.supportsArguments(action));
            if (!button.isEnabled()) {
                button.setToolTipText("Supported inputs are int, double, boolean, and String.");
            }
            button.addActionListener(event -> invokeAction(action));
            actions.add(button);
        }
        if (petClass.actions.isEmpty()) {
            actions.add(new JLabel("No public pet actions found."));
        }
        actions.revalidate();
        actions.repaint();
        record("Initial state", initial, null);
        updateControls();
    }

    private void invokeAction(Method action) {
        withTimePaused(() -> {
            Object[] arguments = ArgumentsPanel.prompt(this, action);
            if (arguments == null) {
                return;
            }
            StringJoiner values = new StringJoiner(", ", action.getName() + "(", ")");
            for (Object argument : arguments) {
                values.add(PetClass.displayValue(argument));
            }
            String event = values.toString();
            String message = null;
            try {
                Object result = action.invoke(pet, arguments);
                if (action.getReturnType() == String.class) {
                    message = (String) result;
                } else if (action.getReturnType() != void.class) {
                    event += " returned " + PetClass.displayValue(result);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                event += " failed: " + describe(error);
            }
            refreshAfter(event, message);
        });
    }

    private void customTicks() {
        withTimePaused(() -> {
            while (true) {
                String value = JOptionPane.showInputDialog(this, "How many ticks?", "Custom Ticks",
                        JOptionPane.QUESTION_MESSAGE);
                if (value == null) {
                    return;
                }
                try {
                    int count = Integer.parseInt(value.trim());
                    if (count > 0) {
                        start(count);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // Show the same validation message for invalid integers and overflow.
                }
                ArgumentsPanel.showInputError(this, "Enter a positive whole number.");
            }
        });
    }

    private void withTimePaused(Runnable operation) {
        timer.stop();
        dialogOpen = true;
        try {
            operation.run();
        } finally {
            dialogOpen = false;
            if (remaining != 0) {
                timer.restart();
            }
        }
    }

    private void start(int count) {
        remaining = count;
        updateControls();
        if (!dialogOpen) {
            timer.start();
        }
    }

    private void stop() {
        timer.stop();
        remaining = 0;
        updateControls();
    }

    private void onTimer() {
        if (remaining == 0 || dialogOpen) {
            return;
        }
        if (advanceOne() && remaining > 0) {
            remaining--;
            if (remaining == 0) {
                stop();
            }
        }
        updateControls();
    }

    private boolean advanceOne() {
        try {
            String message = (String) petClass.tick.invoke(pet);
            ticks++;
            boolean refreshed = refreshAfter("tick()");
            if (message != null && !message.isEmpty()) {
                history.append(message + "\n");
                history.setCaretPosition(history.getDocument().getLength());
                Toolkit.getDefaultToolkit().beep();
            }
            return refreshed;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            stop();
            refreshAfter("tick() failed: " + describe(error));
            return false;
        }
    }

    private boolean refreshAfter(String event) {
        return refreshAfter(event, null);
    }

    private boolean refreshAfter(String event, String message) {
        try {
            record(event, petClass.snapshot(pet), message);
            updateControls();
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            stop();
            history.append("[Tick " + ticks + "] " + event
                    + " | Could not read state: " + describe(error) + "\n");
            history.setCaretPosition(history.getDocument().getLength());
            return false;
        }
    }

    private void record(String event, Map<String, String> snapshot, String message) {
        StringBuilder current = new StringBuilder();
        StringJoiner logValues = new StringJoiner(", ");
        boolean changed = false;
        for (Map.Entry<String, String> field : snapshot.entrySet()) {
            String name = field.getKey();
            String value = field.getValue();
            boolean fieldChanged = previous.containsKey(name) && !Objects.equals(previous.get(name), value);
            current.append(name).append(" = ").append(value);
            if (fieldChanged) {
                current.append("   [was ").append(previous.get(name)).append(']');
                changed = true;
            }
            current.append('\n');
            logValues.add(name + "=" + value);
        }
        state.setText(current.length() > 0 ? current.toString() : "No instance fields.");
        state.setCaretPosition(0);
        String unchanged = !changed && !event.equals("Initial state") ? " (no state change)" : "";
        String entry = message != null && !message.isEmpty()
                ? message
                : event + " | " + (snapshot.isEmpty() ? "No instance fields" : logValues.toString())
                        + unchanged;
        history.append("[Tick " + ticks + "] " + entry + "\n");
        history.setCaretPosition(history.getDocument().getLength());
        previous = snapshot;
    }

    private void updateSpeed() {
        int rate = speed.getValue();
        timer.setDelay(1000 / rate);
        speedLabel.setText(rate + (rate == 1 ? " tick/s" : " ticks/s"));
    }

    private void updateControls() {
        boolean loaded = petClass != null;
        boolean paused = remaining == 0;
        for (JButton button : new JButton[] {single, five, ten, custom}) {
            button.setEnabled(loaded && paused);
        }
        run.setEnabled(loaded);
        speed.setEnabled(loaded);
        run.setText(paused ? "Run" : remaining == CONTINUOUS ? "Pause" : "Stop Batch");
        if (loaded) {
            String mode = paused ? "Paused" : remaining == CONTINUOUS ? "Running" : remaining + " left";
            status.setText(petClass.type.getSimpleName() + "  |  Tick " + ticks + "  |  " + mode);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Could not open pet", JOptionPane.ERROR_MESSAGE);
    }

    static String describe(Throwable error) {
        if (error instanceof InvocationTargetException && error.getCause() != null) {
            error = error.getCause();
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    void close() {
        stop();
        closeLoader(petClass);
    }

    private static void closeLoader(PetClass loadedClass) {
        if (loadedClass != null) {
            try {
                loadedClass.close();
            } catch (IOException error) {
                System.err.println("Could not close pet class: " + error.getMessage());
            }
        }
    }
}
