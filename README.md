# Virtual Pet Runner

Compile a Java pet, open its `.class` file in the runner, and step through its behavior. The runner displays the pet's current fields and records ticks and actions in the Activity log. It uses standard Java 8 and Swing with no external libraries.

## Quick start

From the extracted project folder, run `java -jar run/PetRunner.jar`, then open `run/SamplePet.class` from the file chooser. Both precompiled files target Java 8. The source is in `src` if you want to rebuild or edit it.

## Run in jGRASP

1. Extract the ZIP. Open the four Java files in `src` and compile them. Compile `PetRunner.java` from the same folder as `PetClass.java` and `ArgumentsPanel.java` so the compiler can resolve those classes.
2. Run `PetRunner.java`. Choose **Open Pet Class...** and select the compiled `SamplePet.class` in jGRASP's output location (normally next to `SamplePet.java` unless configured otherwise).
3. Choose a constructor. Try the three-argument constructor with `Mochi`, `8`, and `7`. Click **5 Ticks**, then **1 Tick**. On tick 6, hunger reaches 8, health drops from 8 to 7, and the runner beeps and logs “I'm hungry!” Try `feed(int)` with 3 portions and compare the log.
4. Try **Run**, adjust the speed, and click **Pause**. Open the same `.class` file again to create a fresh pet. Recompile and reopen a student pet whenever its code changes.

## Run in IntelliJ IDEA

Open the extracted folder as a Java project, set a JDK, and mark `src` as a Sources Root. Build the project, run `PetRunner.main()`, and select the compiled `SamplePet.class` from the project's compiler output folder. With a newer JDK, select Java 8 as the compilation target if the resulting classes will also be run on Java 8.

## Run and test from a terminal

From the extracted project folder with a JDK on your path:

```sh
mkdir -p out
javac -parameters -d out src/*.java tests/*.java
java -cp out PetRunner
java -Djava.awt.headless=true -cp out PetRunnerTest out/SamplePet.class
```

The command above targets the JDK you compile with. On JDK 9 or later, add `--release 8` to `javac` to produce Java 8-compatible class files. On JDK 8, use the command as written. The optional `-parameters` flag lets the runner display names such as **portions (int)** in its input forms. Without it, the runner uses labels such as **Argument 1 (int)**.

## Pet class contract

- Write a public, concrete, top-level class in the default package.
- The runner needs one usable public constructor. When you declare no constructors, Java supplies a public no-argument constructor for your public class. If you write your own constructors, at least one must be public and take either no arguments or arguments of type `int`, `double`, `boolean`, or `String`.
- Declare `public String tick()` in your pet class. This is the only required method. Each call represents one step in the simulation. Return `""` for a step with no alert, or return a message to have the runner beep once and add it to the Activity log. Use `\n` for separate lines in a message.

Save this minimal pet as `MyPet.java`, compile it, and open `MyPet.class` in the runner.

```java
public class MyPet {
    public String tick() {
        return "";
    }
}
```

## Design ideas

- Give your pet private fields such as `name`, `health`, `happiness`, or `hunger`. Try a rule that connects two fields, such as reducing health when hunger reaches a threshold.
- Add public actions such as `feed(int portions)` or `play()`. Try alternate constructors for different starting conditions.
- Write a small driver that creates two pets and checks that changing one leaves the other alone.

## Runner behavior

- The Current state pane displays declared instance fields, including private fields. The runner turns declared public instance methods into action buttons, skipping conventional field getters and giving `tick()` its own controls. A non-empty `String` returned by an action appears with the tick number in the Activity log.
- The runner labels unsupported constructors and disables action buttons with unsupported parameter types. For a `String` argument, `Brian` and `"Brian"` pass the same value. The runner removes one pair of outer double quotes and keeps the text inside as typed.
- **Custom Ticks** accepts a positive whole number. The speed slider controls continuous mode and timed batches. **Stop Batch** cancels the remaining ticks in a batch. Argument dialogs temporarily suspend time.
- Keep constructors and actions quick so the window can respond. Open only pet classes you would run as ordinary Java programs. Constructors and methods execute inside the runner.
