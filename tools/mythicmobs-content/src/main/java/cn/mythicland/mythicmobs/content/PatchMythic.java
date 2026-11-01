package cn.mythicland.mythicmobs.content;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Reproducible, digest-checked adaptation of the operator-supplied MM 4.12.0 binary. */
public final class PatchMythic {
    private static final String SHA256 = "88560e579eac78cf830850ac8ae0b1ac50d994e8139f612c8c5f9250c082a2b5";
    private static final String PREFIX = "io/lumine/xikage/mythicmobs/";
    private static final String HELPER = "cn/mythicland/mythicmobs/content/ContentPaths";
    private static final Set<String> LOADERS = Set.of("loadItems", "loadMobs", "loadSkills", "loadDropTables");
    private static final Set<String> TARGETS = Set.of(
            "io/IOLoader.class", "io/IOHandler.class", "items/ItemManager.class", "mobs/MobManager.class",
            "skills/SkillManager.class", "drops/DropManager.class", "spawning/spawners/SpawnerManager.class",
            "commands/items/ImportCommand.class", "commands/ReloadCommand.class"
    );

    private PatchMythic() {
    }

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        if (!SHA256.equals(digest)) throw new IllegalArgumentException("Unsupported MM input digest: " + digest);
        Path output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        Map<String, Integer> changes = new HashMap<>();
        try (var source = new ZipInputStream(Files.newInputStream(input));
             var target = new ZipOutputStream(Files.newOutputStream(output))) {
            for (ZipEntry entry; (entry = source.getNextEntry()) != null;) {
                byte[] bytes = source.readAllBytes();
                if (entry.getName().equals("plugin.yml"))
                    bytes = (new String(bytes, StandardCharsets.UTF_8) + "\ndepend: [DreamLib]\n").getBytes(StandardCharsets.UTF_8);
                if (entry.getName().startsWith(PREFIX) && TARGETS.contains(entry.getName().substring(PREFIX.length()))) {
                    ClassReader reader = new ClassReader(bytes);
                    ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                    reader.accept(adapter(writer, entry.getName(), changes), 0);
                    bytes = writer.toByteArray();
                }
                put(target, entry.getName(), bytes);
            }
            put(target, HELPER + ".class", Files.readAllBytes(Path.of(args[2]).resolve(HELPER + ".class")));
            put(target, "dreamrpg-content.properties", ("format=direct\nsource-sha256=" + SHA256 + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("directory", 1), Map.entry("defaults", 1), Map.entry("packs", 4), Map.entry("import", 1),
                Map.entry("spawners", 5), Map.entry("reload-save", 1), Map.entry("files", 1), Map.entry("spawner-source", 3),
                Map.entry("mkdir", 1), Map.entry("reload-config-save", 1), Map.entry("spawner-save", 1));
        if (!changes.equals(expected)) {
            Files.delete(output);
            throw new IllegalStateException("MM patch points changed: " + changes + "; expected " + expected);
        }
        System.out.println("Verified MM direct-content adaptation: " + changes);
    }

    private static void put(ZipOutputStream output, String name, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static ClassVisitor adapter(ClassWriter writer, String file, Map<String, Integer> changes) {
        return new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor original = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (file.equals(PREFIX + "spawning/spawners/SpawnerManager.class") && name.equals("saveSpawners") && descriptor.equals("()V")) {
                    original.visitCode();
                    original.visitVarInsn(Opcodes.ALOAD, 0);
                    original.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "saveSpawners", "(L" + PREFIX + "spawning/spawners/SpawnerManager;)V", false);
                    original.visitInsn(Opcodes.RETURN);
                    original.visitMaxs(1, 1);
                    original.visitEnd();
                    changes.merge("spawner-save", 1, Integer::sum);
                    return null;
                }
                if (file.equals(PREFIX + "io/IOHandler.class") && name.equals("getAllFiles") && descriptor.equals("(Ljava/lang/String;)Ljava/util/List;")) {
                    original.visitCode();
                    original.visitVarInsn(Opcodes.ALOAD, 0);
                    original.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "files", descriptor, false);
                    original.visitInsn(Opcodes.ARETURN);
                    original.visitMaxs(1, 1);
                    original.visitEnd();
                    changes.merge("files", 1, Integer::sum);
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9, original) {
                    private boolean packGuard;
                    private final boolean loaderConstructor = file.equals(PREFIX + "io/IOLoader.class") && name.equals("<init>")
                            && descriptor.equals("(Lorg/bukkit/plugin/java/JavaPlugin;Ljava/lang/String;Ljava/lang/String;)V");

                    private void changed(String point) {
                        changes.merge(point, 1, Integer::sum);
                    }

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (!loaderConstructor) return;
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 3);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "directory", "(Lorg/bukkit/plugin/java/JavaPlugin;Ljava/lang/String;)Ljava/lang/String;", false);
                        super.visitVarInsn(Opcodes.ASTORE, 3);
                        changed("directory");
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if ("Packs".equals(value) && LOADERS.contains(name)) packGuard = true;
                        if ("Spawners".equals(value) && file.equals(PREFIX + "spawning/spawners/SpawnerManager.class")) {
                            value = "../DreamRPG-content/spawners";
                            changed("spawners");
                        }
                        if ("Items".equals(value) && file.equals(PREFIX + "commands/items/ImportCommand.class")) {
                            value = "../DreamRPG-content/common/items";
                            changed("import");
                        }
                        if (".txt".equals(value) && file.equals(PREFIX + "io/IOHandler.class")) {
                            value = ".yaml";
                            changed("yaml");
                        }
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                        if (loaderConstructor && owner.equals("java/io/File") && method.equals("mkdir")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "ensureDirectory", "(Ljava/io/File;)Z", false);
                            changed("mkdir");
                            return;
                        }
                        if (loaderConstructor && method.equals("reloadCustomConfig")) {
                            super.visitVarInsn(Opcodes.ALOAD, 3);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "allowDefaults", "(ZLjava/lang/String;)Z", false);
                            changed("defaults");
                        }
                        if (packGuard && owner.equals("java/io/File") && method.equals("exists")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "packExists", "(Ljava/io/File;)Z", false);
                            packGuard = false;
                            changed("packs");
                            return;
                        }
                        if (file.equals(PREFIX + "commands/ReloadCommand.class") && method.equals("resetAndSaveAll")) {
                            super.visitInsn(Opcodes.POP);
                            changed("reload-save");
                            return;
                        }
                        if (file.equals(PREFIX + "commands/ReloadCommand.class") && owner.equals(PREFIX + "io/ConfigManager") && method.equals("save")) {
                            super.visitInsn(Opcodes.POP);
                            changed("reload-config-save");
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                        if (file.equals(PREFIX + "spawning/spawners/SpawnerManager.class")
                                && owner.equals("java/io/File") && method.equals("<init>")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "sourceFile", "(Ljava/io/File;)Ljava/io/File;", false);
                            changed("spawner-source");
                        }
                    }
                };
            }
        };
    }
}
