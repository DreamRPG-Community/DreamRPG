package cn.mythicland.dreamrpg.experience;

import cn.mythicland.dreamrpg.api.*;
import cn.mythicland.dreamrpg.config.ExperienceConfiguration;
import cn.mythicland.dreamrpg.config.ExperienceSettings;
import cn.mythicland.dreamrpg.database.ExperienceStore;
import cn.mythicland.dreamrpg.event.RpgLevelUpEvent;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.lib.storage.VersionedPlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DreamRPG's authoritative experience ledger and vanilla-bar presentation adapter.
 */
@InjectComponent
@ServiceComponent(ExperienceApi.class)
public final class ExperienceService implements ExperienceApi {

    private static final long AUTOSAVE_PERIOD_TICKS = 20L;
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(ExperienceCalculator.SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(ExperienceCalculator.SCALE, RoundingMode.HALF_UP);
    private static final CompletableFuture<Void> COMPLETED_SAVE = CompletableFuture.completedFuture(null);

    private final LibApi lib;
    private final PluginTaskScope tasks;
    private final ExperienceStore store;
    private final ExperienceConfiguration configuration;
    private final Logger logger;
    private final Map<UUID, VersionedPlayerSession<ExperienceState>> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<ExperienceState>> loads = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingNativeExperience = new ConcurrentHashMap<>();
    private final Map<UUID, TimedMultiplier> playerMultipliers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<RegisteredModifier> modifiers = new CopyOnWriteArrayList<>();
    private final Map<UUID, Boolean> readyPlayers = new ConcurrentHashMap<>();
    private volatile TimedMultiplier serverMultiplier;
    private BukkitTask autosaveTask;
    private boolean closed;

    /**
     * Creates the authoritative experience service.
     *
     * @param lib           shared Lib service
     * @param tasks         plugin-owned task scope
     * @param store         persistent experience store
     * @param configuration experience configuration
     * @param logger        DreamRPG logger
     */
    public ExperienceService(
            LibApi lib,
            PluginTaskScope tasks,
            ExperienceStore store,
            ExperienceConfiguration configuration,
            Logger logger
    ) {
        this.lib = Objects.requireNonNull(lib, "lib");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.store = Objects.requireNonNull(store, "store");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("DreamRPG experience requires the main thread");
    }

    private static BigDecimal activeMultiplier(TimedMultiplier timed) {
        if (timed == null || !timed.expiresAt().isAfter(Instant.now())) return ONE;
        return timed.multiplier();
    }

    private static TimedMultiplier timedMultiplier(BigDecimal multiplier, Instant expiresAt) {
        BigDecimal value = Objects.requireNonNull(multiplier, "multiplier");
        Instant expiration = Objects.requireNonNull(expiresAt, "expiresAt");
        if (value.signum() < 0) throw new IllegalArgumentException("multiplier cannot be negative");
        if (!expiration.isAfter(Instant.now())) throw new IllegalArgumentException("expiresAt must be in the future");
        return new TimedMultiplier(value, expiration);
    }

    private static int toVisibleInt(BigDecimal value) {
        if (value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) return Integer.MAX_VALUE;
        if (value.signum() <= 0) return 0;
        return value.setScale(0, RoundingMode.FLOOR).intValue();
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    /**
     * Starts the main-thread autosave loop.
     */
    public void start() {
        ensurePrimaryThread();
        if (closed) throw new IllegalStateException("DreamRPG experience service is closed");
        if (autosaveTask != null) return;
        autosaveTask = tasks.runTimer(
                AUTOSAVE_PERIOD_TICKS,
                AUTOSAVE_PERIOD_TICKS,
                this::flushAllAndReport
        );
    }

    /**
     * Loads a player's experience asynchronously.
     *
     * @param uniqueId player UUID
     * @return loaded state future
     */
    public CompletableFuture<ExperienceState> load(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        if (closed) return CompletableFuture.failedFuture(
                new IllegalStateException("DreamRPG experience service is closed")
        );
        VersionedPlayerSession<ExperienceState> session = sessions.get(uniqueId);
        if (session != null) return CompletableFuture.completedFuture(session.snapshot());
        CompletableFuture<ExperienceState> existing = loads.get(uniqueId);
        if (existing != null) return existing;
        CompletableFuture<ExperienceState> created = new CompletableFuture<>();
        CompletableFuture<ExperienceState> selected = loads.putIfAbsent(uniqueId, created);
        if (selected != null) return selected;
        lib.supplyAsync(() -> loadFromStore(uniqueId)).whenComplete((state, failure) -> {
            if (failure != null) created.completeExceptionally(failure);
            else created.complete(state);
            loads.remove(uniqueId, created);
        });
        return created;
    }

    /**
     * Marks a loaded player ready for experience mutations and synchronizes the vanilla bar.
     *
     * @param player online player whose data has just been applied
     */
    public void activate(Player player) {
        ensurePrimaryThread();
        Player target = Objects.requireNonNull(player, "player");
        UUID uniqueId = target.getUniqueId();
        if (!sessions.containsKey(uniqueId)) {
            throw new IllegalStateException("DreamRPG experience is not loaded: " + uniqueId);
        }
        // Mark ready before draining so queued orbs pass through exactly the same grant path.
        readyPlayers.put(uniqueId, Boolean.TRUE);
        syncPresentation(target);
        Long queued = pendingNativeExperience.remove(uniqueId);
        if (queued != null && queued > 0L) {
            grant(new ExperienceGrantRequest(uniqueId, queued, "orb", "queued-native"));
        }
    }

    /**
     * Queues native experience received while player data is still loading.
     *
     * @param uniqueId player UUID
     * @param amount   native orb amount
     */
    public void queueNativeExperience(UUID uniqueId, long amount) {
        ensurePrimaryThread();
        Objects.requireNonNull(uniqueId, "uniqueId");
        if (amount <= 0L) return;
        pendingNativeExperience.merge(uniqueId, amount, ExperienceService::saturatingAdd);
    }

    /**
     * Returns whether a loaded player can receive experience.
     *
     * @param uniqueId player UUID
     * @return true when the ready gate is open
     */
    @Override
    public boolean isReady(UUID uniqueId) {
        return uniqueId != null && readyPlayers.containsKey(uniqueId);
    }

    /**
     * Discards a player whose asynchronous load was abandoned.
     *
     * @param uniqueId player UUID
     */
    public void discard(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        sessions.remove(uniqueId);
        loads.remove(uniqueId);
        readyPlayers.remove(uniqueId);
        pendingNativeExperience.remove(uniqueId);
    }

    /**
     * Releases a completed player session after its quit save has finished.
     *
     * @param uniqueId player UUID
     */
    public void release(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        sessions.remove(uniqueId);
        loads.remove(uniqueId);
        readyPlayers.remove(uniqueId);
        pendingNativeExperience.remove(uniqueId);
    }

    /**
     * Returns whether a persistent experience session exists.
     *
     * @param uniqueId player UUID
     * @return true when loaded
     */
    public boolean isLoaded(UUID uniqueId) {
        return uniqueId != null && sessions.containsKey(uniqueId);
    }

    /**
     * Flushes one player session asynchronously.
     *
     * @param uniqueId player UUID
     * @return save future
     */
    public CompletableFuture<Void> flush(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        VersionedPlayerSession<ExperienceState> session = sessions.get(uniqueId);
        if (session == null) return COMPLETED_SAVE;
        CompletableFuture<Void> next;
        synchronized (saveChains) {
            CompletableFuture<Void> previous = saveChains.get(uniqueId);
            CompletableFuture<Void> ready = previous == null ? COMPLETED_SAVE : previous;
            next = ready.thenCompose(ignored -> lib.runAsync(() -> saveUntilClean(session)));
            saveChains.put(uniqueId, next);
        }
        next.whenComplete((ignored, failure) -> removeSaveChain(uniqueId, next));
        return next;
    }

    /**
     * Flushes every loaded player session.
     *
     * @return future completed after all saves
     */
    public CompletableFuture<Void> flushAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID uniqueId : sessions.keySet()) futures.add(flush(uniqueId));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Reloads mutable experience settings and reapplies the presentation projection.
     */
    public void reload() {
        ensurePrimaryThread();
        ExperienceSettings settings = configuration.snapshot();
        for (VersionedPlayerSession<ExperienceState> session : sessions.values()) {
            ExperienceState state = session.snapshot();
            if (settings.maxLevel() >= 0L && state.level() >= settings.maxLevel()) {
                BigDecimal fullProgress = BigDecimal.valueOf(
                        ExperienceCurve.requiredForNextLevel(settings.maxLevel())
                );
                if (state.level() == settings.maxLevel()
                        && state.currentExperience().compareTo(fullProgress) == 0) {
                    continue;
                }
                session.replace(state.withProgress(
                        settings.maxLevel(),
                        fullProgress
                ));
            }
        }
        syncAllOnline();
    }

    /**
     * Synchronizes all online ready players' vanilla experience fields.
     */
    public void syncAllOnline() {
        ensurePrimaryThread();
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (isReady(player.getUniqueId())) syncPresentation(player);
        }
    }

    /**
     * Updates Bukkit's level, normalized progress, and compatibility total projection.
     *
     * @param player online target
     */
    public void syncPresentation(Player player) {
        ensurePrimaryThread();
        Player target = Objects.requireNonNull(player, "player");
        ExperienceSnapshot snapshot = snapshot(target.getUniqueId());
        if (!snapshot.ready()) return;
        long level = snapshot.level();
        int visibleLevel = level > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) level;
        if (level > Integer.MAX_VALUE) {
            logger.warning("RPG level exceeds Bukkit's integer presentation range: " + target.getUniqueId());
        }
        target.setLevel(visibleLevel);
        target.setExp((float) snapshot.progress());
        BigDecimal total = ExperienceCurve.totalExperienceAtLevel(level)
                .add(snapshot.currentExperience());
        target.setTotalExperience(toVisibleInt(total));
    }

    /**
     * Returns the current immutable snapshot.
     *
     * @param uniqueId player UUID
     * @return snapshot, marked not ready when no active session exists
     */
    @Override
    public ExperienceSnapshot snapshot(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        VersionedPlayerSession<ExperienceState> session = sessions.get(uniqueId);
        if (session == null) return ExperienceSnapshot.notReady(uniqueId);
        return snapshot(session.snapshot(), isReady(uniqueId));
    }

    /**
     * Grants experience through the authoritative DreamRPG calculation pipeline.
     *
     * @param request grant request
     * @return immutable grant result
     */
    @Override
    public ExperienceGrantResult grant(ExperienceGrantRequest request) {
        ensurePrimaryThread();
        ExperienceGrantRequest value = Objects.requireNonNull(request, "request");
        VersionedPlayerSession<ExperienceState> session = sessions.get(value.uniqueId());
        if (session == null || !isReady(value.uniqueId())) {
            ExperienceSnapshot notReady = snapshot(value.uniqueId());
            return new ExperienceGrantResult(
                    ExperienceGrantResult.Status.NOT_READY,
                    value.uniqueId(),
                    BigDecimal.valueOf(value.baseAmount()),
                    ZERO,
                    notReady.level(),
                    notReady.level(),
                    0L,
                    notReady
            );
        }

        ExperienceState before = session.snapshot();
        ExperienceSnapshot beforeSnapshot = snapshot(before, true);
        BigDecimal requested = BigDecimal.valueOf(value.baseAmount());
        if (value.baseAmount() == 0L) {
            return result(
                    ExperienceGrantResult.Status.NO_EXPERIENCE,
                    value,
                    requested,
                    ZERO,
                    before,
                    before
            );
        }
        if (isCapped(before)) {
            return new ExperienceGrantResult(
                    ExperienceGrantResult.Status.CAPPED,
                    value.uniqueId(),
                    requested,
                    ZERO,
                    before.level(),
                    before.level(),
                    0L,
                    beforeSnapshot
            );
        }

        BigDecimal multiplier = multiplierFor(value, before.level());
        BigDecimal effective = ExperienceCalculator.normalize(requested.multiply(multiplier));
        if (effective.signum() == 0) {
            return result(
                    ExperienceGrantResult.Status.NO_EXPERIENCE,
                    value,
                    requested,
                    ZERO,
                    before,
                    before
            );
        }

        ExperienceCalculator.Calculation calculation = ExperienceCalculator.apply(
                before.level(),
                before.currentExperience(),
                effective,
                configuration.snapshot().maxLevel()
        );
        ExperienceState after = before.withProgress(
                calculation.level(),
                calculation.currentExperience()
        );
        session.replace(after);
        Player player = Bukkit.getPlayer(value.uniqueId());
        if (player != null && player.isOnline()) syncPresentation(player);
        if (calculation.levelsGained() > 0L) {
            Bukkit.getPluginManager().callEvent(new RpgLevelUpEvent(
                    value.uniqueId(),
                    before.level(),
                    calculation.level(),
                    value.source(),
                    value.sourceId()
            ));
        }
        return result(
                calculation.capped()
                        ? ExperienceGrantResult.Status.CAPPED
                        : ExperienceGrantResult.Status.APPLIED,
                value,
                requested,
                effective,
                before,
                after
        );
    }

    /**
     * Registers an external multiplier participant.
     *
     * @param modifier modifier implementation
     * @return unregister handle
     */
    @Override
    public ExperienceMultiplierHandle registerModifier(ExperienceModifier modifier) {
        ExperienceModifier value = Objects.requireNonNull(modifier, "modifier");
        String id = Objects.requireNonNull(value.id(), "modifier.id").trim();
        if (id.isBlank()) throw new IllegalArgumentException("modifier.id cannot be blank");
        RegisteredModifier registered = new RegisteredModifier(value, id);
        modifiers.add(registered);
        modifiers.sort(Comparator.comparingInt((RegisteredModifier item) -> item.modifier().priority())
                .thenComparing(RegisteredModifier::id));
        AtomicBoolean closedHandle = new AtomicBoolean();
        return () -> {
            if (closedHandle.compareAndSet(false, true)) modifiers.remove(registered);
        };
    }

    /**
     * Sets a server-wide timed multiplier.
     *
     * @param multiplier non-negative multiplier
     * @param expiresAt  expiration instant
     */
    @Override
    public void setServerMultiplier(BigDecimal multiplier, Instant expiresAt) {
        ensurePrimaryThread();
        serverMultiplier = timedMultiplier(multiplier, expiresAt);
    }

    /**
     * Sets a player's timed multiplier.
     *
     * @param uniqueId   player UUID
     * @param multiplier non-negative multiplier
     * @param expiresAt  expiration instant
     */
    @Override
    public void setPlayerMultiplier(UUID uniqueId, BigDecimal multiplier, Instant expiresAt) {
        ensurePrimaryThread();
        playerMultipliers.put(
                Objects.requireNonNull(uniqueId, "uniqueId"),
                timedMultiplier(multiplier, expiresAt)
        );
    }

    /**
     * Clears the server-wide timed multiplier.
     */
    @Override
    public void clearServerMultiplier() {
        ensurePrimaryThread();
        serverMultiplier = null;
    }

    /**
     * Clears a player's timed multiplier.
     *
     * @param uniqueId player UUID
     */
    @Override
    public void clearPlayerMultiplier(UUID uniqueId) {
        ensurePrimaryThread();
        playerMultipliers.remove(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    /**
     * Flushes loaded sessions and closes the experience service.
     */
    public void close() {
        ensurePrimaryThread();
        if (closed) return;
        tasks.cancel(autosaveTask);
        autosaveTask = null;
        try {
            flushAll().join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("Failed to flush DreamRPG experience", exception.getCause());
        } finally {
            closed = true;
            sessions.clear();
            loads.clear();
            saveChains.clear();
            readyPlayers.clear();
            pendingNativeExperience.clear();
            playerMultipliers.clear();
            modifiers.clear();
            serverMultiplier = null;
        }
    }

    private ExperienceState loadFromStore(UUID uniqueId) {
        try {
            ExperienceState state = store.loadOrCreate(uniqueId);
            sessions.putIfAbsent(
                    uniqueId,
                    new VersionedPlayerSession<>(uniqueId, state, state.databaseVersion())
            );
            return sessions.get(uniqueId).snapshot();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load DreamRPG experience: " + uniqueId, exception);
        }
    }

    private void saveUntilClean(VersionedPlayerSession<ExperienceState> session) {
        while (true) {
            VersionedPlayerSession.SaveCandidate<ExperienceState> candidate = session.saveCandidate();
            if (!candidate.dirty()) return;
            try {
                long nextVersion = store.save(candidate.snapshot(), candidate.databaseVersion());
                session.completeSave(candidate, nextVersion);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Failed to save DreamRPG experience: " + candidate.uniqueId(),
                        exception
                );
            }
        }
    }

    private void flushAllAndReport() {
        ensurePrimaryThread();
        flushAll().whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(
                        Level.SEVERE,
                        "DreamRPG experience autosave failed: " + LibApi.rootCauseMessage(failure),
                        failure
                );
            }
        });
    }

    private BigDecimal multiplierFor(ExperienceGrantRequest request, long level) {
        BigDecimal result = activeMultiplier(serverMultiplier);
        result = result.multiply(activeMultiplier(playerMultipliers.get(request.uniqueId())));
        ExperienceModifierContext context = new ExperienceModifierContext(
                request.uniqueId(),
                request.source(),
                request.sourceId(),
                level,
                request.metadata()
        );
        for (RegisteredModifier registered : modifiers) {
            try {
                BigDecimal factor = registered.modifier().multiplier(context);
                if (factor == null || factor.signum() < 0) {
                    throw new IllegalArgumentException("multiplier must be non-negative");
                }
                result = result.multiply(factor);
            } catch (RuntimeException exception) {
                logger.log(
                        Level.WARNING,
                        "Ignoring invalid DreamRPG experience modifier '" + registered.id() + "'.",
                        exception
                );
            }
        }
        return ExperienceCalculator.normalize(result.max(ZERO));
    }

    private ExperienceGrantResult result(
            ExperienceGrantResult.Status status,
            ExperienceGrantRequest request,
            BigDecimal requested,
            BigDecimal applied,
            ExperienceState before,
            ExperienceState after
    ) {
        ExperienceSnapshot snapshot = snapshot(after, true);
        return new ExperienceGrantResult(
                status,
                request.uniqueId(),
                requested,
                applied,
                before.level(),
                after.level(),
                after.level() - before.level(),
                snapshot
        );
    }

    private ExperienceSnapshot snapshot(ExperienceState state, boolean ready) {
        ExperienceSettings settings = configuration.snapshot();
        boolean capped = settings.maxLevel() >= 0L && state.level() >= settings.maxLevel();
        long required = ExperienceCurve.requiredForNextLevel(state.level());
        BigDecimal current = ExperienceCalculator.normalize(state.currentExperience());
        double progress = capped
                ? 1.0D
                : Math.min(1.0D, ExperienceCurve.progress(current, state.level()));
        return new ExperienceSnapshot(
                state.uniqueId(),
                state.level(),
                current,
                required,
                progress,
                capped,
                ready
        );
    }

    private boolean isCapped(ExperienceState state) {
        long cap = configuration.snapshot().maxLevel();
        return cap >= 0L && state.level() >= cap;
    }

    private void removeSaveChain(UUID uniqueId, CompletableFuture<Void> chain) {
        synchronized (saveChains) {
            saveChains.remove(uniqueId, chain);
        }
    }

    private record TimedMultiplier(BigDecimal multiplier, Instant expiresAt) {
    }

    private record RegisteredModifier(ExperienceModifier modifier, String id) {
    }
}
