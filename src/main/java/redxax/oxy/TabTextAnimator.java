package redxax.oxy;

import java.util.Timer;
import java.util.TimerTask;

public class TabTextAnimator {
    private String fullText;
    private String currentText = "";
    private int currentIndex = 0;
    private Timer timer = new Timer();
    private final int delay;
    private final int period;
    private boolean isAnimating = false;
    private boolean isReversing = false;
    private boolean hasCompleted = false;
    private Runnable onAnimationEnd;

    public TabTextAnimator(String text, int delay, int period) {
        this.fullText = text;
        this.delay = delay;
        this.period = period;
    }

    public void start() {
        if (isAnimating || hasCompleted) return;
        isAnimating = true;
        isReversing = false;
        currentText = "";
        currentIndex = 0;
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentIndex < fullText.length()) {
                    currentText += fullText.charAt(currentIndex);
                    currentIndex++;
                } else {
                    timer.cancel();
                    isAnimating = false;
                    hasCompleted = true;
                    if (onAnimationEnd != null) onAnimationEnd.run();
                }
            }
        }, delay, period);
    }

    public void reverse() {
        if (isAnimating) return;
        isAnimating = true;
        isReversing = true;
        hasCompleted = false;
        currentIndex = fullText.length();
        timer = new Timer();
        int fastPeriod = 10;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentIndex > 0) {
                    currentText = currentText.substring(0, currentIndex - 1);
                    currentIndex--;
                } else {
                    timer.cancel();
                    isAnimating = false;
                    if (onAnimationEnd != null) onAnimationEnd.run();
                }
            }
        }, delay, fastPeriod);
    }

    public void updateText(String newText) {
        if (isAnimating) {
            timer.cancel();
            isAnimating = false;
        }
        this.fullText = newText;
        hasCompleted = false;
        start();
    }

    public String getCurrentText() {
        return currentText;
    }

    public void setOnAnimationEnd(Runnable onAnimationEnd) {
        this.onAnimationEnd = onAnimationEnd;
    }
}
