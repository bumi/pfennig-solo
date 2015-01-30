package pfennig;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;

import com.google.common.base.Joiner;

public class WalletCli {

    public static void main(String[] args) throws Exception {
        String network = System.getenv("NETWORK");
        if (network == null) {
            network = NetworkParameters.ID_TESTNET;
        }
        NetworkParameters params = NetworkParameters.fromID(network);

        if (params == null) {
            throw new Exception("please provide a NETWORK as environment variable: " + NetworkParameters.ID_MAINNET + ", " + NetworkParameters.ID_TESTNET + ", " + NetworkParameters.ID_REGTEST);
        }
        System.out.println("using " + network);
        try {
            String command = args[0];
            if (command.equals("create")) {
                createWallet(params, args[1]);
            } else if (command.equals("show")) {
                showWallet(params, args[1]);
            } else if (command.equals("send")) {
                transferFunds(params, args[1], args[2], args[3]);
            } else {
                System.out.println("available commands are: ");
                System.out.println("create [wallet file]");
                System.out.println("send [wallet file] [address] [amount in BTC]");
            }
        } catch(ArrayIndexOutOfBoundsException e) {
            System.out.println("commands: create, send");
        }
    }

    public static void showWallet(NetworkParameters params, String file) throws Exception {
        File walletFile = new File(file);
        System.out.println("wallet file: " + walletFile.getAbsolutePath());
        Wallet wallet = Wallet.loadFromFile(walletFile);

        BlockStore chainStore = new MemoryBlockStore(params);
        BlockChain blockChain = new BlockChain(params, chainStore);
        PeerGroup peerGroup = new PeerGroup(params, blockChain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));

        System.out.println("Do you want to download and replay the blockchain? [y|n]");
        BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
        boolean downloadBlockchain = bufferRead.readLine().toLowerCase().equals("y");

        if (downloadBlockchain) {
            System.out.println("Downloading the blockchain in memory. Please be patient.");

            blockChain.addWallet(wallet);
            peerGroup.addWallet(wallet);

            peerGroup.startAsync();
            peerGroup.awaitRunning();
            peerGroup.downloadBlockChain();
        }

        System.out.println(wallet.toString());

        System.out.println("--------------------------------------------------------------------------------------");
        if (downloadBlockchain) {
            System.out.println("Wallet balance: " + wallet.getBalance().toFriendlyString());
        } else {
            System.out.println("Wallet balance: " + wallet.getBalance().toFriendlyString() + " BUT: Blockchain not synced ");
        }
        DeterministicSeed seed = wallet.getKeyChainSeed();
        System.out.println("Seed words are: " + Joiner.on(" ").join(seed.getMnemonicCode()));
        System.out.println("Seed birthday is: " + seed.getCreationTimeSeconds());

        DeterministicKey watchingKey = wallet.getWatchingKey();

        System.out.println("Watching key data: " + watchingKey.serializePubB58());
        System.out.println("Watching key birthday: " + watchingKey.getCreationTimeSeconds());
        System.out.println("Receive address: " + wallet.currentReceiveAddress().toString());
        System.out.println("--------------------------------------------------------------------------------------");
        if (downloadBlockchain) {
            wallet.saveToFile(walletFile);
            peerGroup.stopAsync();
            peerGroup.awaitTerminated();
        }

        System.out.println("DONE...");
    }
    
    public static void transferFunds(NetworkParameters params, String file, String addressHash, String amount) throws Exception {
        Coin value = Coin.parseCoin(amount);
        Address to = new Address(params, addressHash);

        File walletFile = new File(file);
        Wallet wallet = Wallet.loadFromFile(walletFile);

        BlockStore chainStore = new MemoryBlockStore(params);
        BlockChain blockChain = new BlockChain(params, chainStore);
        PeerGroup peerGroup = new PeerGroup(params, blockChain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));

        blockChain.addWallet(wallet);
        peerGroup.addWallet(wallet);

        peerGroup.startAsync();
        peerGroup.awaitRunning();

        try {
            System.out.println("Sending " + value.toFriendlyString() + " to " + to.toString());
            System.out.println("enter Y to confirm: ");
            BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
            boolean confirmTransfer = bufferRead.readLine().toLowerCase().equals("y");
            if (confirmTransfer) {
                Wallet.SendResult result = wallet.sendCoins(peerGroup, to, value);
                Thread.sleep(4000); // wait a bit for the transaction to be propagated
                System.out.println("Coins sent. transaction hash: " + result.tx.getHashAsString());
                System.out.println("Wallet balance: " + wallet.getBalance().toFriendlyString());
                peerGroup.stopAsync();
                peerGroup.awaitTerminated();
            } else {
                System.out.println("ok, canceled");
            }
        } catch (InsufficientMoneyException e) {
            System.out.println("Not enough coins in your wallet. Missing " + e.missing.getValue() + " satoshis are missing (including fees)");
        }
    }

    public static void createWallet(NetworkParameters params, String file) throws Exception {
        System.out.println("Welcome, let's create a new wallet!");
        File walletFile = new File(file);
        System.out.println("Saving wallet to: " + walletFile.getAbsolutePath());
        Wallet wallet = new Wallet(params);
        wallet.saveToFile(walletFile);
        System.out.println("...wallet saved");

        showWallet(params, file);
    }

}
