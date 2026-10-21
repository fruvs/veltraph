package dev.veltraph.client.ui.render;

import dev.veltraph.client.ui.UiTextures;
import dev.veltraph.client.ui.font.VeltraphFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Default {@link UiRenderer} backed by Minecraft's {@link GuiGraphicsExtractor} for primitives/textures. Text
 * uses the client font renderer for a clean anti-aliased-looking result at common GUI scales; the
 * custom UI identity comes from Veltraph's renderer, layout, texture skin, and widgets rather than the
 * earlier low-resolution bitmap font that looked blocky in live screenshots.
 */
public final class DrawContextRenderer implements UiRenderer {

    private GuiGraphicsExtractor ctx;
    private Font textRenderer;

    public DrawContextRenderer() {
    }

    /** Bind the per-frame draw context. */
    public void begin(GuiGraphicsExtractor context) {
        this.ctx = context;
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public void fill(int x, int y, int width, int height, int argb) {
        ctx.fill(x, y, x + width, y + height, argb);
    }

    @Override
    public void gradientV(int x, int y, int width, int height, int topArgb, int bottomArgb) {
        ctx.fillGradient(x, y, x + width, y + height, topArgb, bottomArgb);
    }

    @Override
    public void border(int x, int y, int width, int height, int argb) {
        fill(x, y, width, 1, argb);
        fill(x, y + height - 1, width, 1, argb);
        fill(x, y, 1, height, argb);
        fill(x + width - 1, y, 1, height, argb);
    }

    @Override
    public void roundedRect(int x, int y, int width, int height, int radius, int argb) {
        if (radius <= 0 || width < 2 * radius || height < 2 * radius) {
            fill(x, y, width, height, argb);
            return;
        }
        // Straight regions.
        fill(x + radius, y, width - 2 * radius, height, argb);
        fill(x, y + radius, radius, height - 2 * radius, argb);
        fill(x + width - radius, y + radius, radius, height - 2 * radius, argb);

        // Anti-aliased corners: each scanline fills the solid half-chord, then blends one fractional
        // edge pixel by its sub-pixel coverage so the rounded corner reads smooth instead of stepped.
        int baseA = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        for (int row = 0; row < radius; row++) {
            int dy = radius - 1 - row;
            double xf = Math.sqrt((double) radius * radius - (double) dy * dy);
            int run = (int) Math.floor(xf);
            if (run > 0) {
                fill(x + radius - run, y + row, run, 1, argb);
                fill(x + width - radius, y + row, run, 1, argb);
                fill(x + radius - run, y + height - 1 - row, run, 1, argb);
                fill(x + width - radius, y + height - 1 - row, run, 1, argb);
            }
            double frac = xf - run;
            if (frac > 0.02 && baseA > 0) {
                int a = (int) Math.round(baseA * frac);
                if (a > 0) {
                    int edge = (a << 24) | rgb;
                    fill(x + radius - run - 1, y + row, 1, 1, edge);
                    fill(x + width - radius + run, y + row, 1, 1, edge);
                    fill(x + radius - run - 1, y + height - 1 - row, 1, 1, edge);
                    fill(x + width - radius + run, y + height - 1 - row, 1, 1, edge);
                }
            }
        }
    }

    @Override
    public void text(String text, int x, int y, int argb) {
        text(text, x, y, argb, false);
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        if (textRenderer != null) {
            ctx.text(textRenderer, text == null ? "" : text, x, y, argb, shadow);
            return;
        }
        if (shadow) {
            VeltraphFont.draw(this, text, x + 1, y + 1, 0xBF000000);
        }
        VeltraphFont.draw(this, text, x, y, argb);
    }

    @Override
    public int textWidth(String text) {
        return textRenderer == null ? VeltraphFont.width(text) : textRenderer.width(text == null ? "" : text);
    }

    @Override
    public int lineHeight() {
        return textRenderer == null ? VeltraphFont.height() : textRenderer.lineHeight;
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow, int scale) {
        textScaled(text, x, y, argb, shadow, Math.max(1, scale));
    }

    @Override
    public int textWidth(String text, int scale) {
        return textWidth(text) * Math.max(1, scale);
    }

    @Override
    public int lineHeight(int scale) {
        return lineHeight() * Math.max(1, scale);
    }

    @Override
    public void textScaled(String text, int x, int y, int argb, boolean shadow, float scale) {
        float s = Math.max(0.05f, scale);
        if (textRenderer == null) {
            // Fallback only (no Minecraft TextRenderer, e.g. headless tests): the bitmap font.
            int is = Math.max(1, Math.round(s));
            if (shadow) {
                VeltraphFont.draw(this, text, x + is, y + is, 0xBF000000, is);
            }
            VeltraphFont.draw(this, text, x, y, argb, is);
            return;
        }
        if (s == 1.0f) {
            text(text, x, y, argb, shadow);
            return;
        }
        // Smooth scaled text: scale the GUI matrix around (x, y) and draw the vector font at the origin.
        var matrices = ctx.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(s, s);
        ctx.text(textRenderer, text == null ? "" : text, 0, 0, argb, shadow);
        matrices.popMatrix();
    }

    @Override
    public int textWidthScaled(String text, float scale) {
        return Math.round(textWidth(text) * Math.max(0.01f, scale));
    }

    @Override
    public int lineHeightScaled(float scale) {
        return Math.round(lineHeight() * Math.max(0.01f, scale));
    }

    @Override
    public void softShadow(int x, int y, int width, int height, int radius, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        if (a <= 0) {
            return;
        }
        // Three concentric AA rounded passes growing outward with falling alpha → a feathered edge.
        for (int i = 2; i >= 0; i--) {
            int la = Math.round(a * (1f - i * 0.30f));
            if (la <= 0) {
                continue;
            }
            roundedRect(x - i, y - i, width + 2 * i, height + 2 * i, radius + i, (la << 24) | rgb);
        }
    }

    @Override
    public void pushScissor(int x, int y, int width, int height) {
        ctx.enableScissor(x, y, x + width, y + height);
    }

    @Override
    public void popScissor() {
        ctx.disableScissor();
    }

    @Override
    public void texture(String texturePath, int x, int y, int width, int height, int texW, int texH) {
        // Scale the whole source image into width x height.
        textureRegion(texturePath, x, y, width, height, 0, 0, texW, texH, texW, texH);
    }

    @Override
    public void textureRegion(String texturePath, int x, int y, int width, int height,
                              int u, int v, int regionW, int regionH, int texW, int texH) {
        if (width <= 0 || height <= 0) {
            return;
        }
        try {
            Identifier id = Identifier.fromNamespaceAndPath(UiTextures.NAMESPACE, texturePath);
            // Scaling blit: dest (width x height) sampling regionW x regionH texels from (u, v).
            ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, (float) u, (float) v,
                    width, height, regionW, regionH, texW, texH);
        } catch (Throwable ignored) {
            // Texture backend unavailable — the caller has already drawn a procedural fallback.
        }
    }

    @Override
    public void nineSlice(String texturePath, int x, int y, int width, int height,
                          int texW, int texH, int inset) {
        nineSlice(texturePath, x, y, width, height, texW, texH, inset, inset);
    }

    @Override
    public void nineSlice(String texturePath, int x, int y, int width, int height,
                          int texW, int texH, int srcInset, int dstInset) {
        for (NineSlice.Region r : NineSlice.regions(x, y, width, height, texW, texH, srcInset, dstInset)) {
            textureRegion(texturePath, r.dx(), r.dy(), r.dw(), r.dh(), r.u(), r.v(), r.sw(), r.sh(), texW, texH);
        }
    }
}
