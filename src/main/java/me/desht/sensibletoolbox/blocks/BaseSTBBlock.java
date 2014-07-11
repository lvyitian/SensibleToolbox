package me.desht.sensibletoolbox.blocks;

import me.desht.dhutils.Debugger;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.PermissionUtils;
import me.desht.dhutils.PersistableLocation;
import me.desht.sensibletoolbox.SensibleToolboxPlugin;
import me.desht.sensibletoolbox.api.*;
import me.desht.sensibletoolbox.energynet.EnergyNetManager;
import me.desht.sensibletoolbox.gui.InventoryGUI;
import me.desht.sensibletoolbox.gui.STBGUIHolder;
import me.desht.sensibletoolbox.items.BaseSTBItem;
import me.desht.sensibletoolbox.storage.LocationManager;
import me.desht.sensibletoolbox.util.RelativePosition;
import me.desht.sensibletoolbox.util.STBUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.ChatPaginator;

import java.util.BitSet;
import java.util.UUID;

public abstract class BaseSTBBlock extends BaseSTBItem implements STBBlock {
    public static final String STB_BLOCK = "STB_Block";
    public static final String STB_MULTI_BLOCK = "STB_MultiBlock_Origin";
    private PersistableLocation persistableLocation;
    private BlockFace facing;
    private long ticksLived;
    private InventoryGUI inventoryGUI = null;
    private final STBGUIHolder guiHolder = new STBGUIHolder(this);
    private RedstoneBehaviour redstoneBehaviour;
    private AccessControl accessControl;
    private final BitSet labelSigns = new BitSet(4);
    private UUID owner;

    protected BaseSTBBlock() {
        super();
        setFacing(BlockFace.SELF);
        redstoneBehaviour = RedstoneBehaviour.IGNORE;
        accessControl = AccessControl.PUBLIC;
        ticksLived = 0;
    }

    public BaseSTBBlock(ConfigurationSection conf) {
        super(conf);
        setFacing(BlockFace.valueOf(conf.getString("facing", "SELF")));
        if (conf.contains("owner")) {
            setOwner(UUID.fromString(conf.getString("owner")));
        }
        redstoneBehaviour = RedstoneBehaviour.valueOf(conf.getString("redstoneBehaviour", "IGNORE"));
        accessControl = AccessControl.valueOf(conf.getString("accessControl", "PUBLIC"));
        ticksLived = 0;
        byte faces = (byte) conf.getInt("labels", 0);
        labelSigns.or(BitSet.valueOf(new byte[] { faces }));
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        if (getOwner() != null) {
            conf.set("owner", getOwner().toString());
        }
        conf.set("facing", getFacing().toString());
        conf.set("redstoneBehaviour", getRedstoneBehaviour().toString());
        conf.set("accessControl", getAccessControl().toString());
        conf.set("labels", labelSigns.isEmpty() ? 0 : labelSigns.toByteArray()[0]);
        return conf;
    }

    @Override
    public final RedstoneBehaviour getRedstoneBehaviour() {
        return redstoneBehaviour;
    }

    @Override
    public final void setRedstoneBehaviour(RedstoneBehaviour redstoneBehaviour) {
        this.redstoneBehaviour = redstoneBehaviour;
        update(false);
    }

    @Override
    public final AccessControl getAccessControl() {
        return accessControl;
    }

    @Override
    public final void setAccessControl(AccessControl accessControl) {
        this.accessControl = accessControl;
        update(false);
    }

    public final STBGUIHolder getGuiHolder() {
        return guiHolder;
    }

    @Override
    public final InventoryGUI getGUI() {
        return inventoryGUI;
    }

    protected final void setGUI(InventoryGUI inventoryGUI) {
        this.inventoryGUI = inventoryGUI;
    }

    @Override
    public final BlockFace getFacing() {
        return facing;
    }

    @Override
    public final void setFacing(BlockFace facing) {
        this.facing = facing;
    }

    @Override
    public final UUID getOwner() {
        return owner;
    }

    @Override
    public final void setOwner(UUID owner) {
        this.owner = owner;
    }

    public final long getTicksLived() {
        return ticksLived;
    }

    @Override
    public final boolean hasAccessRights(Player player) {
        switch (getAccessControl()) {
            case PUBLIC:
                return true;
            case PRIVATE:
                return getOwner().equals(player.getUniqueId()) || PermissionUtils.isAllowedTo(player, "stb.access.any");
            default:
                return false;
        }
    }

    @Override
    public final boolean hasAccessRights(UUID uuid) {
        switch (getAccessControl()) {
            case PUBLIC:
                return true;
            case PRIVATE:
                return uuid == null || getOwner().equals(uuid);
            default:
                return false;
        }
    }

    /**
     * Check if this block is active based on its redstone behaviour settings and the presence
     * or absence of a redstone signal.
     *
     * @return true if the block is active, false otherwise
     */
    protected final boolean isRedstoneActive() {
        switch (getRedstoneBehaviour()) {
            case IGNORE:
                return true;
            case HIGH:
                return getLocation().getBlock().isBlockIndirectlyPowered();
            case LOW:
                return !getLocation().getBlock().isBlockIndirectlyPowered();
            default:
                return false;
        }
    }

    /**
     * Called when an STB block receives a damage event.  The default behaviour is to ignore
     * the event.
     *
     * @param event the block damage event
     */
    public void onBlockDamage(BlockDamageEvent event) {
    }

    /**
     * Called when an STB block receives a physics event.  The default behaviour is to ignore
     * the event.
     *
     * @param event the block physics event
     */
    public void onBlockPhysics(BlockPhysicsEvent event) {
    }

    /**
     * Called when an STB block is interacted with by a player.  The default behaviour allows
     * for the block to be labelled by left-clicking it with a sign in hand.  If you override
     * this method and want to keep this behaviour, be sure to call super.onInteractBlock()
     *
     * @param event the interaction event
     */
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getPlayer().getItemInHand().getType() == Material.SIGN
                && event.getClickedBlock().getType() != Material.WALL_SIGN && event.getClickedBlock().getType() != Material.SIGN_POST) {
            // attach a label sign
            if (attachLabelSign(event)) {
                labelSigns.set(STBUtil.getFaceRotation(getFacing(), event.getBlockFace()));
            }
            event.setCancelled(true);
        }
    }

    /**
     * Called when a sign attached to an STB block is updated.  The default behaviour is to ignore
     * the event.
     *
     * @param event the sign change event
     * @return true if the sign should be popped off the block
     */
    public boolean onSignChange(SignChangeEvent event) {
        return false;
    }

    /**
     * Called when this STB block has been hit by an explosion.  The default behaviour is to return
     * true; STB blocks will break and drop their item form if hit by an explosion.
     *
     * @param event the explosion event
     * @return true if the explosion should cause the block to break, false otherwise
     */
    public boolean onEntityExplode(EntityExplodeEvent event) {
        return true;
    }

    /**
     * Get a list of extra blocks this STB block has.  By default this returns an empty list,
     * but multi-block structures should override this.  Each element of the list is a vector
     * containing a relative offset from the item's base location.
     *
     * @return an array of relative offsets for extra blocks in the item
     */
    @Override
    public RelativePosition[] getBlockStructure() {
        return new RelativePosition[0];
    }

    /**
     * This method should not be called directly or overridden; it is automatically
     * called every tick for every block.
     */
    public final void tick() {
        ticksLived++;
    }

    /**
     * Called every tick for each STB block that is placed in the world, for
     * any STB block where {@link #getTickRate()} returns a non-zero value.
     * Override this method to define any periodic behaviour of the block.
     */
    public void onServerTick() {
    }

    /**
     * Defines the rate at which the block ticks. {@link #onServerTick()} will
     * be called this frequently.  Override this method to have the block
     * tick less frequently.  The default rate of 0 means that the block will
     * not tick at all.
     */
    public int getTickRate() {
        return 0;
    }

    /**
     * Called when the chunk that an STB block is in gets loaded.
     */
    public void onChunkLoad() {
    }

    /**
     * Called when the chunk that an STB block is in gets unloaded.
     */
    public void onChunkUnload() {
    }

    @Override
    public final Location getLocation() {
        return persistableLocation == null ? null : persistableLocation.getLocation();
    }

    @Override
    public final Location getRelativeLocation(BlockFace face) {
        return getLocation().add(face.getModX(), face.getModY(), face.getModZ());
    }

    @Override
    public final PersistableLocation getPersistableLocation() {
        return persistableLocation;
    }

    /**
     * Set the location of the base block of this STB block.  This should only be called when the
     * block is first placed, or when deserialized.
     *
     * @param loc the base block location
     * @throws IllegalStateException if the caller attempts to set a non-null location when the object already has a location set
     */
    public void setLocation(Location loc) {
        if (loc != null) {
            if (persistableLocation != null && !loc.equals(persistableLocation.getLocation())) {
                throw new IllegalStateException("Attempt to change the location of existing STB block @ " + persistableLocation);
            }
            persistableLocation = new PersistableLocation(loc);
            for (RelativePosition pos : getBlockStructure()) {
                Block b1 = getMultiBlock(loc, pos);
                Debugger.getInstance().debug(2, "Multiblock for " + this + " -> " + b1);
                b1.setMetadata(STB_MULTI_BLOCK, new FixedMetadataValue(SensibleToolboxPlugin.getInstance(), this));
            }
            reattachLabelSigns(loc);
            setGUI(createGUI());
        } else {
            if (persistableLocation != null) {
                Location l = getLocation();
                for (RelativePosition pos : getBlockStructure()) {
                    Block b1 = getMultiBlock(l, pos);
                    b1.removeMetadata(STB_MULTI_BLOCK, SensibleToolboxPlugin.getInstance());
                }
            }
            persistableLocation = null;
            setGUI(null);
        }
    }

    private void reattachLabelSigns(Location loc) {
        Block b = loc.getBlock();
        boolean rescanNeeded = false;
        for (int rotation = 0; rotation < 4; rotation++) {
            if (labelSigns.get(rotation)) {
                BlockFace face = STBUtil.getRotatedFace(getFacing(), rotation);
                if (!placeLabelSign(b.getRelative(face), face)) {
                    rescanNeeded = true;
                }
            }
        }
        if (rescanNeeded) {
            scanForAttachedLabelSigns();
            update(false);
        }
    }

    private Block getMultiBlock(Location loc, RelativePosition pos) {
        Block b = loc.getBlock();
        int dx = 0, dz = 0;
        switch (getFacing()) {
            case NORTH:
                dz = -pos.getFront();
                dx = -pos.getLeft();
                break;
            case SOUTH:
                dz = pos.getFront();
                dx = pos.getLeft();
                break;
            case EAST:
                dz = -pos.getLeft();
                dx = pos.getFront();
                break;
            case WEST:
                dz = pos.getLeft();
                dx = -pos.getFront();
                break;
        }
        return b.getRelative(dx, pos.getUp(), dz);
    }

    public final void moveTo(final Location oldLoc, final Location newLoc) {
        for (RelativePosition pos : getBlockStructure()) {
            Block b1 = getMultiBlock(oldLoc, pos);
            b1.removeMetadata(STB_MULTI_BLOCK, SensibleToolboxPlugin.getInstance());
        }
        if (this instanceof ChargeableBlock) {
            EnergyNetManager.onMachineRemoved((ChargeableBlock) this);
        }

        persistableLocation = new PersistableLocation(newLoc);

        for (RelativePosition pos : getBlockStructure()) {
            Block b1 = getMultiBlock(newLoc, pos);
            Debugger.getInstance().debug(2, "multiblock for " + this + " -> " + b1);
            b1.setMetadata(STB_MULTI_BLOCK, new FixedMetadataValue(SensibleToolboxPlugin.getInstance(), this));
        }
        if (this instanceof ChargeableBlock) {
            EnergyNetManager.onMachinePlaced((ChargeableBlock) this);
        }

        Bukkit.getScheduler().runTask(SensibleToolboxPlugin.getInstance(), new Runnable() {
            public void run() {
                Block b = oldLoc.getBlock();
                for (int rotation = 0; rotation < 4; rotation++) {
                    if (labelSigns.get(rotation)) {
                        Block signBlock = b.getRelative(STBUtil.getRotatedFace(getFacing(), rotation));
                        if (signBlock.getType() == Material.WALL_SIGN) {
                            signBlock.setType(Material.AIR);
                        }
                    }
                }
            }
        });

        Bukkit.getScheduler().runTaskLater(SensibleToolboxPlugin.getInstance(), new Runnable() {
            public void run() {
                Block b = newLoc.getBlock();
                for (int rotation = 0; rotation < 4; rotation++) {
                    if (labelSigns.get(rotation)) {
                        BlockFace face = STBUtil.getRotatedFace(getFacing(), rotation);
                        Block signBlock = b.getRelative(face);
                        if (!placeLabelSign(signBlock, face)) {
                            labelSigns.set(rotation, false);
                        }
                    }
                }
            }
        }, 2L);
    }

    /**
     * Called when an STB block is placed.  Subclasses may override this
     * method, but should take care to call the superclass method.
     * <p>
     * This event is called with MONITOR priority; do not change the outcome
     * of the event!
     *
     * @param event the block place event
     */
    public void onBlockPlace(BlockPlaceEvent event) {
        placeBlock(event.getBlock(), event.getPlayer(), STBUtil.getFaceFromYaw(event.getPlayer().getLocation().getYaw()).getOppositeFace());
    }

    /**
     * Validate that this STB block (which may be a multi-block structure) is
     * placeable at the given location.
     *
     * @param baseLoc the location of the STB block's base block
     * @return true if the STB block is placeable; false otherwise
     */
    public boolean validatePlaceable(Location baseLoc) {
        for (RelativePosition rPos : getBlockStructure()) {
            Block b = getMultiBlock(baseLoc, rPos);
            if (b.getType() != Material.AIR && b.getType() != Material.WATER && b.getType() != Material.STATIONARY_WATER) {
                return false;
            }
        }
        return true;
    }

    /**
     * Called when an STB block is actually broken (the event handler runs with MONITOR
     * priority).  You must not alter the outcome of this event!
     * <p/>
     * Subclasses may override this method, but should take care to call the superclass method.
     *
     * @param event the block break event
     */
    public void onBlockBreak(BlockBreakEvent event) {
        breakBlock(event.getBlock());
    }

    protected void placeBlock(Block b, Player p, BlockFace facing) {
        setFacing(facing);
        setOwner(p.getUniqueId());
        LocationManager.getManager().registerLocation(b.getLocation(), this, true);
    }

    public final void breakBlock(Block b) {
        Location baseLoc = this.getLocation();
        Block origin = baseLoc.getBlock();
        scanForAttachedLabelSigns();
        for (int rotation = 0; rotation < 4; rotation++) {
            if (labelSigns.get(rotation)) {
                origin.getRelative(STBUtil.getRotatedFace(getFacing(), rotation)).setType(Material.AIR);
            }
        }
        LocationManager.getManager().unregisterLocation(baseLoc, this);
        origin.setType(Material.AIR);
        for (RelativePosition pos : getBlockStructure()) {
            Block b1 = getMultiBlock(baseLoc, pos);
            b1.setType(Material.AIR);
        }
        b.getWorld().dropItemNaturally(b.getLocation(), toItemStack());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseSTBBlock that = (BaseSTBBlock) o;

        if (persistableLocation != null ? !persistableLocation.equals(that.persistableLocation) : that.persistableLocation != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return persistableLocation != null ? persistableLocation.hashCode() : 0;
    }

    @Override
    public final void update(boolean redraw) {
        Location loc = getLocation();
        if (loc != null) {
            if (redraw) {
                Block b = loc.getBlock();
                // maybe one day Bukkit will have a block set method which takes a MaterialData
                b.setTypeIdAndData(getMaterial().getId(), getMaterialData().getData(), true);
            }
            LocationManager.getManager().updateLocation(loc);
        }
    }

    private boolean attachLabelSign(PlayerInteractEvent event) {
        if (event.getBlockFace().getModY() != 0) {
            // only support placing a label sign on the side of a machine, not the top
            event.setCancelled(true);
            return false;
        }
        Block signBlock = event.getClickedBlock().getRelative(event.getBlockFace());
        Player player = event.getPlayer();

        if (!signBlock.isEmpty()) {
            // looks like some non-solid block is in the way
            STBUtil.complain(player, "There is no room to place a label sign there!");
            event.setCancelled(true);
            return false;
        }

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(signBlock, signBlock.getState(), event.getClickedBlock(), event.getItem(), player, true);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            STBUtil.complain(player);
            return false;
        }

        // ok, player is allowed to put a sign here
        placeLabelSign(signBlock, event.getBlockFace());

        ItemStack stack = player.getItemInHand();
        stack.setAmount(stack.getAmount() - 1);
        player.setItemInHand(stack.getAmount() <= 0 ? null : stack);

        player.playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1.0f, 1.0f);

        return true;
    }

    private boolean placeLabelSign(Block signBlock, BlockFace face) {
        if (!signBlock.isEmpty() && signBlock.getType() != Material.WALL_SIGN) {
            // something in the way!
            Debugger.getInstance().debug(this + ": can't place label sign @ " + signBlock + ", face = " + face);
            signBlock.getWorld().dropItemNaturally(signBlock.getLocation(), new ItemStack(Material.SIGN));
            return false;
        } else {
            Debugger.getInstance().debug(this + ": place label sign @ " + signBlock + ", face = " + face);
            // using setTypeIdAndData() here because we don't want to cause a physics update
            System.out.println("attached block " + signBlock.getRelative(face.getOppositeFace()));
            signBlock.setTypeIdAndData(Material.WALL_SIGN.getId(), (byte) 0, false);
            Sign sign = (Sign) signBlock.getState();
            org.bukkit.material.Sign s = (org.bukkit.material.Sign) sign.getData();
            s.setFacingDirection(face);
            sign.setData(s);
            String[] text = getSignLabel(face);
            for (int i = 0; i < text.length; i++) {
                sign.setLine(i, text[i]);
            }
            sign.update(false, false);
            return true;
        }
    }

    protected String[] getSignLabel(BlockFace face) {
        String[] lines = ChatPaginator.wordWrap(makeItemLabel(face), 13);
        String[] res = new String[4];
        for (int i = 0; i < 4; i++) {
            res[i] = i < lines.length ? lines[i] : "";
        }
        return res;
    }

    @Override
    public String toString() {
        return "STB " + getItemName() + " @ " +
                (getLocation() == null ? "(null)" : MiscUtil.formatLocation(getLocation()));
    }

    private void scanForAttachedLabelSigns() {
        labelSigns.clear();
        if (getLocation() == null) {
            return;
        }
        Block b = getLocation().getBlock();
        for (BlockFace face : new BlockFace[]{BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH}) {
            Block b1 = b.getRelative(face);
            if (b1.getType() == Material.WALL_SIGN) {
                Sign sign = (Sign) b.getRelative(face).getState();
                org.bukkit.material.Sign s = (org.bukkit.material.Sign) sign.getData();
                if (s.getAttachedFace() == face.getOppositeFace()) {
                    labelSigns.set(STBUtil.getFaceRotation(getFacing(), face));
                }
            }
        }
    }

    public void detachLabelSign(BlockFace face) {
        Debugger.getInstance().debug(this + ": detach label sign on face " + face);
        labelSigns.set(STBUtil.getFaceRotation(getFacing(), face), false);
        update(false);
    }

    protected void updateAttachedLabelSigns() {
        Location loc = getLocation();
        if (loc == null || labelSigns == null || labelSigns.isEmpty()) {
            return;
        }
        Block b = loc.getBlock();
        for (int rotation = 0; rotation < 4; rotation++) {
            if (!labelSigns.get(rotation)) {
                continue;
            }
            BlockFace face = STBUtil.getRotatedFace(getFacing(), rotation);
            String[] text = getSignLabel(face);
            Block b1 = b.getRelative(face);
            if (b1.getType() == Material.WALL_SIGN) {
                Sign sign = (Sign) b1.getState();
                for (int i = 0; i < text.length; i++) {
                    sign.setLine(i, text[i]);
                }
                sign.update();
            } else {
                // no sign here (the sign must have been replaced or broken at some point)
                labelSigns.set(rotation, false);
            }
        }
    }

    private static final String[] faceSymbol = { "▣", "▶", "▼", "◀" };

    protected String makeItemLabel(BlockFace face) {
        int rotation = STBUtil.getFaceRotation(getFacing(), face);
        return rotation == 1 ?
                ChatColor.DARK_BLUE + getItemName() + faceSymbol[rotation] :
                ChatColor.DARK_BLUE + faceSymbol[rotation] + getItemName();
    }

    /**
     * Temporarily override the item display name, just before the item is placed.  The item
     * display name is used as the inventory title for blocks such as the dropper.
     *
     * @param event the block place event
     */
    protected void setInventoryTitle(BlockPlaceEvent event, final String tempTitle) {
        ItemStack inHand = event.getItemInHand();
        final Player player = event.getPlayer();
        ItemMeta meta = inHand.getItemMeta();
        meta.setDisplayName(tempTitle);
        inHand.setItemMeta(meta);
        if (inHand.getAmount() > 1) {
            // any remaining items need to have their proper title restored
            Bukkit.getScheduler().runTask(SensibleToolboxPlugin.getInstance(), new Runnable() {
                @Override
                public void run() {
                    ItemStack inHand = player.getItemInHand();
                    if (inHand.getType() == getMaterial()) {
                        ItemMeta meta = inHand.getItemMeta();
                        if (meta.getDisplayName().equals(tempTitle)) {
                            player.setItemInHand(toItemStack(inHand.getAmount()));
                        }
                    }
                }
            });
        }
    }

    /**
     * Builds the inventory-based GUI for this block.  Override in subclasses.
     *
     * @return the GUI object (may be null if this block doesn't have a GUI)
     */
    protected InventoryGUI createGUI() {
        return null;
    }

    @Override
    public PistonMoveReaction getPistonMoveReaction() {
        return PistonMoveReaction.MOVE;
    }
}
