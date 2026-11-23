# Virtual Pet Runner

Compile a Java pet, open its `.class` file in the runner, and step through its behavior. The runner displays the pet's current fields and logs the full state after each action and tick. It uses standard Java 8 and Swing with no external libraries.

## Quick start

From the extracted project folder, run `java -jar run/PetRunner.jar`, then open `run/VirtualPet.class` from the file chooser. Both precompiled files target Java 8. The source is in `src` if you want to rebuild or edit it.

## Run in jGRASP

1. Extract the ZIP. Open the four Java files in `src` and compile them. Compile `PetRunner.java` from the same folder as `PetClass.java` and `ArgumentsPanel.java` so the compiler can resolve those classes.
2. Run `PetRunner.java`. Choose **Open Pet Class...** and select the compiled `VirtualPet.class` in jGRASP's output location (normally next to `VirtualPet.java` unless configured otherwise).
3. Choose a constructor. Try the three-argument constructor with `Mochi`, `8`, and `7`. Click **5 Ticks**, then **1 Tick**. On tick 6, hunger reaches 8 and health drops from 8 to 7. Try `feed(int)` with 3 portions and compare the log.
4. Try **Run**, adjust the speed, and click **Pause**. Open the same `.class` file again to create a fresh pet. Recompile and reopen a student pet whenever its code changes.

## Run in IntelliJ IDEA

Open the extracted folder as a Java project, set a JDK, and mark `src` as a Sources Root. Build the project, run `PetRunner.main()`, and select the compiled `VirtualPet.class` from the project's compiler output folder. With a newer JDK, select Java 8 as the compilation target if the resulting classes will also be run on Java 8.

## Run and test from a terminal

From the extracted project folder with a JDK on your path:

```sh
mkdir -p out
javac -parameters -d out src/*.java tests/*.java
java -cp out PetRunner
java -Djava.awt.headless=true -cp out PetRunnerTest out/VirtualPet.class
```

The command above targets the JDK you compile with. On JDK 9 or later, add `--release 8` to `javac` to produce Java 8-compatible class files. On JDK 8, use the command as written. The optional `-parameters` flag lets the runner display names such as **portions (int)** in its input forms. Without it, the runner uses labels such as **Argument 1 (int)**.

## Pet class contract

- Write a public, concrete class in the default package and provide at least one public constructor and `public void tick()`. Each call to `tick()` should advance the simulation one step. Keep constructors and actions quick so the window can respond.
- Keep `name`, `health`, `happiness`, and at least one additional field private. Use a conditional rule in `tick()` that connects two pieces of state. For example, hunger above a threshold can reduce health.
- Provide at least two public actions, one with a parameter. Supported inputs are `int`, `double`, `boolean`, and `String`. The runner labels unsupported constructors in the dropdown and disables action buttons with unsupported parameters.
- The runner displays declared instance fields, including private fields. It makes buttons from the pet's declared public instance methods, with dedicated controls for `tick()` and automatic filtering of conventional getters for displayed fields. Action return values appear in the log.
- **Custom Ticks** accepts a positive whole number. The speed slider controls continuous mode and timed batches. While a batch is running, **Stop Batch** cancels its remaining ticks. Argument dialogs temporarily suspend time.
- Open only pet classes you would run as ordinary Java programs. Constructors and methods execute inside the runner.

The sample pet is one possible design. Use the runner to try different starting conditions, then write a small driver that creates two pets and checks that changing one leaves the other alone.
