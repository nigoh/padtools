package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * クラス・インタフェース・enum・@interface・AIDL interface の宣言情報。
 */
public class JavaClassInfo {

    /** 種別。 */
    public enum Kind { CLASS, INTERFACE, ENUM, ANNOTATION, AIDL_INTERFACE }

    private String packageName = "";
    private String simpleName = "";
    private Kind kind = Kind.CLASS;
    private final List<String> modifiers = new ArrayList<>();
    private final List<String> annotations = new ArrayList<>();
    private String superClass;
    private final List<String> interfaces = new ArrayList<>();
    private final List<JavaFieldInfo> fields = new ArrayList<>();
    private final List<JavaMethodInfo> methods = new ArrayList<>();
    private String enclosingClass;
    private String aaosCategory;

    /** 完全修飾名。{@code com.foo.Outer.Inner} 形式。 */
    public String getQualifiedName() {
        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.isEmpty()) {
            sb.append(packageName).append('.');
        }
        if (enclosingClass != null && !enclosingClass.isEmpty()) {
            sb.append(enclosingClass).append('.');
        }
        sb.append(simpleName);
        return sb.toString();
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName == null ? "" : packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<JavaFieldInfo> getFields() {
        return fields;
    }

    public List<JavaMethodInfo> getMethods() {
        return methods;
    }

    public boolean isAbstract() {
        return modifiers.contains("abstract") || kind == Kind.INTERFACE
                || kind == Kind.AIDL_INTERFACE;
    }

    public String getEnclosingClass() {
        return enclosingClass;
    }

    public void setEnclosingClass(String enclosingClass) {
        this.enclosingClass = enclosingClass;
    }

    public String getAaosCategory() {
        return aaosCategory;
    }

    public void setAaosCategory(String aaosCategory) {
        this.aaosCategory = aaosCategory;
    }
}
