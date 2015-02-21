package pfennig;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.AbstractWalletEventListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DownloadListener;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.SPVBlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class Treasury {
    public static Treasury instance; //TODO: how to singleton? 
    public String environment;
    private boolean useLocalhost;
    public NetworkParameters params;
    public Wallet wallet;
    private File chainFile;
    private SPVBlockStore chainStore;
    public BlockChain blockChain;
    private PeerGroup peerGroup;
    public File walletFile;
    static Logger logger = LoggerFactory.getLogger(Treasury.class.getName());

    public Treasury(String environment) throws Exception {
        this(environment, new File("."));
    }

    public Treasury(String environment, File directory) throws Exception {
        this(environment, directory, false);
    }

    public Treasury(String environment, File directory, boolean useLocalhost) throws Exception {
        this.useLocalhost = useLocalhost;
        this.environment = environment;
        this.params = this.paramsForEnvironment(environment);
        this.chainFile = new File(directory, "pfennig-" + this.params.getId() + ".spvchain");

        this.chainStore = new SPVBlockStore(params, this.chainFile);
        this.blockChain = new BlockChain(params, this.chainStore);
        this.peerGroup = new PeerGroup(params, this.blockChain);
    }


    public void start() throws Exception {
        if (this.params.getId().equals(NetworkParameters.ID_REGTEST) || this.useLocalhost) {
            InetAddress localhost = InetAddress.getLocalHost();
            PeerAddress localPeer = new PeerAddress(localhost, this.params.getPort());
            this.peerGroup.addAddress(localPeer);
        } else {
            this.peerGroup.addPeerDiscovery(new DnsDiscovery(this.params));
        }

        DownloadListener bListener = new DownloadListener() {
            @Override
            public void doneDownload() {
                logger.info("blockchain downloaded");
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    logger.info("shutting down");
                    wallet.saveToFile(walletFile);
                    logger.info("saved all wallets, BYE");
                } catch (Exception e) {
                    logger.error("error saving wallet");
                }
            }
        });
        this.peerGroup.startAsync();
        this.peerGroup.awaitRunning();
        this.peerGroup.startBlockChainDownload(bListener);
        bListener.await();
    }

    public void loadWalletFromWatchingKey(String watchingKey, File walletFile, long keyBirthday) throws IOException {
        DeterministicKey key = DeterministicKey.deserializeB58(null, watchingKey);
        this.wallet = Wallet.fromWatchingKey(this.params, key, keyBirthday);
        this.walletFile = walletFile;
        this.wallet.saveToFile(this.walletFile);
        this.wallet.autosaveToFile(this.walletFile, 300, TimeUnit.MILLISECONDS, null);
        this.wallet.addEventListener(new Treasury.WalletListener(this.params));
        this.blockChain.addWallet(this.wallet);
        this.peerGroup.addWallet(this.wallet);
    }

    public String freshReceiveAddress() {
        return this.wallet.freshReceiveAddress().toString();
    }

    public boolean addWatchedAddress(String addressHash) {
        try {
            Address address = new Address(this.params, addressHash);
            return this.wallet.addWatchedAddress(address);
        } catch (AddressFormatException e) {
            return false;
        }
    }

    public Integer getChainHeight() {
        return this.blockChain.getBestChainHeight();
    }

    private NetworkParameters paramsForEnvironment(String networkId) {
        if (networkId == null) {
            networkId = NetworkParameters.ID_TESTNET;
        }
        return NetworkParameters.fromID(networkId);
    }

    static class WalletListener extends AbstractWalletEventListener {
        private NetworkParameters params;

        public WalletListener(NetworkParameters params) {
            this.params = params;
        }

        public String addressHashFor(Transaction tx, Wallet wallet) {
            List<TransactionOutput> outputs = tx.getOutputs();
            for (TransactionOutput output : outputs) {
                Script script = output.getScriptPubKey();
                if (output.isMine(wallet) && script.isSentToAddress()) {
                    return script.getToAddress(this.params).toString();
                }
            }
            return null;
        }

        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            logger.info("received transaction: " + tx.getHashAsString() + " value: " + tx.getValue(wallet));

            String addressHash = this.addressHashFor(tx, wallet);
            Payment.create(addressHash, tx, wallet);

            Futures.addCallback(tx.getConfidence().getDepthFuture(2), new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(Transaction result) {
                    // "result" here is the same as "tx" above, but we use it anyway for clarity.
                    String addressHash = WalletListener.this.addressHashFor(result, wallet);

                    Payment payment = Payment.findByTransactionHash(tx.getHashAsString());
                    if (payment != null) {
                        payment.markAsConfirmed(addressHash, result, wallet);
                    } else {
                        logger.info("missing payment for transaction=" + tx.getHashAsString() + " address=" + addressHash);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    // This kind of future can't fail, just rethrow in case something very weird happens.
                    throw new RuntimeException(t);
                }
            });
        }
    }
}
