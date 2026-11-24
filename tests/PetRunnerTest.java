import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Dependency-free checks for the sample, reflection, input form, and Swing controls. */
public class PetRunnerTest {
    public static void main(String[] args) throws Exception {
        Path file = Paths.get(args.length > 0 ? args[0] : "SamplePet.class");
        try (PetClass petClass = PetClass.load(file)) {
            checkModel(petClass);
        }
        checkForm();
        checkControls(file);
        System.out.println("PetRunner tests passed.");
    }

    private static void checkModel(PetClass petClass) throws Exception {
        check(petClass.type != SamplePet.class, "The selected class needs its own loader.");
        check(petClass.constructors.size() == 3, "Discover all three sample constructors.");

        Object mochi = petClass.type.getConstructor().newInstance();
        Constructor<?> named = petClass.type.getConstructor(String.class);
        Object pip = named.newInstance("Pip");
        Object custom = petClass.type.getConstructor(String.class, int.class, int.class)
                .newInstance("Sora", 4, 6);
        check("4".equals(petClass.snapshot(custom).get("health")), "Use the chosen constructor.");

        Map<String, String> pipBefore = petClass.snapshot(pip);
        check("\"Pip\"".equals(pipBefore.get("name")), "Read a private name.");
        for (int i = 0; i < 6; i++) {
            String expected = i == 5 ? "I'm hungry!" : "";
            check(expected.equals(petClass.tick.invoke(mochi)), "Alert when hunger reaches eight.");
        }
        Map<String, String> afterTicks = petClass.snapshot(mochi);
        check("8".equals(afterTicks.get("hunger")), "Six ticks add six to hunger.");
        check("7".equals(afterTicks.get("health")), "Hunger at eight reduces health.");
        petClass.type.getMethod("feed", int.class).invoke(mochi, 3);
        check("5".equals(petClass.snapshot(mochi).get("hunger")), "Feeding uses its argument.");
        check("".equals(petClass.tick.invoke(mochi)), "Feeding lets hunger fall below the threshold.");
        check("".equals(petClass.tick.invoke(mochi)), "Hunger at seven is below the threshold.");
        check("I'm hungry!".equals(petClass.tick.invoke(mochi)), "Alert again after feeding.");
        check("".equals(petClass.tick.invoke(mochi)), "Staying hungry produces no repeat alert.");
        check(pipBefore.equals(petClass.snapshot(pip)), "Each pet has its own independent state.");
        check(petClass.actions.stream().noneMatch(method -> method.equals(petClass.tick)),
                "The time controls own tick().");
    }

    private static void checkForm() throws Exception {
        onEdt(() -> {
            Method method = InputTypes.class.getMethod("act",
                    int.class, double.class, boolean.class, String.class, String.class);
            ArgumentsPanel form = new ArgumentsPanel(method);
            ((JTextField) form.getComponent(1)).setText("bad");
            try {
                form.values();
                throw new AssertionError("Reject an invalid integer.");
            } catch (IllegalArgumentException expected) {
                // The dialog can display this error and let the user correct the input.
            }
            ((JTextField) form.getComponent(1)).setText("3");
            ((JTextField) form.getComponent(3)).setText(" 2.5 ");
            ((JComboBox<?>) form.getComponent(5)).setSelectedItem(Boolean.TRUE);
            JTextField firstString = (JTextField) form.getComponent(7);
            JTextField secondString = (JTextField) form.getComponent(9);
            firstString.setText(" Song ");
            secondString.setText("\"Say \"hello\"\"");
            Object[] values = form.values();
            check(values[0].equals(3) && values[1].equals(2.5)
                    && values[2].equals(true) && values[3].equals(" Song ")
                    && values[4].equals("Say \"hello\""),
                    "Read all four supported input types.");
            firstString.setText("\"Brian\"");
            secondString.setText("\"\"");
            values = form.values();
            check(values[3].equals("Brian") && values[4].equals(""),
                    "Strip paired outer quotes from each String argument.");
            firstString.setText("\"");
            secondString.setText("\"Brian");
            values = form.values();
            check(values[3].equals("\"") && values[4].equals("\"Brian"),
                    "Preserve unmatched quotes.");

            Constructor<SamplePet> constructor = SamplePet.class.getConstructor(String.class);
            ArgumentsPanel constructorForm = new ArgumentsPanel(constructor);
            JTextField name = (JTextField) constructorForm.getComponent(1);
            name.setText("Brian");
            Object[] unquoted = constructorForm.values();
            name.setText("\"Brian\"");
            check("Brian".equals(unquoted[0]) && unquoted[0].equals(constructorForm.values()[0]),
                    "Constructors receive the same String with or without outer quotes.");
            return null;
        });
    }

    public static class InputTypes {
        public void act(int count, double amount, boolean active, String song, String title) {
        }
    }

    private static void checkControls(Path file) throws Exception {
        PetClass petClass = PetClass.load(file);
        Object pet = petClass.type.getConstructor().newInstance();
        PetRunner runner = onEdt(PetRunner::new);
        try {
            onEdt(() -> {
                check(!button(runner, "Run").isEnabled(), "Start with the time controls disabled.");
                runner.activate(petClass, pet);
                check(log(runner).contains("[Tick 0] Initial state"), "Log the initial state.");
                find(runner, JSlider.class, slider -> true).setValue(10);
                button(runner, "1 Tick").doClick(0);
                check(tickCount(runner) == 1, "Single-step exactly once.");
                button(runner, "5 Ticks").doClick(0);
                return null;
            });
            await(() -> onEdt(() -> button(runner, "1 Tick").isEnabled()));
            onEdt(() -> {
                check(tickCount(runner) == 6, "The five-tick batch logs every step.");
                check(log(runner).contains("hunger=8, name=\"Mochi\"\nI'm hungry!\n"),
                        "Append the alert to the regular tick output.");
                button(runner, "10 Ticks").doClick(0);
                return null;
            });
            await(() -> onEdt(() -> button(runner, "1 Tick").isEnabled()));
            onEdt(() -> {
                check(tickCount(runner) == 16, "The ten-tick batch stops after exactly ten ticks.");
                check(log(runner).indexOf("I'm hungry!") == log(runner).lastIndexOf("I'm hungry!"),
                        "The activity log contains a single hunger alert.");
                button(runner, "sleep()").doClick(0);
                check(log(runner).contains("[Tick 16] sleep() | asleep=true"),
                        "Actions report state without advancing time.");
                button(runner, "Run").doClick(0);
                return null;
            });
            await(() -> onEdt(() -> tickCount(runner) > 16));
            int pausedAt = onEdt(() -> {
                button(runner, "Pause").doClick(0);
                return tickCount(runner);
            });
            Thread.sleep(250);
            check(onEdt(() -> tickCount(runner)) == pausedAt, "Pause stops continuous ticking.");
        } finally {
            onEdt(() -> {
                runner.close();
                return null;
            });
            petClass.close();
        }
    }

    private static JButton button(PetRunner runner, String label) {
        JButton button = find(runner, JButton.class, candidate -> label.equals(candidate.getText()));
        check(button != null, "Find button " + label);
        return button;
    }

    private static String log(PetRunner runner) {
        return find(runner, JTextArea.class, area -> area.getText().startsWith("[Tick 0]")).getText();
    }

    private static int tickCount(PetRunner runner) {
        return (int) java.util.Arrays.stream(log(runner).split("\n"))
                .filter(line -> line.contains("] tick() |")).count();
    }

    private static <T extends Component> T find(Component root, Class<T> type,
            java.util.function.Predicate<T> matches) {
        if (type.isInstance(root) && matches.test(type.cast(root))) {
            return type.cast(root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                T found = find(child, type, matches);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        FutureTask<T> work = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(work);
        return work.get();
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.call()) {
            check(System.nanoTime() < deadline, "Timed out waiting for the Swing timer.");
            Thread.sleep(20);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
