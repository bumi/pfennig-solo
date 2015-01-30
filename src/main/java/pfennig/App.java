package pfennig;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.SparkBase.setPort;

import java.io.File;
import java.net.URI;

import org.bitcoinj.utils.Fiat;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;

public class App {

    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger(App.class.getName());
        logger.info("WELCOME! starting up");
        String databaseUrl = System.getenv("DATABASE_URL");

        // fall back to localhost
        if (databaseUrl == null) {
            databaseUrl = "postgresql://postgres:@localhost:5432/pfennig";
        }
        logger.info("connecting to DB: " + databaseUrl);

        boolean ddlRun = System.getenv("DATABASE_DDL_RUN") != null && System.getenv("DATABASE_DDL_RUN").equals("1");
        EbeanServer ebeanServer = createEbeanServerFromUrl(databaseUrl, ddlRun);
        Invoice.databaseConnnection = ebeanServer;
        WatchingAddress.databaseConnnection = ebeanServer;
        Payment.databaseConnnection = ebeanServer;

        PriceCalculator.init();

        String environment = System.getenv("BITCOIN_NETWORK");
        if (environment == null) {
            environment = "org.bitcoin.test";
        }
        logger.info("using network " + environment);
        
        String localhost = System.getenv("USE_BITCOIND");
        boolean useLocalhost = localhost != null && localhost.equals("1");

        String walletPath = System.getenv("WALLET_PATH");
        if (walletPath == null) {
            walletPath = "./wallets/main.wallet";
        }
        logger.info("loading wallet from: " + walletPath);

        String rootDir = System.getenv("ROOT_DIR");
        if (rootDir == null) {
            rootDir = "./";
        }
        logger.info("using root directory: " + rootDir);

        long keyBirthday;
        if (System.getenv("WATCHING_KEY") == null) {
            throw new Exception("please provide a watching key as WATCHING_KEY environment varibale");
        }
        String watchingKey = System.getenv("WATCHING_KEY");
        if (System.getenv("WATCHING_KEY_BIRTHDAY") != null) {
            keyBirthday = Long.parseLong(System.getenv("WATCHING_KEY_BIRTHDAY"));
        } else {
            keyBirthday = new java.util.Date().getTime();
        }

        final Treasury treasury = new Treasury(environment, new File(rootDir), useLocalhost);
        treasury.loadWalletFromWatchingKey(watchingKey, new File(walletPath), keyBirthday);
        treasury.start();
        Treasury.instance = treasury;

        String port = System.getenv("PORT");
        if (port != null) {
            setPort(Integer.parseInt(port));
        }

        before("/*", (request, response) -> {
            logger.info(request.requestMethod() + " " + request.pathInfo() + " ip=" + request.ip());
        });

        get("/", (req, res) -> {
            return "ping BestChainHeight=" + treasury.getChainHeight() + " time=" + new java.util.Date().getTime();
        });

        post("/api/invoices", (req, res) -> {
            Invoice invoice = Invoice.fromQueryMap(req.queryMap().get("invoice"));

            invoice.setAddressHash(treasury.freshReceiveAddress());

            res.type("application/json");
            if (invoice.save()) {
                return invoice.toJson();
            } else {
                logger.info("failed to save invoice");
                res.status(422);
                JSONObject error = new JSONObject();
                error.put("error", "invalid input. please check our params");
                return error.toJSONString();
            }
        });
        
        get("/api/invoices/:identifier", (req, res) -> {
            Invoice invoice = Invoice.findByIdentifier(req.params("identifier"));
            res.type("application/json");
            if(invoice == null) {
                res.status(404);
                return "{}";
            } else {
                return invoice.toJson();
            }
        });
        
        post("/api/addresses", (req, res) -> {
            WatchingAddress address = WatchingAddress.fromQueryMap(req.queryMap().get("address"));
            res.type("application/json");
            if (address.save() && treasury.addWatchedAddress(address.getAddressHash())) {
                return address.toJson();
            } else {
                logger.info("failed to save watching address");
                res.status(422);
                JSONObject error = new JSONObject();
                error.put("error", "invalid input. please check our params");
                return error.toJSONString();
            }
        });

        get("/api/addresses/:identifier", (req, res) -> {
            WatchingAddress address = WatchingAddress.findByIdentifier(req.params("identifier"));
            res.type("application/json");
            if (address == null) {
                res.status(404);
                return "{}";
            } else {
                return address.toJson();
            }
        });

        get("/api/price", (req, res) -> {
            String currency = req.queryParams("currency");
            if (currency == null) {
                currency = "EUR";
            }
            long satoshi;
            if (req.queryParams("satoshi") == null) {
                satoshi = new Long(100000000);
            }
            else {
                satoshi = Long.parseLong(req.queryParams("satoshi"));
            }
            Fiat fiat = PriceCalculator.forCurrency(currency).coinToFiat(satoshi);
            JSONObject price = new JSONObject();
            price.put("satoshi", satoshi);
            price.put("value", fiat.value);
            price.put(fiat.currencyCode, fiat.toPlainString());
            price.put("friendly", fiat.toFriendlyString());
            return price.toJSONString();
        });
    }

    private static EbeanServer createEbeanServerFromUrl(String url, boolean ddlRun) throws Exception {
        URI dbUri = new URI(url);
        String dbUsername = null;
        String dbPassword = null;
        String[] userInfo = dbUri.getUserInfo().split(":");
        if (userInfo.length != 0)
            dbUsername = userInfo[0];
        if (userInfo.length > 1)
            dbPassword = userInfo[1];
        Integer dbPort = dbUri.getPort();
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost();
        if (dbPort != -1)
            dbUrl += ":" + dbPort.toString();
        dbUrl += dbUri.getPath();

        ServerConfig config = new ServerConfig();
        config.setName("pfennig");
        DataSourceConfig postgresDb = new DataSourceConfig();
        postgresDb.setDriver("org.postgresql.Driver");
        postgresDb.setUsername(dbUsername);
        if (dbPassword == null) {
            dbPassword = ""; //something not null seems to be required
        }
        postgresDb.setPassword(dbPassword);
        
        postgresDb.setUrl(dbUrl);
        postgresDb.setHeartbeatSql("select 1");
        config.setDataSourceConfig(postgresDb);
        config.setDdlGenerate(true);
        config.setDdlRun(ddlRun);
        config.setDefaultServer(true);

        config.addClass(Invoice.class);
        config.addClass(WatchingAddress.class);
        config.addClass(Payment.class);

        return EbeanServerFactory.create(config);
    }

}
