package org.zmy.observabilityplatform.shared.security.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    private boolean enabled = true;
    private UserAccount readOnly = new UserAccount("viewer", "viewer-local");
    private UserAccount operator = new UserAccount("operator", "operator-local");
    private UserAccount admin = new UserAccount("admin", "admin-local");

    @Getter
    @Setter
    public static class UserAccount {
        private String username;
        private String password;

        public UserAccount() {
        }

        public UserAccount(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
