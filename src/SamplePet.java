/**
 * A sample pet for trying out PetRunner. Health, happiness, and hunger
 * use a scale from 0 to 10. A higher hunger value means a hungrier pet.
 */
public class SamplePet {
    private static final int HUNGRY_THRESHOLD = 8;

    private final String name;
    private int health;
    private int happiness;
    private int hunger;
    private boolean asleep;

    public SamplePet() {
        this("Mochi");
    }

    public SamplePet(String name) {
        this(name, 8, 7);
    }

    public SamplePet(String name, int health, int happiness) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Give the pet a name.");
        }
        this.name = name.trim();
        this.health = bounded(health);
        this.happiness = bounded(happiness);
        hunger = 2;
        asleep = false;
    }

    /** Advance the simulation by one tick and return any new attention message. */
    public String tick() {
        boolean wasHungry = hunger >= HUNGRY_THRESHOLD;
        hunger = bounded(hunger + 1);
        boolean hungry = hunger >= HUNGRY_THRESHOLD;
        if (hungry) {
            health = bounded(health - 1);
        }
        if (!asleep) {
            happiness = bounded(happiness - 1);
        }
        if (!wasHungry && hungry) {
            return "I'm hungry!";
        }
        return "";
    }

    public void feed(int portions) {
        if (portions < 1 || portions > 10) {
            throw new IllegalArgumentException("Enter 1 to 10 portions.");
        }
        if (!asleep) {
            hunger = bounded(hunger - portions);
        }
    }

    public void play() {
        if (!asleep && hunger < HUNGRY_THRESHOLD) {
            happiness = bounded(happiness + 2);
            hunger = bounded(hunger + 1);
        }
    }

    public String singTo(String song) {
        if (song.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a song title.");
        }
        if (asleep) {
            return name + " slept through the song.";
        }
        happiness = bounded(happiness + 1);
        return name + " listened to \"" + song + ".\"";
    }

    public void sleep() {
        asleep = true;
    }

    public void wakeUp() {
        asleep = false;
    }

    private static int bounded(int value) {
        return Math.max(0, Math.min(10, value));
    }
}
