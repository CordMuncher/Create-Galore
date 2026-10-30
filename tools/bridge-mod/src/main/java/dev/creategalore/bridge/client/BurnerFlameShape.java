package dev.creategalore.bridge.client;

/** Aeronautics' burner_flame.fsh worked out on the CPU, line for line, for BurnerFlame. */
final class BurnerFlameShape {
    private BurnerFlameShape() {
    }

    /** One posterized cell (uv with +y up) in the palette row's colour (ABGR), 0 where the shader discards. */
    static int cell(float ux, float uy, float time, float intensity, int[] row) {
        ux = (ux - 0.5F) * 1.8F;
        uy = (uy - 0.12F) * 1.8F;

        float[] flame = mainFlame(ux, uy, time);
        float[] color = new float[4];
        composite(color, flame[0], flame[1], flame[2], flame[3]);
        composite(color, 0.01F, 0.01F, 0.01F, circle(ux, uy, 0.15F));

        ux -= 0.3F;
        uy += 0.3F;
        float[] cutout1 = cutout(ux, uy, time, intensity);
        ux = -ux - 0.6F;
        float[] cutout2 = cutout(ux, uy, time + 0.35F, intensity);

        color[3] *= cutout1[1] * cutout2[1];
        float minDistance = Math.min(cutout1[0], cutout2[0]);
        if (color[3] > 0.0F) {
            float edgeBurn = Math.clamp((1.0F - minDistance - 0.95F) * 100.0F, 0.0F, 1.0F);
            color[0] = lerp(edgeBurn * 0.3F, color[0], 1.0F);
        }
        if (color[3] < 1.0F) {
            return 0;
        }
        // sample_palette: nearest texel, wrapping, of 1 - (r + mix(0.9, 0.2, Intensity))
        float i = color[0] + lerp(intensity, 0.9F, 0.2F);
        float u = 1.0F - i;
        u -= (float) Math.floor(u);
        return row[Math.min(row.length - 1, (int) (u * row.length))];
    }

    /** fg over bg the way Aeronautics' alpha_composite does it, into bg. */
    private static void composite(float[] bg, float r, float g, float b, float a) {
        float keep = bg[3] * (1.0F - a);
        bg[0] = r * a + bg[0] * keep;
        bg[1] = g * a + bg[1] * keep;
        bg[2] = b * a + bg[2] * keep;
        bg[3] = a * a + bg[3] * keep;
    }

    private static float circle(float x, float y, float radius) {
        return (float) Math.sqrt(x * x + y * y) - radius <= 0.0F ? 1.0F : 0.0F;
    }

    private static float mod(float x, float y) {
        return x - y * (float) Math.floor(x / y);
    }

    private static float[] mainFlame(float ux, float uy, float time) {
        float[] color = new float[4];
        final int count = 6;
        float speed = time * 0.3F;
        float[][] positions = new float[count][2];
        for (int i = 0; i < count; i++) {
            float normalY = mod((float) i / count + speed, 1.0F);
            int index = Math.clamp((int) ((1.0F - normalY) * count), 0, count);
            float xOffset = (i & 1) * 0.05F - 0.05F / 2.0F;
            if (index < count) {
                positions[index][0] = xOffset;
                positions[index][1] = normalY;
            }
        }
        for (int i = 0; i < count; i++) {
            float px = positions[i][0];
            float py = positions[i][1];
            float rx = ux - px;
            float ry = uy - py;
            float mask = circle(rx, ry, lerp(py, 0.15F, 0.4F));
            float r = mask * py * 0.8F;
            float a = mask;
            float rgb = r;
            if (a > 0.0F) {
                float t = Math.abs(rx);
                rgb = lerp(t, rgb, 1.0F);
                a = lerp(t, a, 1.0F);
            }
            composite(color, rgb, rgb, rgb, a);
        }
        return color;
    }

    /** (distance to the nearest cutout circle, 0 when inside one else 1) */
    private static float[] cutout(float ux, float uy, float time, float intensity) {
        final int count = 3;
        float grow = (1.0F - intensity) * 0.2F;
        float speed = time * 1.2F;
        float minDistance = 5.0F;
        for (int i = 0; i < count; i++) {
            float normalY = mod((float) i / count * 2.0F + speed, 2.0F);
            float xOffset = (i & 1) * 0.1F - 0.1F / 2.0F;
            float radius = lerp(normalY, 0.08F, 0.23F + grow);
            float rx = ux - xOffset;
            float ry = uy - normalY;
            minDistance = Math.min(minDistance, (float) Math.sqrt(rx * rx + ry * ry) - radius);
            if (circle(rx, ry, radius) >= 1.0F) {
                return new float[] {0.0F, 0.0F};
            }
        }
        return new float[] {minDistance, 1.0F};
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
}
