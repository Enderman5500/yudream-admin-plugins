package online.yudream.base.plugin.mail.application;

/** 业务「不存在」异常，由接口层统一映射为 404。 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
