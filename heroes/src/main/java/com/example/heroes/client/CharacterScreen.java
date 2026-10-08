package com.example.heroes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** The K character sheet: a 5e-style page with name, level, XP, the six scores and their modifiers, proficiency, HP, armor class and traits. */
public class CharacterScreen extends Screen {
    private static final String[] NAMES = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
    private static final String[] LONG = {"Strength", "Dexterity", "Constitution", "Intelligence", "Wisdom", "Charisma"};

    public CharacterScreen() {
        super(Component.literal("Character"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        CompoundTag sheet = OriginClient.sheet;
        int w = 300, h = 220;
        int x = (width - w) / 2, y = (height - h) / 2;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF3B2F1E);
        g.fill(x, y, x + w, y + h, 0xFFE8DCC0);
        int ink = 0xFF2A1F12, faint = 0xFF6B5A3E;
        if (!sheet.contains("Race")) {
            g.drawCenteredString(font, "No character yet.", x + w / 2, y + h / 2 - 4, ink);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        int level = sheet.getInt("Level");
        g.drawString(font, sheet.getString("Subtype"), x + 10, y + 10, ink, false);
        g.drawString(font, "Level " + level, x + w - 10 - font.width("Level " + level), y + 10, ink, false);
        g.drawString(font, sheet.getString("Race"), x + 10, y + 22, faint, false);

        // XP bar
        int next = sheet.getInt("XpNext"), xp = sheet.getInt("Xp"), this_ = sheet.getInt("XpThis");
        int bx = x + 10, by = y + 38, bw = w - 20;
        g.fill(bx, by, bx + bw, by + 8, 0xFF8C7A55);
        float frac = next <= 0 ? 1F : Math.max(0F, Math.min(1F, (xp - this_) / (float) (next - this_)));
        g.fill(bx + 1, by + 1, bx + 1 + (int) ((bw - 2) * frac), by + 7, 0xFF3F9B3F);
        String xpText = next <= 0 ? xp + " XP (max level)" : xp + " / " + next + " XP";
        g.drawString(font, xpText, bx, by + 11, faint, false);

        // Ability scores
        int[] scores = sheet.getIntArray("Scores");
        int[] rolled = sheet.getIntArray("Rolled");
        for (int i = 0; i < 6 && i < scores.length; i++) {
            int cx = x + 10 + (i % 3) * 94, cy = y + 66 + (i / 3) * 52;
            g.fill(cx, cy, cx + 86, cy + 46, 0xFFD3C4A0);
            g.fill(cx + 1, cy + 1, cx + 85, cy + 45, 0xFFEFE5CC);
            int mod = Math.floorDiv(scores[i] - 10, 2);
            g.drawCenteredString(font, NAMES[i], cx + 43, cy + 4, faint);
            g.drawCenteredString(font, (mod >= 0 ? "+" : "") + mod, cx + 43, cy + 16, ink);
            g.drawCenteredString(font, String.valueOf(scores[i]), cx + 43, cy + 30, faint);
            if (mouseX >= cx && mouseX < cx + 86 && mouseY >= cy && mouseY < cy + 46 && i < rolled.length) {
                g.renderTooltip(font, Component.literal(LONG[i] + ": rolled " + rolled[i] + ", race bonus " + (scores[i] - rolled[i])), mouseX, mouseY);
            }
        }

        // Combat numbers
        var player = Minecraft.getInstance().player;
        int row = y + 174;
        g.drawString(font, "Proficiency +" + sheet.getInt("Proficiency"), x + 10, row, ink, false);
        if (player != null) {
            g.drawString(font, "HP " + (int) Math.ceil(player.getHealth()) + "/" + (int) player.getMaxHealth(), x + 110, row, ink, false);
            g.drawString(font, "AC " + (10 + player.getArmorValue()), x + 190, row, ink, false);
        }
        int ty = row + 12;
        for (Tag t : sheet.getList("Notes", Tag.TAG_STRING)) {
            g.drawString(font, "- " + t.getAsString(), x + 10, ty, faint, false);
            ty += 10;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
