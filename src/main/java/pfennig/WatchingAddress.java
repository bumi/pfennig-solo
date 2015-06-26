package pfennig;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import net.sf.oval.ConstraintViolation;
import net.sf.oval.Validator;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.QueryParamsMap;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.EbeanServer;

@Entity
@Table(name = "watching_addresses")
public class WatchingAddress {
    static EbeanServer databaseConnnection;
    static Logger logger = LoggerFactory.getLogger(WatchingAddress.class.getName());

    @Id
    Integer id;

    @NotNull
    @NotEmpty
    @Column(name = "notification_url")
    String notificationUrl;

    @NotNull
    @NotEmpty
    String identifier;

    @NotNull
    @NotEmpty
    @Column(name = "address_hash")
    String addressHash;
    @Column(name = "received_satoshi")
    Long receivedSatoshi;

    String label;

    Timestamp createdAt;

    @Transient
    List<ConstraintViolation> violations;

    public static WatchingAddress findByAddressHash(String addressHash) {
        if (addressHash == null) {
            return null;
        }
        List<WatchingAddress> addresses = Ebean.find(WatchingAddress.class).where().eq("address_hash", addressHash.trim()).setMaxRows(1).findList();
        if (addresses.isEmpty())
            return null;
        return addresses.get(0);
    }

    public static WatchingAddress findByIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        List<WatchingAddress> addresses = Ebean.find(WatchingAddress.class).where().eq("identifier", identifier.trim()).setMaxRows(1).findList();
        if (addresses.isEmpty())
            return null;
        return addresses.get(0);
    }

    public static WatchingAddress fromQueryMap(QueryParamsMap params) {
        WatchingAddress address = new WatchingAddress();

        address.setNotificationUrl(params.get("notification_url").value());
        address.setLabel(params.get("label").value());
        address.setAddressHash(params.get("hash").value());
        return address;
    }

    public boolean sendNotification() {
        if (this.getNotificationUrl() == null || this.getNotificationUrl().trim().isEmpty()) {
            logger.info("no notificationUrl for address: " + this.getIdentifier());
            return true;
        }

        logger.info("sending notification for address " + this.getIdentifier() + " to: " + this.getNotificationUrl());
        if (Utils.sendNotification(this.getNotificationUrl(), this.toJson())) {
            logger.info("notification successful for address " + this.getIdentifier());
            return true;
        } else {
            logger.error("notification failed for address " + this.getIdentifier());
            return true;
        }

    }

    public boolean save() {
        if (this.identifier == null)
            this.identifier = UUID.randomUUID().toString();

        if (this.isValid()) {
            WatchingAddress.databaseConnnection.save(this);
            return true;
        } else {
            return false;
        }
    }

    public boolean isValid() {
        Validator validator = new Validator();
        this.violations = validator.validate(this);
        return this.violations.size() == 0;
    }

    public String toJson() {
        JSONObject addressJson = new JSONObject();
        addressJson.put("identifier", this.getIdentifier());
        addressJson.put("address_hash", this.getAddressHash());

        addressJson.put("label", this.getLabel());
        addressJson.put("transactions", this.getTransactionHashes());

        return addressJson.toJSONString();
    }

    public Map<String, Integer> getTransactionHashes() {
        Map<String, Integer> hashes = new HashMap<String, Integer>();
        for (Payment payment : this.getPayments()) {
            hashes.put(payment.getTransactionHash(), payment.getConfidence());
        }
        return hashes;
    }

    public List<Payment> getPayments() {
        return Payment.findByAddressHash(this.getAddressHash());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getAddressHash() {
        return addressHash;
    }

    public void setAddressHash(String addressHash) {
        this.addressHash = addressHash;
    }

    public Long getReceivedSatoshi() {
        return receivedSatoshi;
    }

    public void setReceivedSatoshi(Long receivedSatoshi) {
        this.receivedSatoshi = receivedSatoshi;
    }

    public List<ConstraintViolation> getViolations() {
        return violations;
    }

    public void setViolations(List<ConstraintViolation> violations) {
        this.violations = violations;
    }
}
