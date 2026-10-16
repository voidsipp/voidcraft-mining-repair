package net.voidcraft.wandcompat;

import static org.junit.jupiter.api.Assertions.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

/** Executes compiled guard bytecode; only external game/I/O collaborators are replaced. */
public final class CompiledUltimineGuardTest {
    public interface FakeAccessor { List<Object> voidcraft$heldItems(); }
    public static final class FakeCollector implements FakeAccessor {
        final List<Object> items = new ArrayList<>();
        @Override public List<Object> voidcraft$heldItems() { return items; }
    }
    public static final class FakeLazy {
        final FakeCollector collector = new FakeCollector();
        public Object get() { return collector; }
    }
    public static final class FakeHold {
        static boolean unresolved, failPreserve, observedActive;
        static int calls, xp;
        static List<Object> preserved;
        static Throwable originalFailure;
        public static boolean hasUnresolved(Object player) { return unresolved; }
        public static void preserve(Object player, Object pos, List<Object> stacks, int value,
                                    boolean active, Throwable original) {
            calls++;
            observedActive = active;
            preserved = new ArrayList<>(stacks);
            xp = value;
            originalFailure = original;
            if (failPreserve) throw new IllegalStateException("archive unavailable");
            unresolved = true;
        }
    }

    private Object target;
    private Class<?> type;
    private Method wrapper;
    private FakeLazy lazy;

    @BeforeEach void loadActualGuard() throws Exception {
        FakeHold.unresolved = false;
        FakeHold.failPreserve = false;
        FakeHold.calls = 0;
        FakeHold.preserved = null;
        FakeHold.originalFailure = null;
        String source = "net/voidcraft/wandcompat/mixin/UltimineCaptureMixin";
        String isolated = "net/voidcraft/wandcompat/IsolatedUltimineGuard";
        String nested = "net/voidcraft/wandcompat/CompiledUltimineGuardTest$";
        ClassNode node = new ClassNode();
        try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(source + ".class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(node, 0);
        }
        node.access &= ~Opcodes.ACC_ABSTRACT;
        ClassWriter output = new ClassWriter(0);
        node.accept(new ClassRemapper(output, new SimpleRemapper(Map.of(
            source, isolated,
            "net/minecraft/core/BlockPos", "java/lang/Object",
            "net/minecraft/world/level/block/state/BlockState", "java/lang/Object",
            "net/minecraft/world/level/LevelAccessor", "java/lang/Object",
            "net/minecraft/server/level/ServerPlayer", "java/lang/Object",
            "dev/ftb/mods/ftblibrary/util/Lazy", nested + "FakeLazy",
            "dev/ftb/mods/ftbultimine/utils/ItemCollector", nested + "FakeCollector",
            "net/voidcraft/wandcompat/mixin/ItemCollectorAccessor", nested + "FakeAccessor",
            "net/voidcraft/wandcompat/UltimineFailureHold", nested + "FakeHold"
        ))));
        class OwnedLoader extends ClassLoader {
            OwnedLoader() { super(CompiledUltimineGuardTest.class.getClassLoader()); }
            Class<?> define(byte[] bytes) { return defineClass(isolated.replace('/', '.'), bytes, 0, bytes.length); }
        }
        type = new OwnedLoader().define(output.toByteArray());
        target = type.getConstructor().newInstance();
        lazy = new FakeLazy();
        set("tempBlockDropsList", lazy);
        wrapper = type.getDeclaredMethod("voidcraft$releaseCaptureOnFailure", Object.class, Object.class,
            Object.class, Object.class, Operation.class);
        wrapper.setAccessible(true);
    }

    private void set(String name, Object value) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private Object get(String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private boolean call(Operation<Boolean> operation) {
        try { return (boolean) wrapper.invoke(target, "level", "pos", "state", "player", operation); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void startCapture() { set("voidcraft$operationStarted", true); set("isBreakingBlock", true); }

    @Test void successRetainsOriginalCollectorAndReturnValue() {
        Object item = new Object();
        assertTrue(call(args -> {
            startCapture(); lazy.collector.items.add(item); set("isBreakingBlock", false); return true;
        }));
        assertEquals(List.of(item), lazy.collector.items);
        assertEquals(0, FakeHold.calls);
        assertEquals(false, get("isBreakingBlock"));
    }

    @Test void nestedRejectedCallPreservesOuterCapture() {
        startCapture();
        assertFalse(call(args -> false));
        assertEquals(true, get("isBreakingBlock"));
        assertEquals(true, get("voidcraft$operationStarted"));
        assertEquals(0, FakeHold.calls);
    }

    @Test void activeCaptureFailureReleasesFlagPreservesDataAndRethrowsSameError() {
        RuntimeException cause = new IllegalStateException("upstream block callback");
        Object item = new Object();
        RuntimeException observed = assertThrows(RuntimeException.class, () -> call(args -> {
            startCapture(); lazy.collector.items.add(item); set("tempBlockDroppedXp", 7); throw cause;
        }));
        assertSame(cause, observed);
        assertSame(cause, FakeHold.originalFailure);
        assertEquals(false, get("isBreakingBlock"));
        assertEquals(false, get("voidcraft$operationStarted"));
        assertEquals(true, get("voidcraft$heldForReview"));
        assertEquals(List.of(item), FakeHold.preserved);
        assertEquals(List.of(item), lazy.collector.items);
        assertEquals(7, FakeHold.xp);
        assertTrue(FakeHold.observedActive);
        assertFalse(call(args -> { fail("held operation must not clear collector"); return true; }));
    }

    @Test void postFlushFailureIsMarkedAmbiguousAndNeverReplayed() {
        RuntimeException cause = new IllegalStateException("partial flush");
        assertSame(cause, assertThrows(RuntimeException.class, () -> call(args -> {
            startCapture(); lazy.collector.items.add(new Object()); set("isBreakingBlock", false); throw cause;
        })));
        assertFalse(FakeHold.observedActive);
        assertEquals(1, FakeHold.calls);
        assertEquals(false, get("isBreakingBlock"));
    }

    @Test void failureBeforeOwnedCaptureDoesNotSnapshotStaleCollection() {
        lazy.collector.items.add(new Object());
        assertThrows(IllegalStateException.class, () -> call(args -> { throw new IllegalStateException("early"); }));
        assertEquals(0, FakeHold.calls);
        assertEquals(false, get("voidcraft$heldForReview"));
        assertEquals(false, get("isBreakingBlock"));
    }

    @Test void preservationFailureStillReleasesFlagHoldsMemoryAndPreservesOriginalError() {
        FakeHold.failPreserve = true;
        RuntimeException cause = new IllegalStateException("block failed");
        Object item = new Object();
        assertSame(cause, assertThrows(RuntimeException.class, () -> call(args -> {
            startCapture(); lazy.collector.items.add(item); throw cause;
        })));
        assertEquals(1, cause.getSuppressed().length);
        assertEquals(false, get("isBreakingBlock"));
        assertEquals(List.of(item), lazy.collector.items);
        assertFalse(call(args -> { fail("held collector cannot be reused"); return true; }));
    }

    @Test void persistentHoldBlocksNewSingletonFromClearingCapturedState() {
        FakeHold.unresolved = true;
        assertFalse(call(args -> { fail("persistent hold must be respected"); return true; }));
        assertEquals(false, get("isBreakingBlock"));
    }
}
