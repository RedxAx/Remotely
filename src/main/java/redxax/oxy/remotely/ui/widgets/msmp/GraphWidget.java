package redxax.oxy.remotely.ui.widgets.msmp;

import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.util.MathHelper;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.RemotelyClient.tr;
import static restudio.rescreen.config.Config.shadow;

public class GraphWidget extends AnimatedWidget {

    private final List<Double> dataPoints = new ArrayList<>();
    private final int maxDataPoints;
    private final String label;
    private final double maxValue;
    private final int color;

    public GraphWidget(int x, int y, int width, int height, String label, int maxDataPoints, double maxValue, int color) {
        super(x, y, width, height, label);
        this.label = label;
        this.maxDataPoints = maxDataPoints;
        this.maxValue = maxValue;
        this.color = color;
        this.animateElevation = false;
    }

    public void addDataPoint(double value) {
        dataPoints.add(value);
        if (dataPoints.size() > maxDataPoints) {
            dataPoints.removeFirst();
        }
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int padding = 15;
        int graphX = getX() + padding;
        int graphY = getY() + padding;
        int graphWidth = getWidth() - padding * 2;
        int graphHeight = getHeight() - padding * 2;

        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x33000000);

        ctx.drawText(label, getX() + 5, getY() + 2, textColor, shadow);
        double lastValue = dataPoints.isEmpty() ? 0 : dataPoints.getLast();
        String valueStr = String.format("%.1f", lastValue);
        ctx.drawText(valueStr, getX() + getWidth() - tr.getWidth(valueStr) - 5, getY() + 2, textColor, shadow);

        if (dataPoints.size() < 2) return;

        double min = 0;
        double max = maxValue;

        for (int i = 0; i < dataPoints.size() - 1; i++) {
            double val1 = dataPoints.get(i);
            double val2 = dataPoints.get(i + 1);

            int x1 = (int) (graphX + (double) i / (maxDataPoints - 1) * graphWidth);
            int y1 = (int) (graphY + graphHeight - MathHelper.clamp((val1 - min) / (max - min), 0, 1) * graphHeight);
            int x2 = (int) (graphX + (double) (i + 1) / (maxDataPoints - 1) * graphWidth);
            int y2 = (int) (graphY + graphHeight - MathHelper.clamp((val2 - min) / (max - min), 0, 1) * graphHeight);

            ctx.getMatrices().push();
            ctx.fillGradient(x1, Math.min(y1, y2), x2 + 1, getY() + getHeight() - padding, color & 0x55FFFFFF, color & 0x00FFFFFF, false);
            ctx.getMatrices().pop();

            ctx.fill(x1, Math.min(y1, y2), x2, Math.max(y1, y2), color);
        }
    }
}