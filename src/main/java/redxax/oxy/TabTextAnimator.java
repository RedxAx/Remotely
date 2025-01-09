package redxax.oxy;

import java.util.Timer;
import java.util.TimerTask;

public class TabTextAnimator {
    private String fullText;
    private String previousText = "";
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
        this.previousText = text;
        this.currentText = text;
        this.delay = delay;
        this.period = period;
        this.hasCompleted = true;
    }

    public void start() {
        if (isAnimating || hasCompleted) return;
        isAnimating = true;
        isReversing = false;
        int commonLength = getCommonPrefixLength(previousText, fullText);
        currentText = previousText.substring(0, commonLength);
        currentIndex = commonLength;
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
                    previousText = fullText;
                    if (onAnimationEnd != null) onAnimationEnd.run();
                }
            }
        }, delay, period);
    }

    public void reverse() {
        if (isAnimating) return;
        isAnimating = true;
        isReversing = true;
        int commonLength = getCommonPrefixLength(previousText, fullText);
        currentText = previousText;
        currentIndex = previousText.length();
        timer = new Timer();
        int fastPeriod = 10;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentIndex > commonLength) {
                    currentText = currentText.substring(0, currentIndex - 1);
                    currentIndex--;
                } else {
                    timer.cancel();
                    isAnimating = false;
                    previousText = fullText;
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
        if (newText.startsWith(previousText)) {
            this.fullText = newText;
            hasCompleted = false;
            start();
        } else if (previousText.startsWith(newText)) {
            this.fullText = newText;
            hasCompleted = false;
            reverse();
        } else {
            this.fullText = newText;
            hasCompleted = false;
            start();
        }
    }

    public String getCurrentText() {
        return currentText;
    }

    public void setOnAnimationEnd(Runnable onAnimationEnd) {
        this.onAnimationEnd = onAnimationEnd;
    }

    private int getCommonPrefixLength(String a, String b) {
        int min = Math.min(a.length(), b.length());
        int i = 0;
        while (i < min && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
