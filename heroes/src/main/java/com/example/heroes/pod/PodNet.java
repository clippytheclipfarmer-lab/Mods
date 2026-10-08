package com.example.heroes.pod;

import com.example.heroes.space.PlanetDef;
import com.example.heroes.space.Planets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Star map packets. Client asks for the bodies; server answers; client then picks a destination. */
public final class PodNet {
    public static final ResourceLocation REQUEST = new ResourceLocation("heroes", "bodies_request");
    public static final ResourceLocation BODIES = new ResourceLocation("heroes", "bodies");
    public static final ResourceLocation LAUNCH = new ResourceLocation("heroes", "pod_launch");

    private PodNet() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) ->
                server.execute(() -> sendBodies(player)));
        ServerPlayNetworking.registerGlobalReceiver(LAUNCH, (server, player, handler, buf, sender) -> {
            String id = buf.readUtf();
            server.execute(() -> launch(player, id));
        });
    }

    private static void launch(ServerPlayer player, String id) {
        if (!(player.getVehicle() instanceof SpacePodEntity pod)) {
            return;
        }
        if (id.isEmpty()) {
            pod.setTarget(null);
            player.displayClientMessage(Component.literal("Autopilot off."), true);
            return;
        }
        PlanetDef def = Planets.get(new ResourceLocation(id));
        if (def == null) {
            return;
        }
        pod.setTarget(def.id);
        player.displayClientMessage(Component.literal("Autopilot engaged: heading for " + def.name + "."), true);
    }

    /** Where the map is centered: the pod in space, or the planet you are on (its sphere in space) otherwise. */
    private static void sendBodies(ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Vec3 view = player.position();
        String origin = "";
        if (!player.level().dimension().equals(Planets.SPACE)) {
            PlanetDef here = Planets.forDimension(player.level().dimension());
            if (here != null) {
                view = here.position;
                origin = here.id.toString();
            }
        }
        buf.writeUtf(origin);
        buf.writeDouble(view.x);
        buf.writeDouble(view.y);
        buf.writeDouble(view.z);
        ResourceLocation target = player.getVehicle() instanceof SpacePodEntity pod ? pod.getTarget() : null;
        buf.writeUtf(target == null ? "" : target.toString());
        var bodies = Planets.inSpace();
        buf.writeInt(bodies.size());
        for (PlanetDef def : bodies) {
            buf.writeUtf(def.id.toString());
            buf.writeUtf(def.name);
            buf.writeInt(def.kind.ordinal());
            buf.writeDouble(def.position.x);
            buf.writeDouble(def.position.y);
            buf.writeDouble(def.position.z);
            buf.writeInt(def.radius);
            buf.writeInt(def.color);
        }
        ServerPlayNetworking.send(player, BODIES, buf);
    }
}
