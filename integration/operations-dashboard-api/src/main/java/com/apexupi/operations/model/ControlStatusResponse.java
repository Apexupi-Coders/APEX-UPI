package com.apexupi.operations.model;

/**
 * DTO matching the live control status API response.
 */
public class ControlStatusResponse {

    private Toggles toggles;
    private TransactionCounts transactionCounts;
    private ServiceSizes serviceSizes;
    private Infrastructure infrastructure;

    public ControlStatusResponse() {
    }

    public ControlStatusResponse(Toggles toggles, TransactionCounts transactionCounts, ServiceSizes serviceSizes, Infrastructure infrastructure) {
        this.toggles = toggles;
        this.transactionCounts = transactionCounts;
        this.serviceSizes = serviceSizes;
        this.infrastructure = infrastructure;
    }

    public Toggles getToggles() {
        return toggles;
    }

    public void setToggles(Toggles toggles) {
        this.toggles = toggles;
    }

    public TransactionCounts getTransactionCounts() {
        return transactionCounts;
    }

    public void setTransactionCounts(TransactionCounts transactionCounts) {
        this.transactionCounts = transactionCounts;
    }

    public ServiceSizes getServiceSizes() {
        return serviceSizes;
    }

    public void setServiceSizes(ServiceSizes serviceSizes) {
        this.serviceSizes = serviceSizes;
    }

    public Infrastructure getInfrastructure() {
        return infrastructure;
    }

    public void setInfrastructure(Infrastructure infrastructure) {
        this.infrastructure = infrastructure;
    }

    public static class Toggles {
        private boolean npciFailureMode;
        private boolean cbsFailureMode;
        private boolean npciWebhookSuppressed;

        public Toggles() {
        }

        public Toggles(boolean npciFailureMode, boolean cbsFailureMode, boolean npciWebhookSuppressed) {
            this.npciFailureMode = npciFailureMode;
            this.cbsFailureMode = cbsFailureMode;
            this.npciWebhookSuppressed = npciWebhookSuppressed;
        }

        public boolean isNpciFailureMode() {
            return npciFailureMode;
        }

        public void setNpciFailureMode(boolean npciFailureMode) {
            this.npciFailureMode = npciFailureMode;
        }

        public boolean isCbsFailureMode() {
            return cbsFailureMode;
        }

        public void setCbsFailureMode(boolean cbsFailureMode) {
            this.cbsFailureMode = cbsFailureMode;
        }

        public boolean isNpciWebhookSuppressed() {
            return npciWebhookSuppressed;
        }

        public void setNpciWebhookSuppressed(boolean npciWebhookSuppressed) {
            this.npciWebhookSuppressed = npciWebhookSuppressed;
        }
    }

    public static class TransactionCounts {
        private int PENDING;
        private int SUBMITTED;
        private int SUCCESS;
        private int FAILED;
        private int UNKNOWN;
        private int COMPENSATED;

        public TransactionCounts() {
        }

        public TransactionCounts(int PENDING, int SUBMITTED, int SUCCESS, int FAILED, int UNKNOWN, int COMPENSATED) {
            this.PENDING = PENDING;
            this.SUBMITTED = SUBMITTED;
            this.SUCCESS = SUCCESS;
            this.FAILED = FAILED;
            this.UNKNOWN = UNKNOWN;
            this.COMPENSATED = COMPENSATED;
        }

        public int getPENDING() {
            return PENDING;
        }

        public void setPENDING(int PENDING) {
            this.PENDING = PENDING;
        }

        public int getSUBMITTED() {
            return SUBMITTED;
        }

        public void setSUBMITTED(int SUBMITTED) {
            this.SUBMITTED = SUBMITTED;
        }

        public int getSUCCESS() {
            return SUCCESS;
        }

        public void setSUCCESS(int SUCCESS) {
            this.SUCCESS = SUCCESS;
        }

        public int getFAILED() {
            return FAILED;
        }

        public void setFAILED(int FAILED) {
            this.FAILED = FAILED;
        }

        public int getUNKNOWN() {
            return UNKNOWN;
        }

        public void setUNKNOWN(int UNKNOWN) {
            this.UNKNOWN = UNKNOWN;
        }

        public int getCOMPENSATED() {
            return COMPENSATED;
        }

        public void setCOMPENSATED(int COMPENSATED) {
            this.COMPENSATED = COMPENSATED;
        }
    }

    public static class ServiceSizes {
        private int totalTransactions;
        private int idempotencyKeys;
        private int ledgerEntries;

        public ServiceSizes() {
        }

        public ServiceSizes(int totalTransactions, int idempotencyKeys, int ledgerEntries) {
            this.totalTransactions = totalTransactions;
            this.idempotencyKeys = idempotencyKeys;
            this.ledgerEntries = ledgerEntries;
        }

        public int getTotalTransactions() {
            return totalTransactions;
        }

        public void setTotalTransactions(int totalTransactions) {
            this.totalTransactions = totalTransactions;
        }

        public int getIdempotencyKeys() {
            return idempotencyKeys;
        }

        public void setIdempotencyKeys(int idempotencyKeys) {
            this.idempotencyKeys = idempotencyKeys;
        }

        public int getLedgerEntries() {
            return ledgerEntries;
        }

        public void setLedgerEntries(int ledgerEntries) {
            this.ledgerEntries = ledgerEntries;
        }
    }

    public static class Infrastructure {
        private String database;
        private String cache;
        private String messaging;
        private boolean cryptoEnabled;

        public Infrastructure() {
        }

        public Infrastructure(String database, String cache, String messaging, boolean cryptoEnabled) {
            this.database = database;
            this.cache = cache;
            this.messaging = messaging;
            this.cryptoEnabled = cryptoEnabled;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getCache() {
            return cache;
        }

        public void setCache(String cache) {
            this.cache = cache;
        }

        public String getMessaging() {
            return messaging;
        }

        public void setMessaging(String messaging) {
            this.messaging = messaging;
        }

        public boolean isCryptoEnabled() {
            return cryptoEnabled;
        }

        public void setCryptoEnabled(boolean cryptoEnabled) {
            this.cryptoEnabled = cryptoEnabled;
        }
    }
}