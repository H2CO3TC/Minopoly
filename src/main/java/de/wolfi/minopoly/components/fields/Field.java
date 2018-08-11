package de.wolfi.minopoly.components.fields;

import com.sk89q.worldedit.*;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.world.registry.WorldData;
import de.wolfi.minopoly.components.GameObject;
import de.wolfi.minopoly.components.Minopoly;
import de.wolfi.minopoly.components.Player;
import de.wolfi.minopoly.events.FieldEvent;
import de.wolfi.minopoly.utils.Dangerous;
import de.wolfi.minopoly.utils.FigureType;
import de.wolfi.minopoly.utils.I18nHelper;
import de.wolfi.minopoly.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.material.MaterialData;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Field extends GameObject {

    private static final HashMap<FieldColor, ArrayList<Field>> members = new HashMap<>();
    private static final long serialVersionUID = 2119752416278230984L;
    protected final Minopoly game;
    /**
     *
     */
    private final float r;
    private final FieldColor color;
    private final String name;
    private final HashMap<String, Object> storedLocation, storedHome;
    private final int price, billing;
    private boolean isOwned = false;
    private transient Location location, tp, stored_home;
    private transient ArrayList<ArmorStand> nametag;
    private FigureType owner;

    public Field(String name, FieldColor color, Location l, Minopoly game, int size, int price) {
        this.color = color;
        this.name = name;
        this.price = price;

        this.billing = price / 11;

        this.storedHome = new HashMap<>();
        this.storedLocation = new HashMap<>(l.serialize());
        this.location = l;
        this.game = game;
        this.r = size + 0.5F;
        Field.add(color, this);

        this.load();
    }

    private static final void add(FieldColor color2, Field field) {
        ArrayList<Field> l = Field.members.get(color2);
        if (l == null)
            l = new ArrayList<>();
        if (!l.contains(field))
            l.add(field);
        Field.members.put(color2, l);
    }

    public void byPass(Player player) {
    }

    public void setHome(Location loc) {
        this.stored_home = loc;
        this.storedHome.putAll(loc.serialize());
    }

    @SuppressWarnings("deprecation")
    protected void getCircle(int yAdd, boolean falling, MaterialData m) {
        final World w = this.location.getWorld();

        // final double increment = (2 * Math.PI) / amount;
        int radiusCeil = (int) Math.ceil(r);
        for (double x = -radiusCeil; x <= radiusCeil; x++) {

            for (double z = -radiusCeil; z <= radiusCeil; z++) {

                final Location l = new Location(w, this.location.getX() + x, this.location.getY(),
                        this.location.getZ() + z);
                if (this.location.distance(l) > this.r)
                    continue;
                l.add(0, yAdd, 0);
                l.getBlock().setType(m.getItemType());
                l.getBlock().setData(m.getData(), false);
                // l.getBlock().setType(m.getItemType());
                // l.getBlock().setData(m.getData());
                if (falling)
                    w.spawnEntity(l, EntityType.FALLING_BLOCK);
            }
        }
    }

    public abstract MaterialData getBlock();

    public FieldColor getColor() {
        return this.color;
    }

    public Location getLocation() {
        return this.location;
    }

    public Location getTeleportLocation() {
        return this.tp;
    }

    private String getName() {
        return this.name;
    }

    public int getPrice() {
        return this.price;
    }

    public int getBilling() {
        return billing;
    }

    public Minopoly getGame() {
        return game;
    }

    @Override
    public String toString() {
        return this.getColor().getColorChat() + this.getName();
    }

    public void moveProperty(Player player) {
        Player oldOwner = this.getOwner();
        this.setOwner(player);
        removeName();
        createNametag();
        I18nHelper.broadcast("minopoly.gameplay.field.moved_property", false, oldOwner.getDisplay(), this.toString(),
                player.getDisplay());
        player.sendMessage("minopoly.ferdinand.field.moved_property", true);
        // Messages.FIELD_PROPERTY_MOVED.broadcast(oldOwner.getDisplay(),this.toString());

    }

    public void sell() {
        Player oldOwner = this.getOwner();
        this.setOwner(null);
        oldOwner.addMoney(this.price, "Sell " + this.toString());
        this.removeHouse();
        removeName();
        createNametag();
        // Messages.FIELD_SOLD.broadcast(oldOwner.getDisplay(),this.toString());
        I18nHelper.broadcast("minopoly.gameplay.field.sold", false, oldOwner.getDisplay(), this.toString());
        oldOwner.sendMessage("minopoly.ferdinand.field.sold", true);
    }

    public boolean buy(Player player) {
        player.removeMoney(this.price, "Buy " + this.toString());
        this.setOwner(player);
        this.spawnHouse();
        removeName();
        createNametag();
        // Messages.FIELD_BOUGHT.broadcast(player.getDisplay(),this.toString());
        I18nHelper.broadcast("minopoly.gameplay.field.bought", false, player.getDisplay(), this.toString());
        player.sendMessage("minopoly.ferdinand.field.bought", true);
        return true;
    }

    public Player getOwner() {
        return this.game.getByFigureType(this.owner);
    }

    public void setOwner(Player owner) {
        this.owner = owner.getFigure();
        this.isOwned = true;
    }

    public FigureType getTypeOwner() {
        return this.owner;

    }

    public World getWorld() {
        return this.location.getWorld();
    }

    public boolean isOwned() {
        return this.isOwned;
    }

    @Dangerous(y = "Internal use ONLY!")
    @Override
    public void load() {
        this.location = Location.deserialize(this.storedLocation);
        this.tp = this.location.clone().add(0, 1, 0);
        this.nametag = new ArrayList<>();
        this.createNametag();
        this.spawn();
        if (this.storedHome.size() == 6) try {
            this.stored_home = Location.deserialize(storedHome);
            if (this.isOwned())
                this.spawnHouse();
        } catch (Exception e) {
            Bukkit.broadcastMessage(e.toString());
            // XXX TODO FIX
        }

    }

    private void removeHouse() {
        if (this.stored_home == null)
            return;
        WorldEdit worldEdit = WorldEdit.getInstance();
        LocalConfiguration config = worldEdit.getConfiguration();
        com.sk89q.worldedit.world.World world = new BukkitWorld(stored_home.getWorld());
        EditSession esession = worldEdit.getEditSessionFactory().getEditSession(world, 9999);
        File dir = worldEdit.getWorkingDirectoryFile(config.saveDir);
        File f = new File(dir, "UNOWNED.schematic");

        if (!f.exists()) {
            Bukkit.broadcastMessage("ERROR NO SCHEMATIC FOR " + f.getName());
            // player.printError("Schematic " + filename + " does not exist!");
            return;
        }
        ClipboardFormat format = ClipboardFormat.findByAlias("schematic");
        if (format == null) {
            Bukkit.broadcastMessage("W00T");
            // player.printError("Unknown schematic format: " + formatName);
            return;
        }
        LocalSession session = new LocalSession();
        Closer closer = Closer.create();
        doWorldeditMagic(world, esession, f, format, session, closer);

    }

    private void doWorldeditMagic(com.sk89q.worldedit.world.World world, EditSession esession, File f, ClipboardFormat format, LocalSession session, Closer closer) {
        try {
            FileInputStream fis = closer.register(new FileInputStream(f));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ClipboardReader reader = format.getReader(bis);

            WorldData worldData = world.getWorldData();
            Clipboard clipboard = reader.read(worldData);
            session.setClipboard(new ClipboardHolder(clipboard, worldData));

            Logger.getGlobal().info(" loaded " + f.getCanonicalPath());
            // Vector to = atOrigin ? clipboard.getOrigin() :
            // session.getPlacementPosition(player);
            Operation operation = session.getClipboard().createPaste(esession, worldData)
                    .to(new Vector(this.stored_home.getBlockX(), this.stored_home.getY(), this.stored_home.getBlockZ()))
                    .ignoreAirBlocks(true).build();

            Operations.completeLegacy(operation);

            // player.print("The clipboard has been pasted at " + to);

            // player.print(filename + " loaded. Paste it with //paste");
        } catch (IOException e) {
            // player.printError("Schematic could not read or it does not exist:
            // " + e.getMessage());
            Logger.getGlobal().log(Level.WARNING, "Failed to load a saved clipboard", e);
        } catch (MaxChangedBlocksException | EmptyClipboardException e) {
            e.printStackTrace();
        } finally {
            try {
                closer.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void spawnHouse() {
        if (this.stored_home == null)
            return;
        WorldEdit worldEdit = WorldEdit.getInstance();
        LocalConfiguration config = worldEdit.getConfiguration();
        com.sk89q.worldedit.world.World world = new BukkitWorld(stored_home.getWorld());
        EditSession esession = worldEdit.getEditSessionFactory().getEditSession(world, 9999);
        File dir = worldEdit.getWorkingDirectoryFile(config.saveDir);
        File f = new File(dir, this.owner.getName() + "_" + this.color.toString() + ".schematic");

        if (!f.exists()) {
            Bukkit.broadcastMessage("ERROR NO SCHEMATIC FOR " + f.getName());
            // player.printError("Schematic " + filename + " does not exist!");
            return;
        }
        ClipboardFormat format = ClipboardFormat.findByAlias("schematic");
        if (format == null) {
            Bukkit.broadcastMessage("W00T");
            // player.printError("Unknown schematic format: " + formatName);
            return;
        }
        LocalSession session = new LocalSession();
        Closer closer = Closer.create();
        doWorldeditMagic(world, esession, f, format, session, closer);

    }
    // worldEdit.getEditSessionFactory().getEditSession(worldEdit., 99999);

    private void createNametag() {
        Location loc = this.location.clone().add(.5, 2.5, .5);
        if (isOwned)
            createStand(loc, this.color.getColorChat() + "Owner: " + this.owner.getDisplay());
        else
            createStand(loc, this.color.getColorChat() + "Price: " + this.getPrice());
        createStand(loc.add(0, .5, 0), this.color.getColorChat() + "Billing: " + this.getBilling());
        createStand(loc.add(0, .5, 0), this.color.getColorChat() + this.getName());
    }

    private void createStand(Location loc, String name) {
        ArmorStand stand = this.game.getWorld().spawn(loc, ArmorStand.class);
        stand.setGravity(false);
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        stand.setVisible(false);
        this.nametag.add(stand);
    }

    public void playerStand(Player player) {
        I18nHelper.broadcast("minopoly.gameplay.field.normal.entered", false, player.getDisplay(), this.toString());
        Messages.FIELD_ENTERED.broadcast(player.getDisplay(), this.toString());
        Bukkit.getPluginManager().callEvent(new FieldEvent(player, this));
        if (this.isOwned())
            if (!this.owner.equals(player.getFigure())) {
                I18nHelper.broadcast("minopoly.gameplay.field.others.entered", false, this.getOwner().getName(),
                        String.valueOf(this.getBilling()), Messages.Econemy);
                player.sendMessage("minopoly.ferdinand.field.others.entered", true);
                // Messages.OTHER_FIELD_ENTERED.broadcast(player.getName(),
                // this.owner.getName(),this.color.getColorChat() +
                // this.getName());
            }
    }

    public final void spawn() {
        //this.getCircle(0, false, new MaterialData(Material.AIR));
        this.getCircle(0, false, this.getBlock());

    }

    public boolean isOwnedBy(Player player) {
        return this.isOwned() && this.getTypeOwner().equals(player.getFigure());
    }

    private final void removeName() {
        for (ArmorStand name : nametag)
            name.remove();
        nametag.clear();
    }

    @Dangerous(y = "Internal use ONLY!")
    @Override
    public void unload() {
        removeName();
    }

}
