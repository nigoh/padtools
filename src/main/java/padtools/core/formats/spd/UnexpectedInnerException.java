package padtools.core.formats.spd;

/**
 * 予期しない内部エラー。
 */
public class UnexpectedInnerException extends ParseErrorException {
    public UnexpectedInnerException(String msg){
        super(
                "Inner error: " + msg,
                "内部エラー:" + msg
        );
    }
}
