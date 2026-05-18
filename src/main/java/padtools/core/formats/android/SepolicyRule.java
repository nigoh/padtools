package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SELinux ポリシーの 1 ルール ({@code allow A B:class { perm1 perm2 };} 等)。
 *
 * <p>{@code allow}, {@code neverallow}, {@code dontaudit}, {@code auditallow} 等を
 * 同じデータ構造で扱う。{@link #ruleType} に rule の種類を保持する。</p>
 */
public final class SepolicyRule {

    private final String ruleType;
    private final String sourceType;
    private final String targetType;
    private final String objectClass;
    private final List<String> permissions;

    public SepolicyRule(String ruleType, String sourceType, String targetType,
                        String objectClass, List<String> permissions) {
        this.ruleType = ruleType == null ? "" : ruleType;
        this.sourceType = sourceType == null ? "" : sourceType;
        this.targetType = targetType == null ? "" : targetType;
        this.objectClass = objectClass == null ? "" : objectClass;
        this.permissions = permissions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(permissions));
    }

    /** {@code allow} / {@code neverallow} / {@code dontaudit} / {@code auditallow}。 */
    public String getRuleType() {
        return ruleType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getTargetType() {
        return targetType;
    }

    /** {@code binder}, {@code file}, {@code service_manager} 等。 */
    public String getObjectClass() {
        return objectClass;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return ruleType + " " + sourceType + " " + targetType + ":" + objectClass
                + " " + permissions;
    }
}
