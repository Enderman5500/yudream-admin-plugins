package online.yudream.base.plugin.mail.domain.support;

import java.util.UUID;

/** 插件内文档 ID 生成（32 位无横线十六进制，避免与宿主雪花 ID 混淆）。 */
public final class Ids {

    private Ids() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
