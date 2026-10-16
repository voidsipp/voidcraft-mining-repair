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
import org.objectweb.asm.tree.FieldNode;

/**
 * Executes the actual compiled production wrapper, remapping only opaque Minecraft
 * context types to Object. No copied wrapper implementation or Minecraft boot.
 * Native tests still prove actual Mixin integration and real destruction callbacks.
 */
final class CompiledContextTest {
    private Object target;
    private Field state;
    private Field entity;
    private Method wrapper;

    @BeforeEach void loadActualCompiledWrapper() throws Exception {
        String source = "net/voidcraft/wandcompat/mixin/NestedWandBreakMixin";
        String isolated = "net/voidcraft/wandcompat/IsolatedCompiledContext";
        ClassNode parsed = new ClassNode();
        try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(source + ".class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(parsed, 0);
        }
        parsed.access &= ~Opcodes.ACC_ABSTRACT;
        // These reviewed fields are introduced by the upstream Wand mixin in the
        // actual target. Supply just their opaque storage for this isolated test.
        parsed.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "architectural_building_wand$brokenState",
            "Lnet/minecraft/world/level/block/state/BlockState;", null, null));
        parsed.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "architectural_building_wand$brokenEntity",
            "Lnet/minecraft/world/level/block/entity/BlockEntity;", null, null));
        ClassWriter output = new ClassWriter(0);
        parsed.accept(new ClassRemapper(output, new SimpleRemapper(Map.of(
            source, isolated,
            "net/minecraft/server/level/ServerPlayerGameMode", isolated,
            "net/minecraft/core/BlockPos", "java/lang/Object",
            "net/minecraft/world/level/block/state/BlockState", "java/lang/Object",
            "net/minecraft/world/level/block/entity/BlockEntity", "java/lang/Object"
        ))));
        class OwnedLoader extends ClassLoader {
            OwnedLoader() { super(CompiledContextTest.class.getClassLoader()); }
            Class<?> define(byte[] bytes) { return defineClass(isolated.replace('/', '.'), bytes, 0, bytes.length); }
        }
        Class<?> type = new OwnedLoader().define(output.toByteArray());
        target = type.getConstructor().newInstance();
        state = type.getDeclaredField("architectural_building_wand$brokenState");
        entity = type.getDeclaredField("architectural_building_wand$brokenEntity");
        wrapper = type.getDeclaredMethod("voidcraft$preserveNestedWandContext", Object.class, Operation.class);
        state.setAccessible(true);
        entity.setAccessible(true);
        wrapper.setAccessible(true);
    }

    private boolean invoke(Object pos, Operation<Boolean> operation) throws Throwable {
        try {
            return (boolean) wrapper.invoke(target, pos, operation);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private void set(Object block, Object blockEntity) {
        try { state.set(target, block); entity.set(target, blockEntity); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void expect(Object block, Object blockEntity) {
        try { assertSame(block, state.get(target)); assertSame(blockEntity, entity.get(target)); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private boolean call(Object pos, Operation<Boolean> operation) {
        try { return invoke(pos, operation); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new AssertionError(failure); }
    }

    @Test void nestedSuccessPreservesBothCallbacksAndOuterContext() throws Throwable {
        Object outerState = new Object(), innerState = new Object();
        Object outerEntity = new Object(), innerEntity = new Object();
        List<String> callbacks = new ArrayList<>();
        assertTrue(invoke("outer", args -> {
            assertEquals("outer", args[0]);
            set(outerState, outerEntity); // Original Wand HEAD.
            assertTrue(call("inner", innerArgs -> {
                assertEquals("inner", innerArgs[0]);
                set(innerState, innerEntity);
                expect(innerState, innerEntity);
                callbacks.add("inner"); // Original successful RETURN callback.
                set(null, null); // Original Wand clears its shared fields.
                return true;
            }));
            expect(outerState, outerEntity);
            callbacks.add("outer");
            set(null, null);
            return true;
        }));
        assertEquals(List.of("inner", "outer"), callbacks);
        expect(null, null);
    }

    @Test void failedNestedBreakRestoresPreviousContextAndFalseResult() {
        Object savedState = new Object(), savedEntity = new Object();
        set(savedState, savedEntity);
        assertFalse(call("failed", args -> { set(new Object(), null); set(null, null); return false; }));
        expect(savedState, savedEntity);
    }

    @Test void exceptionPropagatesUnchangedAndRestoresBothFields() {
        Object savedState = new Object(), savedEntity = new Object();
        set(savedState, savedEntity);
        RuntimeException originalFailure = new IllegalStateException("upstream callback failed");
        RuntimeException observed = assertThrows(RuntimeException.class,
            () -> call("throw", args -> { set(new Object(), new Object()); throw originalFailure; }));
        assertSame(originalFailure, observed);
        expect(savedState, savedEntity);
    }

    @Test void deeplyNestedBreaksUnwindEveryContextWithoutLeaks() {
        List<Integer> observed = new ArrayList<>();
        nested(24, observed);
        assertEquals(25, observed.size());
        assertEquals(0, observed.getFirst());
        assertEquals(24, observed.getLast());
        expect(null, null);
    }

    private void nested(int depth, List<Integer> observed) {
        Object block = new Object(), blockEntity = new Object();
        assertTrue(call(depth, args -> {
            set(block, blockEntity);
            if (depth > 0) nested(depth - 1, observed);
            expect(block, blockEntity);
            observed.add(depth);
            set(null, null);
            return true;
        }));
    }
}
