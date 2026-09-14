package online.yudream.base.plugin.mail.infrastructure.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;

/** 把 jakarta.mail 的异常翻译成管理员能看懂的中文原因。 */
final class MailErrors {

    private MailErrors() {
    }

    static String describe(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AuthenticationFailedException) {
                return "认证失败，请检查用户名与密码/授权码";
            }
            current = current.getCause();
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        if (throwable instanceof MessagingException messaging) {
            Exception next = messaging.getNextException();
            if (next != null && next != messaging && next.getMessage() != null && !next.getMessage().isBlank()) {
                return message + "（" + next.getMessage() + "）";
            }
        }
        return message;
    }
}
