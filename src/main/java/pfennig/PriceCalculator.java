package pfennig;

import java.math.BigDecimal;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kevinsawicki.http.HttpRequest;

public class PriceCalculator {
    public static PriceCalculator EUR;
    public static PriceCalculator USD;
    public static PriceCalculator GBP;

    private String currency;
    private String exchange;
    private ExchangeRate rate;
    private long lastUpdateTime = 0;
    static Integer cacheTime = 5 * 60 * 1000; // in milliseconds
    static Logger logger = LoggerFactory.getLogger(PriceCalculator.class.getName());

    public static ExchangeRate currentExchangeRate(String currency, String exchange) {
        String raw = HttpRequest.get("https://api.bitcoinaverage.com/exchanges/" + currency).body();

        Object obj = JSONValue.parse(raw);
        JSONObject ticker = (JSONObject) obj;
        JSONObject kraken = (JSONObject) ticker.get(exchange);
        JSONObject rates = (JSONObject) kraken.get("rates");
        double price = (Double) rates.get("last");

        // see Fiat.SMALLEST_UNIT_EXPONENT 
        Fiat fiat = Fiat.valueOf(currency, new BigDecimal(price * (10000)).intValue());
        return new ExchangeRate(fiat);
    }
    public void updateExchangeRate() {
        if (!(this.lastUpdateTime < new java.util.Date().getTime() - cacheTime)) {
            return;
        }
        new Thread() {
            @Override
            public void run() {
                try {
                    logger.info("updating bitcoin price");
                    rate = PriceCalculator.currentExchangeRate(currency, exchange);
                    lastUpdateTime = new java.util.Date().getTime();
                } catch (Exception e) {
                    logger.error("error updating exchange rate: " + e.getMessage());
                }
            }
        }.start();
    }

    public static void init() {
        PriceCalculator.EUR = new PriceCalculator("EUR");
        PriceCalculator.USD = new PriceCalculator("USD");
    }

    public static PriceCalculator forCurrency(String currency) {
        if (currency.toLowerCase().equals("eur")) {
            return PriceCalculator.EUR;
        } else if (currency.toLowerCase().equals("usd")) {
            return PriceCalculator.USD;
        } else {
            // until we refactor the exchange price handling to be more configurable we just return EUR as default. - this will cause problems for those who use other/unsupported currencies
            return PriceCalculator.EUR;
        }
    }

    public PriceCalculator(String currency) {
        this.exchange = "kraken";
        this.currency = currency.toUpperCase();
        rate = PriceCalculator.currentExchangeRate(currency, exchange);
        lastUpdateTime = new java.util.Date().getTime();
    }

    public PriceCalculator(String currencyCode, String exchangeName) {
        this.currency = currencyCode.toUpperCase();
        this.exchange = exchangeName.toLowerCase();
        this.updateExchangeRate();
    }

    public Fiat coinToFiat(Coin convertCoin) {
        this.updateExchangeRate();
        return this.rate.coinToFiat(convertCoin);
    }

    public Coin fiatToCoin(Fiat convertFiat) {
        this.updateExchangeRate();
        return this.rate.fiatToCoin(convertFiat);
    }

    public Coin fiatToCoin(String currencyCode, long value) {
        // we expect value to be in cents. the fiat class uses 4 as the SMALLEST_UNIT_EXPONENT thus we add another two digits
        return this.fiatToCoin(Fiat.valueOf(currencyCode, value * 100));
    }

    public Fiat coinToFiat(long satoshis) {
        return this.coinToFiat(Coin.valueOf(satoshis));
    }

}
