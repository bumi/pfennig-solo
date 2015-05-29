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

import org.bitcoinj.core.Coin;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.QueryParamsMap;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.annotation.CreatedTimestamp;
import com.github.kevinsawicki.http.HttpRequest;

@Entity
@Table(name = "invoices")
public class Invoice {
    static EbeanServer databaseConnnection;
    static Logger logger = LoggerFactory.getLogger(Invoice.class.getName());

    @Id
    Integer id;

    @NotNull
    @NotEmpty
    Long price; // price is in cent/the smallest unit of currency

    @NotNull
    @NotEmpty
    @Column(name = "satoshi_value")
    Long satoshiValue;

    String currency;
    @Column(name = "notification_url")
    String notificationUrl;
    @Column(name = "order_id")
    String orderId;
    String description;

    @NotNull
    @NotEmpty
    String identifier;

    @CreatedTimestamp
    @Column(name = "created_at")
    Timestamp createdAt;
    @Column(name = "paid_at")
    Timestamp paidAt;
    
    String label;
    
    @Column(name = "address_hash")
    String addressHash;

    @Transient
    List<ConstraintViolation> violations;


    public static Invoice findByAddressHash(String addressHash) {
        return Invoice.findBy("address_hash", addressHash);
    }

    public static Invoice findByIdentifier(String identifier) {
        return Invoice.findBy("identifier", identifier);
    }

    public static Invoice findByOrderId(String orderId) {
        return Invoice.findBy("order_id", orderId);
    }

    public static Invoice findBy(String attribute, String value) {
        if (value == null) {
            return null;
        }
        List<Invoice> invoices = Ebean.find(Invoice.class).where().eq(attribute, value.trim()).setMaxRows(1).findList();
        if (invoices.isEmpty())
            return null;
        return invoices.get(0);
    }

    public static Invoice fromQueryMap(QueryParamsMap params) {
        Invoice invoice = new Invoice();
        logger.debug("new invoice with:" + 
                " notification_url=" + params.get("notification_url").value() + 
                " description=" + params.get("description").value() +
 " orderId=" + params.get("orderId").value() +
                " currency=" + params.get("currency").value() +
 " price=" + params.get("price").value());
        
        try {
            invoice.insertPrice(params.get("price").longValue(), params.get("currency").value());
        } catch (NumberFormatException e) {
        }
        
        invoice.setNotificationUrl(params.get("notification_url").value());
        invoice.setDescription(params.get("description").value());
        invoice.setOrderId(params.get("orderId").value());
        invoice.setLabel(params.get("label").value());
        return invoice;
    }

    public boolean sendNotification() {
        if (this.notificationUrl == null || this.notificationUrl.trim().isEmpty())
            return true;
        logger.info("sending notification to: " + this.notificationUrl);
        HttpRequest request = HttpRequest.post(this.notificationUrl);
        request.header("Content-Type", "application/json");
        request.send(this.toJson());

        if (request.ok()) {
            logger.info("notification successful for invoice#" + this.getIdentifier());
            return true;
        } else {
            logger.error("notification failed for invoice#" + this.getIdentifier());
            logger.info("notification response: " + request.body());
            return false;
        }
    }

    public void insertPrice(long price, String currency) {
        this.currency = currency;
        this.price = price;
        if (currency.equals("BTC")) {
            this.setSatoshiValue(price);
        } else {
            this.setSatoshiValue(PriceCalculator.forCurrency(this.currency).fiatToCoin(currency, price).getValue());
        }
    }

    public boolean save() {
        if (this.identifier == null)
            this.identifier = UUID.randomUUID().toString();
        if (this.isValid()) {
            Invoice.databaseConnnection.save(this);
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
        JSONObject invoiceJson = new JSONObject();
        invoiceJson.put("identifier", this.getIdentifier());
        invoiceJson.put("addressHash", this.getAddressHash());

        invoiceJson.put("satoshi", this.getSatoshi().getValue());
        invoiceJson.put("priceInBtc", this.getBtcPrice());
        invoiceJson.put("price", this.getPrice());
        invoiceJson.put("currency", this.getCurrency());
        invoiceJson.put("receivedSatoshi", this.getReceivedSatoshi().getValue());
        invoiceJson.put("satoshiMissing", this.getMissingSatoshi().getValue());
        invoiceJson.put("btcMissing", this.getMissingSatoshi().toPlainString());

        invoiceJson.put("label", this.getLabel());
        invoiceJson.put("description", this.getDescription());
        invoiceJson.put("orderId", this.getOrderId());
        invoiceJson.put("confirmations", this.getConfidence());
        invoiceJson.put("appearedAt", this.getAppearedAtChainHeight());

        invoiceJson.put("transactions", this.getTransactionHashes());
        invoiceJson.put("status", this.getStatus());
        invoiceJson.put("paid", this.isPaid());

        return invoiceJson.toJSONString();
    }

    public List<Payment> getPayments() {
        return Payment.findByAddressHash(this.getAddressHash());
    }
    
    public String getBtcPrice() {
        return this.getSatoshi().toPlainString();
    }

    public Coin getReceivedSatoshi() {
        Coin sum = Coin.valueOf(0);
        List<Payment> payments = this.getPayments();
        for (Payment payment : payments) {
            sum = sum.add(payment.getReceivedSatoshi());
        }
        return sum;
    }

    /**
     * returns the difference of the amount paid and the received amount.
     * 
     * @return Coin
     */
    public Coin getMissingSatoshi() {
        return this.getSatoshi().subtract(this.getReceivedSatoshi());
    }

    public Map<String, Integer> getTransactionHashes() {
        Map<String,Integer> hashes = new HashMap<String, Integer>();
        for (Payment payment : this.getPayments()) {
            hashes.put(payment.getTransactionHash(), payment.getConfidence());
        }
        return hashes;
    }

    /**
     * returns true if the invoice is paid or paidOver - if the customer pays too much we are still ok with that.
     * 
     * @return boolean
     */
    public boolean isPaid() {
        return "paid".equals(this.getStatus()) || "paidOver".equals(this.getStatus());
    }

    /**
     * returns the status of the invoice depending on the amount received:
     * nothing: invoice is pending
     * exactly the correct amount: paid
     * too little: paidPartial
     * too much: paidOver
     * 
     * @return String - status
     */
    public String getStatus() {
        if (this.getReceivedSatoshi().isZero()) {
            return "pending";
        }
        if (this.getReceivedSatoshi().getValue() == this.getSatoshi().getValue()) {
            return "paid";
        }
        if (this.getReceivedSatoshi().getValue() > this.getSatoshi().getValue()) {
            return "paidOver";
        }
        if (this.getReceivedSatoshi().getValue() < this.getSatoshi().getValue()) {
            return "paidPartial";
        }
        return null;
    }
    
    /**
     * returns the block number of the last received payment. null if it is not yet in a block
     * 
     * @return Integer or null of not yet confirmed
     */
    public Integer getAppearedAtChainHeight() {
        List<Payment> payments = this.getPayments();
        if (payments.size() > 0) {
            return payments.get(payments.size() - 1).getAppearedAtChainHeight();
        }
        return null;
    }

    /**
     * returns the confirmations/confidence of the last received payment. null if it is not yet in a block
     * 
     * @return Integer or null
     */
    public Integer getConfidence() {
        List<Payment> payments = this.getPayments();
        if (payments.size() > 0) {
            return payments.get(payments.size() - 1).getConfidence();
        }
        return null;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = new Long(price);
    }

    public Coin getSatoshi() {
        return Coin.valueOf(this.getSatoshiValue());
    }

    public Long getSatoshiValue() {
        return satoshiValue;
    }

    public void setSatoshiValue(Long satoshi) {
        this.satoshiValue = satoshi;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<ConstraintViolation> getViolations() {
        return violations;
    }

    public void setViolations(List<ConstraintViolation> violations) {
        this.violations = violations;
    }

}
