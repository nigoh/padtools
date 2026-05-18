package padtools.app.uml;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * クラス図 / パッケージ図の表示範囲を絞り込むためのスコープ指定 (不変)。
 *
 * <p>適用順序:</p>
 * <ol>
 *   <li>{@link #includedModules} に該当するクラスのみ (空ならフィルタなし)</li>
 *   <li>{@link #includedPackages} に該当するクラスのみ (前方一致 + 完全一致、空ならフィルタなし)</li>
 *   <li>{@link #classNameRegex} に simpleName/qualifiedName のいずれかが一致するクラスのみ
 *       (null ならフィルタなし)</li>
 *   <li>{@link #seedQualifiedNames} のクラスを起点に、継承/実装/フィールド型を辿って
 *       {@link #neighborHops} 以内のクラスのみ (空ならフィルタなし)</li>
 *   <li>{@link #maxClasses} を超えるクラスは末尾から切り捨て、警告フッタを出す
 *       (0 以下なら無制限)</li>
 * </ol>
 */
public final class DiagramScope {

    /** 何も絞り込まない既定スコープ。 */
    public static final DiagramScope ALL = new Builder().build();

    private final Set<String> includedPackages;
    private final Set<String> includedModules;
    private final Pattern classNameRegex;
    private final Set<String> seedQualifiedNames;
    private final int neighborHops;
    private final int maxClasses;

    private DiagramScope(Builder b) {
        this.includedPackages = Collections.unmodifiableSet(new LinkedHashSet<>(b.includedPackages));
        this.includedModules = Collections.unmodifiableSet(new LinkedHashSet<>(b.includedModules));
        this.classNameRegex = b.classNameRegex;
        this.seedQualifiedNames = Collections.unmodifiableSet(
                new LinkedHashSet<>(b.seedQualifiedNames));
        this.neighborHops = Math.max(0, b.neighborHops);
        this.maxClasses = b.maxClasses;
    }

    public Set<String> getIncludedPackages() {
        return includedPackages;
    }

    public Set<String> getIncludedModules() {
        return includedModules;
    }

    public Pattern getClassNameRegex() {
        return classNameRegex;
    }

    public Set<String> getSeedQualifiedNames() {
        return seedQualifiedNames;
    }

    public int getNeighborHops() {
        return neighborHops;
    }

    public int getMaxClasses() {
        return maxClasses;
    }

    /** 何のフィルタも持たない (= 全件通過) ならば true。 */
    public boolean isEmpty() {
        return includedPackages.isEmpty()
                && includedModules.isEmpty()
                && classNameRegex == null
                && seedQualifiedNames.isEmpty()
                && maxClasses <= 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** ビルダ。 */
    public static final class Builder {
        private final Set<String> includedPackages = new LinkedHashSet<>();
        private final Set<String> includedModules = new LinkedHashSet<>();
        private Pattern classNameRegex;
        private final Set<String> seedQualifiedNames = new LinkedHashSet<>();
        private int neighborHops;
        private int maxClasses;

        public Builder includePackage(String pkg) {
            if (pkg != null && !pkg.isEmpty()) {
                includedPackages.add(pkg);
            }
            return this;
        }

        public Builder includePackages(Set<String> pkgs) {
            if (pkgs != null) {
                for (String p : pkgs) {
                    includePackage(p);
                }
            }
            return this;
        }

        public Builder includeModule(String module) {
            if (module != null && !module.isEmpty()) {
                includedModules.add(module);
            }
            return this;
        }

        public Builder includeModules(Set<String> mods) {
            if (mods != null) {
                for (String m : mods) {
                    includeModule(m);
                }
            }
            return this;
        }

        public Builder classNameRegex(Pattern p) {
            this.classNameRegex = p;
            return this;
        }

        public Builder classNameRegex(String regex) {
            this.classNameRegex = (regex == null || regex.isEmpty())
                    ? null : Pattern.compile(regex);
            return this;
        }

        public Builder seed(String qualifiedName) {
            if (qualifiedName != null && !qualifiedName.isEmpty()) {
                seedQualifiedNames.add(qualifiedName);
            }
            return this;
        }

        public Builder seeds(Set<String> qns) {
            if (qns != null) {
                for (String q : qns) {
                    seed(q);
                }
            }
            return this;
        }

        public Builder neighborHops(int hops) {
            this.neighborHops = hops;
            return this;
        }

        public Builder maxClasses(int max) {
            this.maxClasses = max;
            return this;
        }

        public DiagramScope build() {
            return new DiagramScope(this);
        }
    }
}
