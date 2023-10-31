package de.tum.cit.ase.config;

import de.tum.cit.ase.service.util.ArtemisServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArtemisConfiguration {

    @Value("${artemis.local.url}")
    private String localUrl;

    @Value("${artemis.local.websocket_url}")
    private String localWebsocketUrl;

    @Value("${artemis.local.username_template}")
    private String localUsername;

    @Value("${artemis.local.password_template}")
    private String localPassword;

    @Value("${artemis.local.admin_username}")
    private String localAdminUsername;

    @Value("${artemis.local.admin_password}")
    private String localAdminPassword;

    @Value("${artemis.ts1.url}")
    private String test1Url;

    @Value("${artemis.ts1.websocket_url}")
    private String test1WebsocketUrl;

    @Value("${artemis.ts1.username_template}")
    private String test1Username;

    @Value("${artemis.ts1.password_template}")
    private String test1Password;

    @Value("${artemis.ts1.admin_username}")
    private String test1AdminUsername;

    @Value("${artemis.ts1.admin_password}")
    private String test1AdminPassword;

    @Value("${artemis.ts3.url}")
    private String test3Url;

    @Value("${artemis.ts3.websocket_url}")
    private String test3WebsocketUrl;

    @Value("${artemis.ts3.username_template}")
    private String test3Username;

    @Value("${artemis.ts3.password_template}")
    private String test3Password;

    @Value("${artemis.ts3.admin_username}")
    private String test3AdminUsername;

    @Value("${artemis.ts3.admin_password}")
    private String test3AdminPassword;

    @Value("${artemis.staging.url}")
    private String stagingUrl;

    @Value("${artemis.staging.websocket_url}")
    private String stagingWebsocketUrl;

    @Value("${artemis.staging.username_template}")
    private String stagingUsername;

    @Value("${artemis.staging.password_template}")
    private String stagingPassword;

    @Value("${artemis.staging.admin_username}")
    private String stagingAdminUsername;

    @Value("${artemis.staging.admin_password}")
    private String stagingAdminPassword;

    public String getUrl(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localUrl;
            case TS1 -> test1Url;
            case TS3 -> test3Url;
            case STAGING -> stagingUrl;
        };
    }

    public String getWebsocketUrl(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localWebsocketUrl;
            case TS1 -> test1WebsocketUrl;
            case TS3 -> test3WebsocketUrl;
            case STAGING -> stagingWebsocketUrl;
        };
    }

    public String getUsernameTemplate(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localUsername;
            case TS1 -> test1Username;
            case TS3 -> test3Username;
            case STAGING -> stagingUsername;
        };
    }

    public String getPasswordTemplate(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localPassword;
            case TS1 -> test1Password;
            case TS3 -> test3Password;
            case STAGING -> stagingPassword;
        };
    }

    public String getAdminUsername(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localAdminUsername;
            case TS1 -> test1AdminUsername;
            case TS3 -> test3AdminUsername;
            case STAGING -> stagingAdminUsername;
        };
    }

    public String getAdminPassword(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localAdminPassword;
            case TS1 -> test1AdminPassword;
            case TS3 -> test3AdminPassword;
            case STAGING -> stagingAdminPassword;
        };
    }
}
