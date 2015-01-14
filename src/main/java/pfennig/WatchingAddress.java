package pfennig;

import java.sql.Timestamp;
import java.util.List;
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

import org.bitcoinj.core.Coin;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.QueryParamsMap;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.EbeanServer;
import com.github.kevinsawicki.http.HttpRequest;

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
    @Column(name = "transaction_hash")
    String transactionHash;
    String label;
    Integer confidence;
    Integer chainHeight;
    Timestamp createdAt;
    @Column(name = "paid_at")
    Timestamp paidAt;

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

    public void markAsPaid(String txHash, Timestamp paidAt, Coin coin) {
        this.setTransactionHash(txHash);
        this.setPaidAt(paidAt);
        this.setReceivedSatoshi(coin.value);
        this.save();
        this.sendNotification();
    }

    public boolean sendNotification() {
        if (this.notificationUrl == null || this.notificationUrl.trim().isEmpty())
            return true;

        logger.info("sending notification to: " + this.notificationUrl);
        HttpRequest request = HttpRequest.post(this.notificationUrl);
        request.header("Content-Type", "application/json");
        request.send(this.toJson());

        if (request.ok()) {
            logger.info("notification successful for watching address " + this.getIdentifier());
            return true;
        } else {
            logger.info("notification failed for watching address " + this.getIdentifier());
            return false;
        }
    }
    public void markAsConfirmed(Integer confidence, Integer height) {
        this.setConfidence(confidence);
        this.setChainHeight(height);
        this.save();
        this.sendNotification();
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
        addressJson.put("confidence", this.getConfidence());
        addressJson.put("chain_height", this.getChainHeight());
        addressJson.put("paid_at", (this.getPaidAt() == null ? null : new java.text.SimpleDateFormat("Y-m-d k:M:S Z").format(this.getPaidAt())));
        addressJson.put("paid", (this.getPaidAt() != null));
        addressJson.put("transaction_hash", this.getTransactionHash());

        return addressJson.toJSONString();
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

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Timestamp paidAt) {
        this.paidAt = paidAt;
    }

    public String getAddressHash() {
        return addressHash;
    }

    public void setAddressHash(String addressHash) {
        this.addressHash = addressHash;
    }

    public Integer getChainHeight() {
        return chainHeight;
    }

    public void setChainHeight(Integer chainHeight) {
        this.chainHeight = chainHeight;
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
