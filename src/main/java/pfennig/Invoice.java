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
    Long satoshi;
    @Column(name = "received_satoshi")
    Long receivedSatoshi;

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
    @Column(name = "transaction_hash")
    String transactionHash;
    String label;
    Integer confidence;
    Integer chainHeight;

    @Column(name = "address_hash")
    String addressHash;

    @Transient
    List<ConstraintViolation> violations;


    public static Invoice findByAddressHash(String addressHash) {
        if (addressHash == null) {
            return null;
        }
        List<Invoice> invoices = Ebean.find(Invoice.class).where().eq("address_hash", addressHash.trim()).setMaxRows(1).findList();
        if (invoices.isEmpty())
            return null;
        return invoices.get(0);
    }

    public static Invoice findByIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        List<Invoice> invoices = Ebean.find(Invoice.class).where().eq("identifier", identifier.trim()).setMaxRows(1).findList();
        if (invoices.isEmpty())
            return null;
        return invoices.get(0);
    }

    public static Invoice fromQueryMap(QueryParamsMap params) {
        Invoice invoice = new Invoice();
        logger.debug("new invoice with:" + 
                " notification_url=" + params.get("notification_url").value() + 
                " description=" + params.get("description").value() +
                " order_id=" + params.get("order_id").value() +
                " currency=" + params.get("currency").value() +
 " price=" + params.get("price").value());
        
        try {
            invoice.insertPrice(params.get("price").longValue(), params.get("currency").value());
        } catch (NumberFormatException e) {
        }
        
        invoice.setNotificationUrl(params.get("notification_url").value());
        invoice.setDescription(params.get("description").value());
        invoice.setOrderId(params.get("order_id").value());
        invoice.setLabel(params.get("label").value());
        return invoice;
    }

    public boolean sendNotification() {
        if (this.notificationUrl == null || this.notificationUrl.trim().isEmpty())
            return true;

        HttpRequest request = HttpRequest.post(this.notificationUrl);
        request.header("Content-Type", "application/json");
        request.send(this.toJson());

        if (request.ok()) {
            logger.info("notification successful for invoice#" + this.getIdentifier());
            return true;
        } else {
            logger.error("notification failed for invoice#" + this.getIdentifier());
            return false;
        }
    }

    public void markAsPaid(String txHash, Timestamp paidAt, Coin coin) {
        this.setTransactionHash(txHash);
        System.out.println(this.transactionHash);
        this.setPaidAt(paidAt);
        System.out.println(this.paidAt);
        this.setReceivedSatoshi(coin.value);
        if (this.save())
            this.sendNotification();
        else
            this.logger.error("faild setting invoice as paid invoice #" + this.getIdentifier());

    }

    public void markAsConfirmed(Integer confidence, Integer height) {
        this.setConfidence(confidence);
        this.setChainHeight(height);
        this.save();
        this.sendNotification();
    }

    public void insertPrice(long price, String currency) {
        this.currency = currency;
        this.price = price;
        if (currency.equals("BTC")) {
            this.setSatoshi(price);
        } else {
            this.setSatoshi(PriceCalculator.forCurrency(this.currency).fiatToCoin(currency, price).getValue());
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
        invoiceJson.put("address_hash", this.getAddressHash());

        invoiceJson.put("satoshi", this.getSatoshi());
        invoiceJson.put("price_in_btc", this.getBtcPrice());
        invoiceJson.put("price", this.getPrice());
        invoiceJson.put("currency", this.getCurrency());
        invoiceJson.put("label", this.getLabel());
        invoiceJson.put("orderId", this.getOrderId());
        invoiceJson.put("confidence", this.getConfidence());
        invoiceJson.put("chain_height", this.getChainHeight());
        invoiceJson.put("paid_at", (this.getPaidAt() == null ? null : new java.text.SimpleDateFormat("Y-m-d k:M:S Z").format(this.getPaidAt())));
        invoiceJson.put("paid", (this.getPaidAt() != null));
        invoiceJson.put("transaction_hash", this.getTransactionHash());

        return invoiceJson.toJSONString();
    }

    public Map toMustache() {
        Map vars = new HashMap();
        vars.put("identifier", this.getIdentifier());
        vars.put("satoshi", this.getSatoshi());
        vars.put("price", this.getPrice());
        vars.put("price_in_btc", this.getBtcPrice());
        vars.put("currency", this.getCurrency());
        vars.put("orderId", this.getOrderId());
        vars.put("description", this.getDescription());
        vars.put("address_hash", this.getAddressHash());
        vars.put("label", "label");
        return vars;
    }

    public String getBtcPrice() {
        return Coin.valueOf(this.satoshi).toPlainString();
    }


    public Long getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = new Long(price);
    }

    public Long getSatoshi() {
        return satoshi;
    }

    public void setSatoshi(Long satoshi) {
        this.satoshi = satoshi;
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

    public Long getReceivedSatoshi() {
        return receivedSatoshi;
    }

    public void setReceivedSatoshi(Long receivedSatoshi) {
        this.receivedSatoshi = receivedSatoshi;
    }

    public Integer getChainHeight() {
        return chainHeight;
    }

    public void setChainHeight(Integer chainHeight) {
        this.chainHeight = chainHeight;
    }

    public List<ConstraintViolation> getViolations() {
        return violations;
    }

    public void setViolations(List<ConstraintViolation> violations) {
        this.violations = violations;
    }

}
