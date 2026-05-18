package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * メソッド宣言情報。
 *
 * <p>シーケンス図生成のため {@link #getCalls()} に「呼び出し先メソッド」を保持できる。</p>
 */
public class JavaMethodInfo {

    /** {@code receiver.method(...)} 形式の呼び出し情報。 */
    public static class Call {
        private final String receiver;
        private final String methodName;

        public Call(String receiver, String methodName) {
            this.receiver = receiver;
            this.methodName = methodName;
        }

        public String getReceiver() {
            return receiver;
        }

        public String getMethodName() {
            return methodName;
        }
    }

    private String name;
    private String returnType;
    private final List<String> parameterTypes = new ArrayList<>();
    private final List<String> parameterNames = new ArrayList<>();
    private Visibility visibility = Visibility.PACKAGE;
    private boolean isStatic;
    private boolean isAbstract;
    private boolean isConstructor;
    private final List<String> annotations = new ArrayList<>();
    private final List<Call> calls = new ArrayList<>();
    private String comment;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getParameterNames() {
        return parameterNames;
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

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public void setConstructor(boolean constructor) {
        isConstructor = constructor;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public List<Call> getCalls() {
        return calls;
    }

    /** JavaDoc / 直前コメントを整形した文字列。未取得時は null。 */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
