package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * フィールド宣言情報。クラス図生成に必要な最小限のメタデータを保持する。
 */
public class JavaFieldInfo {

    private String name;
    private String type;
    private Visibility visibility = Visibility.PACKAGE;
    private boolean isStatic;
    private boolean isFinal;
    private final List<String> annotations = new ArrayList<>();
    private String comment;
    private String initializer;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    /** JavaDoc / 直前コメントを整形した文字列。未取得時は null。 */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * フィールド宣言の {@code = <expr>} の右辺を原文文字列で保持する。
     * 末尾の {@code ;} は含まない。代入式が無いフィールド (宣言のみ) では null。
     * VehiclePropertyIds 等の整数定数 {@code public static final int PERF_VEHICLE_SPEED = 291504647;}
     * を後段で解決するのに使う。
     */
    public String getInitializer() {
        return initializer;
    }

    public void setInitializer(String initializer) {
        this.initializer = initializer;
    }
}
