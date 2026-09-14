package online.yudream.base.plugin.mail.application.dto;

/**
 * 收发信连通性测试结果。
 *
 * @param ok           是否成功
 * @param message      面向管理员的结果说明（失败原因已翻译成中文）
 * @param messageCount IMAP 测试时的收件夹邮件数，SMTP 测试为 null
 */
public record TransportTestResult(boolean ok, String message, Integer messageCount) {

    public static TransportTestResult ok(String message, Integer messageCount) {
        return new TransportTestResult(true, message, messageCount);
    }

    public static TransportTestResult failed(String message) {
        return new TransportTestResult(false, message, null);
    }
}
