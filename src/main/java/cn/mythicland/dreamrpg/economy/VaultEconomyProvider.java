package cn.mythicland.dreamrpg.economy;

import cn.mythicland.dreamrpg.api.CoinService;
import cn.mythicland.dreamrpg.api.CoinTransaction;
import cn.mythicland.dreamrpg.api.InsufficientCoinsException;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Registers DreamRPG's coin ledger as Vault's Economy service without a compile-time Vault
 * dependency.
 */
@InjectComponent
@ListenerComponent
public final class VaultEconomyProvider implements Listener {

    private static final String VAULT_PLUGIN = "Vault";
    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";
    private static final String ECONOMY_RESPONSE_CLASS = "net.milkbowl.vault.economy.EconomyResponse";
    private static final String RESPONSE_TYPE_CLASS =
            "net.milkbowl.vault.economy.EconomyResponse$ResponseType";

    private final JavaPlugin plugin;
    private final CoinService coins;
    private Class<?> economyType;
    private Object provider;

    /**
     * Creates the Vault provider adapter.
     *
     * @param plugin owning plugin
     * @param coins DreamRPG authoritative coin service
     */
    public VaultEconomyProvider(JavaPlugin plugin, CoinService coins) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.coins = Objects.requireNonNull(coins, "coins");
    }

    /**
     * Registers the provider when Vault is installed and rejects an existing economy provider.
     */
    public void register() {
        if (provider != null) return;
        Plugin vault = plugin.getServer().getPluginManager().getPlugin(VAULT_PLUGIN);
        if (vault == null || !vault.isEnabled()) {
            plugin.getLogger().warning(
                    "Vault is unavailable; DreamRPG coins remain active but no Vault economy provider was registered."
            );
            return;
        }
        ClassLoader vaultClassLoader = Objects.requireNonNull(
                vault.getClass().getClassLoader(),
                "Vault class loader"
        );
        try {
            Class<?> resolvedEconomyType = Class.forName(ECONOMY_CLASS, true, vaultClassLoader);
            RegisteredServiceProvider<?> existing = registration(resolvedEconomyType);
            if (existing != null && existing.getProvider() != null) {
                throw new IllegalStateException(
                        "Vault economy provider already exists: "
                                + existing.getProvider().getClass().getName()
                );
            }
            EconomyResponseFactory resolvedResponseFactory = EconomyResponseFactory.load(vaultClassLoader);
            Object resolvedProvider = Proxy.newProxyInstance(
                    vaultClassLoader,
                    new Class<?>[]{resolvedEconomyType},
                    new EconomyInvocationHandler(coins, resolvedResponseFactory)
            );
            registerService(resolvedEconomyType, resolvedProvider);
            economyType = resolvedEconomyType;
            provider = resolvedProvider;
            plugin.getLogger().info("Registered DreamRPG coins as the Vault economy provider.");
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IllegalStateException("Vault economy API is incompatible with DreamRPG", exception);
        }
    }

    /**
     * Rechecks a previously unavailable optional Vault integration.
     */
    public void reload() {
        if (provider == null) register();
    }

    /**
     * Unregisters the DreamRPG provider during plugin shutdown.
     */
    public void unregister() {
        if (provider == null || economyType == null) return;
        unregisterService(economyType, provider);
        provider = null;
        economyType = null;
    }

    /**
     * Rejects an economy provider that appears after DreamRPG's startup check.
     *
     * @param event newly registered Bukkit service
     */
    @EventHandler
    public void onServiceRegistered(ServiceRegisterEvent event) {
        if (provider == null || economyType == null) return;
        RegisteredServiceProvider<?> registration = event.getProvider();
        if (registration.getService() != economyType) return;
        if (registration.getProvider() == provider) return;
        String conflictingProvider = registration.getProvider().getClass().getName();
        unregister();
        plugin.getLogger().severe(
                "A second Vault economy provider was registered after DreamRPG: " + conflictingProvider
                        + "; DreamRPG is being disabled."
        );
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }

    @SuppressWarnings("unchecked")
    private RegisteredServiceProvider<?> registration(Class<?> serviceType) {
        return plugin.getServer().getServicesManager().getRegistration((Class<Object>) serviceType);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerService(Class<?> serviceType, Object service) {
        plugin.getServer().getServicesManager().register(
                (Class) serviceType,
                service,
                plugin,
                ServicePriority.Normal
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void unregisterService(Class<?> serviceType, Object service) {
        plugin.getServer().getServicesManager().unregister((Class) serviceType, service);
    }

    private static final class EconomyInvocationHandler implements InvocationHandler {

        private final CoinService coins;
        private final EconomyResponseFactory responses;

        private EconomyInvocationHandler(
                CoinService coins,
                EconomyResponseFactory responses
        ) {
            this.coins = coins;
            this.responses = responses;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String methodName = method.getName();
            if (method.getDeclaringClass() == Object.class) return invokeObjectMethod(proxy, methodName, arguments);
            return switch (methodName) {
                case "isEnabled" -> true;
                case "getName" -> "DreamRPG";
                case "hasBankSupport" -> false;
                case "fractionalDigits" -> 2;
                case "format" -> format(formatAmount(arguments));
                case "currencyNamePlural", "currencyNameSingular" -> "硬币";
                case "hasAccount", "createPlayerAccount" -> createAccount(arguments);
                case "getBalance" -> balance(account(arguments));
                case "has" -> has(account(arguments), amount(arguments));
                case "depositPlayer" -> adjust(account(arguments), amount(arguments), true);
                case "withdrawPlayer" -> adjust(account(arguments), amount(arguments), false);
                case "getBanks" -> List.of();
                case "createBank", "deleteBank", "bankBalance", "bankHas", "bankWithdraw",
                        "bankDeposit", "isBankOwner", "isBankMember" -> responses.notImplemented(
                        "DreamRPG coins do not support Vault bank accounts"
                );
                default -> throw new UnsupportedOperationException(
                        "Unsupported Vault Economy method: " + method
                );
            };
        }

        private Object invokeObjectMethod(Object proxy, String methodName, Object[] arguments) {
            return switch (methodName) {
                case "toString" -> "DreamRPG Vault Economy provider";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException("Unsupported Object method: " + methodName);
            };
        }

        private boolean createAccount(Object[] arguments) {
            coins.balance(account(arguments));
            return true;
        }

        private double balance(UUID uniqueId) {
            return coins.balance(uniqueId).doubleValue();
        }

        private boolean has(UUID uniqueId, BigDecimal amount) {
            return coins.has(uniqueId, amount);
        }

        private Object adjust(UUID uniqueId, BigDecimal amount, boolean deposit) {
            try {
                CoinTransaction transaction = deposit
                        ? coins.deposit(uniqueId, amount, "Vault")
                        : coins.withdraw(uniqueId, amount, "Vault");
                return responses.success(amount.doubleValue(), transaction.balance().doubleValue());
            } catch (InsufficientCoinsException | IllegalArgumentException exception) {
                return responses.failure(
                        amount.doubleValue(),
                        coins.balance(uniqueId).doubleValue(),
                        exception.getMessage()
                );
            }
        }

        @SuppressWarnings("deprecation")
        private static UUID account(Object[] arguments) {
            if (arguments == null || arguments.length == 0) {
                throw new IllegalArgumentException("Vault Economy account argument is missing");
            }
            Object account = arguments[0];
            if (account instanceof OfflinePlayer player) return player.getUniqueId();
            if (account instanceof String playerName && !playerName.isBlank()) {
                return Bukkit.getOfflinePlayer(playerName).getUniqueId();
            }
            throw new IllegalArgumentException("Vault Economy account must be a player name or OfflinePlayer");
        }

        private static BigDecimal amount(Object[] arguments) {
            if (arguments == null || arguments.length == 0) {
                throw new IllegalArgumentException("Vault Economy amount argument is missing");
            }
            Object value = arguments[arguments.length - 1];
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("Vault Economy amount must be numeric");
            }
            double amount = number.doubleValue();
            if (!Double.isFinite(amount) || amount <= 0.0D) {
                throw new IllegalArgumentException("Vault Economy amount must be finite and positive");
            }
            return BigDecimal.valueOf(amount);
        }

        private static BigDecimal formatAmount(Object[] arguments) {
            if (arguments == null || arguments.length == 0) {
                throw new IllegalArgumentException("Vault Economy format amount is missing");
            }
            Object value = arguments[0];
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("Vault Economy format amount must be numeric");
            }
            double amount = number.doubleValue();
            if (!Double.isFinite(amount)) {
                throw new IllegalArgumentException("Vault Economy format amount must be finite");
            }
            return BigDecimal.valueOf(amount);
        }

        private static String format(BigDecimal amount) {
            DecimalFormat formatter = new DecimalFormat(
                    "#,##0.##",
                    DecimalFormatSymbols.getInstance(Locale.ROOT)
            );
            return formatter.format(amount) + " 硬币";
        }
    }

    private static final class EconomyResponseFactory {

        private final Constructor<?> constructor;
        private final Class<?> responseType;

        private EconomyResponseFactory(Constructor<?> constructor, Class<?> responseType) {
            this.constructor = constructor;
            this.responseType = responseType;
        }

        private static EconomyResponseFactory load(ClassLoader classLoader)
                throws ClassNotFoundException, NoSuchMethodException {
            Class<?> responseClass = Class.forName(ECONOMY_RESPONSE_CLASS, true, classLoader);
            Class<?> responseType = Class.forName(RESPONSE_TYPE_CLASS, true, classLoader);
            return new EconomyResponseFactory(
                    responseClass.getConstructor(double.class, double.class, responseType, String.class),
                    responseType
            );
        }

        private Object success(double amount, double balance) {
            return create(amount, balance, "SUCCESS", null);
        }

        private Object failure(double amount, double balance, String message) {
            return create(amount, balance, "FAILURE", message);
        }

        private Object notImplemented(String message) {
            return create(0.0D, 0.0D, "NOT_IMPLEMENTED", message);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Object create(double amount, double balance, String typeName, String message) {
            try {
                Object type = Enum.valueOf((Class<? extends Enum>) responseType, typeName);
                return constructor.newInstance(amount, balance, type, message);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to create a Vault EconomyResponse", exception);
            }
        }
    }
}
