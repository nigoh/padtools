// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import org.junit.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ProjectSettingsPersistorTest {

    @Test
    public void restoreAndPersist_withNullRoot_isNoOp() {
        AtomicBoolean called = new AtomicBoolean(false);
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> null, () -> {}, () -> called.set(true));
        p.restoreAndPersist(null);
        assertFalse(called.get());
    }

    @Test
    public void saveCurrentProjectSettings_withNullRoot_isNoOp() {
        AtomicBoolean called = new AtomicBoolean(false);
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> null, () -> called.set(true), () -> {});
        p.saveCurrentProjectSettings(null);
        assertFalse(called.get());
    }

    @Test
    public void restoreAndPersist_withNullSetting_doesNotThrow() {
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> null, () -> {}, () -> {});
        // 未知プロジェクト (存在しないディレクトリ) でも例外が出ないこと
        p.restoreAndPersist(new File("/nonexistent/project"));
    }
}
