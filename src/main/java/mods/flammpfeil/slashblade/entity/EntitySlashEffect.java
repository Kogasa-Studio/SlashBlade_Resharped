package mods.flammpfeil.slashblade.entity;

import com.google.common.collect.Lists;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.event.FallHandler;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.EnumSetConverter;
import mods.flammpfeil.slashblade.util.KnockBacks;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class EntitySlashEffect extends Projectile implements IShootable {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.<Integer>defineId(EntitySlashEffect.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.<Integer>defineId(EntitySlashEffect.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ROTATION_OFFSET = SynchedEntityData.<Float>defineId(EntitySlashEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROTATION_ROLL = SynchedEntityData.<Float>defineId(EntitySlashEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BASESIZE = SynchedEntityData.<Float>defineId(EntitySlashEffect.class, EntityDataSerializers.FLOAT);

    private int lifetime = 10;
    private KnockBacks action = KnockBacks.cancel;

    private double damage = 1.0D;

    private boolean cycleHit = false;

    private List<Entity> alreadyHits = Lists.newArrayList();


    public KnockBacks getKnockBack() {
        return action;
    }
    public void setKnockBack(KnockBacks action){
        this.action = action;
    }
    public void setKnockBackOrdinal(int ordinal){
        if(0 <= ordinal && ordinal < KnockBacks.values().length)
            this.action = KnockBacks.values()[ordinal];
        else
            this.action = KnockBacks.cancel;
    }

    public boolean doCycleHit() {
        return cycleHit;
    }

    public void setCycleHit(boolean cycleHit) {
        this.cycleHit = cycleHit;
    }

    public UUID shootingEntity;

    private SoundEvent livingEntitySound = SoundEvents.WITHER_HURT;
    protected SoundEvent getHitEntitySound() {
        return this.livingEntitySound;
    }
    public EntitySlashEffect(EntityType<? extends Projectile> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        this.setNoGravity(true);
        //this.setGlowing(true);
    }

    public static EntitySlashEffect createInstance(PlayMessages.SpawnEntity packet, Level worldIn){
        return new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, worldIn);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(COLOR, 0x3333FF);
        this.entityData.define(FLAGS, 0);

        this.entityData.define(ROTATION_OFFSET, 0.0f);
        this.entityData.define(ROTATION_ROLL, 0.0f);
        this.entityData.define(BASESIZE, 1.0f);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {

        NBTHelper.getNBTCoupler(compound)
                .put("RotationOffset", this.getRotationOffset())
                .put("RotationRoll", this.getRotationRoll())
                .put("BaseSize", this.getBaseSize())
                .put("Color", this.getColor())
                .put("damage", this.damage)
                .put("crit", this.getIsCritical())
                .put("clip", this.isNoClip())
                .put("OwnerUUID", this.shootingEntity)
                .put("Lifetime", this.getLifetime())
                .put("Knockback", this.getKnockBack().ordinal());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        NBTHelper.getNBTCoupler(compound)
                .get("RotationOffset", this::setRotationOffset)
                .get("RotationRoll", this::setRotationRoll)
                .get("BaseSize", this::setBaseSize)
                .get("Color", this::setColor)
                .get("damage",  ((Double v)->this.damage = v), this.damage)
                .get("crit",this::setIsCritical)
                .get("clip",this::setNoClip)
                .get("OwnerUUID",  ((UUID v)->this.shootingEntity = v), true)
                .get("Lifetime",this::setLifetime)
                .get("Knockback", this::setKnockBackOrdinal);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        this.setDeltaMovement(0,0,0);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d0 = this.getBoundingBox().getSize() * 10.0D;
        if (Double.isNaN(d0)) {
            d0 = 1.0D;
        }

        d0 = d0 * 64.0D * getViewScale();
        return distance < d0 * d0;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.setPos(x, y, z);
        this.setRot(yaw, pitch);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(0, 0, 0);
    }

    enum FlagsState {
        Critical,
        NoClip,
        Mute,
        Indirect,
    }

    EnumSet<FlagsState> flags = EnumSet.noneOf(FlagsState.class);
    int intFlags = 0;

    private void setFlags(FlagsState value) {
        this.flags.add(value);
        refreshFlags();
    }
    private void removeFlags(FlagsState value){
        this.flags.remove(value);
        refreshFlags();
    }

    private void refreshFlags(){
        if(this.level.isClientSide){
            int newValue = this.entityData.get(FLAGS).intValue();
            if(intFlags != newValue){
                intFlags = newValue;
                flags = EnumSetConverter.convertToEnumSet(FlagsState.class, intFlags);
            }
        }else{
            int newValue = EnumSetConverter.convertToInt(this.flags);
            if(this.intFlags != newValue) {
                this.entityData.set(FLAGS, newValue);
                this.intFlags = newValue;
            }
        }
    }

    public void setIndirect(boolean value) {
        if(value)
            setFlags(FlagsState.Indirect);
        else
            removeFlags(FlagsState.Indirect);
    }
    public boolean getIndirect() {
        refreshFlags();
        return flags.contains(FlagsState.Indirect);
    }

    public void setMute(boolean value) {
        if(value)
            setFlags(FlagsState.Mute);
        else
            removeFlags(FlagsState.Mute);
    }
    public boolean getMute() {
        refreshFlags();
        return flags.contains(FlagsState.Mute);
    }

    public void setIsCritical(boolean value) {
        if(value)
            setFlags(FlagsState.Critical);
        else
            removeFlags(FlagsState.Critical);
    }
    public boolean getIsCritical() {
        refreshFlags();
        return flags.contains(FlagsState.Critical);
    }
    
    public void setNoClip(boolean value) {
        this.noPhysics = value;
        if(value)
            setFlags(FlagsState.NoClip);
        else
            removeFlags(FlagsState.NoClip);
    }
    //disallowedHitBlock
    public boolean isNoClip() {
        if (!this.level.isClientSide) {
            return this.noPhysics;
        } else {
            refreshFlags();
            return flags.contains(FlagsState.NoClip);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (tickCount == 2){

            if (!getMute())
                this.playSound(SoundEvents.TRIDENT_THROW, 0.80F, 0.625F + 0.1f * this.random.nextFloat());
            else
                this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));

            if(getIsCritical())
                this.playSound(getHitEntitySound(), 0.2F, 0.4F + 0.25f * this.random.nextFloat());
        }

        if(getShooter() != null && !getShooter().isInWaterOrRain() && tickCount < (getLifetime() * 0.75)){
            Vec3 start = this.position();
            Vector4f normal = new Vector4f(1,0,0,1);

            float progress = this.tickCount / (float)lifetime;

            normal.transform(new Quaternion(Vector3f.YP,-this.getYRot() -90, true));
            normal.transform(new Quaternion(Vector3f.ZP,this.getXRot(), true));
            normal.transform(new Quaternion(Vector3f.XP,this.getRotationRoll(), true));
            normal.transform(new Quaternion(Vector3f.YP,140 + this.getRotationOffset() -200.0F * progress, true));

            Vec3 normal3d = new Vec3(normal.x(), normal.y(), normal.z());

            BlockHitResult rayResult = this.getCommandSenderWorld().clip(
                    new ClipContext(
                            start.add(normal3d.scale(1.5)),
                            start.add(normal3d.scale(3)),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.ANY,
                            null));

            if(rayResult.getType() == HitResult.Type.BLOCK){
                FallHandler.spawnLandingParticle(this, rayResult.getLocation(), normal3d , 3);
            }
        }

        if(this.getShooter() != null) {
            AABB bb = this.getBoundingBox();

            //no cyclehit
            if (this.tickCount % 2 == 0) {
                boolean forceHit = true;

                //todo: isCritical = hp direct attack & magic damage & melee damage & armor piercing & event override force hit

                //this::onHitEntity ro KnockBackHandler::setCancel
                List<Entity> hits;
                if(!getIndirect() && getShooter() instanceof LivingEntity) {
                    LivingEntity shooter = (LivingEntity) getShooter();
                    float ratio = (float)damage * (getIsCritical() ? 1.1f : 1.0f);
                    hits = AttackManager.areaAttack(shooter, this.action.action, ratio, forceHit,false, true, alreadyHits);
                }else{
                    hits = AttackManager.areaAttack(this, this.action.action,4.0, forceHit,false, alreadyHits);
                }

                if(!this.doCycleHit())
                    alreadyHits.addAll(hits);
            }
        }

        tryDespawn();

    }

    protected void tryDespawn() {
        if(!this.level.isClientSide){
            if (getLifetime() < this.tickCount)
                this.remove(RemovalReason.DISCARDED);
        }
    }

    public int getColor(){
        return this.getEntityData().get(COLOR);
    }
    public void setColor(int value){
        this.getEntityData().set(COLOR,value);
    }

    public int getLifetime(){
        return Math.min(this.lifetime , 1000);
    }
    public void setLifetime(int value){
        this.lifetime = value;
    }

    public float getRotationOffset(){
        return this.getEntityData().get(ROTATION_OFFSET);
    }
    public void setRotationOffset(float value){
        this.getEntityData().set(ROTATION_OFFSET, value);
    }
    public float getRotationRoll(){
        return this.getEntityData().get(ROTATION_ROLL);
    }
    public void setRotationRoll(float value){
        this.getEntityData().set(ROTATION_ROLL, value);
    }public float getBaseSize(){
        return this.getEntityData().get(BASESIZE);
    }
    public void setBaseSize(float value){
        this.getEntityData().set(BASESIZE, value);
    }

    @Nullable
    @Override
    public Entity getShooter() {
        return this.shootingEntity != null && this.level instanceof ServerLevel ? ((ServerLevel)this.level).getEntity(this.shootingEntity) : null;
    }

    @Override
    public void setShooter(Entity shooter) {
        setOwner(shooter);
    }

    @Override
    public void setOwner(Entity shooter) {
        this.shootingEntity = (shooter != null) ? shooter.getUUID() : null;
    }

    public List<MobEffectInstance> getPotionEffects(){
        List<MobEffectInstance> effects = PotionUtils.getAllEffects(this.getPersistentData());

        if(effects.isEmpty())
            effects.add(new MobEffectInstance(MobEffects.POISON, 1, 1));

        return effects;
    }

    public void setDamage(double damageIn) {
        this.damage = damageIn;
    }

    @Override
    public double getDamage() {
        return this.damage;
    }


    @Nullable
    public EntityHitResult getRayTrace(Vec3 p_213866_1_, Vec3 p_213866_2_) {
        return ProjectileUtil.getEntityHitResult(this.level, this, p_213866_1_, p_213866_2_, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D), (p_213871_1_) -> {
            return !p_213871_1_.isSpectator() && p_213871_1_.isAlive() && p_213871_1_.isPickable() && (p_213871_1_ != this.getShooter());
        });
    }
}
