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
}
