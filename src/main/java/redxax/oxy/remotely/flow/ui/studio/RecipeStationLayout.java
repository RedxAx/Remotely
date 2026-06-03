package redxax.oxy.remotely.flow.ui.studio;

public record RecipeStationLayout(String texture, int fallbackWidth, int fallbackHeight, int[][] ingredients, int[][] templates, int[] output) {
    public boolean hasTexture() {
        return texture != null && !texture.isBlank();
    }
}
