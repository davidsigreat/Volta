package io.github.davidsigreat.volta.configs;

/** How kG applies. */
public enum GravityType {
    /** The same everywhere: a lift or slide that gravity pulls equally at any height. */
    ELEVATOR,
    /**
     * kG times the cosine of the arm's angle from horizontal: full when level, none when straight
     * up or down. Set armHorizontalPosition and positionToRadians in ClosedLoopConfig.
     */
    ARM_COSINE
}
