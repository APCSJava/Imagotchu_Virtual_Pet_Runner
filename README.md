# Virtual Pet Runner

Compile a Java pet, open its `.class` file in the runner, and step through its behavior. The runner displays the pet's current fields and logs the full state after each action and tick. It uses standard Java 8 and Swing with no external libraries.

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

- Write a public, concrete class in the default package and provide at least one public constructor and `public String tick()`. Each call to `tick()` should advance the simulation one step. Keep constructors and actions quick so the window can respond.
- Return `""` when there is no new alert. Return an attention message to have the runner beep once and add it to the Activity log. Use `\n` between updates, as in `"I'm hungry!\nI need a nap!"`, to display them on separate lines. Returning the message lets the caller choose how to present it.
- The runner also loads older pets with `public void tick()`. Those ticks advance the simulation silently.
- Keep `name`, `health`, `happiness`, and at least one additional field private. Use a conditional rule in `tick()` that connects two pieces of state. For example, hunger above a threshold can reduce health.
- Provide at least two public actions, one with a parameter. Supported inputs are `int`, `double`, `boolean`, and `String`. The runner labels unsupported constructors in the dropdown and disables action buttons with unsupported parameters.
- For any `String` argument in a constructor or action, type `Brian` or `"Brian"`. Both pass the same value. The runner removes one pair of outer double quotes and keeps the text inside as typed.
- The runner displays declared instance fields, including private fields. It makes buttons from the pet's declared public instance methods, with dedicated controls for `tick()` and automatic filtering of conventional getters for displayed fields. When an action returns a non-empty `String`, its Activity log entry shows the tick number and the message. The Current state pane still updates.
- **Custom Ticks** accepts a positive whole number. The speed slider controls continuous mode and timed batches. While a batch is running, **Stop Batch** cancels its remaining ticks. Argument dialogs temporarily suspend time.
- Open only pet classes you would run as ordinary Java programs. Constructors and methods execute inside the runner.

The sample pet is one possible design. Use the runner to try different starting conditions, then write a small driver that creates two pets and checks that changing one leaves the other alone.
