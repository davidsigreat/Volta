package io.github.davidsigreat.voltagecontrol;

// The motion behind Motion Magic: speed up at a fixed acceleration to the cruise velocity, cruise,
// then slow down to stop exactly on the goal. Short moves never reach cruise and make a triangle.
//
// Use it one step at a time, from the last setpoint, the way Motion Magic does:
//     step(dt, setpoint, goal, maxVelocity, maxAcceleration, setpoint);
// Starting from the last setpoint, not the measured position, keeps the motion smooth. Because it's
// worked out fresh each step, the goal can change mid-move and it carries on from where it is.
//
// The same algorithm as WPILib's TrapezoidProfile. No jerk limit (Phoenix's S-curve): at FTC loop
// rates the difference is small, and the trapezoid is simpler to reason about. Plain Java, so it can
// be tested without a robot. Writes into an existing State, so it makes no garbage each loop.
public class TrapezoidProfile {

    public static class State {
        public double position;
        public double velocity;
        public double acceleration;

        public State() {
        }

        public State(double position, double velocity) {
            this.position = position;
            this.velocity = velocity;
        }

        public void set(double position, double velocity, double acceleration) {
            this.position = position;
            this.velocity = velocity;
            this.acceleration = acceleration;
        }
    }

    private TrapezoidProfile() {
    }

    // Where the motion is t seconds after current, heading for goal. Writes into out, which can be
    // the same object as current. With no limits (0 or less), it jumps straight to the goal.
    public static void step(double t, State current, State goal, double maxVelocity, double maxAcceleration,
                            State out) {
        if (maxVelocity <= 0 || maxAcceleration <= 0) {
            out.set(goal.position, goal.velocity, 0);
            return;
        }

        // Worked out as if moving in the + direction, then flipped back at the end.
        double direction = current.position > goal.position ? -1 : 1;
        double startPosition = current.position * direction;
        double startVelocity = Math.min(current.velocity * direction, maxVelocity);
        double goalPosition = goal.position * direction;
        double goalVelocity = Math.min(goal.velocity * direction, maxVelocity);

        // Multiplying by these is cheaper than dividing by the limits.
        double inverseAcceleration = 1 / maxAcceleration;
        double halfAcceleration = maxAcceleration * 0.5;

        // Pretend both ends are at rest, by extending the profile back to where the start velocity
        // would have been 0 and on to where the goal velocity would reach 0.
        double cutoffBegin = startVelocity * inverseAcceleration;
        double cutoffDistanceBegin = cutoffBegin * cutoffBegin * halfAcceleration;
        double cutoffEnd = goalVelocity * inverseAcceleration;
        double cutoffDistanceEnd = cutoffEnd * cutoffEnd * halfAcceleration;
        double fullDistance = cutoffDistanceBegin + (goalPosition - startPosition) + cutoffDistanceEnd;

        double accelerationTime = maxVelocity * inverseAcceleration;
        double cruiseDistance = fullDistance - accelerationTime * accelerationTime * maxAcceleration;
        if (cruiseDistance < 0) {
            // Too short to reach cruise: a triangle.
            accelerationTime = Math.sqrt(fullDistance * inverseAcceleration);
            cruiseDistance = 0;
        }

        double endAcceleration = accelerationTime - cutoffBegin;
        double endCruise = endAcceleration + cruiseDistance * (1 / maxVelocity);
        double endDeceleration = endCruise + accelerationTime - cutoffEnd;

        double position;
        double velocity;
        double acceleration;
        if (t < endAcceleration) {
            velocity = startVelocity + t * maxAcceleration;
            position = startPosition + (startVelocity + t * halfAcceleration) * t;
            acceleration = maxAcceleration;
        } else if (t < endCruise) {
            velocity = maxVelocity;
            position = startPosition + (startVelocity + endAcceleration * halfAcceleration) * endAcceleration
                    + maxVelocity * (t - endAcceleration);
            acceleration = 0;
        } else if (t <= endDeceleration) {
            double timeLeft = endDeceleration - t;
            velocity = goalVelocity + timeLeft * maxAcceleration;
            position = goalPosition - (goalVelocity + timeLeft * halfAcceleration) * timeLeft;
            acceleration = -maxAcceleration;
        } else {
            velocity = goalVelocity;
            position = goalPosition;
            acceleration = 0;
        }
        out.set(position * direction, velocity * direction, acceleration * direction);
    }

    // Whether the setpoint has reached the goal and stopped.
    public static boolean isFinished(State setpoint, State goal) {
        return setpoint.position == goal.position && setpoint.velocity == goal.velocity;
    }
}
