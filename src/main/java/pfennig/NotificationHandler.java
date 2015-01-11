package pfennig;

import java.sql.Timestamp;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;

public class NotificationHandler {
    Transaction tx;
    Wallet wallet;
    String addressHash;
    Invoice invoice;
    WatchingAddress watchingAddress;

    public NotificationHandler(String addressHash, Transaction tx, Wallet wallet) {
        this.tx = tx;
        this.wallet = wallet;
        this.addressHash = addressHash;
        this.invoice = Invoice.findByAddressHash(this.addressHash);
        this.watchingAddress = WatchingAddress.findByAddressHash(this.addressHash);
    }

    public void notifyPaid() {
        if (this.invoice != null)
            invoice.markAsPaid(this.tx.getHashAsString(), new Timestamp(new java.util.Date().getTime()), this.tx.getValue(this.wallet));
        if (this.watchingAddress != null)
            this.watchingAddress.markAsPaid(this.tx.getHashAsString(), new Timestamp(new java.util.Date().getTime()), this.tx.getValue(this.wallet));
    }

    public void notifyConfirmed() {
        if (this.invoice != null)
            invoice.markAsConfirmed(this.tx.getConfidence().getDepthInBlocks(), this.tx.getConfidence().getAppearedAtChainHeight());
        if (this.watchingAddress != null)
            this.watchingAddress.markAsConfirmed(this.tx.getConfidence().getDepthInBlocks(), this.tx.getConfidence().getAppearedAtChainHeight());
    }

}
