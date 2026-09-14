package online.yudream.base.plugin.yggc.application.service;

import java.net.URI;

final class UrlSupports {

    private UrlSupports() {
    }

    static String originOf(String url) {
        try {
            URI uri = URI.create(YggcAppService.stripTrailingSlash(url));
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String origin = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() > 0) {
                origin += ":" + uri.getPort();
            }
            return origin;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
