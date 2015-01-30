package pfennig;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.annotation.CreatedTimestamp;

@Entity
@Table(name = "payments")
public class Payment {
    static EbeanServer databaseConnnection;
    static Logger logger = LoggerFactory.getLogger(Payment.class.getName());

    @Id
    Integer id;

    @NotNull
    @NotEmpty
    @Column(name = "received_satoshi_value")
    Long receivedSatoshiValue;

    // TODO: validate uniqueness
    @Column(name = "transaction_hash")
    String transactionHash;

    Integer appearedAtChainHeight;

    @Column(name = "address_hash")
    String addressHash;

    @CreatedTimestamp
    @Column(name = "created_at")
    Timestamp createdAt;

    // once we have received the payment
    @Column(name = "paid_at")
    Timestamp paidAt;

    @Column(name = "confirmed_at")
    Timestamp confirmedAt;

    @Transient
    List<ConstraintViolation> violations;

    public static List<Payment> findByAddressHash(String addressHash) {
        return Ebean.find(Payment.class).where().eq("address_hash", addressHash.trim()).order("id DESC").findList();
    }

    public static Payment create(String address_hash, Transaction tx, Wallet wallet) {
        Payment payment;

        // check if we already have a payment
        payment = Payment.findByTransactionHash(tx.getHashAsString());
        if (payment != null) {
            return payment;
        }

        payment = new Payment();
        payment.setAddressHash(address_hash);
        if (tx.getConfidence().getConfidenceType() == ConfidenceType.BUILDING) {
            payment.setAppearedAtChainHeight(tx.getConfidence().getAppearedAtChainHeight());
        }
        payment.setTransactionHash(tx.getHashAsString());
        payment.setReceivedSatoshiValue(tx.getValue(wallet).getValue());
        payment.setPaidAt(new Timestamp(new java.util.Date().getTime()));
        payment.save();
        payment.notifyPaid();

        return payment;
    }

    public static Payment findByTransactionHash(String transactionHash) {
        List<Payment> payments = Ebean.find(Payment.class).where().eq("transaction_hash", transactionHash.trim()).setMaxRows(1).findList();
        if (payments.isEmpty()) {
            return null;
        }
        return payments.get(0);
    }

    public void markAsConfirmed(String addressHash, Transaction tx, Wallet wallet) {
        if (tx.getConfidence().getConfidenceType() == ConfidenceType.BUILDING) {
            this.setAppearedAtChainHeight(tx.getConfidence().getAppearedAtChainHeight());
            this.setConfirmedAt(new Timestamp(new java.util.Date().getTime()));
            this.save();
            this.notifyConfirmed();
        }
    }

    public void notifyConfirmed() {
        Invoice invoice = this.getInvoice();
        WatchingAddress watchingAddress = this.getWatchingAddress();
        if (invoice != null) {
            invoice.sendNotification();
        } else if (watchingAddress != null) {
            watchingAddress.sendNotification();
        }
    }
    public void notifyPaid() {
        Invoice invoice = this.getInvoice();
        WatchingAddress watchingAddress = this.getWatchingAddress();
        if (invoice != null) {
            invoice.sendNotification();
        } else if (watchingAddress != null) {
            watchingAddress.sendNotification();
        }
    }

    public boolean save() {
        if (this.isValid()) {
            Payment.databaseConnnection.save(this);
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

    public Invoice getInvoice() {
        return Invoice.findByAddressHash(this.getAddressHash());
    }

    public WatchingAddress getWatchingAddress() {
        return WatchingAddress.findByAddressHash(this.getAddressHash());
    }

    public Integer getConfidence() {
        Integer appearsAt = this.getAppearedAtChainHeight();
        if (appearsAt != null) {
            return Treasury.instance.getChainHeight() - appearsAt + 1; // +1 because we count the first appearance as one confirmation
        }
        return 0;
    }

    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("receivedSatoshi", this.getReceivedSatoshi().getValue());
        attributes.put("transactionHash", this.getTransactionHash());
        attributes.put("addressHash", this.getAddressHash());
        attributes.put("paidAt", (this.getPaidAt() == null ? null : new java.text.SimpleDateFormat("Y-m-d k:M:S Z").format(this.getPaidAt())));
        attributes.put("confirmedAt", (this.getConfirmedAt() == null ? null : new java.text.SimpleDateFormat("Y-m-d k:M:S Z").format(this.getConfirmedAt())));
        attributes.put("confirmations", this.getConfidence());
        attributes.put("appearsAt", this.getAppearedAtChainHeight());

        return attributes;
    }

    public Coin getReceivedSatoshi() {
        return Coin.valueOf(this.getReceivedSatoshiValue());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getReceivedSatoshiValue() {
        return receivedSatoshiValue;
    }

    public void setReceivedSatoshiValue(Long receivedSatoshiValue) {
        this.receivedSatoshiValue = receivedSatoshiValue;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getAppearedAtChainHeight() {
        return appearedAtChainHeight;
    }

    public void setAppearedAtChainHeight(Integer appearedAtChainHeight) {
        this.appearedAtChainHeight = appearedAtChainHeight;
    }

    public String getAddressHash() {
        return addressHash;
    }

    public void setAddressHash(String addressHash) {
        this.addressHash = addressHash;
    }

    public Timestamp getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Timestamp paidAt) {
        this.paidAt = paidAt;
    }

    public Timestamp getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Timestamp confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
