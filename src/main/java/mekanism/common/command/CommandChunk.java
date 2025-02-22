package mekanism.common.command;

import mekanism.common.command.CommandMek.Cmd;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.server.command.CommandTreeBase;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class CommandChunk extends CommandTreeBase {

    private Set<Long> chunkWatchers = new HashSet<>();

    public CommandChunk() {
        addSubcommand(new Cmd("watch", "cmd.mek.chunk.watch", this::addWatcher));
        addSubcommand(new Cmd("unwatch", "cmd.mek.chunk.unwatch", this::removeWatcher));
        addSubcommand(new Cmd("clear", "cmd.mek.chunk.clear", this::clearWatchers));
        addSubcommand(new Cmd("flush", "cmd.mek.chunk.flush", this::flushChunks));
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Nonnull
    @Override
    public String getName() {
        return "chunk";
    }

    @Nonnull
    @Override
    public String getUsage(@Nonnull ICommandSender sender) {
        return "cmd.mek.chunk.usage";
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        handleChunkEvent(event, "Loaded");
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        handleChunkEvent(event, "Unloaded");
    }

    private void handleChunkEvent(ChunkEvent event, String direction) {
        if (event.getWorld().isRemote) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        long key = ChunkPos.asLong(pos.x, pos.z);
        if (chunkWatchers.contains(key)) {
            String msg = String.format("%s chunk %d, %d", direction, pos.x, pos.z);
            MinecraftServer server = event.getWorld().getMinecraftServer();
            if (server != null) {
                server.getPlayerList().sendMessage(new TextComponentString(msg));
            }
        }
    }

    public void addWatcher(MinecraftServer server, ICommandSender sender, String[] args) {
        ChunkPos cpos = new ChunkPos(sender.getPosition());
        chunkWatchers.add(ChunkPos.asLong(cpos.x, cpos.z));
        CommandBase.notifyCommandListener(sender, this, "cmd.mek.chunk.watch", cpos.x, cpos.z);
    }

    public void removeWatcher(MinecraftServer server, ICommandSender sender, String[] args) {
        ChunkPos cpos = new ChunkPos(sender.getPosition());
        chunkWatchers.remove(ChunkPos.asLong(cpos.x, cpos.z));
        CommandBase.notifyCommandListener(sender, this, "cmd.mek.chunk.unwatch", cpos.x, cpos.z);
    }

    public void clearWatchers(MinecraftServer server, ICommandSender sender, String[] args) {
        int count = chunkWatchers.size();
        chunkWatchers.clear();
        CommandBase.notifyCommandListener(sender, this, "cmd.mek.chunk.clear", count);
    }

    public void flushChunks(MinecraftServer server, ICommandSender sender, String[] args) {
        if (sender.getEntityWorld().isRemote) {
            return;
        }
        ChunkProviderServer sp = (ChunkProviderServer) sender.getEntityWorld().getChunkProvider();
        int startCount = sp.getLoadedChunkCount();
        sp.queueUnloadAll();
        sp.tick();
        notifyCommandListener(sender, this, "cmd.mek.chunk.flush", startCount - sp.getLoadedChunkCount());
    }
}
