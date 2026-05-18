package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SELinux の {@code type <name>, <attr1>, <attr2>;} 宣言。
 *
 * <p>{@code domain} 属性を持つ型は AOSP の「実行ドメイン」(プロセスのコンテキスト) に
 * 対応する。コンポーネント図の domain ノード化に使う。</p>
 */
public final class SepolicyType {

    private final String name;
    private final List<String> attributes;

    public SepolicyType(String name, List<String> attributes) {
        this.name = name == null ? "" : name;
        this.attributes = attributes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attributes));
    }

    public String getName() {
        return name;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    /** この型が {@code domain} 属性を持つ ( = 実行ドメイン) か。 */
    public boolean isDomain() {
        return attributes.contains("domain");
    }

    @Override
    public String toString() {
        return name + (attributes.isEmpty() ? "" : " " + attributes);
    }
}
