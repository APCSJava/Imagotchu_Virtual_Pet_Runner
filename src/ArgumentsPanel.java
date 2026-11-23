import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Window;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Builds the input form shared by constructor and action dialogs. */
final class ArgumentsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Parameter[] parameters;
    private final Component[] inputs;

    ArgumentsPanel(Executable executable) {
        super(new GridLayout(0, 2, 8, 6));
        parameters = executable.getParameters();
        inputs = new Component[parameters.length];
        if (!PetClass.supportsArguments(executable)) {
            add(new JLabel("Supported inputs are int, double, boolean, and String."));
            return;
        }
        if (parameters.length == 0) {
            add(new JLabel("No arguments required."));
        }
        for (int i = 0; i < parameters.length; i++) {
            JLabel label = new JLabel(labelFor(i));
            inputs[i] = parameters[i].getType() == boolean.class
                    ? new JComboBox<>(new Boolean[] {Boolean.FALSE, Boolean.TRUE})
                    : new JTextField(18);
            label.setLabelFor(inputs[i]);
            add(label);
            add(inputs[i]);
        }
    }

    Object[] values() {
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < arguments.length; i++) {
            if (inputs[i] instanceof JComboBox<?>) {
                arguments[i] = ((JComboBox<?>) inputs[i]).getSelectedItem();
                continue;
            }
            String input = ((JTextField) inputs[i]).getText();
            Class<?> type = parameters[i].getType();
            try {
                if (type == int.class) {
                    arguments[i] = Integer.valueOf(input.trim());
                } else if (type == double.class) {
                    double value = Double.parseDouble(input.trim());
                    if (!Double.isFinite(value)) {
                        throw new NumberFormatException();
                    }
                    arguments[i] = value;
                } else {
                    arguments[i] = input;
                }
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Enter a valid value for " + labelFor(i) + ".");
            }
        }
        return arguments;
    }

    private String labelFor(int index) {
        Parameter parameter = parameters[index];
        String name = parameter.isNamePresent() ? parameter.getName() : "Argument " + (index + 1);
        return name + " (" + parameter.getType().getSimpleName() + ")";
    }

    static Object construct(Component parent, PetClass petClass) throws ReflectiveOperationException {
        JComboBox<String> choices = new JComboBox<>();
        for (Constructor<?> constructor : petClass.constructors) {
            choices.addItem(PetClass.signature(constructor)
                    + (PetClass.supportsArguments(constructor) ? "" : " [unsupported inputs]"));
        }
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.add(choices, BorderLayout.NORTH);
        JPanel formContainer = new JPanel(new BorderLayout());
        form.add(formContainer, BorderLayout.CENTER);
        Runnable updateForm = () -> {
            formContainer.removeAll();
            formContainer.add(new ArgumentsPanel(petClass.constructors.get(choices.getSelectedIndex())));
            formContainer.revalidate();
            formContainer.repaint();
            Window window = SwingUtilities.getWindowAncestor(form);
            if (window != null) {
                window.pack();
            }
        };
        choices.addActionListener(event -> updateForm.run());
        updateForm.run();

        while (JOptionPane.showConfirmDialog(parent, form, "Create pet",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            Constructor<?> constructor = petClass.constructors.get(choices.getSelectedIndex());
            if (!PetClass.supportsArguments(constructor)) {
                showInputError(parent, "Choose a constructor with supported input types.");
                continue;
            }
            ArgumentsPanel arguments = (ArgumentsPanel) formContainer.getComponent(0);
            try {
                return constructor.newInstance(arguments.values());
            } catch (IllegalArgumentException error) {
                showInputError(parent, error.getMessage());
            } catch (InvocationTargetException error) {
                showInputError(parent, "Constructor threw " + PetRunner.describe(error));
            }
        }
        return null;
    }

    static Object[] prompt(Component parent, Executable executable) {
        if (executable.getParameterCount() == 0) {
            return new Object[0];
        }
        ArgumentsPanel form = new ArgumentsPanel(executable);
        while (JOptionPane.showConfirmDialog(parent, form, PetClass.signature(executable),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                return form.values();
            } catch (IllegalArgumentException error) {
                showInputError(parent, error.getMessage());
            }
        }
        return null;
    }

    static void showInputError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Check the input", JOptionPane.ERROR_MESSAGE);
    }
}
