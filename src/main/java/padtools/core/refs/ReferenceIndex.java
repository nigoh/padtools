package padtools.core.refs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逆参照インデックス。シンボル (クラス/メソッド/フィールド) → そのシンボルを参照している箇所のマップ。
 *
 * <p>Stage A/B モデルとは独立に保持し、{@link ReferenceIndexBuilder} が Stage B 化したクラスから
 * エントリを追加する。</p>
 *
 * <p>「クラス X を消したら何が壊れるか」「メソッド M を呼んでいるのは誰か」といった
 * Impact Analysis の基盤として使用する。</p>
 */
public final class ReferenceIndex {

    private final Map<ReferenceKey, Set<ReferenceSite>> sitesByKey = new ConcurrentHashMap<>();
    /** 名前解決できなかったシンボル (デバッグ・診断用)。 */
    private final Set<String> unresolved = Collections.synchronizedSet(new LinkedHashSet<>());

    /** 1 件の参照を登録する。 */
    public void addReference(ReferenceKey key, ReferenceSite site) {
        if (key == null || site == null) {
            return;
        }
        sitesByKey
                .computeIfAbsent(key, k -> Collections.synchronizedSet(new LinkedHashSet<>()))
                .add(site);
    }

    /** 名前解決失敗を記録 (FQN に解決できなかった単純名など)。 */
    public void addUnresolved(String symbol) {
        if (symbol != null && !symbol.isEmpty()) {
            unresolved.add(symbol);
        }
    }

    /** 指定キーの参照箇所 (登録順)。該当無しなら空リスト。 */
    public List<ReferenceSite> sites(ReferenceKey key) {
        Set<ReferenceSite> set = sitesByKey.get(key);
        if (set == null) {
            return Collections.emptyList();
        }
        synchronized (set) {
            return new ArrayList<>(set);
        }
    }

    /**
     * クラスを参照している全 site を返す。{@link ReferenceKey#ofClass(String)} に加えて、
     * 同じクラスに属するメソッド/フィールドへの参照も含めて統合した一覧を返す
     * (「このクラスを完全に消したら壊れるすべての地点」を出すための便利メソッド)。
     */
    public List<ReferenceSite> sitesForClass(String fqn) {
        List<ReferenceSite> out = new ArrayList<>();
        for (Map.Entry<ReferenceKey, Set<ReferenceSite>> e : sitesByKey.entrySet()) {
            if (fqn.equals(e.getKey().getOwnerFqn())) {
                synchronized (e.getValue()) {
                    out.addAll(e.getValue());
                }
            }
        }
        return out;
    }

    /** 全シンボルキー (順序は不定)。 */
    public Collection<ReferenceKey> keys() {
        return Collections.unmodifiableCollection(sitesByKey.keySet());
    }

    /** インデックスに登録された参照件数の合計。 */
    public int totalSites() {
        int n = 0;
        for (Set<ReferenceSite> s : sitesByKey.values()) {
            n += s.size();
        }
        return n;
    }

    /** インデックスに登録されたシンボル種数。 */
    public int symbolCount() {
        return sitesByKey.size();
    }

    /** 名前解決できなかったシンボル一覧 (順序保持)。 */
    public List<String> unresolved() {
        synchronized (unresolved) {
            return new ArrayList<>(unresolved);
        }
    }

    /** 全エントリを消去する。 */
    public void clear() {
        sitesByKey.clear();
        unresolved.clear();
    }
}
