package com.envyful.api.player.attribute;

import com.envyful.api.player.EnvyPlayer;
import com.envyful.api.player.PlayerManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 *
 * An interface designed for storing specific
 * data for each mod / plugin about a player.
 *
 *
 * @param <A> The manager type
 * @param <B> The API's player type
 * @param <C> The platform's player type
 * @param <D> The player manager's type
 */
public abstract class PlayerAttribute<A, B extends EnvyPlayer<C>, C, D extends PlayerManager<B, C>>
        extends ManagedAttribute<UUID, A> {

    protected final transient D playerManager;

    protected transient B parent;

    protected PlayerAttribute(A manager, D playerManager) {
        super(manager);

        this.playerManager = playerManager;
    }

    public void setParent(B parent) {
        this.parent = parent;
    }

    @Override
    public CompletableFuture<UUID> getId(UUID playerUuid) {
        this.id = playerUuid;
        return CompletableFuture.completedFuture(playerUuid);
    }

    @Override
    public CompletableFuture<UUID> getId() {
        return CompletableFuture.completedFuture(this.id);
    }

    public UUID getUuid() {
        return this.id;
    }

    @Override
    public void save(UUID id) {
        this.id = id;
        this.parent = this.playerManager.getPlayer(this.id);

        if (!this.shouldSave()) {
            return;
        }

        this.save();
    }

    @Override
    public void load(UUID id) {
        this.id = id;
        this.parent = this.playerManager.getPlayer(this.id);

        this.load();
    }
}
