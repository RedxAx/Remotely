package redxax.oxy.remotely.flow.ui.studio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

final class StudioMutationTransaction {
    private final List<Step> steps = new ArrayList<>();

    StudioMutationTransaction add(BooleanSupplier apply, BooleanSupplier rollback) {
        steps.add(new Step(apply, rollback));
        return this;
    }

    boolean execute() {
        Deque<Step> applied = new ArrayDeque<>();
        for (Step step : steps) {
            try {
                if (!step.apply().getAsBoolean()) {
                    rollback(step);
                    rollback(applied);
                    return false;
                }
                applied.addFirst(step);
            } catch (RuntimeException exception) {
                rollback(step);
                rollback(applied);
                return false;
            }
        }
        return true;
    }

    private void rollback(Deque<Step> applied) {
        for (Step step : applied) {
            rollback(step);
        }
    }

    private void rollback(Step step) {
        try {
            step.rollback().getAsBoolean();
        } catch (RuntimeException ignored) {
        }
    }

    private record Step(BooleanSupplier apply, BooleanSupplier rollback) {
    }
}
