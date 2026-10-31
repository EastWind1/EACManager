package pers.eastwind.billmanager.common.util;

import org.junit.jupiter.api.Test;

import pers.eastwind.billmanager.attach.util.SafePath;
import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafePathTest {

    private final Path root = Path.of("build", "safe-path-root").toAbsolutePath().normalize();

    @Test
    void shouldResolveInsideRoot() {
        Path target = SafePath.of(root).resolve("bill-1").resolve("a.pdf").toPath();
        assertEquals(root.resolve("bill-1").resolve("a.pdf"), target);
    }

    @Test
    void shouldRejectParentTraversal() {
        assertThrows(BizException.class, () -> SafePath.of(root).resolve("../evil"));
        assertThrows(BizException.class, () -> SafePath.of(root).resolve("bill-1").resolve("../../evil"));
    }

    @Test
    void shouldRejectAbsoluteEscape() {
        Path outside = root.getParent().resolve("evil");
        assertThrows(BizException.class, () -> SafePath.of(root).resolve(outside));
    }
}
