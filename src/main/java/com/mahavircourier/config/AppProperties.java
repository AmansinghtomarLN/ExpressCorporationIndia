package com.mahavircourier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Mail mail = new Mail();
    private final Sms sms = new Sms();
    private final Report report = new Report();
    private final Monitor monitor = new Monitor();
    private String companyName = "Express Corporation of India";

    public Mail getMail() {
        return mail;
    }

    public Sms getSms() {
        return sms;
    }

    public Report getReport() {
        return report;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public static class Mail {
        private boolean enabled = true;
        private String from;
        /** Always BCC this address on every outbound application email. */
        private String alwaysBcc = "amansinghtomar2209@gmail.com";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getAlwaysBcc() {
            return alwaysBcc;
        }

        public void setAlwaysBcc(String alwaysBcc) {
            this.alwaysBcc = alwaysBcc;
        }
    }

    public static class Sms {
        private boolean enabled = true;
        private String provider = "twilio";
        private final Twilio twilio = new Twilio();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Twilio getTwilio() {
            return twilio;
        }

        public static class Twilio {
            private String accountSid = "";
            private String authToken = "";
            private String fromNumber = "";

            public String getAccountSid() {
                return accountSid;
            }

            public void setAccountSid(String accountSid) {
                this.accountSid = accountSid;
            }

            public String getAuthToken() {
                return authToken;
            }

            public void setAuthToken(String authToken) {
                this.authToken = authToken;
            }

            public String getFromNumber() {
                return fromNumber;
            }

            public void setFromNumber(String fromNumber) {
                this.fromNumber = fromNumber;
            }

            public boolean isConfigured() {
                return accountSid != null && !accountSid.isBlank()
                        && authToken != null && !authToken.isBlank()
                        && fromNumber != null && !fromNumber.isBlank();
            }
        }
    }

    public static class Report {
        private String email = "amansinghtomar2209@gmail.com";
        private boolean schedulerEnabled = true;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public boolean isSchedulerEnabled() {
            return schedulerEnabled;
        }

        public void setSchedulerEnabled(boolean schedulerEnabled) {
            this.schedulerEnabled = schedulerEnabled;
        }
    }

    public static class Monitor {
        private String alertEmail = "amansinghtomar2209@gmail.com";
        private long delayedThreshold = 1;
        private long unpaidCodThreshold = 1;
        private long failedNotifyThreshold = 1;

        public String getAlertEmail() {
            return alertEmail;
        }

        public void setAlertEmail(String alertEmail) {
            this.alertEmail = alertEmail;
        }

        public long getDelayedThreshold() {
            return delayedThreshold;
        }

        public void setDelayedThreshold(long delayedThreshold) {
            this.delayedThreshold = delayedThreshold;
        }

        public long getUnpaidCodThreshold() {
            return unpaidCodThreshold;
        }

        public void setUnpaidCodThreshold(long unpaidCodThreshold) {
            this.unpaidCodThreshold = unpaidCodThreshold;
        }

        public long getFailedNotifyThreshold() {
            return failedNotifyThreshold;
        }

        public void setFailedNotifyThreshold(long failedNotifyThreshold) {
            this.failedNotifyThreshold = failedNotifyThreshold;
        }
    }
}
