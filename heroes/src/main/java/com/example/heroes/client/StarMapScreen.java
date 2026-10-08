package com.example.heroes.client;

import com.example.heroes.pod.PodNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-down map of every planet, station, star and black hole. Click one (on the map or in the list) and press
 * Launch: the pod then flies there by itself. Distances are drawn on a square-root scale so near bodies stay readable.
 */
public class StarMapScreen extends Screen {
    private static final int SIDEBAR = 190;
    private static final String[] KINDS = {"Planet", "Station", "Star", "Black hole"};

    public record Body(String id, String name, int kind, Vec3 pos, int radius, int color) {
    }

    private static List<Body> bodies = new ArrayList<>();
    private static Vec3 view = Vec3.ZERO;
    private static String origin = "";
    private static String target = "";

    private int selected = -1;
    private Button launch;
    private Button cancel;

    public StarMapScreen() {
        super(Component.literal("Star Map"));
    }

    static void receive(FriendlyByteBuf buf) {
        origin = buf.readUtf();
        view = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        target = buf.readUtf();
        int n = buf.readInt();
        List<Body> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Body(buf.readUtf(), buf.readUtf(), buf.readInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readInt(), buf.readInt()));
        }
        bodies = list;
    }

    static void request() {
        ClientPlayNetworking.send(PodNet.REQUEST, PacketByteBufs.create());
    }

    @Override
    protected void init() {
        launch = addRenderableWidget(Button.builder(Component.literal("Launch"), b -> {
            if (selected >= 0 && selected < bodies.size()) {
                send(bodies.get(selected).id());
                onClose();
            }
        }).bounds(width - SIDEBAR + 10, height - 52, 80, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.literal("Stop autopilot"), b -> {
            send("");
            onClose();
        }).bounds(width - SIDEBAR + 10, height - 28, 110, 20).build());
    }

    private static void send(String id) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(id);
        ClientPlayNetworking.send(PodNet.LAUNCH, buf);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ geometry

    private int mapWidth() {
        return width - SIDEBAR;
    }

    private double maxDistance() {
        double max = 50;
        for (Body b : bodies) {
            max = Math.max(max, flat(b));
        }
        return max;
    }

    private double flat(Body b) {
        return Math.hypot(b.pos().x - view.x, b.pos().z - view.z);
    }

    /** Screen position of a body: same direction as in space, distance on a square-root scale. */
    private int[] project(Body b) {
        double dx = b.pos().x - view.x, dz = b.pos().z - view.z;
        double dist = Math.hypot(dx, dz);
        double radius = Math.min(mapWidth(), height) / 2.0 - 34;
        double scaled = dist <= 0 ? 0 : Math.sqrt(dist / maxDistance()) * radius;
        double nx = dist <= 0 ? 0 : dx / dist, nz = dist <= 0 ? 0 : dz / dist;
        return new int[]{(int) (mapWidth() / 2.0 + nx * scaled), (int) (height / 2.0 + nz * scaled)};
    }

    private int pixelRadius(Body b) {
        return switch (b.kind()) {
            case 2 -> 10;
            case 3 -> 8;
            case 1 -> 4;
            default -> 5 + Math.min(4, b.radius() / 20);
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < bodies.size(); i++) {
            int[] p = project(bodies.get(i));
            if (Math.hypot(mx - p[0], my - p[1]) <= pixelRadius(bodies.get(i)) + 4) {
                selected = i;
                return true;
            }
        }
        int row = (int) ((my - 34) / 12);
        if (mx >= width - SIDEBAR + 8 && row >= 0 && row < bodies.size()) {
            selected = row;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xE0050814);
        g.fill(width - SIDEBAR, 0, width, height, 0xF0101828);
        g.drawCenteredString(font, "Star Map", mapWidth() / 2, 8, 0xFFFFFF);
        g.drawString(font, origin.isEmpty() ? "You are in space" : "Launching from " + origin.substring(origin.indexOf(':') + 1), 8, 8, 0x8899BB);

        int cx = mapWidth() / 2, cy = height / 2;
        for (int ring = 1; ring <= 3; ring++) {
            double r = (Math.min(mapWidth(), height) / 2.0 - 34) * Math.sqrt(ring / 3.0);
            circleOutline(g, cx, cy, (int) r, 0x30506090);
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);

        int hover = -1;
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            int[] p = project(b);
            int pr = pixelRadius(b);
            int color = 0xFF000000 | b.color();
            if (b.kind() == 3) {
                circleFill(g, p[0], p[1], pr, 0xFF000000);
                circleOutline(g, p[0], p[1], pr, color);
                circleOutline(g, p[0], p[1], pr + 4, 0x808a3dff);
            } else {
                circleFill(g, p[0], p[1], pr, color);
            }
            if (b.id().equals(target)) {
                circleOutline(g, p[0], p[1], pr + 5, 0xFF55FF55);
            }
            if (i == selected) {
                circleOutline(g, p[0], p[1], pr + 3, 0xFFFFFFFF);
            }
            if (Math.hypot(mx - p[0], my - p[1]) <= pr + 4) {
                hover = i;
            }
            g.drawCenteredString(font, b.name(), p[0], p[1] + pr + 5, i == selected ? 0xFFFFFF : 0xAABBCC);
        }

        // Sidebar list
        g.drawString(font, "Destinations", width - SIDEBAR + 8, 20, 0xFFFFFF);
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            int y = 34 + i * 12;
            int color = i == selected ? 0xFFFFFF : (i == hover ? 0xDDEEFF : 0x8899BB);
            if (i == selected) {
                g.fill(width - SIDEBAR + 4, y - 2, width - 4, y + 10, 0x40FFFFFF);
            }
            g.drawString(font, b.name(), width - SIDEBAR + 8, y, color);
            String dist = (int) b.pos().distanceTo(view) + "m";
            g.drawString(font, dist, width - 8 - font.width(dist), y, 0x667799);
        }
        if (selected >= 0 && selected < bodies.size()) {
            Body b = bodies.get(selected);
            g.drawString(font, b.name() + " - " + KINDS[Math.min(b.kind(), 3)], width - SIDEBAR + 8, height - 78, 0xFFFFFF);
        }
        launch.active = selected >= 0;
        cancel.active = !target.isEmpty();
        super.render(g, mx, my, partial);
    }

    private static void circleFill(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void circleOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 6);
        for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
