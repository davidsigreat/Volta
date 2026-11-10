package io.github.davidsigreat.volta.configs;

/** Which way kS pushes. */
public enum StaticSign {
    /**
     * The direction of the target velocity. For velocity control and profiled motion, where
     * friction opposes the commanded motion.
     */
    VELOCITY,
    /**
     * The direction of the error. For holding a position with no profile, so kS helps the last
     * bit of error break static friction.
     */
    ERROR
}
